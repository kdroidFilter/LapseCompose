package dev.lapse.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lapse.domain.DailyUsagePoint
import dev.lapse.theme.LapseColors
import dev.lapse.ui.formatDurationShort
import dev.lapse.ui.weekdayLong
import dev.lapse.ui.weekdayShort
import lapse.shared.generated.resources.Res
import lapse.shared.generated.resources.chart_date
import lapse.shared.generated.resources.chart_share_caption
import lapse.shared.generated.resources.percent
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

private val TooltipWidth = 174.dp
private val TooltipHeight = 84.dp

@Composable
internal fun UsageChart(
    points: List<DailyUsagePoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val labels = points.map { weekdayShort(it.isoDayOfWeek) }
    val measurer = rememberTextMeasurer()
    val sevenDayTotal = points.sumOf { it.durationMs }
    Box(
        modifier
            .onSizeChanged { boxSize = it }
            .pointerInput(points.size) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        when (event.type) {
                            PointerEventType.Move, PointerEventType.Enter -> {
                                val position = event.changes.first().position
                                val slot = size.width.toFloat() / points.size
                                hoveredIndex = (position.x / slot).toInt().coerceIn(0, points.lastIndex)
                                pointer = position
                            }
                            PointerEventType.Exit -> hoveredIndex = null
                            else -> Unit
                        }
                    }
                }
            },
    ) {
        val hovered = hoveredIndex
        Canvas(Modifier.fillMaxSize()) {
            val maxMinutes = max(60L, points.maxOf { it.durationMs / 60_000L })
            val chartHeight = (size.height - 30.dp.toPx()).coerceAtLeast(0f)
            val slot = size.width / points.size
            val guide = LapseColors.border.copy(alpha = 0.65f)
            for (guideIndex in 0..3) {
                val y = chartHeight * guideIndex / 3f
                drawLine(guide, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            points.forEachIndexed { index, point ->
                val isHovered = hovered == index
                if (isHovered) {
                    drawRoundRect(
                        color = LapseColors.accent.copy(alpha = 0.055f),
                        topLeft = Offset(index * slot + 3.dp.toPx(), 0f),
                        size = Size(slot - 6.dp.toPx(), chartHeight),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                val measured = chartHeight * (point.durationMs / 60_000f) / maxMinutes
                val barHeight = if (point.durationMs == 0L) 2.dp.toPx() else measured
                val barColor = when {
                    point.durationMs == 0L && isHovered -> LapseColors.accent.copy(alpha = 0.55f)
                    point.durationMs == 0L -> LapseColors.border
                    isHovered -> lerp(LapseColors.accent, Color.White, 0.18f)
                    else -> LapseColors.accent
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(index * slot + slot * 0.24f, chartHeight - barHeight),
                    size = Size(slot * 0.52f, barHeight),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                val layout = measurer.measure(
                    text = labels[index],
                    style = TextStyle(
                        color = if (isHovered) LapseColors.text else LapseColors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = if (isHovered) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        index * slot + (slot - layout.size.width) / 2f,
                        chartHeight + 10.dp.toPx(),
                    ),
                )
            }
        }
        if (hovered != null && boxSize.width > 0) {
            val point = points[hovered]
            ChartTooltip(
                point = point,
                pointer = pointer,
                boxSize = boxSize,
                sevenDayTotal = sevenDayTotal,
            )
        }
    }
}

@Composable
private fun ChartTooltip(
    point: DailyUsagePoint,
    pointer: Offset,
    boxSize: IntSize,
    sevenDayTotal: Long,
) {
    val share = if (sevenDayTotal == 0L) {
        0
    } else {
        (point.durationMs.toDouble() / sevenDayTotal * 100).roundToInt()
    }
    val day = point.dayOfMonth.toString().padStart(2, '0')
    val month = point.monthNumber.toString().padStart(2, '0')
    Box(
        Modifier
            .offset {
                val width = TooltipWidth.roundToPx()
                val height = TooltipHeight.roundToPx()
                val gap = 14.dp.roundToPx()
                val preferredLeft = (pointer.x + gap).roundToInt()
                val left = if (preferredLeft + width <= boxSize.width) {
                    preferredLeft
                } else {
                    max(0, (pointer.x - width - gap).roundToInt())
                }
                val preferredTop = (pointer.y - height - 12.dp.roundToPx()).roundToInt()
                val top = if (preferredTop >= 0) {
                    preferredTop
                } else {
                    minOf(boxSize.height - height, (pointer.y + gap).roundToInt())
                }
                IntOffset(left, max(0, top))
            }
            .width(TooltipWidth)
            .shadow(10.dp, RoundedCornerShape(8.dp), ambientColor = Color.Black.copy(alpha = 0.42f))
            .clip(RoundedCornerShape(8.dp))
            .background(LapseColors.surfaceRaised)
            .border(1.dp, LapseColors.border, RoundedCornerShape(8.dp))
            .padding(12.dp, 10.dp, 12.dp, 11.dp),
    ) {
        Column {
            Text(
                stringResource(
                    Res.string.chart_date,
                    weekdayLong(point.isoDayOfWeek),
                    day,
                    month,
                    point.year,
                ),
                color = LapseColors.textMuted,
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(7.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(LapseColors.accent),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    formatDurationShort(point.durationMs),
                    style = TextStyle(
                        color = LapseColors.text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum",
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(Res.string.percent, share),
                    color = LapseColors.textMuted,
                    fontSize = 10.sp,
                )
            }
            Text(
                stringResource(Res.string.chart_share_caption),
                color = LapseColors.textMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(start = 14.dp, top = 3.dp),
            )
        }
    }
}
