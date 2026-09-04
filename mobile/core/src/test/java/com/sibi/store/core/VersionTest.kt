package com.sibi.store.core

import org.junit.Assert.*
import org.junit.Test

class VersionTest {
    private fun version(code:Long,minSdk:Int=26,abis:List<String> = emptyList(),cert:List<String> = listOf("a")) = Release("test","Test",code,"display-only",12,minSdk,abis,true,"hash",cert,"/artifact","","app.apk")
    @Test fun choosesHighestCompatibleBuild() {
        val app=StoreApp("test","Test",null,listOf(version(41),version(42,35),version(43,26,listOf("x86"))))
        assertEquals(41L,newest(app,34,listOf("arm64-v8a"))!!.versionCode)
        assertEquals(42L,newest(app,35,listOf("arm64-v8a"))!!.versionCode)
    }
    @Test fun handlesInstallUpdateCurrentNewerAndSignatureMismatch() {
        assertEquals(Availability.INSTALL,availability(version(42),null))
        assertEquals(Availability.UPDATE,availability(version(42),Installed(41,"ignored",listOf("a"))))
        assertEquals(Availability.CURRENT,availability(version(42),Installed(42,"ignored",listOf("a"))))
        assertEquals(Availability.NEWER,availability(version(42),Installed(43,"ignored",listOf("a"))))
        assertEquals(Availability.SIGNATURE_MISMATCH,availability(version(42),Installed(41,"ignored",listOf("b"))))
        assertEquals(Availability.INCOMPATIBLE,availability(null,null))
    }
    @Test fun versionComparisonDoesNotLose64BitPrecision() {
        assertEquals(Availability.UPDATE,availability(version(9007199254740993),Installed(9007199254740992,"",listOf("a"))))
    }
}
