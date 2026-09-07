package com.sibi.store.vr

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.sibi.store.core.Availability
import com.sibi.store.core.Download
import com.sibi.store.core.Host
import com.sibi.store.core.Release
import com.sibi.store.core.StoreApp
import com.sibi.store.core.StoreModel
import com.sibi.store.core.StoreState
import com.sibi.store.core.bytesLabel
import com.sibi.store.core.downloadFile
import com.sibi.store.core.install
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thread-safe Unity facade for the Android store core.
 *
 * Unity may call [snapshot], [command], and [close] from any thread. Mutations are
 * posted to the Android main thread. The snapshot schema is versioned and is
 * replaced atomically after every StoreState update.
 *
 * Commands and values:
 * - `connect`: a `host` object from the snapshot, or an address such as
 *   `192.168.1.20:8743`.
 * - `discover`, `refresh`, `clearDownloads`, `dismissMessage`: empty value.
 * - `download`, `action`: an app package name.
 * - `pause`, `resume`, `cancel`: a download SHA-256 hash.
 * - `deleteAfterInstall`: `true` or `false`.
 * - `lifecycleResume`, `lifecyclePause`: empty value.
 *
 * The bridge starts resumed because Unity normally creates it from an active
 * player Activity. Unity should mirror OnApplicationPause with the lifecycle
 * commands and call [close] before discarding the bridge.
 */
class StoreBridge(private val activity: Activity) {
    @Volatile
    private var snapshotJson = initialSnapshot()

    private var model: StoreModel? = null
    private var modelStore: ViewModelStore? = null
    private var scope: CoroutineScope? = null
    private var stateJob: Job? = null
    private var receiverRegistered = false
    private var resumed = false
    private var closed = false

    private val installReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            model?.readInstallResult()
        }
    }

    init {
        activity.runOnUiThread {
            runCatching { initialize() }.onFailure { error ->
                shutdown()
                snapshotJson = JSONObject(failureSnapshot(error.message ?: "Could not start Sibi Store"))
                    .put("closed", true)
                    .toString()
            }
        }
    }

    /** Returns the latest complete JSON snapshot without blocking Unity's thread. */
    fun snapshot(): String = snapshotJson

    /** Dispatches one of the commands documented on [StoreBridge]. */
    fun command(action: String, value: String) {
        activity.runOnUiThread {
            if (!closed) runCatching { execute(action, value) }
                .onFailure { model?.report(it.message ?: "Store command failed") }
        }
    }

    /** Stops discovery, state delivery, and the install-result receiver. */
    fun close() {
        activity.runOnUiThread { shutdown() }
    }

    private fun initialize() {
        if (closed || model != null) return
        val ownerStore = ViewModelStore()
        // Unity imports peer AARs: do not depend on their resource merge priority
        // to select the VR catalog instead of the phone default.
        val store = ViewModelProvider(ownerStore, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == StoreModel::class.java)
                return modelClass.cast(StoreModel(activity.application, vrClientOverride = true))!!
            }
        })[StoreModel::class.java]
        val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        model = store
        modelStore = ownerStore
        scope = bridgeScope
        registerInstallReceiver()
        resumed = true
        store.start()
        stateJob = bridgeScope.launch {
            store.state.collect { state ->
                snapshotJson = serialize(state)
                if (resumed) store.claimReadyInstall()?.let { app ->
                    runCatching { performAction(app) }
                        .onFailure { store.report(it.message ?: "Could not install this app") }
                }
            }
        }
    }

    private fun execute(action: String, value: String) {
        val store = model ?: error("Store is still starting")
        when (action) {
            "connect" -> connect(store, value)
            "discover" -> store.discoverAgain()
            "refresh" -> store.refresh()
            "catalog" -> store.selectHeadsetCatalog(value)
            "download" -> downloadApp(store, value)
            "action" -> performAction(requireApp(store, value))
            "pause" -> store.pause(requireHash(value))
            "resume" -> resumeDownload(store, requireHash(value))
            "cancel" -> store.cancel(requireHash(value))
            "clearDownloads" -> store.clearDownloads()
            "deleteAfterInstall" -> store.setDeleteAfterInstall(parseBoolean(value))
            "dismissMessage" -> store.clearMessage()
            "lifecycleResume" -> {
                resumed = true
                store.start()
                snapshotJson = serialize(store.state.value)
                store.claimReadyInstall()?.let(::performAction)
            }
            "lifecyclePause" -> {
                resumed = false
                store.stop()
                snapshotJson = serialize(store.state.value)
            }
            "close" -> shutdown()
            else -> error("Unknown store command: $action")
        }
    }

    private fun connect(store: StoreModel, value: String) {
        val raw = value.trim()
        require(raw.isNotEmpty()) { "Enter a Mac address" }
        if (raw.startsWith("{")) {
            val json = JSONObject(raw)
            val url = json.optString("url").trim()
            require(url.isNotEmpty()) { "Selected Mac has no address" }
            store.connect(Host(json.optString("name", "My Mac"), url, json.optString("id")))
        } else {
            store.connectAddress(raw)
        }
    }

    private fun resumeDownload(store: StoreModel, hash: String) {
        val release = store.releaseForDownload(hash)
            ?: error("This download is no longer in the selected catalog")
        store.download(release)
    }

    private fun downloadApp(store: StoreModel, packageName: String) {
        val app = requireApp(store, packageName)
        require(store.status(app) in listOf(Availability.INSTALL, Availability.UPDATE)) {
            "This app does not need to be downloaded"
        }
        store.download(requireRelease(store, packageName))
    }

    private fun requireApp(store: StoreModel, packageName: String): StoreApp =
        store.state.value.apps.firstOrNull { it.packageName == packageName }
            ?: error("This app is no longer in the selected catalog")

    private fun requireRelease(store: StoreModel, packageName: String): Release {
        val app = requireApp(store, packageName)
        return store.release(app) ?: error("This app is not compatible with this headset")
    }

    private fun requireHash(value: String): String = value.trim().also {
        require(it.matches(Regex("[a-f0-9]{64}"))) { "Invalid download identifier" }
    }

    private fun parseBoolean(value: String): Boolean = when (value.trim().lowercase()) {
        "true" -> true
        "false" -> false
        else -> error("Expected true or false")
    }

    private fun performAction(app: StoreApp) {
        val store = model ?: return
        val release = store.release(app) ?: run {
            store.report("This app is not compatible with this headset")
            return
        }
        val status = store.status(app)
        val download = store.state.value.downloads[release.sha256]
        when {
            status == Availability.SIGNATURE_MISMATCH ->
                store.report("The available APK signature does not match the installed app")
            status in listOf(Availability.CURRENT, Availability.NEWER) -> openApp(app)
            download?.state == "ready" || downloadFile(activity, release.sha256).exists() -> installRelease(store, release)
            else -> {
                requestNotificationPermission()
                store.download(release)
            }
        }
    }

    private fun openApp(app: StoreApp) {
        val intent = if (model?.release(app)?.vr == true) Intent(Intent.ACTION_MAIN)
            .addCategory(VR_LAUNCHER_CATEGORY).setPackage(app.packageName)
        else activity.packageManager.getLaunchIntentForPackage(app.packageName)
        try {
            require(intent != null && intent.resolveActivity(activity.packageManager) != null) {
                "This app has no launchable activity"
            }
            activity.startActivity(intent)
        } catch (error: Exception) {
            model?.report(error.message ?: "Could not open this app")
        }
    }

    private fun installRelease(store: StoreModel, release: Release) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${activity.packageName}")
            )
            try {
                activity.startActivity(intent)
            } catch (_: Exception) {
                runCatching { activity.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                    .onFailure { store.report("Open Settings and allow Sibi Store to install apps.") }
            }
            store.report("Allow Sibi Store to install apps, then select Install again.")
            return
        }
        scope?.launch {
            try {
                withContext(Dispatchers.IO) { install(activity, release) }
            } catch (error: Exception) {
                store.report(error.message ?: "Installation failed")
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
        }
    }

    @Suppress("DEPRECATION")
    private fun registerInstallReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter("${activity.packageName}.INSTALL_RESULT")
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(installReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            activity.registerReceiver(installReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun shutdown() {
        if (closed) return
        closed = true
        resumed = false
        model?.stop()
        stateJob?.cancel()
        scope?.cancel()
        modelStore?.clear()
        if (receiverRegistered) {
            runCatching { activity.unregisterReceiver(installReceiver) }
            receiverRegistered = false
        }
        snapshotJson = JSONObject(snapshotJson)
            .put("ready", false)
            .put("closed", true)
            .toString()
        stateJob = null
        scope = null
        model = null
        modelStore = null
    }

    private fun serialize(state: StoreState): String {
        val store = model ?: return initialSnapshot()
        val apps = JSONArray()
        state.apps.sortedBy { it.title.lowercase() }.forEach { app ->
            apps.put(serializeApp(store, state, app))
        }
        val releasesByHash = state.apps.flatMap { it.versions }.associateBy { it.sha256 }
        val downloads = JSONArray()
        state.downloads.values.sortedBy { releasesByHash[it.hash]?.title ?: it.hash }.forEach { download ->
            downloads.put(serializeDownload(download).apply {
                val release = releasesByHash[download.hash]
                putNullable("packageName", release?.packageName)
                putNullable("title", release?.title)
            })
        }
        return JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("ready", true)
            .put("closed", false)
            .put("lifecycle", if (resumed) "resumed" else "paused")
            .put("connected", state.connected)
            .put("loading", state.loading)
            .putNullable("host", state.host?.let(::serializeHost))
            .put("hosts", JSONArray().apply { state.hosts.forEach { put(serializeHost(it)) } })
            .put("catalog", state.catalog)
            .put("apps", apps)
            .put("downloads", downloads)
            .put("storage", JSONObject()
                .put("bytes", state.downloadUsage.bytes)
                .put("label", bytesLabel(state.downloadUsage.bytes))
                .put("files", state.downloadUsage.files)
                .put("clearing", state.clearingDownloads))
            .put("settings", JSONObject().put("deleteAfterInstall", state.deleteAfterInstall))
            .putNullable("error", state.error)
            .putNullable("message", state.message)
            .toString()
    }

    private fun serializeApp(store: StoreModel, state: StoreState, app: StoreApp): JSONObject {
        val release = store.release(app)
        val status = store.status(app)
        val installed = state.installed[app.packageName]
        val download = release?.let { state.downloads[it.sha256] }
        val busy = download?.state in listOf("queued", "downloading")
        val ready = download?.state == "ready" && status in listOf(Availability.INSTALL, Availability.UPDATE)
        val enabled = status !in listOf(Availability.INCOMPATIBLE, Availability.SIGNATURE_MISMATCH) &&
            !busy && (state.connected || ready || status in listOf(Availability.CURRENT, Availability.NEWER))
        val primaryLabel = when {
            busy && download?.state == "queued" -> "Queued"
            busy -> "${downloadPercent(download!!)}%"
            ready -> "Install"
            download?.state in listOf("paused", "failed") -> "Resume"
            status == Availability.INSTALL -> "Install"
            status == Availability.UPDATE -> "Update"
            status in listOf(Availability.CURRENT, Availability.NEWER) -> "Open"
            status == Availability.SIGNATURE_MISMATCH -> "Signature mismatch"
            else -> "Not compatible"
        }
        val actions = JSONArray().put(actionJson("action", app.packageName, primaryLabel, enabled))
        if (download != null) {
            when (download.state) {
                "queued", "downloading" -> actions.put(actionJson("pause", download.hash, "Pause", true))
                "paused", "failed" -> actions.put(actionJson("resume", download.hash, "Resume", state.connected))
            }
            if (download.state in listOf("queued", "downloading", "paused", "failed", "ready")) {
                actions.put(actionJson("cancel", download.hash, "Cancel", true))
            }
        }
        return JSONObject()
            .put("packageName", app.packageName)
            .put("title", app.title)
            .putNullable("icon", app.icon)
            .put("availability", status.name.lowercase())
            .put("downloading", busy)
            .putNullable("versionCode", release?.versionCode)
            .putNullable("versionName", release?.versionName)
            .putNullable("size", release?.size)
            .putNullable("availableVersion", release?.let(::serializeRelease))
            .putNullable("installedVersion", installed?.let {
                JSONObject().put("code", it.versionCode).put("name", it.versionName)
            })
            .put("versions", JSONArray().apply {
                app.versions.sortedByDescending { it.versionCode }.forEach { put(serializeRelease(it)) }
            })
            .putNullable("download", download?.let(::serializeDownload))
            .put("primaryAction", actions.getJSONObject(0))
            .put("actions", actions)
    }

    private fun serializeRelease(release: Release) = JSONObject()
        .put("code", release.versionCode)
        .put("name", release.versionName)
        .put("size", release.size)
        .put("sizeLabel", bytesLabel(release.size))
        .put("minSdk", release.minSdk)
        .put("abis", JSONArray(release.abis))
        .put("sha256", release.sha256)
        .put("addedAt", release.addedAt)
        .put("filename", release.filename)
        .put("vr", release.vr)

    private fun serializeDownload(download: Download) = JSONObject()
        .put("hash", download.hash)
        .put("state", download.state)
        .put("bytes", download.bytes)
        .put("total", download.total)
        .put("percent", downloadPercent(download))
        .putNullable("error", download.error)

    private fun serializeHost(host: Host) = JSONObject()
        .put("name", host.name)
        .put("url", host.url)
        .put("id", host.id)

    private fun actionJson(command: String, value: String, label: String, enabled: Boolean) = JSONObject()
        .put("command", command)
        .put("value", value)
        .put("label", label)
        .put("enabled", enabled)

    private fun downloadPercent(download: Download): Int =
        if (download.total > 0) ((download.bytes.toDouble() / download.total) * 100).toInt().coerceIn(0, 100) else 0

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    companion object {
        private const val SCHEMA_VERSION = 1
        private const val NOTIFICATION_REQUEST = 8743
        private const val VR_LAUNCHER_CATEGORY = "com.oculus.intent.category.VR"

        private fun initialSnapshot() = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("ready", false)
            .put("closed", false)
            .put("lifecycle", "starting")
            .put("connected", false)
            .put("loading", false)
            .put("host", JSONObject.NULL)
            .put("hosts", JSONArray())
            .put("apps", JSONArray())
            .put("downloads", JSONArray())
            .put("storage", JSONObject().put("bytes", 0).put("label", "0 KB").put("files", 0).put("clearing", false))
            .put("settings", JSONObject().put("deleteAfterInstall", true))
            .put("error", JSONObject.NULL)
            .put("message", JSONObject.NULL)
            .toString()

        private fun failureSnapshot(message: String) = JSONObject(initialSnapshot())
            .put("lifecycle", "failed")
            .put("error", message)
            .toString()
    }
}
