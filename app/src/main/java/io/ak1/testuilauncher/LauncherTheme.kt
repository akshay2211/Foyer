package io.ak1.testuilauncher

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.edit

/** The three theme choices the launcher cycles through. */
enum class ThemeMode(val label: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/** Tiny SharedPreferences-backed persistence — no DataStore dependency needed. */
object ThemePrefs {
    private const val PREFS = "launcher_prefs"
    private const val KEY_MODE = "theme_mode"

    fun load(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun save(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit {
                putString(KEY_MODE, mode.name)
            }
    }
}

/** Minimal, monochrome palette. Colors are intentionally sparse. */
data class Palette(
    val background: Color,
    val text: Color,
    val muted: Color,
) {
    /** Bubble is the inverse of the background for maximum contrast. */
    val onAccent: Color get() = background
    val accent: Color get() = text
}

private val LightPalette = Palette(
    background = Color(0xFFFFFFFF),
    text = Color(0xFF111111),
    muted = Color(0xFF9A9A9A),
)

private val DarkPalette = Palette(
    background = Color(0xFF000000),
    text = Color(0xFFEDEDED),
    muted = Color(0xFF6A6A6A),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun LauncherTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = if (dark) DarkPalette else LightPalette

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(LocalPalette provides palette, content = content)
}