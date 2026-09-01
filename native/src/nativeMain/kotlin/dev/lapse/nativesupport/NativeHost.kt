package dev.lapse.nativesupport

/**
 * Lock, sleep, and focused window. Windows also supplies millisecond idle via GetLastInputInfo.
 * Boot id and non-Windows idle come from nucleus.system-info.
 */
expect class NativeHost() {
    fun activitySnapshot(): ActivitySnapshot
    fun foregroundApplication(): ForegroundApp?
}
