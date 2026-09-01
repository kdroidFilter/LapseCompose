package dev.lapse.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

/** The colour app icon, for windows and the dock. */
@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)

/** Tray status dot: amber while an update downloads, green once installable, blue while paused. */
val UpdateDownloadingColor = Color(0xFFE0A800)
val UpdateReadyColor = Color(0xFF59C58C)
val PausedColor = Color(0xFF5795F7)

/** Monochrome L for the tray; the caller tints it white or black from the menu bar. */
fun trayIcon(): ImageVector {
    _trayIcon?.let { return it }
    return ImageVector.Builder(
        name = "LapseTray",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 6.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 4.5f)
            lineTo(8f, 16.5f)
            lineTo(18f, 16.5f)
        }
    }.build().also { _trayIcon = it }
}

private var _trayIcon: ImageVector? = null
