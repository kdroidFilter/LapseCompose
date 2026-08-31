package dev.lapse.domain

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageAnalyticsTest {
    private fun session(startMs: Long, durationMs: Long, apps: List<ApplicationUsage> = emptyList()) =
        ComputerSession(
            id = startMs.toString(),
            startedAtMs = startMs,
            activeDurationMs = durationMs,
            applicationUsage = apps,
        )

    @Test
    fun calculatesDailyTotalsAndWeek() {
        val now = InstantLike(2026, 8, 28, 12)
        val summary = UsageAnalytics.summarize(
            listOf(
                session(InstantLike(2026, 8, 28, 9), 2 * 3_600_000),
                session(InstantLike(2026, 8, 28, 14), 1 * 3_600_000),
                session(InstantLike(2026, 8, 26, 9), 4 * 3_600_000),
                session(InstantLike(2026, 8, 20, 12), 9 * 3_600_000),
            ),
            nowMs = now,
        )
        assertEquals(3 * 3_600_000, summary.todayMs)
        assertEquals(2, summary.sessionsToday)
        assertEquals(7 * 3_600_000, summary.thisWeekMs)
        assertEquals(1 * 3_600_000, summary.sevenDayAverageMs)
        assertEquals(5, summary.lastSevenDays.count { it.durationMs == 0L })
    }

    @Test
    fun aggregatesApplicationUsageAcrossSessions() {
        val editor = ApplicationUsage("editor", "Editor", "editor.exe", 20 * 60_000)
        val totals = UsageAnalytics.applicationTotals(
            listOf(
                session(InstantLike(2026, 8, 28, 12), 0, listOf(editor)),
                session(InstantLike(2026, 8, 29, 12), 0, listOf(editor.copy(activeDurationMs = 10 * 60_000))),
            ),
            fromMs = InstantLike(2026, 8, 23, 12),
        )
        assertEquals(30 * 60_000, totals.single().activeDurationMs)
    }
}

private fun InstantLike(year: Int, month: Int, day: Int, hour: Int = 0): Long {
    val dateTime = LocalDateTime(year, month, day, hour, 0)
    return dateTime.toInstant(TimeZone.UTC).toEpochMilliseconds()
}
