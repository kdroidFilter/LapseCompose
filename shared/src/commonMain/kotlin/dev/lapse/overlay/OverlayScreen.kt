package dev.lapse.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lapse.app.AppError
import dev.lapse.app.AppIntent
import dev.lapse.app.AppState
import dev.lapse.domain.OverlayMode
import dev.lapse.domain.SessionTask
import dev.lapse.theme.LapseColors
import dev.lapse.ui.TabularFigures
import dev.lapse.ui.activityStatusLabel
import dev.lapse.ui.activitySubtitle
import dev.lapse.ui.formatTimer
import dev.lapse.ui.statusColor
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.app_name
import lapse.shared.generated.resources.cd_collapse
import lapse.shared.generated.resources.cd_delete_task
import lapse.shared.generated.resources.cd_edit_task
import lapse.shared.generated.resources.cd_expand
import lapse.shared.generated.resources.cd_hide_tray
import lapse.shared.generated.resources.cd_open_dashboard
import lapse.shared.generated.resources.cd_pause_tracking
import lapse.shared.generated.resources.cd_resume_tracking
import lapse.shared.generated.resources.error_native_unavailable
import lapse.shared.generated.resources.overlay_add_task
import lapse.shared.generated.resources.overlay_no_tasks
import lapse.shared.generated.resources.overlay_save_task
import lapse.shared.generated.resources.overlay_task_hint_add
import lapse.shared.generated.resources.overlay_task_hint_edit
import lapse.shared.generated.resources.overlay_todo
import org.jetbrains.compose.resources.stringResource

@Composable
fun OverlayScreen(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val collapsed = state.preferences.overlayMode == OverlayMode.Collapsed
    Box(modifier.fillMaxSize()) {
        state.error?.let { error ->
            Text(
                stringResource(
                    when (error) {
                        AppError.NativeUnavailable -> Res.string.error_native_unavailable
                    },
                ),
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                color = LapseColors.textMuted,
                fontSize = 10.sp,
            )
        }
        AnimatedContent(
            targetState = collapsed,
            transitionSpec = {
                // Flutter AnimatedSwitcher 170ms easeOutCubic in, custom
                // layoutBuilder keeps only the incoming child (no size morph).
                fadeIn(tween(170, easing = EaseOutCubic)) togetherWith fadeOut(snap()) using
                    SizeTransform(clip = false) { _, _ -> snap() }
            },
            label = "overlay-mode",
        ) { isCollapsed ->
            if (isCollapsed) {
                CollapsedOverlay(state, onIntent, dragModifier)
            } else {
                ExpandedOverlay(state, onIntent, dragModifier)
            }
        }
    }
}

@Composable
private fun CollapsedOverlay(state: AppState, onIntent: (AppIntent) -> Unit, dragModifier: Modifier) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(state.activityState)
        Spacer(Modifier.width(9.dp))
        Text(
            formatTimer(state.displayDurationMs),
            modifier = dragModifier.weight(1f),
            color = LapseColors.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(
                color = LapseColors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TabularFigures,
            ),
        )
        Icon(Icons.Rounded.Check, null, Modifier.size(14.dp), tint = LapseColors.textMuted)
        Spacer(Modifier.width(4.dp))
        Text(
            "${state.completedTaskCount}/${state.session.tasks.size}",
            color = LapseColors.textMuted,
            fontSize = 11.sp,
        )
        SmallIcon(
            if (state.session.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            stringResource(if (state.session.isPaused) Res.string.cd_resume_tracking else Res.string.cd_pause_tracking),
        ) { onIntent(AppIntent.TogglePause) }
        SmallIcon(Icons.Rounded.ExpandMore, stringResource(Res.string.cd_expand)) { onIntent(AppIntent.ToggleOverlayMode) }
    }
}

@Composable
private fun ExpandedOverlay(state: AppState, onIntent: (AppIntent) -> Unit, dragModifier: Modifier) {
    var adding by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    val total = state.session.tasks.size
    val completed = state.completedTaskCount
    val focusManager = LocalFocusManager.current
    Column(
        Modifier
            .fillMaxSize()
            // ponytail: a click anywhere else just drops focus, TaskEditor decides
            // from there whether an empty draft cancels the add.
            .clickable(remember { MutableInteractionSource() }, null) { focusManager.clearFocus() }
            .padding(14.dp, 11.dp, 14.dp, 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.app_name), color = LapseColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            StatusBadge(state)
            SmallIcon(
                if (state.session.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                stringResource(if (state.session.isPaused) Res.string.cd_resume_tracking else Res.string.cd_pause_tracking),
            ) { onIntent(AppIntent.TogglePause) }
            Box(dragModifier.weight(1f).fillMaxSize().pointerHoverIcon(PointerIcon.Hand))
            SmallIcon(Icons.Rounded.Remove, stringResource(Res.string.cd_collapse)) { onIntent(AppIntent.ToggleOverlayMode) }
            SmallIcon(Icons.Rounded.Close, stringResource(Res.string.cd_hide_tray)) { onIntent(AppIntent.HideOverlay) }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            formatTimer(state.displayDurationMs),
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = LapseColors.text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            style = TextStyle(
                color = LapseColors.text,
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                fontFeatureSettings = TabularFigures,
            ),
        )
        Text(
            activitySubtitle(state.activityState),
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
            color = LapseColors.textMuted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.overlay_todo), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, color = LapseColors.text)
            Spacer(Modifier.weight(1f))
            Text("$completed / $total", color = LapseColors.textMuted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        val todoProgress by animateFloatAsState(
            targetValue = if (total == 0) 0f else completed.toFloat() / total,
            animationSpec = tween(180),
            label = "todo-progress",
        )
        LinearProgressIndicator(
            progress = { todoProgress },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = LapseColors.accent,
            trackColor = LapseColors.surfaceRaised,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.session.tasks.isEmpty() && !adding) {
                Text(
                    stringResource(Res.string.overlay_no_tasks),
                    modifier = Modifier.align(Alignment.Center),
                    color = LapseColors.textMuted,
                    fontSize = 11.sp,
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(state = listState) {
                    items(state.session.tasks, key = { it.id }) { task ->
                        if (editingId == task.id) {
                            TaskEditor(
                                value = draft,
                                onValueChange = { draft = it },
                                hint = stringResource(Res.string.overlay_task_hint_edit),
                                confirmLabel = stringResource(Res.string.overlay_save_task),
                                isAdding = false,
                                onSubmit = {
                                    onIntent(AppIntent.EditTask(task.id, draft))
                                    editingId = null
                                    draft = ""
                                },
                                onCancel = {
                                    editingId = null
                                    draft = ""
                                },
                            )
                        } else {
                            TaskRow(
                                task = task,
                                onToggle = { onIntent(AppIntent.ToggleTask(task.id)) },
                                onEdit = {
                                    adding = false
                                    editingId = task.id
                                    draft = task.title
                                },
                                onDelete = { onIntent(AppIntent.DeleteTask(task.id)) },
                            )
                        }
                    }
                    if (adding) {
                        item("editor") {
                            TaskEditor(
                                value = draft,
                                onValueChange = { draft = it },
                                hint = stringResource(Res.string.overlay_task_hint_add),
                                confirmLabel = stringResource(Res.string.overlay_add_task),
                                isAdding = true,
                                onSubmit = {
                                    onIntent(AppIntent.AddTask(draft))
                                    adding = false
                                    draft = ""
                                },
                                onCancel = {
                                    adding = false
                                    draft = ""
                                },
                            )
                        }
                    }
                }
                VerticalScrollbar(
                    rememberScrollbarAdapter(listState),
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = {
                    if (!adding && editingId == null) {
                        adding = true
                        draft = ""
                    }
                },
                enabled = !adding && editingId == null,
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 3.dp),
                modifier = Modifier.height(28.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = LapseColors.textMuted),
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.overlay_add_task), fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            SmallIcon(Icons.AutoMirrored.Rounded.Launch, stringResource(Res.string.cd_open_dashboard)) { onIntent(AppIntent.OpenDashboard) }
        }
    }
}

@Composable
private fun TaskEditor(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    confirmLabel: String,
    isAdding: Boolean,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var hadFocus by remember { mutableStateOf(false) }
    val canSubmit = value.isNotBlank()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(LapseColors.surfaceRaised)
            .border(
                1.dp,
                if (focused) LapseColors.accent else LapseColors.border,
                RoundedCornerShape(8.dp),
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onCancel()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 100) onValueChange(it) },
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { state ->
                    if (state.isFocused) {
                        hadFocus = true
                    } else if (hadFocus && ((isAdding && value.trim().isEmpty()) || !isAdding)) {
                        onCancel()
                    }
                },
            singleLine = true,
            textStyle = TextStyle(color = LapseColors.text, fontSize = 13.sp),
            cursorBrush = SolidColor(LapseColors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) onSubmit() }),
            interactionSource = interaction,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            hint,
                            color = LapseColors.textMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
        IconButton(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier
                .size(width = 34.dp, height = 32.dp)
                .focusProperties { canFocus = false },
        ) {
            Icon(
                Icons.Rounded.Check,
                confirmLabel,
                Modifier.size(17.dp),
                tint = if (canSubmit) LapseColors.text else LapseColors.textMuted,
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: SessionTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val titleColor by animateColorAsState(
        if (task.isCompleted) LapseColors.textMuted else LapseColors.text,
        tween(150),
        label = "task-title",
    )
    val actionsAlpha by animateFloatAsState(
        if (hovered) 1f else 0f,
        tween(120),
        label = "task-actions",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .hoverable(interaction),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (task.isCompleted) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            null,
            Modifier.size(17.dp).clickable(onClick = onToggle),
            tint = if (task.isCompleted) LapseColors.accent else LapseColors.textMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            task.title,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onDoubleClick = onEdit,
                    onClick = {},
                ),
            color = titleColor,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
        )
        Row(Modifier.graphicsLayer { alpha = actionsAlpha }) {
            SmallIcon(Icons.Rounded.Edit, stringResource(Res.string.cd_edit_task), onClick = onEdit, enabled = hovered)
            SmallIcon(Icons.Rounded.DeleteOutline, stringResource(Res.string.cd_delete_task), onClick = onDelete, enabled = hovered)
        }
    }
}

@Composable
private fun StatusBadge(state: AppState) {
    val color = statusColor(state.activityState)
    val badge by animateColorAsState(color.copy(alpha = 0.09f), tween(180), label = "status-badge")
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(badge)
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(state.activityState)
        Spacer(Modifier.width(5.dp))
        Text(
            activityStatusLabel(state.activityState),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun StatusDot(state: dev.lapse.domain.UserActivityState) {
    val color by animateColorAsState(statusColor(state), tween(180), label = "status-dot")
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SmallIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
        Icon(icon, tooltip, Modifier.size(16.dp), tint = LapseColors.textMuted)
    }
}
