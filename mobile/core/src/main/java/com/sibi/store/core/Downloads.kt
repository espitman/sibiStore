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
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
        downloadLock(hash).withLock {
            try {
                if (final.exists() && final.length() == expected && sha256(final) == hash) return@withContext Result.success(workDataOf("hash" to hash))
                setForeground(notification(title))
                val client = OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).build()
                transfer(client,url,Build.MODEL,hash,expected,partial,final) { done ->
                    setProgress(workDataOf("bytes" to done,"total" to expected))
                }
                Result.success(workDataOf("hash" to hash))
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { Result.failure(workDataOf("error" to (e.message ?: "Download failed"))) }
        }
    }
    private fun notification(title: String): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("downloads","App downloads",NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Downloading $title").setContentText("Sibi Store · Home network").setOngoing(true).setProgress(0,0,true).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(8743,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(8743,notification)
    }
}
