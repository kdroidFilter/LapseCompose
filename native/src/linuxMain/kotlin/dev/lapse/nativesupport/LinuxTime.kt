@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.clock_gettime
import platform.posix.timespec

internal const val CLOCK_REALTIME = 0
internal const val CLOCK_MONOTONIC = 1
internal const val CLOCK_BOOTTIME = 7

internal fun linuxClockMs(clockId: Int): Long = memScoped {
    val ts = alloc<timespec>()
    if (clock_gettime(clockId, ts.ptr) != 0) return 0L
    ts.tv_sec * 1000L + ts.tv_nsec / 1_000_000L
}
