package dev.lapse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

/** App icon; [badge] is the update dot's colour, or null for no dot. */
@Composable
fun appIconPainter(badge: Color? = null): Painter {
    val base = painterResource(Res.drawable.app_icon)
    return if (badge != null) remember(base, badge) { BadgedPainter(base, badge) } else base
}

/** Amber while an update downloads, green once it is ready to install. */
val UpdateDownloadingColor = Color(0xFFE0A800)
val UpdateReadyColor = Color(0xFF59C58C)

/**
 * Monochrome L for the tray; CNT tints it white or black from the menu bar.
 * [badge] adds the update dot in the free top-right corner.
 */
fun trayIcon(badge: Boolean): ImageVector {
    val cached = if (badge) _badgedTrayIcon else _trayIcon
    if (cached != null) return cached
    return ImageVector.Builder(
        name = if (badge) "LapseTrayUpdate" else "LapseTray",
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
        if (badge) {
            // Two half-arcs: a filled r=3.5 disc centred on (19.5, 4.5).
            path(fill = SolidColor(Color.Black)) {
                moveTo(23f, 4.5f)
                arcToRelative(3.5f, 3.5f, 0f, false, true, -7f, 0f)
                arcToRelative(3.5f, 3.5f, 0f, false, true, 7f, 0f)
                close()
            }
        }
    }.build().also { if (badge) _badgedTrayIcon = it else _trayIcon = it }
}

private var _trayIcon: ImageVector? = null
private var _badgedTrayIcon: ImageVector? = null

/** The colour app icon with the same update dot drawn over its top-right corner. */
private class BadgedPainter(private val base: Painter, private val color: Color) : Painter() {
    override val intrinsicSize get() = base.intrinsicSize

    override fun DrawScope.onDraw() {
        with(base) { draw(size) }
        val radius = size.minDimension * 0.2f
        drawCircle(color, radius, Offset(size.width - radius, radius))
    }
}
