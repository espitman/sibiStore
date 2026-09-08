package com.sibi.store.core

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class PeerFileServerTest {
    @Test fun bytesRequireApprovalAndCapabilityAndSupportResume() {
        val bytes="A direct file between devices".toByteArray()
        var accepted=false
        val server=PeerFileServer { path -> if(path=="/peer/offer/file") PeerSource(bytes.size.toLong(),"a".repeat(64),"secret-capability",{ByteArrayInputStream(bytes)},{accepted}) else null }
        try {
            val client=OkHttpClient()
            fun get(token:String="secret-capability",range:String?=null):Pair<Int,ByteArray> {
                val r=Request.Builder().url("http://127.0.0.1:${server.port}/peer/offer/file").header("Authorization","Bearer $token")
                if(range!=null)r.header("Range",range)
                return client.newCall(r.build()).execute().use { it.code to it.body!!.bytes() }
            }
            assertEquals(403,get().first)
            accepted=true
            assertEquals(403,get("wrong").first)
            val full=get();assertEquals(200,full.first);assertArrayEquals(bytes,full.second)
            val resumed=get(range="bytes=9-");assertEquals(206,resumed.first);assertArrayEquals(bytes.copyOfRange(9,bytes.size),resumed.second)
            assertEquals(416,get(range="bytes=999999999999999999999999-").first)
            accepted=false
            assertEquals(403,get().first)
        } finally { server.close() }
    }
    @Test fun directDownloadResumesAndVerifiesBytes() = runBlocking {
        val root=Files.createTempDirectory("sibi-peer-test").toFile()
        val original=File(root,"original").apply { writeBytes(ByteArray(200000) { (it % 251).toByte() }) }
        val hash=sha256(original)
        val partial=File(root,"download.part").apply { writeBytes(original.readBytes().copyOfRange(0,70000)) }
        val ready=File(root,"download.ready")
        try {
            PeerFileServer { path -> if(path=="/peer/job/file") PeerSource(original.length(),hash,"accepted-token",{original.inputStream()},{true}) else null }.use { server ->
                var progress=0L
                transfer(OkHttpClient(),"http://127.0.0.1:${server.port}/peer/job/file","test",hash,original.length(),partial,ready,mapOf("Authorization" to "Bearer accepted-token")) { progress=it }
                assertEquals(original.length(),progress)
                assertEquals(hash,sha256(ready))
                assertFalse(partial.exists())
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun unknownPathsCannotOpenFiles() {
        var opened=false
        PeerFileServer { path -> if(path=="/peer/offer/file") PeerSource(0,"b".repeat(64),"token",{opened=true;ByteArrayInputStream(byteArrayOf())},{true}) else null }.use { server ->
            val request=Request.Builder().url("http://127.0.0.1:${server.port}/etc/passwd").header("Authorization","Bearer token").build()
            OkHttpClient().newCall(request).execute().use { assertEquals(403,it.code) }
            assertFalse(opened)
        }
    }
}
