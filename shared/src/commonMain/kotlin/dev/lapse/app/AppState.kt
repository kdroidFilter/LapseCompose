package dev.lapse.app

import androidx.compose.runtime.Immutable
import dev.lapse.domain.ComputerSession
import dev.lapse.domain.DashboardPage
import dev.lapse.domain.LapsePreferences
import dev.lapse.domain.SessionTask
import dev.lapse.domain.UserActivityState

@Immutable
data class AppState(
    val session: ComputerSession = ComputerSession(
        id = "",
        startedAtMs = 0,
        activeDurationMs = 0,
    ),
    val preferences: LapsePreferences = LapsePreferences(),
    val activityState: UserActivityState = UserActivityState.Idle,
    val displayDurationMs: Long = 0,
    val isReady: Boolean = false,
    val error: AppError? = null,
    val sessionHistory: List<ComputerSession> = emptyList(),
    val overlayVisible: Boolean = true,
    val dashboardOpen: Boolean = false,
    val dashboardPage: DashboardPage = DashboardPage.Overview,
    /** Bumped on each OpenDashboard / OpenDashboardSettings so the window can take focus. */
    val dashboardFocusSeq: Int = 0,
) {
    val allSessions: List<ComputerSession>
        get() = sessionHistory + session.copy(activeDurationMs = displayDurationMs)

    val completedTaskCount: Int
        get() = session.tasks.count { it.isCompleted }
}

@Immutable
enum class AppError { NativeUnavailable }

@Immutable
sealed interface AppIntent {
    data object TogglePause : AppIntent
    data class AddTask(val title: String) : AppIntent
    data class ToggleTask(val id: String) : AppIntent
    data class EditTask(val id: String, val title: String) : AppIntent
    data class DeleteTask(val id: String) : AppIntent
    data object ToggleOverlayMode : AppIntent
    data object HideOverlay : AppIntent
    data object ShowOverlay : AppIntent
    data object ToggleOverlay : AppIntent
    data object OpenDashboard : AppIntent
    data object OpenDashboardSettings : AppIntent
    data object CloseDashboard : AppIntent
    data class SetDashboardPage(val page: DashboardPage) : AppIntent
    data class SetAlwaysOnTop(val value: Boolean) : AppIntent
    data class SetAutostart(val value: Boolean) : AppIntent
    data class SetGlobalHotkey(val value: Boolean) : AppIntent
    data class SetPauseHotkey(val value: Boolean) : AppIntent
    data class SaveOverlayPosition(val x: Double, val y: Double) : AppIntent
    data class SaveDashboardBounds(val x: Double, val y: Double, val width: Double, val height: Double) : AppIntent
    data object Quit : AppIntent
    data object Tick : AppIntent
    data class ActivityChanged(val state: UserActivityState) : AppIntent
    data class ForegroundChanged(val processId: Int, val path: String, val exe: String, val name: String, val title: String) : AppIntent
    data object ForegroundCleared : AppIntent
}

fun AppState.withTasks(tasks: List<SessionTask>): AppState =
    copy(session = session.copy(tasks = tasks))
