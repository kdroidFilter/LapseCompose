package dev.lapse.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.MapSettings
import dev.lapse.db.AppDatabase
import dev.lapse.domain.ApplicationUsage
import dev.lapse.domain.ComputerSession
import dev.lapse.domain.LapsePreferences
import dev.lapse.domain.OverlayMode
import dev.lapse.domain.PersistedAppState
import dev.lapse.domain.SessionTask
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentStoreTest {
    private fun store(): PersistentStore {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), AppDatabase.Schema)
        return PersistentStore(MapSettings(), driver)
    }

    @Test
    fun roundTripsPrefsSessionAndHistory() {
        val store = store()
        val state = PersistedAppState(
            bootId = "boot-a",
            session = ComputerSession(
                id = "session-a",
                startedAtMs = 1_000,
                activeDurationMs = 95_000,
                isPaused = true,
                tasks = listOf(SessionTask("task-a", "Persist me", isCompleted = true)),
                applicationUsage = listOf(
                    ApplicationUsage("code", "VS Code", "Code.exe", 12_000),
                ),
            ),
            preferences = LapsePreferences(
                overlayMode = OverlayMode.Collapsed,
                alwaysOnTop = false,
                windowX = 123.0,
                windowY = 45.0,
            ),
            sessionHistory = listOf(
                ComputerSession(
                    id = "old",
                    startedAtMs = 500,
                    endedAtMs = 800,
                    activeDurationMs = 2 * 3_600_000,
                ),
            ),
        )
        store.save(state)
        val restored = store.load()
        assertEquals("boot-a", restored?.bootId)
        assertEquals(95_000, restored?.session?.activeDurationMs)
        assertTrue(restored?.session?.isPaused == true)
        assertTrue(restored?.session?.tasks?.single()?.isCompleted == true)
        assertEquals("VS Code", restored?.session?.applicationUsage?.single()?.displayName)
        assertEquals(OverlayMode.Collapsed, restored?.preferences?.overlayMode)
        assertEquals(false, restored?.preferences?.alwaysOnTop)
        assertEquals(123.0, restored?.preferences?.windowX)
        assertEquals("old", restored?.sessionHistory?.single()?.id)
        assertEquals(800, restored?.sessionHistory?.single()?.endedAtMs)
    }

    @Test
    fun emptyStoreLoadNull() {
        assertNull(store().load())
    }

    @Test
    fun replacingHistoryDropsRemovedSessions() {
        val store = store()
        store.save(
            PersistedAppState(
                bootId = "boot-a",
                session = ComputerSession("now", 10, 0),
                preferences = LapsePreferences(),
                sessionHistory = listOf(
                    ComputerSession("a", 1, 1),
                    ComputerSession("b", 2, 2),
                ),
            ),
        )
        store.save(
            PersistedAppState(
                bootId = "boot-a",
                session = ComputerSession("now", 10, 0),
                preferences = LapsePreferences(),
                sessionHistory = listOf(ComputerSession("b", 2, 2)),
            ),
        )
        val restored = store.load()
        assertEquals(listOf("b"), restored?.sessionHistory?.map { it.id })
    }
}
