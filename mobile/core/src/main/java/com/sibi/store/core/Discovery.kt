package com.sibi.store.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** DNS-SD delegates multicast discovery to Android; no subnet scanning or location lookup. */
class Discovery(context: Context, private val found: (Host) -> Unit, private val error: (String) -> Unit) {
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    @Volatile private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private fun newListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) { if(listener === this) listener = null }
        override fun onStartDiscoveryFailed(type: String, code: Int) { if(listener === this) { listener = null; error("Discovery unavailable ($code). Enter your Mac's address.") } }
        override fun onStopDiscoveryFailed(type: String, code: Int) { if(listener === this) listener = null }
        override fun onServiceFound(info: NsdServiceInfo) { synchronized(queue) { if(listener === this) { queue.add(info); resolveNext() } } }
        override fun onServiceLost(info: NsdServiceInfo) { /* HTTP checks decide connection liveness. */ }
    }
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val session = listener ?: return
        if (resolving || queue.isEmpty()) return
        resolving = true
        manager.resolveService(queue.removeFirst(), object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) { synchronized(queue) { resolving = false; resolveNext() } }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val address = info.host?.hostAddress
                if (address != null && listener === session) {
                    val host = if (address.contains(':')) "[$address]" else address
                    found(Host(info.serviceName, "http://$host:${info.port}", info.attributes["serverId"]?.toString(Charsets.UTF_8) ?: ""))
                }
                synchronized(queue) { resolving = false; resolveNext() }
            }
        })
    }
    fun start() {
        if (listener != null) return
        val session = newListener()
        listener = session
        try { manager.discoverServices("_sibistore._tcp.", NsdManager.PROTOCOL_DNS_SD, session) }
        catch(e: Exception) { if(listener === session) listener = null; error(e.message ?: "Discovery unavailable") }
    }
    fun stop() {
        val session = listener
        listener = null
        synchronized(queue) { queue.clear() }
        if(session != null) try { manager.stopServiceDiscovery(session) } catch (_: Exception) {}
    }
}
