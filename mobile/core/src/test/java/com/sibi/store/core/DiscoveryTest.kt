package com.sibi.store.core

import java.net.InetAddress
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    @Test fun coversBothHalvesOfHomeSlash23WithoutNetworkBroadcastOrSelf() {
        val addresses = lanAddresses(InetAddress.getByName("10.22.11.45").address, 23)
        assertEquals(509, addresses.size)
        assertTrue("10.22.10.29" in addresses)
        assertTrue("10.22.11.254" in addresses)
        assertFalse("10.22.10.0" in addresses)
        assertFalse("10.22.11.255" in addresses)
        assertFalse("10.22.11.45" in addresses)
    }
    @Test fun ignoresPublicHugeAndIpv6Networks() {
        assertTrue(lanAddresses(InetAddress.getByName("8.8.8.8").address, 24).isEmpty())
        assertTrue(lanAddresses(InetAddress.getByName("10.0.0.1").address, 8).isEmpty())
        assertTrue(lanAddresses(InetAddress.getByName("::1").address, 24).isEmpty())
    }
    @Test fun acceptsOnlyBoundedSibiProtocolResponses() {
        MockWebServer().use { server ->
            server.start()
            val client = OkHttpClient.Builder().followRedirects(false).build()
            val base = server.url("/").toString().trimEnd('/')
            server.enqueue(MockResponse().setBody("""{"protocolVersion":1,"serverId":"stable-id","name":"Sibi Store — Mac"}"""))
            assertEquals(Host("Sibi Store — Mac", base, "stable-id"), probeSibi(client, base))
            assertEquals("/api/v1/info", server.takeRequest().path)
            for (body in listOf("<html>Router</html>", """{"protocolVersion":2,"serverId":"id","name":"Sibi Store"}""",
                """{"protocolVersion":1,"serverId":"","name":"Sibi Store"}""", "x".repeat(4097))) {
                server.enqueue(MockResponse().setBody(body))
                assertNull(probeSibi(client, base))
            }
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "http://example.invalid/"))
            assertNull(probeSibi(client, base))
        }
    }
}
