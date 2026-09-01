package dev.lapse.nativesupport

/** Lock, sleep, and focused window. Idle time and boot id come from nucleus.system-info. */
expect class NativeHost() {
    fun activitySnapshot(): ActivitySnapshot
    fun foregroundApplication(): ForegroundApp?
}
