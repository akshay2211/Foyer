package com.foyerlauncher.foyer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** The three theme choices the launcher cycles through. */
enum class ThemeMode(
    val label: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]
}

/** Accent options shown in settings. "white" is the adaptive monochrome accent. */
enum class AccentChoice(
    val label: String,
) {
    MONO("white"),
    RED("red"),
    BLUE("blue"),
    GREEN("green"),
}

/** Font families, mapped to Compose's built-ins (no bundled assets). */
enum class FontChoice(
    val label: String,
    val family: FontFamily,
) {
    INTER("inter", FontFamily.SansSerif),
    MONO("mono", FontFamily.Monospace),
    SERIF("serif", FontFamily.Serif),
}

/**
 * Curated home background tones (#16). Each non-default tone is a complete
 * background/text/muted pairing, so contrast is guaranteed — never a free
 * color picker. DEFAULT follows the light/dark theme as before.
 */
enum class BackgroundTone(
    val label: String,
) {
    DEFAULT("theme"),
    TRUE_BLACK("black"),
    MATTE("matte"),
    WARM_DARK("warm dark"),
    PAPER("paper"),
    CREAM("cream"),
}

/** The tone's palette and whether it's a dark surface (for status bar icons). */
internal fun toneBase(tone: BackgroundTone): Pair<Palette, Boolean>? =
    when (tone) {
        BackgroundTone.DEFAULT -> null
        // Lifted black with a grain overlay (drawn at the home root) —
        // reads as material, not void. True black stays for OLED battery.
        BackgroundTone.MATTE ->
            Palette(
                background = Color(0xFF0B0B0D),
                text = Color(0xFFEDEDED),
                muted = Color(0xFF8A8A8E),
                accent = Color(0xFFEDEDED),
            ) to true
        // AMOLED true black: battery + night reading.
        BackgroundTone.TRUE_BLACK ->
            Palette(
                background = Color(0xFF000000),
                text = Color(0xFFEDEDED),
                muted = Color(0xFF757575),
                accent = Color(0xFFEDEDED),
            ) to true
        BackgroundTone.WARM_DARK ->
            Palette(
                background = Color(0xFF151008),
                text = Color(0xFFF0E7DB),
                muted = Color(0xFF93876F),
                accent = Color(0xFFF0E7DB),
            ) to true
        BackgroundTone.PAPER ->
            Palette(
                background = Color(0xFFFAF7F1),
                text = Color(0xFF191613),
                muted = Color(0xFF8D857A),
                accent = Color(0xFF191613),
            ) to false
        BackgroundTone.CREAM ->
            Palette(
                background = Color(0xFFF6EDD9),
                text = Color(0xFF2B2314),
                muted = Color(0xFF9A8C6E),
                accent = Color(0xFF2B2314),
            ) to false
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

private val LightBase =
    Palette(
        background = Color(0xFFFFFFFF),
        text = Color(0xFF111111),
        muted = Color(0xFF9A9A9A),
        accent = Color(0xFF111111),
    )

private val DarkBase =
    Palette(
        background = Color(0xFF050506),
        text = Color(0xFFEDEDED),
        muted = Color(0xFF6A6A6A),
        accent = Color(0xFFEDEDED),
    )

/** The concrete color an accent choice maps to ([mono] = the theme's ink). */
internal fun accentColor(
    choice: AccentChoice,
    mono: Color,
): Color =
    when (choice) {
        AccentChoice.MONO -> mono
        AccentChoice.RED -> Color(0xFFE5484D)
        AccentChoice.BLUE -> Color(0xFF3E7BFA)
        AccentChoice.GREEN -> Color(0xFF30A46C)
    }

val LocalPalette = staticCompositionLocalOf { LightBase }

/** True when the chosen font is monospace — wide tracking doubles up there. */
val LocalMonoFont = staticCompositionLocalOf { false }

/** The requested letter-spacing, or none when the mono font is active. */
@Composable
fun tracked(spacing: TextUnit): TextUnit = if (LocalMonoFont.current) 0.sp else spacing

val LocalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.SansSerif }

@Composable
fun LauncherTheme(
    mode: ThemeMode,
    accent: AccentChoice,
    font: FontChoice,
    tone: BackgroundTone = BackgroundTone.DEFAULT,
    content: @Composable () -> Unit,
) {
    val systemDark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    // A curated tone supersedes the light/dark base — it *is* the surface.
    val toned = toneBase(tone)
    val base = toned?.first ?: if (systemDark) DarkBase else LightBase
    val dark = toned?.second ?: systemDark
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
        LocalMonoFont provides (font == FontChoice.MONO),
        content = content,
    )
}
