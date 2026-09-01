package dev.lapse.platform

import dev.lapse.domain.ActivitySnapshot
import dev.lapse.domain.ForegroundApplication
import dev.lapse.nativesupport.NativeHost
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchConfig
import dev.nucleusframework.autolaunch.AutoLaunchResult
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.systeminfo.SystemInfo

actual fun createPlatformBridge(): PlatformBridge = NativePlatformBridge()

actual fun hotkeyLabel(key: Char): String =
    if (Platform.Current == Platform.MacOS) "⌃⌥$key" else "Ctrl+Alt+$key"

class NativePlatformBridge(
    private val host: NativeHost? = runCatching { NativeHost() }.getOrNull(),
) : PlatformBridge {
    init {
        AutoLaunchConfig.registryValueName = "Lapse"
    }

    override fun activitySnapshot(): ActivitySnapshot {
        val snap = host?.activitySnapshot()
        val idleMs = snap?.idleMilliseconds?.takeIf { it >= 0L } ?: systemIdleMs()
        return ActivitySnapshot(
            idleDurationMs = idleMs,
            locked = snap?.locked == true,
            sleeping = snap?.sleeping == true,
        )
    }

    private fun systemIdleMs(): Long {
        val idleSec = SystemInfo.idleTime()
        return if (idleSec < 0L) 0L else idleSec * 1000L
    }

    override fun bootId(): String {
        val bootTime = SystemInfo.osInfo()?.bootTime ?: 0L
        check(bootTime > 0L) { "SystemInfo bootTime unavailable" }
        return bootTime.toString()
    }

    override fun foregroundApplication(): ForegroundApplication? {
        val app = host?.foregroundApplication() ?: return null
        val path: String
        val executableName: String
        val displayName: String
        if (app.executablePath.isNotEmpty()) {
            path = app.executablePath
            executableName = app.executableName
            displayName = app.displayName.ifEmpty { executableName }
        } else {
            val proc = runCatching { SystemInfo.process(app.processId.toLong()) }.getOrNull()
            path = proc?.exe.orEmpty()
            executableName = app.executableName.ifEmpty { proc?.name.orEmpty() }
            displayName = app.displayName.ifEmpty { executableName }
        }
        if (path.isEmpty() && executableName.isEmpty()) return null
        return ForegroundApplication(
            processId = app.processId,
            executablePath = path,
            executableName = executableName,
            displayName = displayName.ifEmpty { executableName },
            windowTitle = app.windowTitle,
            observedAtMs = System.currentTimeMillis(),
        )
    }

    override fun isAutostartEnabled(): Boolean = AutoLaunch.isEnabled()

    override fun setAutostartEnabled(enabled: Boolean): Boolean {
        val result = if (enabled) AutoLaunch.enable() else AutoLaunch.disable()
        return result == AutoLaunchResult.OK || result == AutoLaunchResult.UNCHANGED
    }
}
