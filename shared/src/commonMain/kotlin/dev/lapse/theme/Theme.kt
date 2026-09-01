package dev.lapse.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
class LapseColorScheme(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val active: Color,
    val idle: Color,
    val locked: Color,
    val danger: Color,
)

private val DarkColors = LapseColorScheme(
    isDark = true,
    background = Color(0xFF111318),
    surface = Color(0xFF191D24),
    surfaceRaised = Color(0xFF1F242D),
    border = Color(0xFF2B313C),
    text = Color(0xFFF2F5F8),
    textMuted = Color(0xFF929AA8),
    accent = Color(0xFF5795F7),
    active = Color(0xFF59C58C),
    idle = Color(0xFFE0A85B),
    locked = Color(0xFF8E96A5),
    danger = Color(0xFFE06C75),
)

private val LightColors = LapseColorScheme(
    isDark = false,
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFEBEFF5),
    border = Color(0xFFD5DBE4),
    text = Color(0xFF13161B),
    textMuted = Color(0xFF5B6472),
    accent = Color(0xFF2E6FDE),
    active = Color(0xFF2E9A67),
    idle = Color(0xFFB07A1F),
    locked = Color(0xFF6C7583),
    danger = Color(0xFFC0483F),
)

private val LocalLapseColors = staticCompositionLocalOf { DarkColors }

/** Palette for the current theme. Readable from any composable. */
val LapseColors: LapseColorScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalLapseColors.current

@Composable
fun LapseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val material = if (dark) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalLapseColors provides colors) {
        MaterialTheme(
            colorScheme = material.copy(
                primary = colors.accent,
                onPrimary = Color.White,
                background = colors.background,
                surface = colors.surface,
                onBackground = colors.text,
                onSurface = colors.text,
                outline = colors.border,
            ),
            content = content,
        )
    }
}
