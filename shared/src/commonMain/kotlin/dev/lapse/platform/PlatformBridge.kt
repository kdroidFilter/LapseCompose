package dev.lapse.platform

import dev.lapse.domain.ActivitySnapshot
import dev.lapse.domain.ForegroundApplication

interface PlatformBridge {
    fun activitySnapshot(): ActivitySnapshot
    fun bootId(): String
    fun foregroundApplication(): ForegroundApplication?
    fun isAutostartEnabled(): Boolean
    fun setAutostartEnabled(enabled: Boolean): Boolean
}

expect fun createPlatformBridge(): PlatformBridge

class FakePlatformBridge(
    var currentBootId: String = "boot-a",
    var autostart: Boolean = true,
    var snapshot: ActivitySnapshot = ActivitySnapshot(0, locked = false, sleeping = false),
    var foreground: ForegroundApplication? = null,
) : PlatformBridge {
    override fun activitySnapshot(): ActivitySnapshot = snapshot
    override fun bootId(): String = currentBootId
    override fun foregroundApplication(): ForegroundApplication? = foreground
    override fun isAutostartEnabled(): Boolean = autostart
    override fun setAutostartEnabled(enabled: Boolean): Boolean {
        autostart = enabled
        return true
    }
}

/** The Ctrl+Alt combo under the platform's own name: "⌃⌥L" on macOS, "Ctrl+Alt+L" elsewhere. */
expect fun hotkeyLabel(key: Char): String
