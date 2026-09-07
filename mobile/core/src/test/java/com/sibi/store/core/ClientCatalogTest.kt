package com.sibi.store.core

import org.junit.Assert.*
import org.junit.Test

class ClientCatalogTest {
    private fun release(tv: Boolean, code: Long = 1, vr: Boolean = false) = Release("app", "App", code, "$code",
        12, 26, emptyList(), tv, "hash$code", listOf("cert"), "/artifact", "", "app.apk", vr)

    @Test fun separatesAppsAndKeepsAnEmptyLibraryEmpty() {
        val phone = StoreApp("phone", "Phone", null, listOf(release(false)))
        val tv = StoreApp("tv", "TV", null, listOf(release(true)))
        val vr = StoreApp("vr", "VR", null, listOf(release(true, vr = true)))
        val empty = StoreApp("empty", "Empty", null, emptyList())
        assertEquals(listOf(phone), catalogForClient(listOf(phone, tv, vr, empty), false))
        assertEquals(listOf(tv), catalogForClient(listOf(phone, tv, vr, empty), true))
        assertEquals(listOf(vr), catalogForClient(listOf(phone, tv, vr, empty), false, true))
        assertTrue(catalogForClient(emptyList(), true).isEmpty())
        assertTrue(catalogForClient(listOf(phone), true).isEmpty())
    }

    @Test fun neverSelectsAnUpdateFromTheOtherPlatformForAMixedPackage() {
        val app = StoreApp("app", "App", null, listOf(release(false, 2), release(true, 9), release(true, 12, true)))
        val phone = catalogForClient(listOf(app), false).single()
        val tv = catalogForClient(listOf(app), true).single()
        val vr = catalogForClient(listOf(app), false, true).single()
        val installed = Installed(9, "9", listOf("cert"))
        assertEquals(Availability.NEWER, availability(newest(phone, 35, emptyList()), installed))
        assertEquals(Availability.CURRENT, availability(newest(tv, 35, emptyList()), installed))
        assertEquals(Availability.UPDATE, availability(newest(vr, 35, emptyList()), installed))
        assertEquals(listOf(2L), phone.versions.map { it.versionCode })
        assertEquals(listOf(9L), tv.versions.map { it.versionCode })
        assertEquals(listOf(12L), vr.versions.map { it.versionCode })
        assertEquals(3, app.versions.size) // Raw cached/server catalog is not modified.
    }

    @Test fun partitionsTheSameRawCatalogUsedForNetworkAndOfflineLoading() {
        val raw = """{"protocolVersion":1,"serverId":"mac","apps":[
          {"packageName":"app","title":"App","versions":[
            {"versionCode":"1","versionName":"1","size":12,"minSdk":26,"abis":[],
             "tv":false,"sha256":"phone","certificates":["cert"],"downloadUrl":"/artifact","addedAt":"","filename":"phone.apk"},
            {"versionCode":"2","versionName":"2","size":12,"minSdk":26,"abis":[],
             "tv":true,"sha256":"tv","certificates":["cert"],"downloadUrl":"/artifact","addedAt":"","filename":"tv.apk"},
            {"versionCode":"3","versionName":"3","size":12,"minSdk":26,"abis":[],
             "tv":true,"vr":true,"sha256":"vr","certificates":["cert"],"downloadUrl":"/artifact","addedAt":"","filename":"vr.apk"}
          ]}]}"""
        val parsed = parseCatalog(raw).second
        assertFalse(parsed.single().versions.first().vr) // Old cached catalogs omit the flag.
        assertTrue(parsed.single().versions.last().vr)
        assertEquals("phone", catalogForClient(parsed, false).single().versions.single().sha256)
        assertEquals("tv", catalogForClient(parsed, true).single().versions.single().sha256)
        assertEquals("vr", catalogForClient(parsed, false, true).single().versions.single().sha256)
    }
}
