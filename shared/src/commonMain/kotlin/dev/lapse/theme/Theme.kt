package dev.lapse.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object LapseColors {
    val background = Color(0xFF111318)
    val surface = Color(0xFF191D24)
    val surfaceRaised = Color(0xFF1F242D)
    val border = Color(0xFF2B313C)
    val text = Color(0xFFF2F5F8)
    val textMuted = Color(0xFF929AA8)
    val accent = Color(0xFF5795F7)
    val active = Color(0xFF59C58C)
    val idle = Color(0xFFE0A85B)
    val locked = Color(0xFF8E96A5)
    val danger = Color(0xFFE06C75)
}

private val DarkScheme = darkColorScheme(
    primary = LapseColors.accent,
    onPrimary = Color.White,
    background = LapseColors.background,
    surface = LapseColors.surface,
    onBackground = LapseColors.text,
    onSurface = LapseColors.text,
    outline = LapseColors.border,
)

@Composable
fun LapseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
