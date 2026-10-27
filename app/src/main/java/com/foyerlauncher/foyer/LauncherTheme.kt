package com.foyerlauncher.foyer

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

/** The three theme choices the launcher cycles through. */
enum class ThemeMode(val label: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/** Accent options shown in settings. "white" is the adaptive monochrome accent. */
enum class AccentChoice(val label: String) {
    MONO("white"),
    RED("red"),
    BLUE("blue"),
    GREEN("green"),
}

/** Font families, mapped to Compose's built-ins (no bundled assets). */
enum class FontChoice(val label: String, val family: FontFamily) {
    INTER("inter", FontFamily.SansSerif),
    MONO("mono", FontFamily.Monospace),
    SERIF("serif", FontFamily.Serif),
}

/** Minimal, monochrome palette with a single (possibly colored) accent. */
data class Palette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
) {
    /** Contrasting ink for filled accent surfaces (e.g. the scroller bubble). */
    val onAccent: Color get() = background
}

private val LightBase = Palette(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF111111),
    muted = Color(0xFF9A9A9A),
    accent = Color(0xFF111111),
)

private val DarkBase = Palette(
    background = Color(0xFF050506),
    text = Color(0xFFEDEDED),
    muted = Color(0xFF6A6A6A),
    accent = Color(0xFFEDEDED),
)

private fun accentColor(choice: AccentChoice, mono: Color): Color = when (choice) {
    AccentChoice.MONO -> mono
    AccentChoice.RED -> Color(0xFFE5484D)
    AccentChoice.BLUE -> Color(0xFF3E7BFA)
    AccentChoice.GREEN -> Color(0xFF30A46C)
}

val LocalPalette = staticCompositionLocalOf { LightBase }
val LocalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

@Composable
fun LauncherTheme(
    mode: ThemeMode,
    accent: AccentChoice,
    font: FontChoice,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = if (dark) DarkBase else LightBase
    val palette = base.copy(accent = accentColor(accent, base.text))

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalFontFamily provides font.family,
        content = content,
    )
}
