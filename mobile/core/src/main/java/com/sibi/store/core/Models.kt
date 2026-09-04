package com.sibi.store.core

import org.json.JSONObject
import org.json.JSONArray

data class Release(val packageName: String, val title: String, val versionCode: Long, val versionName: String,
    val size: Long, val minSdk: Int, val abis: List<String>, val tv: Boolean, val sha256: String,
    val certificates: List<String>, val downloadUrl: String, val addedAt: String, val filename: String)
data class StoreApp(val packageName: String, val title: String, val icon: String?, val versions: List<Release>)
data class Host(val name: String, val url: String, val id: String = "")
data class Installed(val versionCode: Long, val versionName: String, val certificates: List<String>)
data class Download(val hash: String, val bytes: Long = 0, val total: Long = 0, val state: String = "queued", val error: String? = null)
enum class Availability { INSTALL, UPDATE, CURRENT, NEWER, INCOMPATIBLE, SIGNATURE_MISMATCH }
fun compatible(release: Release, sdk: Int, abis: List<String>) = release.minSdk <= sdk && (release.abis.isEmpty() || release.abis.any { it in abis })
fun newest(app: StoreApp, sdk: Int, abis: List<String>): Release? = app.versions.filter { compatible(it, sdk, abis) }.maxByOrNull { it.versionCode }
fun availability(release: Release?, installed: Installed?): Availability = when {
    release == null -> Availability.INCOMPATIBLE
    installed == null -> Availability.INSTALL
    installed.certificates.toSet() != release.certificates.toSet() -> Availability.SIGNATURE_MISMATCH
    release.versionCode > installed.versionCode -> Availability.UPDATE
    release.versionCode < installed.versionCode -> Availability.NEWER
    else -> Availability.CURRENT
}
fun JSONArray.strings() = (0 until length()).map { getString(it) }
fun parseCatalog(raw: String): Pair<String, List<StoreApp>> {
    val root = JSONObject(raw)
    require(root.getInt("protocolVersion") == 1) { "This server uses an unsupported protocol" }
    val apps = root.getJSONArray("apps")
    return root.getString("serverId") to (0 until apps.length()).map { index ->
        val app = apps.getJSONObject(index); val versions = app.getJSONArray("versions")
        StoreApp(app.getString("packageName"), app.getString("title"), app.optString("icon").takeIf { it.startsWith("data:image/") },
            (0 until versions.length()).map { i ->
                val v = versions.getJSONObject(i)
                Release(app.getString("packageName"), app.getString("title"), v.getString("versionCode").toLong(), v.getString("versionName"),
                    v.getLong("size"), v.getInt("minSdk"), v.getJSONArray("abis").strings(), v.optBoolean("tv"),
                    v.getString("sha256"), v.getJSONArray("certificates").strings(), v.getString("downloadUrl"), v.getString("addedAt"), v.getString("filename"))
            })
    }
}
