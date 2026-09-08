package com.sibi.store.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class InboxOffer(val id: String, val name: String, val size: Long, val sha256: String, val downloadUrl: String)

fun parseInbox(raw: String, expectedServerId: String): List<InboxOffer> {
    val root = JSONObject(raw)
    require(root.getString("serverId") == expectedServerId) { "Server identity does not match" }
    val files = root.getJSONArray("files")
    return (0 until files.length()).map { index ->
        val item = files.getJSONObject(index)
        InboxOffer(item.getString("id"), item.getString("name"), item.getLong("size"), item.getString("sha256"), item.getString("downloadUrl")).also {
            require(it.id.matches(Regex("[A-Za-z0-9_-]{1,128}"))) { "Invalid file offer ID" }
            require(it.size >= 0 && it.sha256.matches(Regex("[a-f0-9]{64}"))) { "Invalid file offer metadata" }
            require(it.downloadUrl == "/api/v1/inbox/${it.id}/file") { "Invalid file offer URL" }
        }
    }
}

internal fun safeInboxName(raw: String): String {
    val clean = raw.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001f\\u007f]"), "_").trim()
    if (clean.isEmpty()) return "Shared file"
    if (clean.length <= 180) return clean
    val dot = clean.lastIndexOf('.')
    val extension = if (dot in 1 until clean.lastIndex) clean.substring(dot).take(24) else ""
    return clean.take(180 - extension.length) + extension
}

class InboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val deviceId = inputData.getString("deviceId").orEmpty()
    private val deviceToken = inputData.getString("deviceToken").orEmpty()
    private val offerId = inputData.getString("offerId").orEmpty()
    private val baseUrl = inputData.getString("baseUrl").orEmpty()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(45,TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val name = safeInboxName(inputData.getString("name").orEmpty())
        val hash = inputData.getString("sha256").orEmpty()
        val expected = inputData.getLong("size",0)
        val serverId = inputData.getString("serverId").orEmpty()
        if (deviceId.isEmpty() || deviceToken.isEmpty() || offerId.isEmpty() || serverId.isEmpty() ||
            !hash.matches(Regex("[a-f0-9]{64}")) || expected < 0) return@withContext Result.failure()
        val prefs = applicationContext.getSharedPreferences("sibi",Context.MODE_PRIVATE)
        val savedKey = "inboxSaved:$serverId:$offerId"
        val root = File(applicationContext.filesDir,"inbox").apply { mkdirs() }
        val localId = localOfferKey(serverId,offerId)
        val partial = File(root,"$localId.part")
        val verified = File(root,"$localId.ready")
        try {
            setForeground(notification(name))
            if (!prefs.contains(savedKey)) {
                postStatus("receiving",0,null)
                val url = resolveOfferUrl(baseUrl,inputData.getString("downloadUrl").orEmpty())
                transfer(client,url,Build.MODEL,hash,expected,partial,verified,authHeaders()) { done ->
                    setProgress(workDataOf("bytes" to done,"total" to expected))
                }
                postStatus("receiving",expected,null)
                val saved = publishReceivedFile(applicationContext,verified,name)
                check(prefs.edit().putString(savedKey,saved).commit()) { "Could not save transfer receipt" }
                verified.delete()
            }
            postStatus("completed",expected,null)
            val saved = prefs.getString(savedKey,name) ?: name
            Result.success(workDataOf("savedName" to saved,"offerId" to offerId,"receiptKey" to "$serverId:$offerId"))
        } catch (e: CancellationException) {
            runCatching { postStatus("failed",0,"Transfer cancelled") }
            partial.delete(); verified.delete()
            throw e
        }
        catch (e: OfferCancelledException) {
            partial.delete(); verified.delete()
            Result.failure(workDataOf("error" to "Transfer cancelled by Mac","offerId" to offerId))
        }
        catch (e: Exception) {
            val error = (e.message ?: "File transfer failed").take(240)
            if (runAttemptCount < 2) Result.retry() else {
                val reported = runCatching { postStatus("failed",0,error) }.isSuccess
                if (!reported) prefs.edit().putLong("inboxRetryAfter:$serverId:$offerId",System.currentTimeMillis() + 300_000).apply()
                partial.delete(); verified.delete()
                Result.failure(workDataOf("error" to error,"offerId" to offerId))
            }
        }
    }

    private fun authHeaders() = mapOf("X-Device-Id" to deviceId,"X-Device-Token" to deviceToken,"X-Device-Capabilities" to "files-v1")

    private fun postStatus(status: String, bytes: Long, error: String?) {
        val json = JSONObject().put("status",status).put("bytes",bytes).apply { if(error != null) put("error",error) }
        val request = Request.Builder().url("$baseUrl/api/v1/inbox/$offerId/status")
            .headers(okhttp3.Headers.headersOf(*authHeaders().flatMap { listOf(it.key,it.value) }.toTypedArray()))
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use {
            if (it.code == 409) throw OfferCancelledException()
            require(it.isSuccessful) { "Could not report transfer status (${it.code})" }
        }
    }


    private fun notification(name: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("file-transfers","File transfers",NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext,"file-transfers").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Receiving $name").setContentText("Sibi Store · Home network").setOngoing(true).setProgress(0,0,true).build()
        val id = 12000 + (offerId.hashCode() and 0x3fff)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id,notification)
    }
}

private class OfferCancelledException : Exception()

internal fun resolveOfferUrl(base: String, path: String): String {
    val origin = URI(base)
    val result = origin.resolve(path)
    require(result.scheme == origin.scheme && result.host == origin.host && result.port == origin.port) { "Invalid file source" }
    return result.toString()
}

internal fun localOfferKey(serverId: String, offerId: String): String = MessageDigest.getInstance("SHA-256")
    .digest("$serverId\u0000$offerId".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

private fun reserveUniqueFile(folder: File, name: String): File {
    val dot = name.lastIndexOf('.'); val stem = if(dot > 0) name.substring(0,dot) else name; val ext = if(dot > 0) name.substring(dot) else ""
    var index = 1
    while (true) {
        val candidate = File(folder,if(index == 1) name else "$stem ($index)$ext")
        if(candidate.createNewFile()) return candidate
        index++
    }
}

internal fun publishReceivedFile(context: Context, source: File, name: String): String {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME,name)
                val extension = name.substringAfterLast('.',"").lowercase()
                put(MediaStore.Downloads.MIME_TYPE,MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH,"${Environment.DIRECTORY_DOWNLOADS}/Sibi Store")
                put(MediaStore.Downloads.IS_PENDING,1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values) ?: error("Could not create Downloads file")
            try {
                resolver.openOutputStream(uri,"w")!!.use { output -> FileInputStream(source).use { it.copyTo(output) } }
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING,0); resolver.update(uri,values,null,null)
            } catch (e: Exception) { resolver.delete(uri,null,null); throw e }
            return name
        }
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),"Sibi Store").apply { mkdirs() }
        val target = reserveUniqueFile(folder,name)
        try { FileInputStream(source).use { input -> FileOutputStream(target,false).use { input.copyTo(it) } } }
        catch (e: Exception) { target.delete(); throw e }
        return target.name
    }
