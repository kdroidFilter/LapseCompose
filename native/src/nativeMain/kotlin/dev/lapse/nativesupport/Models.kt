package dev.lapse.nativesupport

data class ForegroundApp(
    val processId: Int,
    val executablePath: String,
    val executableName: String,
    val displayName: String,
    val windowTitle: String,
)

data class ActivitySnapshot(
    val locked: Boolean,
    val sleeping: Boolean,
)
