package dev.lapse.app

import androidx.lifecycle.ViewModel
import dev.lapse.data.AppStore
import dev.lapse.domain.ActiveTimeAccumulator
import dev.lapse.domain.AppConstants
import dev.lapse.domain.ApplicationUsageAccumulator
import dev.lapse.domain.ComputerSession
import dev.lapse.domain.DashboardPage
import dev.lapse.domain.ForegroundApplication
import dev.lapse.domain.LapsePreferences
import dev.lapse.domain.MonotonicClock
import dev.lapse.domain.OverlayMode
import dev.lapse.domain.PersistedAppState
import dev.lapse.domain.SessionTask
import dev.lapse.domain.StopwatchClock
import dev.lapse.domain.UserActivityState
import dev.lapse.domain.currentTimeMs
import dev.lapse.platform.PlatformBridge
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@AssistedInject
class AppViewModel(
    private val store: AppStore,
    private val platform: PlatformBridge,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: MonotonicClock = StopwatchClock(),
    private val now: () -> Long = { currentTimeMs() },
    @Assisted private val onQuit: () -> Unit = {},
) : ViewModel() {

    @AssistedFactory
    fun interface Factory {
        fun create(onQuit: () -> Unit): AppViewModel
    }

    private val job = SupervisorJob()

    @StructuredScope
    private val scope = CoroutineScope(job + dispatcher)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var accumulator = ActiveTimeAccumulator(0, clock)
    private var appAccumulator = ApplicationUsageAccumulator(clock)
    private var bootId = "unknown-boot"
    private var detectedActivity = UserActivityState.Idle
    private var lastForegroundSignature: String? = null
    private var persistJob: Job? = null

    init {
        initialize()
    }

    fun onIntent(intent: AppIntent) {
        when (intent) {
            AppIntent.Quit -> {
                persist(now = true)
                onQuit()
            }
            AppIntent.Tick -> refreshDisplay()
            is AppIntent.ActivityChanged -> onActivity(intent.state)
            is AppIntent.ForegroundChanged -> onForeground(
                ForegroundApplication(
                    processId = intent.processId,
                    executablePath = intent.path,
                    executableName = intent.exe,
                    displayName = intent.name,
                    windowTitle = intent.title,
                    observedAtMs = now(),
                ),
            )
            AppIntent.ForegroundCleared -> onForeground(null)
            else -> {
                mutate { reduce(it, intent) }
                afterReduce(intent)
            }
        }
    }

    override fun onCleared() {
        persist(now = true)
        job.cancel()
        super.onCleared()
    }

    private fun initialize() {
        try {
            bootId = platform.bootId()
            val persisted = store.load()
            val sameBoot = persisted?.bootId == bootId
            val session = if (sameBoot) {
                persisted.session
            } else {
                ComputerSession(
                    id = newId(),
                    startedAtMs = now(),
                    activeDurationMs = 0,
                )
            }
            val history = buildList {
                persisted?.sessionHistory?.let(::addAll)
                if (!sameBoot && persisted != null) {
                    add(persisted.session.copy(endedAtMs = now(), isPaused = false))
                }
            }
            val preferences = (persisted?.preferences ?: LapsePreferences())
                .copy(overlayMode = OverlayMode.Collapsed)
            accumulator = ActiveTimeAccumulator(session.activeDurationMs, clock)
            appAccumulator = ApplicationUsageAccumulator(clock, session.applicationUsage)
            platform.foregroundApplication()?.let(appAccumulator::observe)
            val activity = currentActivity()
            detectedActivity = activity
            val effective = if (session.isPaused) UserActivityState.Paused else activity
            accumulator.transitionTo(effective)
            appAccumulator.setActive(effective == UserActivityState.Active)
            _state.value = AppState(
                session = session,
                preferences = preferences,
                activityState = effective,
                displayDurationMs = accumulator.currentMs,
                isReady = true,
                sessionHistory = if (history.size > 90) history.takeLast(90) else history,
            )
            platform.setAutostartEnabled(preferences.autostart)
            persist(now = true)
            startLoops()
        } catch (_: Exception) {
            _state.update {
                it.copy(isReady = true, error = AppError.NativeUnavailable)
            }
        }
    }

    private fun startLoops() {
        scope.launch {
            while (isActive) {
                delay(AppConstants.ACTIVITY_POLL_MS.milliseconds)
                pollActivity()
                pollForeground()
                onIntent(AppIntent.Tick)
            }
        }
        scope.launch {
            while (isActive) {
                delay(AppConstants.PERSIST_CHECKPOINT_MS.milliseconds)
                persist(now = true)
            }
        }
    }

    private fun pollActivity() {
        val next = currentActivity()
        if (next != detectedActivity) onIntent(AppIntent.ActivityChanged(next))
    }

    private fun pollForeground() {
        val application = runCatching { platform.foregroundApplication() }.getOrNull()
        val signature = application?.let { "${it.id}\u0000${it.displayName}\u0000${it.windowTitle}" }
        if (signature == lastForegroundSignature) return
        lastForegroundSignature = signature
        if (application == null) {
            onIntent(AppIntent.ForegroundCleared)
        } else {
            onIntent(
                AppIntent.ForegroundChanged(
                    processId = application.processId,
                    path = application.executablePath,
                    exe = application.executableName,
                    name = application.displayName,
                    title = application.windowTitle,
                ),
            )
        }
    }

    private fun currentActivity(): UserActivityState {
        val snap = runCatching { platform.activitySnapshot() }.getOrElse {
            return UserActivityState.Idle
        }
        return when {
            snap.sleeping -> UserActivityState.Sleeping
            snap.locked -> UserActivityState.Locked
            snap.idleDurationMs >= AppConstants.IDLE_TIMEOUT_MS -> UserActivityState.Idle
            else -> UserActivityState.Active
        }
    }

    private fun onActivity(next: UserActivityState) {
        detectedActivity = next
        if (_state.value.session.isPaused) return
        applyActivity(next)
    }

    private fun applyActivity(next: UserActivityState) {
        if (next == _state.value.activityState) return
        accumulator.transitionTo(next)
        appAccumulator.setActive(next == UserActivityState.Active)
        mutate {
            it.copy(activityState = next, displayDurationMs = accumulator.currentMs)
        }
        persist()
    }

    private fun onForeground(application: ForegroundApplication?) {
        appAccumulator.observe(application)
        mutate {
            it.copy(session = it.session.copy(applicationUsage = appAccumulator.snapshot()))
        }
        persist()
    }

    private fun refreshDisplay() {
        mutate { it.copy(displayDurationMs = accumulator.currentMs) }
    }

    private fun reduce(s: AppState, intent: AppIntent): AppState = when (intent) {
        AppIntent.TogglePause -> {
            val paused = !s.session.isPaused
            val activity = if (paused) UserActivityState.Paused else detectedActivity
            accumulator.transitionTo(activity)
            appAccumulator.setActive(activity == UserActivityState.Active)
            s.copy(
                session = s.session.copy(isPaused = paused),
                activityState = activity,
                displayDurationMs = accumulator.currentMs,
            )
        }
        is AppIntent.AddTask -> {
            val title = intent.title.trim()
            if (title.isEmpty()) s else s.withTasks(s.session.tasks + SessionTask(newId(), title))
        }
        is AppIntent.ToggleTask -> s.withTasks(
            s.session.tasks.map { if (it.id == intent.id) it.copy(isCompleted = !it.isCompleted) else it },
        )
        is AppIntent.EditTask -> {
            val title = intent.title.trim()
            if (title.isEmpty()) s else s.withTasks(
                s.session.tasks.map { if (it.id == intent.id) it.copy(title = title) else it },
            )
        }
        is AppIntent.DeleteTask -> s.withTasks(s.session.tasks.filterNot { it.id == intent.id })
        AppIntent.ToggleOverlayMode -> s.copy(
            preferences = s.preferences.copy(
                overlayMode = if (s.preferences.overlayMode == OverlayMode.Expanded) {
                    OverlayMode.Collapsed
                } else {
                    OverlayMode.Expanded
                },
            ),
        )
        AppIntent.HideOverlay -> s.copy(overlayVisible = false)
        AppIntent.ShowOverlay -> s.copy(overlayVisible = true)
        AppIntent.ToggleOverlay -> s.copy(overlayVisible = !s.overlayVisible)
        AppIntent.OpenDashboard -> s.copy(
            dashboardOpen = true,
            dashboardPage = DashboardPage.Overview,
            dashboardFocusSeq = s.dashboardFocusSeq + 1,
        )
        AppIntent.OpenDashboardSettings -> s.copy(
            dashboardOpen = true,
            dashboardPage = DashboardPage.Settings,
            dashboardFocusSeq = s.dashboardFocusSeq + 1,
        )
        AppIntent.CloseDashboard -> s.copy(dashboardOpen = false)
        is AppIntent.SetDashboardPage -> s.copy(dashboardPage = intent.page)
        is AppIntent.SetAlwaysOnTop -> s.copy(preferences = s.preferences.copy(alwaysOnTop = intent.value))
        is AppIntent.SetAutostart -> s.copy(preferences = s.preferences.copy(autostart = intent.value))
        is AppIntent.SetGlobalHotkey -> s.copy(preferences = s.preferences.copy(globalHotkey = intent.value))
        is AppIntent.SetPauseHotkey -> s.copy(preferences = s.preferences.copy(pauseHotkey = intent.value))
        is AppIntent.SaveOverlayPosition -> s.copy(
            preferences = s.preferences.copy(windowX = intent.x, windowY = intent.y),
        )
        is AppIntent.SaveDashboardBounds -> s.copy(
            preferences = s.preferences.copy(
                dashboardX = intent.x,
                dashboardY = intent.y,
                dashboardWidth = intent.width,
                dashboardHeight = intent.height,
            ),
        )
        else -> s
    }

    private fun afterReduce(intent: AppIntent) {
        when (intent) {
            is AppIntent.SetAutostart -> platform.setAutostartEnabled(intent.value)
            AppIntent.HideOverlay,
            AppIntent.ShowOverlay,
            AppIntent.ToggleOverlay,
            is AppIntent.SetDashboardPage,
            -> Unit
            else -> persist()
        }
    }

    private fun persist(now: Boolean = false) {
        if (!now) {
            persistJob?.cancel()
            persistJob = scope.launch {
                delay(250)
                persist(now = true)
            }
            return
        }
        val duration = accumulator.checkpoint()
        val session = _state.value.session.copy(
            activeDurationMs = duration,
            applicationUsage = appAccumulator.checkpoint(),
        )
        mutate { it.copy(session = session, displayDurationMs = duration) }
        store.save(
            PersistedAppState(
                bootId = bootId,
                session = session,
                preferences = _state.value.preferences,
                sessionHistory = _state.value.sessionHistory,
            ),
        )
    }

    private fun mutate(block: (AppState) -> AppState) {
        _state.update(block)
    }

    private var idSequence = 0
    private fun newId(): String = "${now()}-${idSequence++}"
}
