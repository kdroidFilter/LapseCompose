package dev.lapse.nativesupport

actual class NativeHost actual constructor() {
    private var lastTickMs: Long = linuxClockMs(CLOCK_MONOTONIC)
    private var lastWallMs: Long = linuxClockMs(CLOCK_REALTIME)
    private var lastSleepDebtMs: Long = sleepDebtMs()
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
        val debt = sleepDebtMs()
        if (debt >= 0L && lastSleepDebtMs >= 0L) {
            sleeping = debt - lastSleepDebtMs > 15_000L
            lastSleepDebtMs = debt
            lastTickMs = linuxClockMs(CLOCK_MONOTONIC)
            lastWallMs = linuxClockMs(CLOCK_REALTIME)
            return
        }
        val tick = linuxClockMs(CLOCK_MONOTONIC)
        val wall = linuxClockMs(CLOCK_REALTIME)
        sleeping = wall - lastWallMs - (tick - lastTickMs) > 15_000L
        lastTickMs = tick
        lastWallMs = wall
    }
}

/** CLOCK_BOOTTIME includes suspend; CLOCK_MONOTONIC does not. Their gap is time spent asleep. */
private fun sleepDebtMs(): Long {
    val boot = linuxClockMs(CLOCK_BOOTTIME)
    if (boot <= 0L) return -1L
    return (boot - linuxClockMs(CLOCK_MONOTONIC)).coerceAtLeast(0L)
}
