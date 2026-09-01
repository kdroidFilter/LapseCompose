package dev.lapse.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lapse.app.AppIntent
import dev.lapse.app.AppState
import dev.lapse.domain.ApplicationUsage
import dev.lapse.domain.ComputerSession
import dev.lapse.domain.DashboardPage
import dev.lapse.domain.UsageAnalytics
import dev.lapse.domain.currentTimeMs
import dev.lapse.theme.LapseColors
import dev.lapse.ui.TabularFigures
import dev.lapse.ui.formatClock
import dev.lapse.ui.formatDurationShort
import dev.lapse.ui.sessionDateLabel
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.apps_empty
import lapse.shared.generated.resources.apps_header_application
import lapse.shared.generated.resources.apps_header_share
import lapse.shared.generated.resources.apps_header_time
import lapse.shared.generated.resources.apps_tracked_seven_days
import lapse.shared.generated.resources.apps_tracked_today
import lapse.shared.generated.resources.metric_active_time
import lapse.shared.generated.resources.metric_seven_day_average
import lapse.shared.generated.resources.metric_sessions_today
import lapse.shared.generated.resources.metric_this_week
import lapse.shared.generated.resources.metric_today
import lapse.shared.generated.resources.nav_applications
import lapse.shared.generated.resources.nav_dashboard
import lapse.shared.generated.resources.nav_sessions
import lapse.shared.generated.resources.nav_settings
import lapse.shared.generated.resources.nav_workspace
import lapse.shared.generated.resources.page_applications_subtitle
import lapse.shared.generated.resources.page_dashboard_subtitle
import lapse.shared.generated.resources.page_sessions_subtitle
import lapse.shared.generated.resources.page_settings_subtitle
import lapse.shared.generated.resources.percent
import lapse.shared.generated.resources.period_seven_days
import lapse.shared.generated.resources.period_today
import lapse.shared.generated.resources.session_active
import lapse.shared.generated.resources.session_current
import lapse.shared.generated.resources.session_range
import lapse.shared.generated.resources.session_tasks
import lapse.shared.generated.resources.sessions_empty
import lapse.shared.generated.resources.settings_always_on_top
import lapse.shared.generated.resources.settings_always_on_top_subtitle
import lapse.shared.generated.resources.settings_autostart
import lapse.shared.generated.resources.settings_autostart_subtitle
import org.jetbrains.compose.resources.stringResource

@Composable
fun DashboardScreen(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val now = currentTimeMs()
    val sessions = state.allSessions
    Row(modifier.fillMaxSize()) {
        Sidebar(state.dashboardPage, onIntent, contentPadding)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            AnimatedContent(
                targetState = state.dashboardPage,
                transitionSpec = {
                    val fade = tween<Float>(180, easing = EaseOutCubic)
                    val slide = tween<IntOffset>(180, easing = EaseOutCubic)
                    (fadeIn(fade) + slideInHorizontally(slide) { (it * 0.012f).toInt() }) togetherWith
                        (fadeOut(fade) + slideOutHorizontally(slide) { (it * 0.012f).toInt() }) using
                        SizeTransform(clip = false) { _, _ -> snap() }
                },
                label = "dashboard-page",
            ) { page ->
                when (page) {
                    DashboardPage.Overview -> OverviewPage(sessions, now)
                    DashboardPage.Applications -> ApplicationsPage(sessions, now)
                    DashboardPage.Sessions -> SessionsPage(sessions.asReversed(), now)
                    DashboardPage.Settings -> SettingsPage(state, onIntent)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    page: DashboardPage,
    onIntent: (AppIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier
            .width(184.dp)
            .fillMaxHeight()
            .padding(contentPadding)
            .padding(start = 9.dp, end = 3.dp, top = 6.dp, bottom = 9.dp)
            .clip(RoundedCornerShape(12.dp))
            .padding(12.dp, 10.dp, 12.dp, 16.dp),
    ) {
        Text(
            stringResource(Res.string.nav_workspace),
            color = LapseColors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Spacer(Modifier.height(12.dp))
        NavItem(Icons.Outlined.SpaceDashboard, stringResource(Res.string.nav_dashboard), page == DashboardPage.Overview) {
            onIntent(AppIntent.SetDashboardPage(DashboardPage.Overview))
        }
        NavItem(Icons.Outlined.Apps, stringResource(Res.string.nav_applications), page == DashboardPage.Applications) {
            onIntent(AppIntent.SetDashboardPage(DashboardPage.Applications))
        }
        NavItem(Icons.Rounded.History, stringResource(Res.string.nav_sessions), page == DashboardPage.Sessions) {
            onIntent(AppIntent.SetDashboardPage(DashboardPage.Sessions))
        }
        NavItem(Icons.Outlined.Settings, stringResource(Res.string.nav_settings), page == DashboardPage.Settings) {
            onIntent(AppIntent.SetDashboardPage(DashboardPage.Settings))
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) LapseColors.accent.copy(alpha = 0.13f) else Color.Transparent,
        tween(140),
        label = "nav-bg",
    )
    val bar by animateColorAsState(
        if (selected) LapseColors.accent else Color.Transparent,
        tween(140),
        label = "nav-bar",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(10.dp, 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bar),
        )
        Spacer(Modifier.width(9.dp))
        Icon(icon, null, Modifier.size(17.dp), tint = if (selected) LapseColors.text else LapseColors.textMuted)
        Spacer(Modifier.width(9.dp))
        Text(label, color = if (selected) LapseColors.text else LapseColors.textMuted, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PageShell(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp, 30.dp, 32.dp, 28.dp)) {
        Text(title, color = LapseColors.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, color = LapseColors.textMuted, fontSize = 12.sp)
        Spacer(Modifier.height(26.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(LapseColors.surface)
            .border(1.dp, LapseColors.border.copy(alpha = 0.8f), RoundedCornerShape(10.dp)),
    ) { content() }
}

@Composable
private fun OverviewPage(sessions: List<ComputerSession>, nowMs: Long) {
    val summary = remember(sessions, nowMs) { UsageAnalytics.summarize(sessions, nowMs) }
    PageShell(stringResource(Res.string.nav_dashboard), stringResource(Res.string.page_dashboard_subtitle)) {
        Column {
            Panel(Modifier.fillMaxWidth()) {
                val metrics = listOf(
                    stringResource(Res.string.metric_today) to formatDurationShort(summary.todayMs),
                    stringResource(Res.string.metric_seven_day_average) to formatDurationShort(summary.sevenDayAverageMs),
                    stringResource(Res.string.metric_this_week) to formatDurationShort(summary.thisWeekMs),
                    stringResource(Res.string.metric_sessions_today) to "${summary.sessionsToday}",
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = if (maxWidth < 760.dp) 2 else 4
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        metrics.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEachIndexed { index, (label, value) ->
                                    Metric(label, value, Modifier.weight(1f), showLeftBorder = index != 0)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(20.dp, 18.dp, 20.dp, 16.dp)) {
                    Text(stringResource(Res.string.metric_active_time), color = LapseColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(16.dp))
                    UsageChart(summary.lastSevenDays, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, showLeftBorder: Boolean) {
    val borderColor = LapseColors.border
    Column(
        modifier
            .drawBehind {
                if (!showLeftBorder) return@drawBehind
                val stroke = 1.dp.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(stroke / 2f, 0f),
                    end = Offset(stroke / 2f, size.height),
                    strokeWidth = stroke,
                )
            }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, color = LapseColors.textMuted, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            color = LapseColors.text,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(
                color = LapseColors.text,
                fontSize = 23.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TabularFigures,
            ),
        )
    }
}

@Composable
private fun ApplicationsPage(sessions: List<ComputerSession>, nowMs: Long) {
    var days by remember { mutableStateOf(1) }
    val from = nowMs - (days - 1) * 24L * 60 * 60 * 1000
    val usages = remember(sessions, days, nowMs) { UsageAnalytics.applicationTotals(sessions, from) }
    val total = usages.sumOf { it.activeDurationMs }
    PageShell(stringResource(Res.string.nav_applications), stringResource(Res.string.page_applications_subtitle)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDurationShort(total),
                    color = LapseColors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(
                        color = LapseColors.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = TabularFigures,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (days == 1) Res.string.apps_tracked_today else Res.string.apps_tracked_seven_days),
                    color = LapseColors.textMuted,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                PeriodPicker(days) { days = it }
            }
            Spacer(Modifier.height(20.dp))
            Panel(Modifier.fillMaxSize()) {
                if (usages.isEmpty()) {
                    Empty(stringResource(Res.string.apps_empty), Icons.Outlined.Apps)
                } else {
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = listState) {
                            item {
                                ApplicationListHeader()
                                HorizontalDivider(color = LapseColors.border)
                            }
                            itemsIndexed(usages) { index, usage ->
                                ApplicationRow(index + 1, usage, total)
                                HorizontalDivider(color = LapseColors.border, modifier = Modifier.padding(start = 64.dp))
                            }
                        }
                        VerticalScrollbar(
                            rememberScrollbarAdapter(listState),
                            Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodPicker(value: Int, onChanged: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LapseColors.surface)
            .border(1.dp, LapseColors.border, RoundedCornerShape(8.dp))
            .padding(3.dp),
    ) {
        PeriodOption(stringResource(Res.string.period_today), value == 1) { onChanged(1) }
        PeriodOption(stringResource(Res.string.period_seven_days), value == 7) { onChanged(7) }
    }
}

@Composable
private fun PeriodOption(label: String, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        if (selected) LapseColors.surfaceRaised else Color.Transparent,
        tween(140),
        label = "period-bg",
    )
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        color = if (selected) LapseColors.text else LapseColors.textMuted,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun ApplicationRow(rank: Int, usage: ApplicationUsage, total: Long) {
    val ratio = if (total == 0L) 0f else usage.activeDurationMs.toFloat() / total
    Row(Modifier.fillMaxWidth().padding(20.dp, 13.dp, 20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (rank == 1) LapseColors.accent.copy(alpha = 0.14f) else LapseColors.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text("$rank", color = if (rank == 1) LapseColors.accent else LapseColors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(usage.displayName, color = LapseColors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(usage.executableName, color = LapseColors.textMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)),
                color = LapseColors.accent,
                trackColor = LapseColors.surfaceRaised,
            )
        }
        Spacer(Modifier.width(20.dp))
        Text(
            formatDurationShort(usage.activeDurationMs),
            color = LapseColors.text,
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
            textAlign = TextAlign.End,
            style = TextStyle(fontFeatureSettings = TabularFigures, fontSize = 12.sp, color = LapseColors.text),
        )
        Text(
            stringResource(Res.string.percent, (ratio * 100).toInt()),
            color = LapseColors.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.width(54.dp),
            maxLines = 1,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SessionsPage(sessions: List<ComputerSession>, nowMs: Long) {
    PageShell(stringResource(Res.string.nav_sessions), stringResource(Res.string.page_sessions_subtitle)) {
        if (sessions.isEmpty()) {
            Empty(stringResource(Res.string.sessions_empty), Icons.Rounded.History)
        } else {
            val listState = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(sessions) { _, session ->
                        val completed = session.tasks.count { it.isCompleted }
                        Panel(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(sessionDateLabel(session.startedAtMs, nowMs), color = LapseColors.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        stringResource(
                                            Res.string.session_range,
                                            formatClock(session.startedAtMs),
                                            session.endedAtMs?.let(::formatClock) ?: stringResource(Res.string.session_current),
                                        ),
                                        color = LapseColors.textMuted,
                                        fontSize = 11.sp,
                                    )
                                }
                                Text(
                                    stringResource(Res.string.session_active, formatDurationShort(session.activeDurationMs)),
                                    color = LapseColors.text,
                                    fontSize = 12.sp,
                                    style = TextStyle(fontFeatureSettings = TabularFigures, fontSize = 12.sp, color = LapseColors.text),
                                )
                                Spacer(Modifier.width(30.dp))
                                Text(stringResource(Res.string.session_tasks, completed, session.tasks.size), color = LapseColors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    rememberScrollbarAdapter(listState),
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun SettingsPage(state: AppState, onIntent: (AppIntent) -> Unit) {
    PageShell(stringResource(Res.string.nav_settings), stringResource(Res.string.page_settings_subtitle)) {
        Column(Modifier.width(620.dp)) {
            SettingRow(
                title = stringResource(Res.string.settings_autostart),
                subtitle = stringResource(Res.string.settings_autostart_subtitle),
                value = state.preferences.autostart,
                onChanged = { onIntent(AppIntent.SetAutostart(it)) },
            )
            Spacer(Modifier.height(8.dp))
            SettingRow(
                title = stringResource(Res.string.settings_always_on_top),
                subtitle = stringResource(Res.string.settings_always_on_top_subtitle),
                value = state.preferences.alwaysOnTop,
                onChanged = { onIntent(AppIntent.SetAlwaysOnTop(it)) },
            )
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, value: Boolean, onChanged: (Boolean) -> Unit) {
    Panel(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = LapseColors.text, fontSize = 13.sp)
                Text(subtitle, color = LapseColors.textMuted, fontSize = 11.sp)
            }
            Switch(
                checked = value,
                onCheckedChange = onChanged,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = LapseColors.accent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = LapseColors.textMuted,
                    uncheckedTrackColor = LapseColors.border,
                    uncheckedBorderColor = LapseColors.border,
                ),
            )
        }
    }
}

@Composable
private fun ApplicationListHeader() {
    Row(
        Modifier.fillMaxWidth().padding(20.dp, 12.dp, 20.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(44.dp))
        Text(
            stringResource(Res.string.apps_header_application),
            modifier = Modifier.weight(1f),
            color = LapseColors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        )
        Text(
            stringResource(Res.string.apps_header_time),
            modifier = Modifier.width(76.dp),
            color = LapseColors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.End,
        )
        Text(
            stringResource(Res.string.apps_header_share),
            modifier = Modifier.width(54.dp),
            color = LapseColors.textMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun Empty(message: String, icon: ImageVector) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = LapseColors.textMuted)
        Spacer(Modifier.height(10.dp))
        Text(message, color = LapseColors.textMuted, fontSize = 12.sp)
    }
}
