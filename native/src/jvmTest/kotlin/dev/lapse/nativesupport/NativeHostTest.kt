package dev.lapse.nativesupport

import kotlin.test.Test
import kotlin.test.assertTrue

class NativeHostTest {
    @Test
    fun activitySnapshotIsSane() {
        NativeHost().use { host ->
            host.activitySnapshot()
        }
    }

    @Test
    fun foregroundApplicationHasIdentityWhenPresent() {
        NativeHost().use { host ->
            val app = host.foregroundApplication() ?: return
            assertTrue(app.processId > 0)
            assertTrue(app.executablePath.isNotEmpty() || app.executableName.isNotEmpty())
            assertTrue(app.displayName.isNotEmpty() || app.executableName.isNotEmpty())
        }
    }
}
