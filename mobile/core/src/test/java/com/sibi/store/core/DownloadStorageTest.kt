package com.sibi.store.core

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadStorageTest {
    private val first = "a".repeat(64)
    private val active = "b".repeat(64)
    private val installing = "c".repeat(64)
    @Test fun measuresApksAndPartialFilesAndPreservesAnythingInUse() = runBlocking {
        val root = Files.createTempDirectory("sibi-storage").toFile()
        try {
            File(root,"$first.apk").writeBytes(ByteArray(1234))
            File(root,"$first.part").writeBytes(ByteArray(66))
            File(root,"$active.part").writeBytes(ByteArray(100))
            File(root,"$installing.apk").writeBytes(ByteArray(200))
            File(root,"other.txt").writeText("must stay")
            assertEquals(DownloadUsage(1600,4),downloadUsage(root))
            val lock = downloadLock(active); lock.lock()
            try {
                val cleared = clearStoredDownloads(root,setOf(installing))
                assertEquals(setOf(first),cleared.hashes)
                assertEquals(0,cleared.failed)
                assertEquals(DownloadUsage(300,2),downloadUsage(root))
            } finally { lock.unlock() }
            clearStoredDownloads(root,emptySet())
            assertEquals(DownloadUsage(),downloadUsage(root))
            assertTrue(File(root,"other.txt").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun neverDeletesOutsideDownloadDirectoryThroughSymlink() {
        val root=Files.createTempDirectory("sibi-storage").toFile()
        val outside=Files.createTempFile("sibi-outside", ".apk").toFile()
        try {
            outside.writeText("keep")
            Files.createSymbolicLink(File(root,"$first.apk").toPath(),outside.toPath())
            assertEquals(DownloadUsage(),downloadUsage(root))
            clearStoredDownloads(root,emptySet())
            assertEquals("keep",outside.readText())
        } finally { root.deleteRecursively(); outside.delete() }
    }
    @Test fun selfUpdateRequiresVerifiedVersionAndSignatureAndCancelledReinstallIsNotSuccess() {
        val current=Installed(5,"0.1.4",listOf("certificate"))
        assertTrue(confirmsInstallation(current,5,4,listOf("certificate"),false))
        assertFalse(confirmsInstallation(current,6,5,listOf("certificate"),false))
        assertFalse(confirmsInstallation(current,5,4,listOf("other"),true))
        assertFalse(confirmsInstallation(null,5,-1,listOf("certificate"),true))
        assertFalse(confirmsInstallation(current,5,5,listOf("certificate"),false))
        assertTrue(confirmsInstallation(current,5,5,listOf("certificate"),true))
    }
}
