package com.sibi.store.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

/** Enumerate only the attached private subnet; never scan Internet or VPN routes. */
internal fun lanAddresses(bytes: ByteArray, prefix: Int): List<String> {
    if (bytes.size != 4 || prefix !in 20..30) return emptyList()
    val ip = bytes.fold(0L) { n, b -> (n shl 8) or (b.toLong() and 255) }
    val a = bytes[0].toInt() and 255
    val b = bytes[1].toInt() and 255
    if (!(a == 10 || a == 172 && b in 16..31 || a == 192 && b == 168)) return emptyList()
    val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
    val network = ip and mask
    val broadcast = network or (mask xor 0xffffffffL)
    return ((network + 1) until broadcast).filter { it != ip }.map { address ->
        listOf(24, 16, 8, 0).joinToString(".") { shift -> ((address shr shift) and 255).toString() }
    }
}

internal fun probeSibi(client: OkHttpClient, base: String): Host? = try {
    client.newCall(Request.Builder().url("$base/api/v1/info").build()).execute().use { response ->
        if (!response.isSuccessful) return null
        // An arbitrary web server must not become a discovered Sibi server.
        val source = response.body?.source() ?: return null
        source.request(4097)
        if (source.buffer.size > 4096) return null
        val info = JSONObject(source.readUtf8())
        val id = info.optString("serverId")
        val name = info.optString("name")
        if (info.optInt("protocolVersion") != 1 || id.isBlank() || !name.startsWith("Sibi Store")) null
        else Host(name, base, id)
    }
} catch (_: Exception) { null }

/** Automatic fallback when Wi-Fi multicast is filtered. Runs only while the app is visible. */
internal class LanDiscovery(context: Context, private val found: (Host) -> Unit) {
    private val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var job: Job? = null
    fun start() {
        if (job != null) return
        job = CoroutineScope(Dispatchers.IO).launch {
            delay(3000) // Give normal DNS-SD discovery a head start.
            while (isActive) {
                try { scan() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { android.util.Log.w("SibiDiscovery", "LAN discovery will retry", e) }
                delay(60000)
            }
        }
    }
    @Suppress("DEPRECATION")
    private suspend fun scan() {
        for (network in manager.allNetworks) {
            currentCoroutineContext().ensureActive()
            val caps = manager.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) continue
            val addresses = manager.getLinkProperties(network)?.linkAddresses.orEmpty()
                .filter { it.address is Inet4Address }
                .flatMap { lanAddresses(it.address.address, it.prefixLength) }.distinct()
            val client = OkHttpClient.Builder().socketFactory(network.socketFactory)
                .proxy(java.net.Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false)
                .connectTimeout(600, TimeUnit.MILLISECONDS).readTimeout(800, TimeUnit.MILLISECONDS)
                .callTimeout(1200, TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
            try {
                coroutineScope {
                    // Fixed worker count bounds sockets and coroutine memory even on a /20.
                    val queue = java.util.concurrent.ConcurrentLinkedQueue(addresses)
                    repeat(24) {
                        launch {
                            while (isActive) {
                                val address = queue.poll() ?: break
                                val host = probeSibi(client, "http://$address:8743")
                                ensureActive()
                                if (host != null) {
                                    android.util.Log.d("SibiDiscovery", "Verified LAN server at ${host.url}")
                                    found(host)
                                }
                            }
                        }
                    }
                }
            } finally {
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }
    fun stop() { job?.cancel(); job = null }
}
