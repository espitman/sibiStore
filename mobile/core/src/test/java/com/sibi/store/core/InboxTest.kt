package com.sibi.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxTest {
    private val hash = "a".repeat(64)

    @Test fun parsesAuthenticatedServerOfferShape() {
        val offers = parseInbox("""{"serverId":"mac-1","files":[{"id":"offer_1","name":"notes.pdf","size":42,"sha256":"$hash","downloadUrl":"/api/v1/inbox/offer_1/file"}]}""","mac-1")
        assertEquals(listOf(InboxOffer("offer_1","notes.pdf",42,hash,"/api/v1/inbox/offer_1/file")),offers)
    }

    @Test fun rejectsServerIdentityAndCrossOriginDownloads() {
        assertTrue(runCatching { parseInbox("""{"serverId":"other","files":[]}""","mac-1") }.isFailure)
        assertTrue(runCatching { resolveOfferUrl("http://192.168.1.2:8743","http://evil.test/file") }.isFailure)
    }

    @Test fun stripsPathsAndUnsafeCharactersFromNames() {
        assertEquals("report_.pdf",safeInboxName("../private/report\u0000.pdf"))
        assertTrue(safeInboxName("x".repeat(300) + ".archive.zip").endsWith(".zip"))
        assertEquals(180,safeInboxName("x".repeat(300) + ".archive.zip").length)
        assertEquals(64,localOfferKey("../../unsafe/server","offer_1").length)
    }
}
