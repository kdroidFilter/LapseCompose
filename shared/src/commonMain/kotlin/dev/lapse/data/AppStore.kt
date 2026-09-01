package dev.lapse.data

import app.cash.sqldelight.db.SqlDriver
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import com.russhwolf.settings.set
import dev.lapse.db.AppDatabase
import dev.lapse.db.Session
import dev.lapse.domain.ApplicationUsage
import dev.lapse.domain.ComputerSession
import dev.lapse.domain.LapsePreferences
import dev.lapse.domain.OverlayMode
import dev.lapse.domain.PersistedAppState
import dev.lapse.domain.SessionTask

private const val KEY_BOOT_ID = "bootId"
private const val KEY_OVERLAY_MODE = "overlayMode"
private const val KEY_ALWAYS_ON_TOP = "alwaysOnTop"
private const val KEY_AUTOSTART = "autostart"
private const val KEY_GLOBAL_HOTKEY = "globalHotkey"
private const val KEY_WINDOW_X = "windowX"
private const val KEY_WINDOW_Y = "windowY"
private const val KEY_DASHBOARD_X = "dashboardX"
private const val KEY_DASHBOARD_Y = "dashboardY"
private const val KEY_DASHBOARD_WIDTH = "dashboardWidth"
private const val KEY_DASHBOARD_HEIGHT = "dashboardHeight"

interface AppStore {
    fun load(): PersistedAppState?
    fun save(state: PersistedAppState)
}

class MemoryStore(initial: PersistedAppState? = null) : AppStore {
    var value: PersistedAppState? = initial
        private set

    override fun load(): PersistedAppState? = value

    override fun save(state: PersistedAppState) {
        value = state
    }
}

expect fun createSettings(): Settings

fun createAppStore(
    settings: Settings = createSettings(),
    driver: SqlDriver = createSqlDriver(),
): AppStore = PersistentStore(settings, driver)

class PersistentStore(
    private val settings: Settings,
    driver: SqlDriver,
) : AppStore {
    private val queries = AppDatabase(driver).sessionQueries

    override fun load(): PersistedAppState? {
        val bootId = settings.getStringOrNull(KEY_BOOT_ID) ?: return null
        val current = queries.selectCurrent().executeAsOneOrNull() ?: return null
        return PersistedAppState(
            bootId = bootId,
            session = current.toDomain(),
            preferences = loadPreferences(),
            sessionHistory = queries.selectHistory().executeAsList().map { it.toDomain() },
        )
    }

    override fun save(state: PersistedAppState) {
        settings[KEY_BOOT_ID] = state.bootId
        savePreferences(state.preferences)
        settings.remove("session")
        settings.remove("sessionHistory")
        queries.transaction {
            queries.deleteAllUsage()
            queries.deleteAllTasks()
            queries.deleteAllSessions()
            insert(state.session, isCurrent = true)
            state.sessionHistory.forEach { insert(it, isCurrent = false) }
        }
    }

    private fun insert(session: ComputerSession, isCurrent: Boolean) {
        queries.insertSession(
            id = session.id,
            startedAtMs = session.startedAtMs,
            endedAtMs = session.endedAtMs,
            activeDurationMs = session.activeDurationMs,
            isPaused = if (session.isPaused) 1L else 0L,
            isCurrent = if (isCurrent) 1L else 0L,
        )
        session.tasks.forEach { task ->
            queries.insertTask(
                id = task.id,
                sessionId = session.id,
                title = task.title,
                isCompleted = if (task.isCompleted) 1L else 0L,
            )
        }
        session.applicationUsage.forEach { usage ->
            queries.insertUsage(
                sessionId = session.id,
                applicationId = usage.applicationId,
                displayName = usage.displayName,
                executableName = usage.executableName,
                activeDurationMs = usage.activeDurationMs,
            )
        }
    }

    private fun Session.toDomain(): ComputerSession = ComputerSession(
        id = id,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        activeDurationMs = activeDurationMs,
        isPaused = isPaused != 0L,
        tasks = queries.selectTasks(id).executeAsList().map {
            SessionTask(id = it.id, title = it.title, isCompleted = it.isCompleted != 0L)
        },
        applicationUsage = queries.selectUsage(id).executeAsList().map {
            ApplicationUsage(
                applicationId = it.applicationId,
                displayName = it.displayName,
                executableName = it.executableName,
                activeDurationMs = it.activeDurationMs,
            )
        },
    )

    private fun loadPreferences(): LapsePreferences {
        val defaults = LapsePreferences()
        val modeName = settings.getStringOrNull(KEY_OVERLAY_MODE)
        return LapsePreferences(
            overlayMode = OverlayMode.entries.firstOrNull { it.name.equals(modeName, ignoreCase = true) }
                ?: defaults.overlayMode,
            alwaysOnTop = settings[KEY_ALWAYS_ON_TOP, defaults.alwaysOnTop],
            autostart = settings[KEY_AUTOSTART, defaults.autostart],
            globalHotkey = settings[KEY_GLOBAL_HOTKEY, defaults.globalHotkey],
            windowX = settings.getDoubleOrNull(KEY_WINDOW_X),
            windowY = settings.getDoubleOrNull(KEY_WINDOW_Y),
            dashboardX = settings.getDoubleOrNull(KEY_DASHBOARD_X),
            dashboardY = settings.getDoubleOrNull(KEY_DASHBOARD_Y),
            dashboardWidth = settings[KEY_DASHBOARD_WIDTH, defaults.dashboardWidth],
            dashboardHeight = settings[KEY_DASHBOARD_HEIGHT, defaults.dashboardHeight],
        )
    }

    private fun savePreferences(prefs: LapsePreferences) {
        settings[KEY_OVERLAY_MODE] = prefs.overlayMode.name
        settings[KEY_ALWAYS_ON_TOP] = prefs.alwaysOnTop
        settings[KEY_AUTOSTART] = prefs.autostart
        settings[KEY_GLOBAL_HOTKEY] = prefs.globalHotkey
        settings.putOrRemove(KEY_WINDOW_X, prefs.windowX)
        settings.putOrRemove(KEY_WINDOW_Y, prefs.windowY)
        settings.putOrRemove(KEY_DASHBOARD_X, prefs.dashboardX)
        settings.putOrRemove(KEY_DASHBOARD_Y, prefs.dashboardY)
        settings[KEY_DASHBOARD_WIDTH] = prefs.dashboardWidth
        settings[KEY_DASHBOARD_HEIGHT] = prefs.dashboardHeight
    }
}

private fun Settings.putOrRemove(key: String, value: Double?) {
    if (value == null) remove(key) else putDouble(key, value)
}
