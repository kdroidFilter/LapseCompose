package dev.lapse.platform

import dev.nucleusframework.systeminfo.SystemInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SystemInfoPlatformTest {
    @Test
    fun bootTimeIsStableAndPositive() {
        val first = SystemInfo.osInfo()?.bootTime
        val second = SystemInfo.osInfo()?.bootTime
        assertTrue(first != null && first > 0L)
        assertEquals(first, second)
    }

    @Test
    fun idleTimeIsSane() {
        val idle = SystemInfo.idleTime()
        assertTrue(idle >= -1L)
    }

    @Test
    fun activitySnapshotIdleIsMilliseconds() {
        val snap = NativePlatformBridge().activitySnapshot()
        assertTrue(snap.idleDurationMs >= 0L)
        assertTrue(snap.idleDurationMs < 7L * 24 * 60 * 60 * 1000)
    }
}
