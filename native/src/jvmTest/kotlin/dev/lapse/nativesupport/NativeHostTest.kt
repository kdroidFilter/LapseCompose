package dev.lapse.nativesupport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeHostTest {
    @Test
    fun bootIdIsStableAndNonBlank() {
        NativeHost().use { host ->
            val first = host.bootId()
            val second = host.bootId()
            assertTrue(first.isNotBlank())
            assertEquals(first, second)
        }
    }

    @Test
    fun activitySnapshotIsSane() {
        NativeHost().use { host ->
            val snap = host.activitySnapshot()
            assertTrue(snap.idleMilliseconds >= 0L)
        }
    }

    @Test
    fun foregroundApplicationHasIdentityWhenPresent() {
        NativeHost().use { host ->
            val app = host.foregroundApplication() ?: return
            assertTrue(app.processId > 0)
            assertTrue(app.executablePath.isNotEmpty())
            assertTrue(app.executableName.isNotEmpty())
            assertTrue(app.displayName.isNotEmpty())
        }
    }
}
