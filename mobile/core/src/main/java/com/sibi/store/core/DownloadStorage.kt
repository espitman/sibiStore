package com.sibi.store.core

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

internal val downloadLocks = ConcurrentHashMap<String, Mutex>()
internal fun downloadLock(hash: String) = downloadLocks.getOrPut(hash) { Mutex() }
private val storedName = Regex("([a-f0-9]{64})\\.(apk|part)")
data class DownloadUsage(val bytes: Long = 0, val files: Int = 0)
internal fun storedFiles(folder: File) = folder.listFiles().orEmpty().filter {
    it.isFile && storedName.matches(it.name) && it.canonicalFile.parentFile == folder.canonicalFile
}
internal fun downloadUsage(folder: File): DownloadUsage {
    val files = storedFiles(folder)
    return DownloadUsage(files.sumOf { it.length() }, files.size)
}
internal data class ClearedDownloads(val hashes: Set<String>, val failed: Int)
internal fun clearStoredDownloads(folder: File, protected: Set<String>): ClearedDownloads {
    val deleted = mutableSetOf<String>()
    var failed = 0
    for ((hash, files) in storedFiles(folder).groupBy { it.name.substringBefore('.') }) {
        if (hash in protected) continue
        val lock = downloadLock(hash)
        if (!lock.tryLock()) continue
        try {
            var allDeleted = true
            for (file in files) if (file.exists() && !file.delete()) { failed++; allDeleted = false }
            if (allDeleted) deleted.add(hash)
        } finally { lock.unlock() }
    }
    return ClearedDownloads(deleted, failed)
}
fun downloadsFolder(context: Context) = File(context.filesDir, "downloads")
fun autoDeleteDownloads(context: Context) = context.getSharedPreferences("sibi", Context.MODE_PRIVATE).getBoolean("deleteAfterInstall", true)

internal fun confirmsInstallation(current: Installed?, expected: Long, before: Long, certificates: List<String>, confirmed: Boolean) =
    current != null && current.versionCode >= expected && (confirmed || current.versionCode > before) &&
        current.certificates.toSet() == certificates.toSet()
