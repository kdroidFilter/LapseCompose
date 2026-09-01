package dev.lapse.nativesupport

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeHostTest {
    @Test
    fun activitySnapshotIsSane() {
        NativeHost().use { host ->
            val snap = host.activitySnapshot()
            assertTrue(snap.idleMilliseconds >= -1L)
        }
    }

    @Test
    fun windowsIdleIsMillisecondsWhenAvailable() {
        NativeHost().use { host ->
            val idle = host.activitySnapshot().idleMilliseconds
            val windows = System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)
            if (windows) {
                assertTrue(idle >= 0L)
                assertTrue(idle < 7L * 24 * 60 * 60 * 1000)
            }
        }
    }

    @Test
    fun activitySnapshotIsNotSleepingWhileAwake() {
        NativeHost().use { host ->
            host.activitySnapshot()
            assertFalse(host.activitySnapshot().sleeping)
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
