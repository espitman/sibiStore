package com.sibi.store.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager

/** DNS-SD with foreground multicast reception and a local HTTP discovery fallback. */
class Discovery(context: Context, private val found: (Host) -> Unit, private val error: (String) -> Unit) {
    private val fallback = LanDiscovery(context, found)
    private val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicast: WifiManager.MulticastLock? = null
    private fun releaseMulticast() { multicast?.let { if (it.isHeld) it.release() }; multicast = null }
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    @Volatile private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private fun newListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) {}
        override fun onDiscoveryStopped(type: String) { if(listener === this) listener = null }
        override fun onStartDiscoveryFailed(type: String, code: Int) { if(listener === this) { listener = null; releaseMulticast(); error("Wi-Fi discovery unavailable ($code). Searching the local network…") } }
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
        fallback.start()
        try {
            multicast = wifi?.createMulticastLock("sibi-discovery")?.apply { setReferenceCounted(false); acquire() }
        } catch (_: SecurityException) { /* HTTP fallback remains available. */ }
        val session = newListener()
        listener = session
        try { manager.discoverServices("_sibistore._tcp.", NsdManager.PROTOCOL_DNS_SD, session) }
        catch(e: Exception) { if(listener === session) { listener = null; releaseMulticast() }; error(e.message ?: "Discovery unavailable") }
    }
    fun stop() {
        fallback.stop()
        releaseMulticast()
        val session = listener
        listener = null
        synchronized(queue) { queue.clear() }
        if(session != null) try { manager.stopServiceDiscovery(session) } catch (_: Exception) {}
    }
}
