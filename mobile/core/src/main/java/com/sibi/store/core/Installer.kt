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
fun install(context: Context, release: Release) {
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
    try {
        installer.openSession(id).use { session ->
            file.inputStream().use { input -> session.openWrite("base.apk",0,file.length()).use { output -> input.copyTo(output); session.fsync(output) } }
            val intent = Intent(context,InstallResultReceiver::class.java).putExtra("package",release.packageName)
            val pending = PendingIntent.getBroadcast(context,id,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    } catch(e: Exception) { installer.abandonSession(id); throw e }
}
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null) {
                try { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
                catch (_: Exception) { /* Record a recoverable error below. */ }
            }
        }
        val message = if(status == PackageInstaller.STATUS_SUCCESS) "Installation complete" else intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation was not completed. Try again from Sibi Store."
        context.getSharedPreferences("sibi",Context.MODE_PRIVATE).edit().putString("installResult",message).apply()
        context.sendBroadcast(Intent("${context.packageName}.INSTALL_RESULT").setPackage(context.packageName))
    }
}
