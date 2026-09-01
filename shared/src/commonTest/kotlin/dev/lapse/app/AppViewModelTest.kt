package dev.lapse.app

import dev.lapse.data.MemoryStore
import dev.lapse.domain.ComputerSession
import dev.lapse.domain.LapsePreferences
import dev.lapse.domain.MutableClock
import dev.lapse.domain.OverlayMode
import dev.lapse.domain.PersistedAppState
import dev.lapse.domain.SessionTask
import dev.lapse.domain.UserActivityState
import dev.lapse.platform.FakePlatformBridge
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private fun persisted(bootId: String = "boot-a") = PersistedAppState(
        bootId = bootId,
        session = ComputerSession(
            id = "session-1",
            startedAtMs = 1_000,
            activeDurationMs = 42 * 60_000,
            tasks = listOf(SessionTask("task-1", "Keep me")),
        ),
        preferences = LapsePreferences(overlayMode = OverlayMode.Collapsed),
    )

    private fun vm(
        store: MemoryStore = MemoryStore(),
        platform: FakePlatformBridge = FakePlatformBridge(),
        clock: MutableClock = MutableClock(),
    ): AppViewModel {
        val dispatcher = StandardTestDispatcher()
        return AppViewModel(
            store = store,
            platform = platform,
            dispatcher = dispatcher,
            clock = clock,
            now = { 1_725_000_000_000L },
        )
    }

    @Test
    fun restoresCurrentSessionOnSameBoot() {
        val viewModel = vm(store = MemoryStore(persisted()), platform = FakePlatformBridge("boot-a"))
        val state = viewModel.state.value
        assertEquals("session-1", state.session.id)
        assertEquals("Keep me", state.session.tasks.single().title)
        assertEquals(42 * 60_000, state.displayDurationMs)
        assertEquals(OverlayMode.Collapsed, state.preferences.overlayMode)

    }

    @Test
    fun restoresSavedWindowPosition() {
        val stored = persisted().let {
            it.copy(preferences = it.preferences.copy(windowX = 40.0, windowY = 80.0))
        }
        val viewModel = vm(store = MemoryStore(stored), platform = FakePlatformBridge("boot-a"))
        assertEquals(40.0, viewModel.state.value.preferences.windowX)
        assertEquals(80.0, viewModel.state.value.preferences.windowY)
    }

    @Test
    fun saveOverlayPositionUpdatesPrefs() {
        val viewModel = vm(store = MemoryStore(persisted()), platform = FakePlatformBridge("boot-a"))
        viewModel.onIntent(AppIntent.SaveOverlayPosition(12.0, 34.0))
        assertEquals(12.0, viewModel.state.value.preferences.windowX)
        assertEquals(34.0, viewModel.state.value.preferences.windowY)
    }

    @Test
    fun alwaysStartsCollapsed() {
        val expanded = persisted().let {
            it.copy(preferences = it.preferences.copy(overlayMode = OverlayMode.Expanded))
        }
        val viewModel = vm(store = MemoryStore(expanded))
        assertEquals(OverlayMode.Collapsed, viewModel.state.value.preferences.overlayMode)

    }

    @Test
    fun startsFreshSessionOnNewBoot() {
        val viewModel = vm(
            store = MemoryStore(persisted("old-boot")),
            platform = FakePlatformBridge("new-boot"),
        )
        val state = viewModel.state.value
        assertNotEquals("session-1", state.session.id)
        assertTrue(state.session.tasks.isEmpty())
        assertEquals(0, state.displayDurationMs)
        assertEquals(1, state.sessionHistory.size)
        assertEquals("session-1", state.sessionHistory.single().id)

    }

    @Test
    fun addsTogglesEditsAndDeletesTasks() {
        val viewModel = vm()
        viewModel.onIntent(AppIntent.AddTask("Write tests"))
        val task = viewModel.state.value.session.tasks.single()
        assertEquals("Write tests", task.title)
        viewModel.onIntent(AppIntent.ToggleTask(task.id))
        assertTrue(viewModel.state.value.session.tasks.single().isCompleted)
        viewModel.onIntent(AppIntent.EditTask(task.id, "Ship tests"))
        assertEquals("Ship tests", viewModel.state.value.session.tasks.single().title)
        viewModel.onIntent(AppIntent.DeleteTask(task.id))
        assertTrue(viewModel.state.value.session.tasks.isEmpty())

    }

    @Test
    fun manualPauseStopsAccumulation() {
        val clock = MutableClock()
        val store = MemoryStore()
        val viewModel = vm(store = store, clock = clock)
        clock.advance(2 * 60_000)
        viewModel.onIntent(AppIntent.TogglePause)
        assertEquals(UserActivityState.Paused, viewModel.state.value.activityState)
        assertTrue(viewModel.state.value.session.isPaused)
        clock.advance(5 * 60_000)
        viewModel.onIntent(AppIntent.Tick)
        assertEquals(2 * 60_000, viewModel.state.value.displayDurationMs)
        viewModel.onIntent(AppIntent.TogglePause)
        clock.advance(60_000)
        viewModel.onIntent(AppIntent.Tick)
        assertEquals(UserActivityState.Active, viewModel.state.value.activityState)
        assertFalse(viewModel.state.value.session.isPaused)
        assertEquals(3 * 60_000, viewModel.state.value.displayDurationMs)

    }

    @Test
    fun toggleOverlayFlipsVisibility() {
        val viewModel = vm(store = MemoryStore(persisted()), platform = FakePlatformBridge("boot-a"))
        assertTrue(viewModel.state.value.overlayVisible)
        viewModel.onIntent(AppIntent.ToggleOverlay)
        assertFalse(viewModel.state.value.overlayVisible)
        viewModel.onIntent(AppIntent.ToggleOverlay)
        assertTrue(viewModel.state.value.overlayVisible)
    }

    @Test
    fun setGlobalHotkeyUpdatesPrefs() {
        val viewModel = vm(store = MemoryStore(persisted()), platform = FakePlatformBridge("boot-a"))
        viewModel.onIntent(AppIntent.SetGlobalHotkey(false))
        assertFalse(viewModel.state.value.preferences.globalHotkey)
    }
}
