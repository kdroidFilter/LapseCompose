package dev.lapse.nativesupport

data class ForegroundApp(
    val processId: Int,
    val executablePath: String,
    val executableName: String,
    val displayName: String,
    val windowTitle: String,
)

data class ActivitySnapshot(
    /** Milliseconds since last input, or -1 if this host does not provide idle. */
    val idleMilliseconds: Long,
    val locked: Boolean,
    val sleeping: Boolean,
)
