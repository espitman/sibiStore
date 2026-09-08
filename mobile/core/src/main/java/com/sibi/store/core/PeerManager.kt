package com.sibi.store.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class PeerDevice(val id:String,val name:String,val platform:String)
data class PeerFile(val id:String,val name:String,val size:Long,val sha256:String)
data class PeerTransfer(val id:String,val senderName:String,val recipientName:String,val incoming:Boolean,val files:List<PeerFile>,val status:String,val bytes:Long=0)
data class PeerState(val peers:List<PeerDevice> = emptyList(),val files:List<PeerFile> = emptyList(),val transfers:List<PeerTransfer> = emptyList(),val busy:Boolean=false,val error:String?=null)

class PeerManager private constructor(private val context:Context) {
    companion object {
        @Volatile private var instance:PeerManager?=null
        fun get(context:Context):PeerManager = instance ?: synchronized(this) { instance ?: PeerManager(context.applicationContext).also { instance=it } }
    }
    private val prefs=context.getSharedPreferences("sibi",Context.MODE_PRIVATE)
    private val records=context.getSharedPreferences("peerFiles",Context.MODE_PRIVATE)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutable=MutableStateFlow(PeerState())
    val state=mutable.asStateFlow()
    private val client=OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(8,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
    private var loop:Job?=null
    private var server:PeerFileServer?=null
    private val jobs=ConcurrentHashMap<String,JSONObject>()
    private val receivers=ConcurrentHashMap<String,Job>()
    private val progress=ConcurrentHashMap<String,Long>()
    @Volatile private var serverIdentity=""
    @Volatile private var lastPollAt=0L
    private val sentBytes=ConcurrentHashMap<String,Long>()
    private val id get()=prefs.getString("deviceId","").orEmpty()
    private val base get()=prefs.getString("url","").orEmpty().trimEnd('/')
    private val serverId get()=prefs.getString("serverId","").orEmpty()
    fun start() {
        if(base.isBlank() || serverId.isBlank() || id.isBlank()) { mutable.update { it.copy(error="Connect to your Mac first") }; return }
        runCatching { ContextCompat.startForegroundService(context,Intent(context,PeerService::class.java)) }
            .onFailure { mutable.update { s->s.copy(error="Open Sibi Store to enable file sharing") } }
    }
    @Synchronized internal fun run() {
        if(loop?.isActive==true)return
        server=PeerFileServer(::source)
        loop=scope.launch {
            while(isActive) {
                try { poll(); mutable.update { it.copy(error=null) } }
                catch(e:CancellationException){throw e}
                catch(e:Exception){mutable.update { it.copy(error=e.message ?: "Could not contact Mac") }}
                delay(3000)
            }
        }
    }
    @Synchronized internal fun stop() {
        loop?.cancel();loop=null
        receivers.values.forEach { it.cancel() };receivers.clear()
        jobs.keys.forEach { PeerService.dismiss(context,it) };jobs.clear()
        server?.close();server=null
        mutable.update { it.copy(peers=emptyList()) }
    }
    private fun api(path:String, body:JSONObject?=null, origin:String=base):JSONObject {
        val platform=when { context.packageName.endsWith(".vr")->"vr";context.packageName.endsWith(".tv")->"tv";else->"phone" }
        val builder=Request.Builder().url("$origin/api/v1/$path")
            .header("X-Device-Id",id).header("X-Device-Token",prefs.getString("deviceToken","").orEmpty())
            .header("X-Device-Capabilities","files-v1,peers-v1").header("X-Device-Platform",platform)
            .header("X-Device-Name","${Build.MANUFACTURER} ${Build.MODEL}".replace(Regex("[^ -~]"),"").take(100))
        if(body!=null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use {
            val response=runCatching { JSONObject(it.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            require(it.isSuccessful) {
                val detail=response.optString("error").take(300)
                if(it.code==404 && detail in listOf("", "Not Found")) "Update the Mac app to enable direct file sharing"
                else detail.ifBlank { "File sharing request failed (${it.code})" }
            }
            response
        }
    }
    private val pollLock=Mutex()
    private suspend fun poll() = pollLock.withLock {
        val current=serverId
        if(current.isBlank())return@withLock
        if(serverIdentity!=current) { receivers.values.forEach { it.cancel() };jobs.keys.forEach { PeerService.dismiss(context,it) };jobs.clear();serverIdentity=current }
        api("peers/register",JSONObject().put("port",server!!.port))
        val list=api("peers")
        require(list.getString("serverId")==current) { "Mac identity changed" }
        val peers=list.getJSONArray("peers").objects().map { PeerDevice(it.getString("id"),it.getString("name"),it.getString("platform")) }
        val response=api("peer-transfers")
        require(response.getString("serverId")==current) { "Mac identity changed" }
        lastPollAt=System.currentTimeMillis()
        val offers=response.getJSONArray("transfers").objects()
        val live=offers.map { it.getString("id") }.toSet()
        jobs.keys.filter { it !in live }.forEach { jobs.remove(it);receivers.remove(it)?.cancel();PeerService.dismiss(context,it) }
        val views=offers.map { offer ->
            val key=offer.getString("id");jobs[key]=offer
            val view=view(offer)
            if(view.incoming && view.status=="pending") PeerService.request(context,view)
            else PeerService.dismiss(context,key)
            if(view.incoming && view.status=="accepted" && receivers[key]?.isActive!=true) {
                receivers[key]=scope.launch { receive(offer,base,current) }
            } else if(view.status !in listOf("pending","accepted")) receivers.remove(key)?.cancel()
            view
        }
        mutable.update { it.copy(peers=peers,transfers=views) }
        cleanupSources(offers)
    }
    private fun cleanupSources(offers:List<JSONObject>) {
        if(mutable.value.busy)return
        val keep=mutable.value.files.map { it.id }.toMutableSet()
        offers.filter { it.optString("status") in listOf("pending","accepted") && it.getJSONObject("sender").getString("id")==id }
            .forEach { o->o.getJSONArray("files").objects().forEach { keep.add(it.getString("id")) } }
        val removed=records.all.filterKeys { it.startsWith("source:") && it.removePrefix("source:") !in keep }
        if(removed.isEmpty())return
        val edit=records.edit();removed.keys.forEach { edit.remove(it) };if(!edit.commit())return
        val retained=records.all.filterKeys { it.startsWith("source:") }.values.toSet()
        removed.values.filterIsInstance<String>().filter { it !in retained }.distinct().forEach { uri->
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(uri),Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
    }
    private fun view(o:JSONObject):PeerTransfer {
        val key=o.getString("id")
        return PeerTransfer(key,o.getJSONObject("sender").getString("name"),o.getJSONObject("recipient").getString("name"),o.getJSONObject("recipient").getString("id")==id,
            o.getJSONArray("files").objects().map(::file),o.getString("status"),if(o.getString("status")=="completed") o.getJSONArray("files").objects().sumOf { it.getLong("size") } else progress[key] ?: 0)
    }
    private fun file(o:JSONObject)=PeerFile(o.getString("id"),safeInboxName(o.getString("name")),o.getLong("size"),o.getString("sha256"))
    fun chooseFiles(uris:List<Uri>) {
        if(mutable.value.busy)return
        mutable.update { it.copy(busy=true,error=null) }
        scope.launch {
            try {
                require(uris.isNotEmpty() && uris.size<=100) { "Choose 1–100 files" }
                val files=uris.distinct().map { uri ->
                    var name="Shared file"
                    context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { c->if(c.moveToFirst()) name=c.getString(0) ?: name }
                    val digest=MessageDigest.getInstance("SHA-256");var size=0L
                    context.contentResolver.openInputStream(uri)!!.use { input->val b=ByteArray(65536);while(true){ensureActive();val n=input.read(b);if(n<0)break;size+=n;digest.update(b,0,n)} }
                    val f=PeerFile(UUID.randomUUID().toString(),safeInboxName(name),size,digest.digest().joinToString(""){"%02x".format(it)})
                    check(records.edit().putString("source:${f.id}",uri.toString()).commit())
                    f
                }
                mutable.update { it.copy(files=files) }
            } catch(e:Exception){mutable.update { it.copy(error=e.message ?: "Could not read files") }}
            finally {
                val retained=records.all.filterKeys { it.startsWith("source:") }.values.toSet()
                uris.filter { it.toString() !in retained }.forEach { uri->runCatching { context.contentResolver.releasePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                mutable.update { it.copy(busy=false) }
            }
        }
    }
    fun send(recipientIds:Set<String>) {
        if(mutable.value.busy)return
        val files=mutable.value.files
        mutable.update { it.copy(busy=true,error=null) }
        scope.launch {
            try {
                require(files.isNotEmpty() && recipientIds.isNotEmpty()) { "Choose files and recipients" }
                require(files.size*recipientIds.size<=1000) { "Send at most 1,000 file/device pairs" }
                val port=server?.port ?: error("Enable file sharing first")
                for(recipient in recipientIds) {
                    val array=JSONArray();files.forEach { array.put(JSONObject().put("id",it.id).put("name",it.name).put("size",it.size).put("sha256",it.sha256)) }
                    api("peer-transfers",JSONObject().put("recipientId",recipient).put("port",port).put("files",array)
                        .put("clientRequestId",localOfferKey(recipient,files.map { it.id }.sorted().joinToString(":"))))
                }
                mutable.update { it.copy(files=emptyList()) };poll()
            }catch(e:Exception){mutable.update { it.copy(error=e.message ?: "Could not send request") }}
            finally{mutable.update { it.copy(busy=false) }}
        }
    }
    fun decide(id:String,accept:Boolean) { scope.launch {
        try { api("peer-transfers/$id/decision",JSONObject().put("accept",accept));PeerService.dismiss(context,id);poll() }
        catch(e:Exception){mutable.update { it.copy(error=e.message ?: "Could not answer request") }}
    } }
    fun cancel(id:String) { scope.launch {
        try { val incoming=jobs[id]?.getJSONObject("recipient")?.getString("id")==this@PeerManager.id
            api("peer-transfers/$id/status",JSONObject().put("status",if(incoming)"failed" else "cancelled").put("error","Cancelled"));poll()
        }catch(e:Exception){mutable.update { it.copy(error=e.message ?: "Could not cancel") }}
    } }
    private fun source(path:String):PeerSource? {
        val parts=path.split('/');if(parts.size!=4 || parts[1]!="peer")return null
        val job=jobs[parts[2]] ?: return null
        if(job.getJSONObject("sender").getString("id")!=id || job.getString("status")!="accepted")return null
        val item=job.getJSONArray("files").objects().find { it.getString("id")==parts[3] } ?: return null
        val uri=records.getString("source:${parts[3]}",null)?.let(Uri::parse) ?: return null
        return PeerSource(item.getLong("size"),item.getString("sha256"),item.getString("capabilityToken"),{context.contentResolver.openInputStream(uri) ?: error("File is unavailable")},
            {serverIdentity==serverId && System.currentTimeMillis()-lastPollAt<15000 && jobs[parts[2]]?.optString("status")=="accepted"}, { bytes->
                sentBytes["${parts[2]}:${parts[3]}"]=bytes
                val total=job.getJSONArray("files").objects().sumOf { sentBytes["${parts[2]}:${it.getString("id")}"] ?: 0L }
                progress[parts[2]]=total
            })
    }
    private suspend fun receive(o:JSONObject,origin:String,identity:String) {
        val key=o.getString("id");val root=File(context.filesDir,"peer-inbox").apply { mkdirs() }
        val sender=o.getJSONObject("sender")
        var completed=0L
        try {
            for(item in o.getJSONArray("files").objects()) {
                currentCoroutineContext().ensureActive()
                val f=file(item);val local=localOfferKey(identity,"$key:${f.id}")
                val partial=File(root,"$local.part");val ready=File(root,"$local.ready")
                val receipt="received:$local"
                if(!records.contains(receipt)) {
                    var failure:Exception?=null
                    for(attempt in 0..2) {
                        try {
                            val latestSender=jobs[key]?.optJSONObject("sender") ?: sender
                            val latestAddress=latestSender.getString("address").removePrefix("::ffff:")
                            val latestHost=if(latestAddress.contains(':')) "[$latestAddress]" else latestAddress
                            transfer(client,"http://$latestHost:${latestSender.getInt("port")}/peer/$key/${f.id}",Build.MODEL,f.sha256,f.size,partial,ready,mapOf("Authorization" to "Bearer ${item.getString("capabilityToken")}")) { bytes->
                                progress[key]=completed+bytes;mutable.update { s->s.copy(transfers=s.transfers.map { if(it.id==key)it.copy(bytes=completed+bytes) else it }) }
                            }
                            failure=null;break
                        }catch(e:CancellationException){throw e}catch(e:Exception){failure=e;if(attempt<2)delay(3000)}
                    }
                    failure?.let { throw it }
                    currentCoroutineContext().ensureActive()
                    require(jobs[key]?.optString("status")=="accepted" && serverId==identity) { "Transfer is no longer accepted" }
                    val saved=publishReceivedFile(context,ready,f.name)
                    check(records.edit().putString(receipt,saved).commit()) { "Could not record received file" };ready.delete()
                }
                completed+=f.size;progress[key]=completed
            }
            api("peer-transfers/$key/status",JSONObject().put("status","completed"),origin)
            PeerService.complete(context,view(o))
        } catch(e:CancellationException){throw e}
        catch(e:Exception) {
            runCatching { api("peer-transfers/$key/status",JSONObject().put("status","failed").put("error",e.message ?: "Transfer failed"),origin) }
                .onSuccess { jobs[key]=JSONObject(o.toString()).put("status","failed") }
            mutable.update { it.copy(error="${e.message}. Keep both devices reachable on the same network.") }
        } finally {
            if(jobs[key]?.optString("status") in listOf("failed","cancelled","rejected")) o.getJSONArray("files").objects().forEach { item->
                val local=localOfferKey(identity,"$key:${item.getString("id")}")
                File(root,"$local.part").delete();File(root,"$local.ready").delete()
            }
        }
    }
}
internal fun JSONArray.objects()=(0 until length()).map { getJSONObject(it) }
