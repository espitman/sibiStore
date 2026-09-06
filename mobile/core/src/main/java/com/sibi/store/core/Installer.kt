package com.sibi.store.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import java.security.MessageDigest
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

@Suppress("DEPRECATION")
fun certificateDigests(info: PackageInfo): List<String> {
    val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners ?: emptyArray() else info.signatures ?: emptyArray()
    return signatures.map { sig -> MessageDigest.getInstance("SHA-256").digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.sorted()
}
@Suppress("DEPRECATION")
fun installed(context: Context, packageName: String): Installed? = try {
    val info = context.packageManager.getPackageInfo(packageName, if(Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    Installed(if(Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong(),info.versionName ?: "",certificateDigests(info))
} catch (_: PackageManager.NameNotFoundException) { null }
@Suppress("DEPRECATION")
suspend fun install(context: Context, release: Release) = downloadLock(release.sha256).withLock {
    val file = downloadFile(context,release.sha256)
    require(file.isFile && file.length() == release.size && sha256(file) == release.sha256) { "APK failed its integrity check" }
    val archive = context.packageManager.getPackageArchiveInfo(file.path, if(Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES) ?: error("Invalid APK")
    val code = if(Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
    require(archive.packageName == release.packageName && code == release.versionCode) { "APK does not match the catalog" }
    require(certificateDigests(archive).toSet() == release.certificates.toSet()) { "APK signing certificate does not match the catalog" }
    val current = installed(context,release.packageName)
    require(current == null || current.certificates.toSet() == release.certificates.toSet()) { "Signing certificate differs from installed app" }
    require(current == null || current.versionCode <= release.versionCode) { "A newer version is already installed" }
    val installer = context.packageManager.packageInstaller
    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply { setAppPackageName(release.packageName); setSize(file.length()) }
    val id = installer.createSession(params)
    val records = context.getSharedPreferences("installSessions", Context.MODE_PRIVATE)
    val record = JSONObject().put("hash", release.sha256).put("package", release.packageName)
        .put("version", release.versionCode).put("before", current?.versionCode ?: -1)
        .put("certificates", JSONArray(release.certificates)).put("confirmed", false)
    try {
        check(records.edit().putString(id.toString(), record.toString()).commit()) { "Could not save install session" }
        installer.openSession(id).use { session ->
            file.inputStream().use { input -> session.openWrite("base.apk",0,file.length()).use { output -> input.copyTo(output); session.fsync(output) } }
            val intent = Intent(context,InstallResultReceiver::class.java).putExtra("package",release.packageName).putExtra("sibiSession", id)
            val pending = PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    } catch(e: Exception) { records.edit().remove(id.toString()).commit(); installer.abandonSession(id); throw e }
}
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val selfUpdated = intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        val status = if (selfUpdated) PackageInstaller.STATUS_SUCCESS else intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                try { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
                catch (_: Exception) { /* Record a recoverable error below. */ }
            }
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val records = context.getSharedPreferences("installSessions", Context.MODE_PRIVATE)
                val id = intent.getIntExtra("sibiSession", intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)).toString()
                val raw = records.getString(id, null)
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    if (raw != null) records.edit().putString(id, JSONObject(raw).put("confirmed", true).toString()).commit()
                    else cleanupLegacyInstall(context, if (selfUpdated) context.packageName else intent.getStringExtra("package") ?: intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME))
                    reconcileInstallDownloads(context)
                } else records.edit().remove(id).commit()
                val message = if(status == PackageInstaller.STATUS_SUCCESS) "Installation complete" else intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation was not completed. Try again from Sibi Store."
                context.getSharedPreferences("sibi",Context.MODE_PRIVATE).edit().putString("installResult",message).apply()
                context.sendBroadcast(Intent("${context.packageName}.INSTALL_RESULT").setPackage(context.packageName))
            } catch(e: Exception) {
                android.util.Log.e("SibiInstall", "Could not process install result", e)
            } finally { pending.finish() }
        }
    }
}

internal fun installationDownloadHashes(context: Context): Set<String> = context.getSharedPreferences("installSessions", Context.MODE_PRIVATE).all.values.mapNotNull {
    runCatching { JSONObject(it as String).getString("hash") }.getOrNull()
}.toSet()

// A self-update can terminate the process before its result receiver runs.
// Persisted metadata lets the next launch confirm the installed version/signature.
suspend fun reconcileInstallDownloads(context: Context) {
    val records = context.getSharedPreferences("installSessions", Context.MODE_PRIVATE)
    val live = context.packageManager.packageInstaller.mySessions.map { it.sessionId.toString() }.toSet()
    for ((id, value) in records.all) {
        val record = runCatching { JSONObject(value as String) }.getOrNull() ?: continue
        val current = installed(context, record.getString("package"))
        val success = confirmsInstallation(current, record.getLong("version"), record.getLong("before"),
            record.getJSONArray("certificates").strings(), record.optBoolean("confirmed"))
        if (success) {
            val hash = record.getString("hash")
            if (hash.matches(Regex("[a-f0-9]{64}"))) downloadLock(hash).withLock {
                val file = downloadFile(context, hash)
                if (!autoDeleteDownloads(context) || !file.exists() || file.delete()) records.edit().remove(id).commit()
            }
        } else if (id !in live) records.edit().remove(id).commit()
    }
}

@Suppress("DEPRECATION")
private suspend fun cleanupLegacyInstall(context: Context, packageName: String?) {
    if (!autoDeleteDownloads(context) || packageName == null) return
    val current = installed(context, packageName) ?: return
    for (file in storedFiles(downloadsFolder(context)).filter { it.extension == "apk" }) {
        downloadLock(file.nameWithoutExtension).withLock {
            val archive = context.packageManager.getPackageArchiveInfo(file.path,
                if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
            val version = archive?.let { if(Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
            if (archive?.packageName == packageName && version == current.versionCode &&
                certificateDigests(archive).toSet() == current.certificates.toSet()) file.delete()
        }
    }
}
