package com.sibi.store.core

import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/** A capability grants only one accepted file, never access to arbitrary paths. */
internal data class PeerSource(val size: Long, val hash: String, val token: String, val open: () -> InputStream, val allowed: () -> Boolean, val progress: (Long) -> Unit = {})
internal class PeerFileServer(private val resolve: (String) -> PeerSource?) : AutoCloseable {
    private val listener = ServerSocket(0)
    private val workers = Executors.newFixedThreadPool(4)
    private val slots = Semaphore(4)
    private val sockets = java.util.concurrent.ConcurrentHashMap.newKeySet<Socket>()
    val port get() = listener.localPort
    init {
        Thread({
            while (!listener.isClosed) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                if (!slots.tryAcquire()) { socket.close(); continue }
                sockets.add(socket)
                workers.execute { try { socket.use(::serve) } catch (_: Exception) { } finally { sockets.remove(socket); slots.release() } }
            }
        }, "sibi-peer-listener").apply { isDaemon = true; start() }
    }
    private fun serve(socket: Socket) {
        socket.soTimeout = 15000
        val input = socket.getInputStream().buffered()
        fun line(): String {
            val out = StringBuilder()
            while (out.length < 4096) {
                val c = input.read(); require(c >= 0) { "Incomplete request" }
                if(c == 10) return out.toString().trimEnd('\r')
                out.append(c.toChar())
            }
            error("Request too large")
        }
        val first = line().split(' ')
        val headers = mutableMapOf<String,String>()
        var ended = false
        repeat(32) {
            if (!ended) { val l = line(); if(l.isEmpty()) ended = true else { val i=l.indexOf(':'); require(i>0); headers[l.substring(0,i).lowercase()]=l.substring(i+1).trim() } }
        }
        require(ended)
        val output = socket.getOutputStream().buffered()
        fun errorResponse(code: Int) { output.write("HTTP/1.1 $code Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); output.flush() }
        if(first.size != 3 || first[0] != "GET") { errorResponse(405); return }
        val source = resolve(first[1])
        if(source == null || !source.allowed() || !MessageDigest.isEqual((headers["authorization"] ?: "").toByteArray(), ("Bearer " + source.token).toByteArray())) { errorResponse(403); return }
        val range = headers["range"]
        val start = if(range != null && (headers["if-range"] == null || headers["if-range"] == "\"${source.hash}\"")) {
            val match=Regex("bytes=(\\d+)-").matchEntire(range)
            val n=match?.groupValues?.get(1)?.toLongOrNull()
            if(n == null || n < 0 || n >= source.size) { errorResponse(416); return }
            n
        } else 0L
        source.open().use { file ->
            var skip=start
            while(skip>0) { val n=file.skip(skip); if(n>0) skip-=n else { require(file.read()>=0); skip-- } }
            val ranged = range != null && (headers["if-range"] == null || headers["if-range"] == "\"${source.hash}\"")
            val head="HTTP/1.1 ${if(ranged) "206 Partial Content" else "200 OK"}\r\nContent-Length: ${source.size-start}\r\nContent-Type: application/octet-stream\r\nETag: \"${source.hash}\"\r\nAccept-Ranges: bytes\r\nConnection: close\r\n" + (if(ranged) "Content-Range: bytes $start-${source.size-1}/${source.size}\r\n" else "") + "\r\n"
            output.write(head.toByteArray()); output.flush()
            val buffer=ByteArray(65536); var remaining=source.size-start
            while(remaining>0 && source.allowed()) { val n=file.read(buffer,0,minOf(remaining,buffer.size.toLong()).toInt()); require(n>0); output.write(buffer,0,n); remaining-=n; source.progress(source.size-remaining) }
            output.flush()
        }
    }
    override fun close() { listener.close(); sockets.forEach { runCatching { it.close() } }; workers.shutdownNow() }
}
