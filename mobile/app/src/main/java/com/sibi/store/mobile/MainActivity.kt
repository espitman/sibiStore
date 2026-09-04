package com.sibi.store.mobile

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.sibi.store.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: StoreModel by viewModels()
    private val receiver = object: BroadcastReceiver() { override fun onReceive(context: Context, intent: Intent) { model.readInstallResult() } }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ContextCompat.registerReceiver(this,receiver,IntentFilter("$packageName.INSTALL_RESULT"),ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { SibiTheme { MobileApp(model,::action) } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { model.state.collect { model.claimReadyInstall()?.let { action(it) } } } }
    }
    override fun onResume() { super.onResume(); model.start() }
    override fun onPause() { model.stop(); super.onPause() }
    override fun onDestroy() { unregisterReceiver(receiver); super.onDestroy() }
    private fun action(app: StoreApp) {
        val release = model.release(app) ?: return
        val download = model.state.value.downloads[release.sha256]
        if(model.status(app) in listOf(Availability.CURRENT,Availability.NEWER)) {
            val launch = packageManager.getLaunchIntentForPackage(app.packageName) ?: packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            if(launch != null) startActivity(launch) else model.report("This app has no launchable screen")
        } else if(download?.state == "ready" || downloadFile(this,release.sha256).exists()) {
            if(!packageManager.canRequestPackageInstalls()) {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
                model.report("Allow Sibi Store to install apps, then tap Install again.")
            } else lifecycleScope.launch { try { withContext(Dispatchers.IO) { install(this@MainActivity,release) } } catch(e: Exception) { model.report(e.message ?: "Installation failed") } }
        } else {
            if(Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
            model.download(release)
        }
    }
}
