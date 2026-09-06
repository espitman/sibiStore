package com.sibi.store.core

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class StoreState(val apps: List<StoreApp> = emptyList(), val installed: Map<String,Installed> = emptyMap(), val hosts: List<Host> = emptyList(),
    val deleteAfterInstall: Boolean = true, val downloadUsage: DownloadUsage = DownloadUsage(), val clearingDownloads: Boolean = false,
    val host: Host? = null, val connected: Boolean = false, val loading: Boolean = false, val error: String? = null, val message: String? = null, val downloads: Map<String,Download> = emptyMap())
class StoreModel(application: Application) : AndroidViewModel(application) {
    private val context = application
    private val tvClient = context.resources.getBoolean(R.bool.sibi_tv_client)
    private val prefs = context.getSharedPreferences("sibi",Context.MODE_PRIVATE)
    private val cache = File(context.filesDir,"catalog.json")
    private val _state = MutableStateFlow(StoreState(deleteAfterInstall = autoDeleteDownloads(context), host = prefs.getString("url",null)?.let { Host(prefs.getString("hostName","My Mac")!!,it,prefs.getString("serverId","")!!) }))
    val state = _state.asStateFlow()
    private val client = OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(15,TimeUnit.SECONDS).build()
    private val work = WorkManager.getInstance(context)
    private var refreshJob: Job? = null
    private var healthJob: Job? = null
    private val workLive = work.getWorkInfosByTagLiveData("sibi-download")
    private fun updateDownloads(infos: List<WorkInfo>?) {
        val downloads = infos.orEmpty().groupBy { it.tags.firstOrNull { tag -> tag.startsWith("hash:") }?.removePrefix("hash:") ?: "" }.mapNotNull { (hash, tasks) ->
            if(hash.isEmpty() || hash in prefs.getStringSet("cancelledDownloads",emptySet())!!) return@mapNotNull null
            val active = tasks.firstOrNull { !it.state.isFinished } ?: tasks.firstOrNull { it.state == WorkInfo.State.SUCCEEDED } ?: tasks.first()
            val release = _state.value.apps.flatMap { it.versions }.find { it.sha256 == hash }
            val file = downloadFile(context,hash)
            if (active.state.isFinished && !file.exists() && !downloadFile(context,hash,".part").exists()) return@mapNotNull null
            val status = when { file.exists() -> "ready"; active.state == WorkInfo.State.RUNNING -> "downloading"; active.state == WorkInfo.State.ENQUEUED || active.state == WorkInfo.State.BLOCKED -> "queued"; active.state == WorkInfo.State.CANCELLED -> "paused"; active.state == WorkInfo.State.FAILED -> "failed"; else -> "queued" }
            hash to Download(hash, if(file.exists()) file.length() else active.progress.getLong("bytes",downloadFile(context,hash,".part").length()),release?.size ?: active.progress.getLong("total",0),status,active.outputData.getString("error"))
        }.toMap()
        _state.update { it.copy(downloads=downloads) }
    }
    private val observer = Observer<List<WorkInfo>> { infos -> updateDownloads(infos); refreshStorage(false) }
    fun refreshStorage(reconcile: Boolean = true) {
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) {
                if (reconcile) reconcileInstallDownloads(context)
                downloadUsage(downloadsFolder(context))
            }
            _state.update { it.copy(downloadUsage=usage) }
            updateDownloads(workLive.value)
        }
    }
    fun setDeleteAfterInstall(enabled: Boolean) {
        prefs.edit().putBoolean("deleteAfterInstall",enabled).apply()
        _state.update { it.copy(deleteAfterInstall=enabled) }
    }
    fun clearDownloads() {
        if (_state.value.clearingDownloads) return
        _state.update { it.copy(clearingDownloads=true) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    reconcileInstallDownloads(context)
                    val active = work.getWorkInfosByTag("sibi-download").get().filter { !it.state.isFinished }
                        .flatMap { it.tags }.filter { it.startsWith("hash:") }.map { it.removePrefix("hash:") }.toSet()
                    clearStoredDownloads(downloadsFolder(context),active + installationDownloadHashes(context))
                }
                val pending=prefs.getStringSet("pendingInstalls",emptySet())!!.toMutableSet().apply { removeAll(result.hashes) }
                prefs.edit().putStringSet("pendingInstalls",pending).apply()
                _state.update { it.copy(message=if(result.failed>0) "Some downloaded files could not be deleted. Try again." else "Downloaded files cleared. Files in use are kept.") }
                refreshStorage(false)
            } catch(e: Exception) { report(e.message ?: "Could not clear downloaded files") }
            finally { _state.update { it.copy(clearingDownloads=false) } }
        }
    }
    private val discovery = Discovery(context,{ host -> viewModelScope.launch {
        _state.update { it.copy(hosts = (it.hosts.filterNot { h -> h.url == host.url || h.id.isNotEmpty() && h.id == host.id } + host)) }
        val saved = _state.value.host
        if (saved != null && saved.id.isNotEmpty() && saved.id == host.id && (! _state.value.connected || saved.url != host.url)) connect(host)
    } },{ error -> _state.update { if(it.connected) it else it.copy(error=error) } })
    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { catalogForClient(parseCatalog(cache.readText()).second,tvClient) }.onSuccess { apps -> _state.update { it.copy(apps=apps) }; refreshInstalled() }
        }
        workLive.observeForever(observer)
    }
    fun start() {
        discovery.start(); refresh(); refreshInstalled(); readInstallResult()
        if (healthJob?.isActive != true) healthJob = viewModelScope.launch {
            while (isActive) { delay(15000); if (!_state.value.loading) refresh() }
        }
    }
    fun stop() { discovery.stop(); healthJob?.cancel(); healthJob = null }
    fun clearMessage() { _state.update { it.copy(error=null,message=null) } }
    fun report(message: String) { _state.update { it.copy(error=message) } }
    fun readInstallResult() { refreshStorage(); prefs.getString("installResult",null)?.let { m -> _state.update { it.copy(message=m) }; prefs.edit().remove("installResult").apply() }; refreshInstalled() }
    fun refreshInstalled() { viewModelScope.launch(Dispatchers.IO) { val values = _state.value.apps.mapNotNull { a -> installed(context,a.packageName)?.let { a.packageName to it } }.toMap(); _state.update { it.copy(installed=values) } } }
    fun discoverAgain() { discovery.stop(); _state.update { it.copy(hosts=emptyList(),error=null) }; discovery.start() }
    fun connectAddress(address: String) {
        try {
            val raw = address.trim().let { if(it.contains("://")) it else "http://$it" }
            val uri = java.net.URI(raw)
            require(uri.scheme in listOf("http","https") && uri.host != null && uri.userInfo == null) { "Enter a valid Mac address, such as 192.168.1.20:8743" }
            val base = "${uri.scheme}://${if(uri.host.contains(':') && !uri.host.startsWith('[')) "[${uri.host}]" else uri.host}:${if(uri.port == -1) 8743 else uri.port}"
            connect(Host("My Mac",base))
        } catch(e: Exception) { report(e.message ?: "Invalid address") }
    }
    fun connect(host: Host) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading=true,error=null) }
            try {
                val info = withContext(Dispatchers.IO) { client.newCall(Request.Builder().url("${host.url}/api/v1/info").build()).execute().use { require(it.isSuccessful) { "Mac returned HTTP ${it.code}" }; JSONObject(it.body!!.string()) } }
                require(info.getInt("protocolVersion") == 1) { "Server protocol is not supported" }
                val id = info.getString("serverId")
                require(host.id.isEmpty() || host.id == id) { "Server identity changed. Select your Mac again." }
                val verified = host.copy(name=info.getString("name"),id=id)
                prefs.edit().putString("url",verified.url).putString("serverId",id).putString("hostName",verified.name).apply()
                if (_state.value.host?.id != id) { cache.delete(); _state.update { it.copy(apps=emptyList(),installed=emptyMap()) } }
                _state.update { it.copy(host=verified) }
                fetchCatalog(verified)
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { _state.update { it.copy(connected=false,error=e.message ?: "Could not connect") } }
            finally { if (isActive) _state.update { it.copy(loading=false) } }
        }
    }
    fun refresh() { _state.value.host?.let { connect(it) } }
    private suspend fun fetchCatalog(host: Host) {
        val raw = withContext(Dispatchers.IO) { client.newCall(Request.Builder().url("${host.url}/api/v1/catalog").build()).execute().use { require(it.isSuccessful) { "Catalog request failed (${it.code})" }; it.body!!.string() } }
        val (id,apps) = parseCatalog(raw); require(id == host.id) { "Server identity does not match" }
        withContext(Dispatchers.IO) { cache.writeText(raw) }
        _state.update { it.copy(apps=catalogForClient(apps,tvClient),connected=true,error=null) }; refreshInstalled()
    }
    fun release(app: StoreApp) = newest(app,Build.VERSION.SDK_INT,Build.SUPPORTED_ABIS.toList())
    fun status(app: StoreApp) = availability(release(app),_state.value.installed[app.packageName])
    fun download(release: Release) {
        if (_state.value.clearingDownloads) return
        val host = _state.value.host ?: return
        val cancelled = prefs.getStringSet("cancelledDownloads",emptySet())!!.toMutableSet().apply { remove(release.sha256) }
        prefs.edit().putStringSet("cancelledDownloads",cancelled).apply()
        val pending=prefs.getStringSet("pendingInstalls",emptySet())!!.toMutableSet().apply { add(release.sha256) }
        prefs.edit().putStringSet("pendingInstalls",pending).apply()
        require(release.downloadUrl.matches(Regex("/artifacts/[a-f0-9]{64}\\.apk")))
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(workDataOf("hash" to release.sha256,"url" to "${host.url}${release.downloadUrl}","size" to release.size,"title" to release.title))
            // WorkManager's CONNECTED constraint can require validated internet on modern Android.
            // A home LAN is sufficient here; the HTTP request handles an unreachable Mac explicitly.
            .addTag("sibi-download").addTag("hash:${release.sha256}").build()
        work.enqueueUniqueWork("download:${release.sha256}",ExistingWorkPolicy.KEEP,request)
    }
    /** Claim once while the activity is resumed; background completion remains ready for later. */
    fun claimReadyInstall(): StoreApp? {
        val pending=prefs.getStringSet("pendingInstalls",emptySet())!!.toMutableSet()
        val app=_state.value.apps.firstOrNull { a -> val r=release(a); r!=null && r.sha256 in pending && _state.value.downloads[r.sha256]?.state=="ready" && status(a) in listOf(Availability.INSTALL,Availability.UPDATE) } ?: return null
        pending.remove(release(app)!!.sha256); prefs.edit().putStringSet("pendingInstalls",pending).apply()
        return app
    }
    fun pause(hash: String) { work.cancelUniqueWork("download:$hash") }
    fun cancel(hash: String) {
        // Remove automatic-install intent before cancellation can race with completion.
        val pending = prefs.getStringSet("pendingInstalls",emptySet())!!.toMutableSet().apply { remove(hash) }
        val cancelled = prefs.getStringSet("cancelledDownloads",emptySet())!!.toMutableSet().apply { add(hash) }
        prefs.edit().putStringSet("pendingInstalls",pending).putStringSet("cancelledDownloads",cancelled).apply()
        work.cancelUniqueWork("download:$hash")
        _state.update { it.copy(downloads=it.downloads - hash,message="Download cancelled") }
        // Retain private partial bytes for an explicit future retry; never install after Cancel.
    }
    override fun onCleared() { discovery.stop(); workLive.removeObserver(observer) }
}
