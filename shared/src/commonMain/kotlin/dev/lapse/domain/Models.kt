package dev.lapse.domain

import androidx.compose.runtime.Immutable

enum class UserActivityState { Active, Paused, Idle, Locked, Sleeping }

enum class OverlayMode { Expanded, Collapsed }

enum class DashboardPage { Overview, Applications, Sessions, Settings }

@Immutable
data class SessionTask(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
)

@Immutable
data class ForegroundApplication(
    val processId: Int,
    val executablePath: String,
    val executableName: String,
    val displayName: String,
    val windowTitle: String = "",
    val observedAtMs: Long = 0,
) {
    val id: String
        get() = (executablePath.ifEmpty { executableName }).lowercase()

    val resolvedDisplayName: String
        get() = applicationDisplayName(
            executableName = executableName,
            reportedDisplayName = displayName,
            windowTitle = windowTitle,
        )
}

@Immutable
data class ApplicationUsage(
    val applicationId: String,
    val displayName: String,
    val executableName: String,
    val activeDurationMs: Long,
)

@Immutable
data class ComputerSession(
    val id: String,
    val startedAtMs: Long,
    val activeDurationMs: Long,
    val tasks: List<SessionTask> = emptyList(),
    val endedAtMs: Long? = null,
    val isPaused: Boolean = false,
    val applicationUsage: List<ApplicationUsage> = emptyList(),
)

@Immutable
data class LapsePreferences(
    val overlayMode: OverlayMode = OverlayMode.Expanded,
    val alwaysOnTop: Boolean = true,
    val autostart: Boolean = false,
    /** Ctrl+Alt+L (Control+Option+L on macOS) toggles the overlay system-wide. */
    val globalHotkey: Boolean = false,
    /** Overlay origin in logical dp. Null until the user moves it. */
    val windowX: Double? = null,
    val windowY: Double? = null,
    /** Dashboard origin in logical dp. Null uses the platform default. */
    val dashboardX: Double? = null,
    val dashboardY: Double? = null,
    val dashboardWidth: Double = 1000.0,
    val dashboardHeight: Double = 680.0,
)

@Immutable
data class PersistedAppState(
    val bootId: String,
    val session: ComputerSession,
    val preferences: LapsePreferences,
    val sessionHistory: List<ComputerSession> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 2
    }
}

@Immutable
data class ActivitySnapshot(
    val idleDurationMs: Long,
    val locked: Boolean,
    val sleeping: Boolean,
)

@Immutable
data class DailyUsagePoint(
    val year: Int,
    val monthNumber: Int,
    val dayOfMonth: Int,
    val isoDayOfWeek: Int,
    val durationMs: Long,
)

@Immutable
data class UsageSummary(
    val todayMs: Long,
    val sevenDayAverageMs: Long,
    val thisWeekMs: Long,
    val sessionsToday: Int,
    val lastSevenDays: List<DailyUsagePoint>,
)

object AppConstants {
    const val NAME = "Lapse"
    const val IDLE_TIMEOUT_MS = 5 * 60 * 1000L
    const val ACTIVITY_POLL_MS = 1_000L
    const val PERSIST_CHECKPOINT_MS = 30_000L
    const val COLLAPSED_WIDTH = 244
    const val COLLAPSED_HEIGHT = 52
    const val EXPANDED_WIDTH = 312
    const val EXPANDED_HEIGHT = 356
}

/** Where the updater stands, surfaced in Settings and the tray. */
enum class UpdateStatus { Checking, UpToDate, Ready, Unsupported, Failed }
