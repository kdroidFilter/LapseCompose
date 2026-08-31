package dev.lapse.platform

import dev.lapse.domain.ActivitySnapshot
import dev.lapse.domain.ForegroundApplication
import dev.lapse.nativesupport.NativeHost
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.autolaunch.AutoLaunchConfig
import dev.nucleusframework.autolaunch.AutoLaunchResult

actual fun createPlatformBridge(): PlatformBridge = NativePlatformBridge()

class NativePlatformBridge(
    private val host: NativeHost = NativeHost(),
) : PlatformBridge {
    init {
        AutoLaunchConfig.registryValueName = "Lapse"
    }

    override fun activitySnapshot(): ActivitySnapshot {
        val snap = host.activitySnapshot()
        return ActivitySnapshot(
            idleDurationMs = snap.idleMilliseconds,
            locked = snap.locked,
            sleeping = snap.sleeping,
        )
    }

    override fun bootId(): String = host.bootId()

    override fun foregroundApplication(): ForegroundApplication? {
        val app = host.foregroundApplication() ?: return null
        return ForegroundApplication(
            processId = app.processId,
            executablePath = app.executablePath,
            executableName = app.executableName,
            displayName = app.displayName,
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
