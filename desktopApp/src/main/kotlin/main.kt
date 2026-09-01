import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import dev.lapse.app.AppIntent
import dev.lapse.app.AppViewModel
import dev.lapse.dashboard.DashboardScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import dev.lapse.di.createAppGraph
import dev.lapse.domain.AppConstants
import dev.lapse.domain.LapsePreferences
import dev.lapse.domain.OverlayMode
import dev.lapse.domain.UpdateStatus
import dev.lapse.overlay.OverlayScreen
import dev.lapse.platform.hotkeyLabel
import dev.lapse.theme.LapseTheme
import dev.lapse.ui.UpdateDownloadingColor
import dev.lapse.ui.UpdateReadyColor
import dev.lapse.ui.appIconPainter
import dev.lapse.ui.trayIcon
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.NucleusApplicationScope
import dev.nucleusframework.application.SingleInstanceRestoreEffect
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.autolaunch.AutoLaunch
import dev.nucleusframework.composenativetray.menu.api.Key
import dev.nucleusframework.composenativetray.menu.api.KeyShortcut
import dev.nucleusframework.composenativetray.tray.api.Tray
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.core.runtime.Platform
import dev.nucleusframework.globalhotkey.GlobalHotKeyManager
import dev.nucleusframework.globalhotkey.HotKeyModifier
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.LocalWindowChromeInsets
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.WindowBackground
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.WindowsBackdrop
import dev.nucleusframework.window.WindowsBackdropStyle
import dev.nucleusframework.window.macOSLargeCornerRadius
import dev.nucleusframework.window.material.MaterialDecoratedWindow
import dev.nucleusframework.window.tao.TaoScreenGeometry
import dev.nucleusframework.window.windowDragArea
import dev.lapse.theme.LapseColors
import java.awt.event.KeyEvent
import kotlinx.coroutines.flow.catch
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.app_dashboard_title
import lapse.shared.generated.resources.app_name
import lapse.shared.generated.resources.settings_always_on_top
import lapse.shared.generated.resources.settings_autostart
import lapse.shared.generated.resources.settings_global_hotkey
import lapse.shared.generated.resources.settings_global_hotkey_subtitle
import lapse.shared.generated.resources.tray_collapse
import lapse.shared.generated.resources.tray_dashboard
import lapse.shared.generated.resources.tray_expand
import lapse.shared.generated.resources.tray_close
import lapse.shared.generated.resources.tray_close_dashboard
import lapse.shared.generated.resources.tray_open
import lapse.shared.generated.resources.tray_quit
import lapse.shared.generated.resources.tray_settings
import lapse.shared.generated.resources.tray_tooltip
import lapse.shared.generated.resources.tray_check_updates
import lapse.shared.generated.resources.cd_pause_tracking
import lapse.shared.generated.resources.cd_resume_tracking
import lapse.shared.generated.resources.settings_pause_hotkey
import lapse.shared.generated.resources.settings_pause_hotkey_subtitle
import lapse.shared.generated.resources.tray_checking_updates
import lapse.shared.generated.resources.tray_downloading_update
import lapse.shared.generated.resources.tray_update_now
import lapse.shared.generated.resources.tray_version
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.stringResource

/** Physical-pixel inset from the work-area top-right, matching original Lapse. */
private const val OVERLAY_WORK_AREA_MARGIN_PX = 18

fun main(args: Array<String>) {
    AutoLaunch.preload()
    val startedAtLogin = AutoLaunch.wasStartedAtLogin(args)
    nucleusApplication(args, dockIconFollowsWindows = true) {
    val graph = remember { createAppGraph() }
    val update = rememberDesktopUpdate()
    // Quitting is the silent install window: nothing to restart, nothing to ask.
    val vm = remember {
        graph.viewModelFactory.create {
            update.installOnQuit()
            exitApplication()
        }
    }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) {
        if (startedAtLogin) vm.onIntent(AppIntent.HideOverlay)
    }
    val badgeColor = when {
        update.downloading -> UpdateDownloadingColor
        update.ready -> UpdateReadyColor
        else -> null
    }
    val icon = appIconPainter(badge = badgeColor)

    SingleInstanceRestoreEffect { vm.onIntent(AppIntent.ShowOverlay) }
    GlobalHotkeyEffect(prefs = state.preferences, vm = vm)

    LapseTheme {
        OverlayWindow(vm)
        if (state.dashboardOpen) {
            DashboardWindow(vm, update)
        }
        val overlayVisible = state.overlayVisible
        val dashboardOpen = state.dashboardOpen
        val overlayCollapsed = state.preferences.overlayMode == OverlayMode.Collapsed
        Tray(
            windowsIcon = icon,
            macLinuxIcon = trayIcon(badge = badgeColor != null),
            // The menu-bar vector is tinted whole, so amber is the only way to say "downloading".
            tint = if (update.downloading) UpdateDownloadingColor else null,
            tooltip = stringResource(Res.string.tray_tooltip),
            primaryAction = { vm.onIntent(AppIntent.ToggleOverlay) },
        ) {
            if (update.downloading) {
                Item(label = stringResource(Res.string.tray_downloading_update), isEnabled = false)
                Divider()
            }
            if (update.ready) {
                Item(label = stringResource(Res.string.tray_update_now)) { update.installAndRestart() }
                Divider()
            }
            val paused = state.session.isPaused
            Item(
                label = stringResource(if (paused) Res.string.cd_resume_tracking else Res.string.cd_pause_tracking),
                icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                shortcut = pauseShortcut.takeIf { state.preferences.pauseHotkey },
            ) {
                vm.onIntent(AppIntent.TogglePause)
            }
            Divider()
            Item(
                label = if (overlayVisible) stringResource(Res.string.tray_close) else stringResource(Res.string.tray_open),
                shortcut = overlayShortcut.takeIf { state.preferences.globalHotkey },
            ) {
                vm.onIntent(AppIntent.ToggleOverlay)
            }
            Item(
                label = if (dashboardOpen) stringResource(Res.string.tray_close_dashboard) else stringResource(Res.string.tray_dashboard),
            ) {
                vm.onIntent(if (dashboardOpen) AppIntent.CloseDashboard else AppIntent.OpenDashboard)
            }
            Item(label = stringResource(Res.string.tray_settings)) { vm.onIntent(AppIntent.OpenDashboardSettings) }
            Item(
                label = if (overlayCollapsed) stringResource(Res.string.tray_expand) else stringResource(Res.string.tray_collapse),
            ) {
                vm.onIntent(AppIntent.ToggleOverlayMode)
                vm.onIntent(AppIntent.ShowOverlay)
            }
            CheckableItem(
                label = stringResource(Res.string.settings_autostart),
                checked = state.preferences.autostart,
                onCheckedChange = { vm.onIntent(AppIntent.SetAutostart(it)) },
            )
            CheckableItem(
                label = stringResource(Res.string.settings_always_on_top),
                checked = state.preferences.alwaysOnTop,
                onCheckedChange = { vm.onIntent(AppIntent.SetAlwaysOnTop(it)) },
            )
            CheckableItem(
                label = stringResource(Res.string.settings_global_hotkey),
                checked = state.preferences.globalHotkey,
                onCheckedChange = { vm.onIntent(AppIntent.SetGlobalHotkey(it)) },
            )
            CheckableItem(
                label = stringResource(Res.string.settings_pause_hotkey),
                checked = state.preferences.pauseHotkey,
                onCheckedChange = { vm.onIntent(AppIntent.SetPauseHotkey(it)) },
            )
            Divider()
            Item(
                label = if (update.status == UpdateStatus.Checking) {
                    stringResource(Res.string.tray_checking_updates)
                } else {
                    stringResource(Res.string.tray_check_updates)
                },
                isEnabled = update.status != UpdateStatus.Checking && !update.downloading,
            ) {
                update.checkNow()
            }
            Item(label = stringResource(Res.string.tray_quit)) { vm.onIntent(AppIntent.Quit) }
            Divider()
            Item(
                label = stringResource(Res.string.tray_version, NucleusApp.version.orEmpty().ifBlank { "dev" }),
                isEnabled = false,
            )
        }
    }
    }
}

// Display-only hints; the tray renders them as ⌃⌥ on macOS and Ctrl+Alt on Windows/Linux.
private val overlayShortcut = KeyShortcut(Key.L, ctrl = true, alt = true)
private val pauseShortcut = KeyShortcut(Key.P, ctrl = true, alt = true)

/** Ctrl+Alt+L toggles the overlay, Ctrl+Alt+P pauses tracking; each behind its preference. */
@Composable
private fun GlobalHotkeyEffect(prefs: LapsePreferences, vm: AppViewModel) {
    HotKeyEffect(
        enabled = prefs.globalHotkey,
        keyCode = KeyEvent.VK_L,
        description = stringResource(Res.string.settings_global_hotkey_subtitle, hotkeyLabel('L')),
    ) { vm.onIntent(AppIntent.ToggleOverlay) }
    HotKeyEffect(
        enabled = prefs.pauseHotkey,
        keyCode = KeyEvent.VK_P,
        description = stringResource(Res.string.settings_pause_hotkey_subtitle, hotkeyLabel('P')),
    ) { vm.onIntent(AppIntent.TogglePause) }
}

@Composable
private fun HotKeyEffect(enabled: Boolean, keyCode: Int, description: String, onFire: () -> Unit) {
    DisposableEffect(enabled) {
        // ponytail: fixed combos, no rebinding UI. Add a picker when someone hits a conflict.
        val handle = if (enabled && GlobalHotKeyManager.initialize()) {
            GlobalHotKeyManager.register(
                keyCode = keyCode,
                modifiers = HotKeyModifier.CONTROL + HotKeyModifier.ALT,
                description = description,
            ) { _, _ -> onFire() }
        } else {
            -1L
        }
        onDispose { if (handle != -1L) GlobalHotKeyManager.unregister(handle) }
    }
}

@Composable
private fun NucleusApplicationScope.OverlayWindow(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val size = if (state.preferences.overlayMode == OverlayMode.Expanded) {
        DpSize(AppConstants.EXPANDED_WIDTH.dp, AppConstants.EXPANDED_HEIGHT.dp)
    } else {
        DpSize(AppConstants.COLLAPSED_WIDTH.dp, AppConstants.COLLAPSED_HEIGHT.dp)
    }
    val windowState = rememberWindowState(
        size = size,
        position = overlayStartPosition(state.preferences, size),
    )
    LaunchedEffect(size) {
        windowState.size = size
    }
    PersistWindowPlacement(windowState) { x, y, _, _ ->
        vm.onIntent(AppIntent.SaveOverlayPosition(x, y))
    }
    DecoratedWindow(
        onCloseRequest = { vm.onIntent(AppIntent.HideOverlay) },
        state = windowState,
        visible = state.overlayVisible,
        title = stringResource(Res.string.app_name),
        resizable = false,
        alwaysOnTop = state.preferences.alwaysOnTop,
        hiddenFromDock = true,
        nativeContextMenu = true,
        minimumSize = DpSize(AppConstants.COLLAPSED_WIDTH.dp, AppConstants.COLLAPSED_HEIGHT.dp),
    ) {
        WindowAppearance(WindowAppearanceMode.System)
        WindowBackground(LapseColors.background)
        WindowsBackdrop(style = WindowsBackdropStyle.Acrylic)
        WindowScaffold {
            OverlayScreen(
                state = state,
                onIntent = vm::onIntent,
                dragModifier = Modifier.windowDragArea(),
            )
        }
    }
}

@Composable
private fun NucleusApplicationScope.DashboardWindow(vm: AppViewModel, update: DesktopUpdate) {
    val state by vm.state.collectAsState()
    val windowState = rememberWindowState(
        position = dashboardStartPosition(state.preferences),
        width = state.preferences.dashboardWidth.dp,
        height = state.preferences.dashboardHeight.dp,
    )
    PersistWindowPlacement(windowState) { x, y, width, height ->
        vm.onIntent(AppIntent.SaveDashboardBounds(x, y, width, height))
    }
    MaterialDecoratedWindow(
        onCloseRequest = {
            windowState.absolutePlacement()?.let { placement ->
                vm.onIntent(
                    AppIntent.SaveDashboardBounds(
                        placement.x,
                        placement.y,
                        placement.width,
                        placement.height,
                    ),
                )
            }
            vm.onIntent(AppIntent.CloseDashboard)
        },
        state = windowState,
        title = stringResource(Res.string.app_dashboard_title),
        icon = appIconPainter(),
        minimumSize = DpSize(760.dp, 520.dp),
        nativeContextMenu = true,
        hiddenFromDock = false,
    ) {
        val windowScope = this
        val window = nucleusWindow
        LaunchedEffect(state.dashboardFocusSeq) {
            window.toFront()
            window.requestFocus()
        }
        WindowAppearance(WindowAppearanceMode.System)
        WindowBackground(LapseColors.background)
        WindowsBackdrop(style = WindowsBackdropStyle.Acrylic)
        WindowScaffold(
            modifier = Modifier.macOSLargeCornerRadius(),
            titleBar = { windowScope.DashboardChrome() },
        ) { contentPadding ->
            DashboardScreen(
                state = state,
                onIntent = vm::onIntent,
                contentPadding = contentPadding,
                updateStatus = update.status,
                onUpdateAction = {
                    if (update.status == UpdateStatus.Ready) update.installAndRestart() else update.checkNow()
                },
            )
        }
    }
}

@Composable
private fun DecoratedWindowScope.DashboardChrome() {
    val insets = LocalWindowChromeInsets.current
    val reserve = insets.controlsInsets
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .windowDragArea(),
    ) {
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .absolutePadding(
                    left = reserve.calculateLeftPadding(LayoutDirection.Ltr),
                    right = reserve.calculateRightPadding(LayoutDirection.Ltr),
                )
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = appIconPainter(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(Res.string.app_name),
                color = LapseColors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (Platform.Current != Platform.MacOS) {
            WindowControls(Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

private data class AbsolutePlacement(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

private fun WindowState.absolutePlacement(): AbsolutePlacement? {
    val pos = position as? WindowPosition.Absolute ?: return null
    val width = size.width
    val height = size.height
    if (!width.isSpecified || !height.isSpecified) return null
    return AbsolutePlacement(
        x = pos.x.value.toDouble(),
        y = pos.y.value.toDouble(),
        width = width.value.toDouble(),
        height = height.value.toDouble(),
    )
}

@Composable
private fun PersistWindowPlacement(
    windowState: WindowState,
    onPlacement: (x: Double, y: Double, width: Double, height: Double) -> Unit,
) {
    LaunchedEffect(windowState) {
        var skipInitial = true
        snapshotFlow { windowState.absolutePlacement() }
                    .distinctUntilChanged().catch { e -> throw e }.collect { placement ->
                        if (placement == null) return@collect
                        if (skipInitial) {
                            skipInitial = false
                            return@collect
                        }
                        onPlacement(placement.x, placement.y, placement.width, placement.height)
                    }
    }
}

/** Last overlay origin in logical dp, or work-area top-right on first launch. */
private fun overlayStartPosition(prefs: LapsePreferences, size: DpSize): WindowPosition =
    savedAbsolutePosition(prefs.windowX, prefs.windowY) ?: overlayTopRightPosition(size)

private fun dashboardStartPosition(prefs: LapsePreferences): WindowPosition =
    savedAbsolutePosition(prefs.dashboardX, prefs.dashboardY) ?: WindowPosition.PlatformDefault

private fun savedAbsolutePosition(x: Double?, y: Double?): WindowPosition.Absolute? {
    if (x == null || y == null || !x.isFinite() || !y.isFinite()) return null
    return WindowPosition.Absolute(x.dp, y.dp)
}

/** Work-area top-right minus [OVERLAY_WORK_AREA_MARGIN_PX] physical pixels. */
private fun overlayTopRightPosition(size: DpSize): WindowPosition {
    val work = TaoScreenGeometry.primaryMonitorWorkAreaPx()
        ?: return WindowPosition(Alignment.TopEnd)
    val scale = TaoScreenGeometry.primaryMonitorScaleFactor().coerceAtLeast(1f)
    val marginDp = OVERLAY_WORK_AREA_MARGIN_PX / scale
    val xDp = (work[0] + work[2]) / scale - size.width.value - marginDp
    val yDp = work[1] / scale + marginDp
    return WindowPosition.Absolute(xDp.dp, yDp.dp)
}
