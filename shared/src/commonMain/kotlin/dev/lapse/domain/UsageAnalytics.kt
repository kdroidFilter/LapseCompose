package dev.lapse.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

object UsageAnalytics {
    fun summarize(sessions: Iterable<ComputerSession>, nowMs: Long): UsageSummary {
        val zone = TimeZone.currentSystemDefault()
        val today = dateOf(nowMs, zone)
        val totals = mutableMapOf<LocalDate, Long>()
        for (session in sessions) {
            val day = dateOf(session.startedAtMs, zone)
            totals[day] = (totals[day] ?: 0) + session.activeDurationMs
        }
        val points = (6 downTo 0).map { offset ->
            val day = today.minus(DatePeriod(days = offset))
            DailyUsagePoint(
                year = day.year,
                monthNumber = day.month.number,
                dayOfMonth = day.day,
                isoDayOfWeek = day.dayOfWeek.ordinal + 1,
                durationMs = totals[day] ?: 0,
            )
        }
        val sevenDayTotal = points.sumOf { it.durationMs }
        val weekStart = today.minus(DatePeriod(days = today.dayOfWeek.ordinal))
        val weekTotal = totals.entries
            .filter { !it.key.isBefore(weekStart) && !it.key.isAfter(today) }
            .sumOf { it.value }
        return UsageSummary(
            todayMs = totals[today] ?: 0,
            sevenDayAverageMs = sevenDayTotal / 7,
            thisWeekMs = weekTotal,
            sessionsToday = sessions.count { dateOf(it.startedAtMs, zone) == today },
            lastSevenDays = points,
        )
    }

    fun applicationTotals(sessions: Iterable<ComputerSession>, fromMs: Long): List<ApplicationUsage> {
        val totals = mutableMapOf<String, ApplicationUsage>()
        for (session in sessions.filter { it.startedAtMs >= fromMs }) {
            for (usage in session.applicationUsage) {
                val previous = totals[usage.applicationId]
                totals[usage.applicationId] = usage.copy(
                    activeDurationMs = (previous?.activeDurationMs ?: 0) + usage.activeDurationMs,
                )
            }
        }
        return totals.values.sortedByDescending { it.activeDurationMs }
    }

    private fun dateOf(epochMs: Long, zone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date

    private fun LocalDate.isBefore(other: LocalDate): Boolean = this < other
    private fun LocalDate.isAfter(other: LocalDate): Boolean = this > other
}
