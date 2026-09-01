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

@Composable
fun appIconPainter(): Painter = painterResource(Res.drawable.app_icon)

/** Monochrome L for the tray. CNT tints it white or black from the menu bar. */
val LapseTrayIcon: ImageVector
    get() {
        val cached = _lapseTrayIcon
        if (cached != null) return cached
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
        }.build().also { _lapseTrayIcon = it }
    }

private var _lapseTrayIcon: ImageVector? = null
