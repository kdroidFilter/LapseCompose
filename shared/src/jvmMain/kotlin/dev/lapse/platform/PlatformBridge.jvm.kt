package dev.lapse.platform

import dev.lapse.domain.ActivitySnapshot
import dev.lapse.domain.ForegroundApplication
import dev.lapse.nativesupport.NativeHost
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchConfig
import dev.nucleusframework.autolaunch.AutoLaunchResult
import dev.nucleusframework.systeminfo.SystemInfo

actual fun createPlatformBridge(): PlatformBridge = NativePlatformBridge()

class NativePlatformBridge(
    private val host: NativeHost? = runCatching { NativeHost() }.getOrNull(),
) : PlatformBridge {
    init {
        AutoLaunchConfig.registryValueName = "Lapse"
    }

    override fun activitySnapshot(): ActivitySnapshot {
        val idleSec = SystemInfo.idleTime()
        val idleMs = if (idleSec < 0L) 0L else idleSec * 1000L
        val snap = host?.activitySnapshot()
        return ActivitySnapshot(
            idleDurationMs = idleMs,
            locked = snap?.locked == true,
            sleeping = snap?.sleeping == true,
        )
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
