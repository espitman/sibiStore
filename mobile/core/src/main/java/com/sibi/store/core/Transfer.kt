package com.sibi.store.core

import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** The production transfer path, independently testable with a real HTTP socket. */
suspend fun transfer(
    client: OkHttpClient, url: String, device: String, hash: String, expected: Long,
    partial: File, final: File, progress: suspend (Long) -> Unit
) {
    require(hash.matches(Regex("[a-f0-9]{64}")) && expected > 0) { "Invalid download metadata" }
    if (final.exists()) {
        if (final.length() == expected && sha256(final) == hash) return
        require(final.delete()) { "Could not replace damaged download" }
    }
    if (partial.length() > expected) require(partial.delete()) { "Could not reset partial download" }
    var offset = partial.length()
    if (offset < expected) {
        val request = Request.Builder().url(url).header("X-Device-Name", device)
        if (offset > 0) request.header("Range", "bytes=$offset-").header("If-Range", "\"$hash\"")
        client.newCall(request.build()).execute().use { response ->
            require(response.code == 200 || response.code == 206) { "Server returned HTTP ${response.code}" }
            if (response.code == 206) {
                require(response.header("Content-Range") == "bytes $offset-${expected-1}/$expected") { "Invalid resumed download range" }
            } else offset = 0
            val body = response.body ?: error("Empty response")
            FileOutputStream(partial, offset > 0).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(65536)
                    var done = offset
                    var last = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        done += count
                        require(done <= expected) { "Download is larger than expected" }
                        output.write(buffer, 0, count)
                        if (System.currentTimeMillis() - last > 250) {
                            progress(done)
                            last = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }
    coroutineContext.ensureActive()
    require(partial.length() == expected) { "Download interrupted. Tap Resume to continue." }
    if (sha256(partial) != hash) {
        partial.delete()
        error("File integrity check failed. Download again.")
    }
    coroutineContext.ensureActive()
    require(partial.renameTo(final)) { "Could not save downloaded APK" }
    progress(expected)
}
