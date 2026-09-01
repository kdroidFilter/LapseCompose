@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.lapse.nativesupport

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.clock_gettime
import platform.posix.timespec

actual class NativeHost actual constructor() {
    private var lastTickMs: Long = monotonicMs()
    private var lastWallMs: Long = wallMs()
    private var sleeping: Boolean = false

    actual fun activitySnapshot(): ActivitySnapshot {
        refreshSleep()
        return ActivitySnapshot(
            locked = LinuxDbus.isLocked() || lockProcessRunning(),
            sleeping = sleeping,
        )
    }

    actual fun foregroundApplication(): ForegroundApp? =
        LinuxIpc.foregroundApplication() ?: LinuxX11.foregroundApplication()

    private fun refreshSleep() {
        val tick = monotonicMs()
        val wall = wallMs()
        sleeping = wall - lastWallMs - (tick - lastTickMs) > 15_000L
        lastTickMs = tick
        lastWallMs = wall
    }
}

private const val CLOCK_REALTIME = 0
private const val CLOCK_MONOTONIC = 1

private fun monotonicMs(): Long = clockMs(CLOCK_MONOTONIC)

private fun wallMs(): Long = clockMs(CLOCK_REALTIME)

private fun clockMs(clockId: Int): Long = memScoped {
    val ts = alloc<timespec>()
    if (clock_gettime(clockId, ts.ptr) != 0) return 0L
    ts.tv_sec * 1000L + ts.tv_nsec / 1_000_000L
}
