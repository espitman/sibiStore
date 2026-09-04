package com.sibi.store.core

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class TransferTest {
    private val bytes = ByteArray(256 * 1024) { (it % 251).toByte() }
    private val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun check(block: suspend (MockWebServer, File, File) -> Unit) = runBlocking {
        val root = Files.createTempDirectory("sibi-transfer-").toFile()
        val server = MockWebServer()
        server.start()
        try { block(server,File(root,"download.part"),File(root,"download.apk")) }
        finally { server.shutdown(); root.deleteRecursively() }
    }

    private suspend fun fetch(server: MockWebServer, partial: File, final: File) =
        transfer(OkHttpClient(),server.url("/app.apk").toString(),"Test",hash,bytes.size.toLong(),partial,final) {}

    @Test fun disconnectPreservesBytesAndResumeUsesExactRange() = check { server, partial, final ->
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertTrue(runCatching { fetch(server,partial,final) }.isFailure)
        assertFalse(final.exists())
        val offset = partial.length().toInt()
        assertTrue(offset > 0 && offset < bytes.size)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes $offset-${bytes.size-1}/${bytes.size}").setBody(Buffer().write(bytes,offset,bytes.size-offset)))
        fetch(server,partial,final)
        val request = server.takeRequest()
        assertEquals("bytes=$offset-",request.getHeader("Range"))
        assertEquals("\"$hash\"",request.getHeader("If-Range"))
        assertArrayEquals(bytes,final.readBytes())
        assertFalse(partial.exists())
    }

    @Test fun fullResponseRestartsInsteadOfAppending() = check { server, partial, final ->
        partial.writeBytes(bytes.copyOf(4096))
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        fetch(server,partial,final)
        assertArrayEquals(bytes,final.readBytes())
    }

    @Test fun pauseKeepsPartialAndDoesNotPublishAnInstallableFile() = check { server, partial, final ->
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val result = runCatching {
            transfer(OkHttpClient(),server.url("/app.apk").toString(),"Test",hash,bytes.size.toLong(),partial,final) {
                throw CancellationException("Paused")
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        val offset = partial.length().toInt()
        assertTrue(offset > 0 && offset < bytes.size)
        assertFalse(final.exists())
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes $offset-${bytes.size-1}/${bytes.size}").setBody(Buffer().write(bytes,offset,bytes.size-offset)))
        fetch(server,partial,final)
        assertArrayEquals(bytes,final.readBytes())
    }

    @Test fun invalidRangeCannotPublishOrModifyThePartialFile() = check { server, partial, final ->
        val prefix = bytes.copyOf(4096)
        partial.writeBytes(prefix)
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range","bytes 0-9/10").setBody("0123456789"))
        assertTrue(runCatching { fetch(server,partial,final) }.isFailure)
        assertArrayEquals(prefix,partial.readBytes())
        assertFalse(final.exists())
    }

    @Test fun checksumFailureIsDiscardedAndDamagedFinalCanBeReplaced() = check { server, partial, final ->
        final.writeText("damaged")
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(bytes.size))))
        assertTrue(runCatching { fetch(server,partial,final) }.isFailure)
        assertFalse(partial.exists())
        assertFalse(final.exists())
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        fetch(server,partial,final)
        assertArrayEquals(bytes,final.readBytes())
    }
}
