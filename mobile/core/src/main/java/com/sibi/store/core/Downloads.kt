package com.sibi.store.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { stream -> val b = ByteArray(65536); while(true) { val n = stream.read(b); if(n < 0) break; digest.update(b,0,n) } }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
fun downloadFile(context: Context, hash: String, suffix: String = ".apk") = File(File(context.filesDir,"downloads").apply { mkdirs() }, "$hash$suffix")
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val hash = inputData.getString("hash") ?: return@withContext Result.failure()
        val url = inputData.getString("url") ?: return@withContext Result.failure()
        val title = inputData.getString("title") ?: "APK"
        val expected = inputData.getLong("size",0)
        if (!hash.matches(Regex("[a-f0-9]{64}")) || expected <= 0) return@withContext Result.failure(workDataOf("error" to "Invalid download metadata"))
        val partial = downloadFile(applicationContext,hash,".part"); val final = downloadFile(applicationContext,hash)
        try {
            if (final.exists() && final.length() == expected && sha256(final) == hash) return@withContext Result.success(workDataOf("hash" to hash))
            setForeground(notification(title))
            if (partial.length() > expected) partial.delete()
            var offset = partial.length()
            if (offset < expected) {
                val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
                val request = Request.Builder().url(url).header("X-Device-Name",Build.MODEL)
                if (offset > 0) request.header("Range","bytes=$offset-").header("If-Range","\"$hash\"")
                client.newCall(request.build()).execute().use { response ->
                    require(response.isSuccessful) { "Server returned HTTP ${response.code}" }
                    if (response.code == 206) {
                        require(response.header("Content-Range") == "bytes $offset-${expected-1}/$expected") { "Invalid resumed download range" }
                    } else offset = 0
                    val body = response.body ?: error("Empty response")
                    FileOutputStream(partial,offset > 0).use { output -> body.byteStream().use { input ->
                        val buffer = ByteArray(65536); var done = offset; var last = 0L
                        while(true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer); if(n < 0) break
                            done += n; require(done <= expected) { "Download is larger than expected" }; output.write(buffer,0,n)
                            if (System.currentTimeMillis()-last > 250) { setProgress(workDataOf("bytes" to done,"total" to expected)); last=System.currentTimeMillis() }
                        }
                    } }
                }
            }
            require(partial.length() == expected) { "Download interrupted. Tap Resume to continue." }
            if (sha256(partial) != hash) { partial.delete(); error("File integrity check failed. Download again.") }
            require(partial.renameTo(final)) { "Could not save downloaded APK" }
            setProgress(workDataOf("bytes" to expected,"total" to expected))
            Result.success(workDataOf("hash" to hash))
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "Download failed"))) }
    }
    private fun notification(title: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("downloads","App downloads",NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Downloading $title").setContentText("Sibi Store · Home network").setOngoing(true).setProgress(0,0,true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(8743,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(8743,notification)
    }
}
