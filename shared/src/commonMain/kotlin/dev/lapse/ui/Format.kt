package dev.lapse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import dev.lapse.domain.UserActivityState
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.activity_active
import lapse.shared.generated.resources.activity_idle
import lapse.shared.generated.resources.activity_locked
import lapse.shared.generated.resources.activity_paused
import lapse.shared.generated.resources.activity_sleeping
import lapse.shared.generated.resources.duration_hours_minutes
import lapse.shared.generated.resources.duration_minutes
import lapse.shared.generated.resources.duration_seconds
import lapse.shared.generated.resources.status_active
import lapse.shared.generated.resources.status_idle
import lapse.shared.generated.resources.status_locked
import lapse.shared.generated.resources.status_paused
import lapse.shared.generated.resources.status_sleeping
import lapse.shared.generated.resources.today
import lapse.shared.generated.resources.weekday_fri
import lapse.shared.generated.resources.weekday_friday
import lapse.shared.generated.resources.weekday_mon
import lapse.shared.generated.resources.weekday_monday
import lapse.shared.generated.resources.weekday_sat
import lapse.shared.generated.resources.weekday_saturday
import lapse.shared.generated.resources.weekday_sun
import lapse.shared.generated.resources.weekday_sunday
import lapse.shared.generated.resources.weekday_thu
import lapse.shared.generated.resources.weekday_thursday
import lapse.shared.generated.resources.weekday_tue
import lapse.shared.generated.resources.weekday_tuesday
import lapse.shared.generated.resources.weekday_wed
import lapse.shared.generated.resources.weekday_wednesday
import lapse.shared.generated.resources.yesterday
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Instant

const val TabularFigures = "tnum"

fun formatTimer(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = (totalSeconds / 3600).toString().padStart(2, '0')
    val minutes = ((totalSeconds % 3600) / 60).toString().padStart(2, '0')
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return "$hours:$minutes:$seconds"
}

@Composable
fun formatDurationShort(durationMs: Long): String {
    if (durationMs < 60_000) return stringResource(Res.string.duration_seconds, durationMs / 1000)
    val hours = durationMs / 3_600_000
    val minutes = (durationMs / 60_000) % 60
    return if (hours == 0L) {
        stringResource(Res.string.duration_minutes, minutes)
    } else {
        stringResource(Res.string.duration_hours_minutes, hours, minutes.toString().padStart(2, '0'))
    }
}

@Composable
fun activitySubtitle(state: UserActivityState): String = stringResource(
    when (state) {
        UserActivityState.Active -> Res.string.activity_active
        UserActivityState.Paused -> Res.string.activity_paused
        UserActivityState.Idle -> Res.string.activity_idle
        UserActivityState.Locked -> Res.string.activity_locked
        UserActivityState.Sleeping -> Res.string.activity_sleeping
    },
)

@Composable
fun activityStatusLabel(state: UserActivityState): String = stringResource(
    when (state) {
        UserActivityState.Active -> Res.string.status_active
        UserActivityState.Paused -> Res.string.status_paused
        UserActivityState.Idle -> Res.string.status_idle
        UserActivityState.Locked -> Res.string.status_locked
        UserActivityState.Sleeping -> Res.string.status_sleeping
    },
)

@Composable
@ReadOnlyComposable
fun statusColor(state: UserActivityState) = when (state) {
    UserActivityState.Active -> dev.lapse.theme.LapseColors.active
    UserActivityState.Paused -> dev.lapse.theme.LapseColors.accent
    UserActivityState.Idle -> dev.lapse.theme.LapseColors.idle
    UserActivityState.Locked, UserActivityState.Sleeping -> dev.lapse.theme.LapseColors.locked
}

fun formatClock(epochMs: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

@Composable
fun sessionDateLabel(epochMs: Long, nowMs: Long): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(zone).date
    val today = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(zone).date
    return when (date) {
        today -> stringResource(Res.string.today)
        today.minus(DatePeriod(days = 1)) -> stringResource(Res.string.yesterday)
        else -> "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}.${date.year}"
    }
}

@Composable
fun weekdayShort(isoDayOfWeek: Int): String {
    val res = listOf(
        Res.string.weekday_mon,
        Res.string.weekday_tue,
        Res.string.weekday_wed,
        Res.string.weekday_thu,
        Res.string.weekday_fri,
        Res.string.weekday_sat,
        Res.string.weekday_sun,
    )
    return stringResource(res.getOrElse(isoDayOfWeek - 1) { Res.string.weekday_mon })
}

@Composable
fun weekdayLong(isoDayOfWeek: Int): String {
    val res = listOf(
        Res.string.weekday_monday,
        Res.string.weekday_tuesday,
        Res.string.weekday_wednesday,
        Res.string.weekday_thursday,
        Res.string.weekday_friday,
        Res.string.weekday_saturday,
        Res.string.weekday_sunday,
    )
    return stringResource(res.getOrElse(isoDayOfWeek - 1) { Res.string.weekday_monday })
}
