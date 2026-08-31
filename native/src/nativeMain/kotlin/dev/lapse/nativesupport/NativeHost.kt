package dev.lapse.nativesupport

expect class NativeHost() {
    fun activitySnapshot(): ActivitySnapshot
    fun bootId(): String
    fun foregroundApplication(): ForegroundApp?
}
