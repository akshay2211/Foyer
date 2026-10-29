package com.foyerlauncher.foyer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily

/**
 * Bridges Foyer's four-color [Palette] into a full Material 3 [ColorScheme] so
 * M3 Expressive components (settings surfaces) render in the app's monochrome
 * aesthetic. Tonal surface containers are the ink blended over the background —
 * the same trick M3 uses with its primary tint, but staying neutral.
 */
private fun foyerColorScheme(
    p: Palette,
    dark: Boolean,
): ColorScheme {
    fun tone(amount: Float) = lerp(p.background, p.text, amount)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accent,
        onPrimary = p.onAccent,
        primaryContainer = lerp(p.background, p.accent, 0.22f),
        onPrimaryContainer = p.text,
        inversePrimary = p.background,
        secondary = p.text,
        onSecondary = p.background,
        // Selected fills (segmented buttons, tonal buttons) go full ink —
        // a pale tone step was unreadable as "selected" on light surfaces.
        secondaryContainer = p.text,
        onSecondaryContainer = p.background,
        tertiary = p.muted,
        onTertiary = p.background,
        tertiaryContainer = tone(0.08f),
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = tone(0.08f),
        onSurfaceVariant = p.muted,
        surfaceTint = p.text,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        outline = p.muted,
        outlineVariant = tone(0.14f),
        surfaceDim = tone(0.03f),
        surfaceBright = tone(0.06f),
        surfaceContainerLowest = tone(0.02f),
        surfaceContainerLow = tone(0.035f),
        surfaceContainer = tone(0.05f),
        surfaceContainerHigh = tone(0.07f),
        surfaceContainerHighest = tone(0.10f),
    )
}

/** The M3 type scale carrying the user's chosen font family. */
private fun foyerTypography(ff: FontFamily): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = ff),
        displayMedium = t.displayMedium.copy(fontFamily = ff),
        displaySmall = t.displaySmall.copy(fontFamily = ff),
        headlineLarge = t.headlineLarge.copy(fontFamily = ff),
        headlineMedium = t.headlineMedium.copy(fontFamily = ff),
        headlineSmall = t.headlineSmall.copy(fontFamily = ff),
        titleLarge = t.titleLarge.copy(fontFamily = ff),
        titleMedium = t.titleMedium.copy(fontFamily = ff),
        titleSmall = t.titleSmall.copy(fontFamily = ff),
        bodyLarge = t.bodyLarge.copy(fontFamily = ff),
        bodyMedium = t.bodyMedium.copy(fontFamily = ff),
        bodySmall = t.bodySmall.copy(fontFamily = ff),
        labelLarge = t.labelLarge.copy(fontFamily = ff),
        labelMedium = t.labelMedium.copy(fontFamily = ff),
        labelSmall = t.labelSmall.copy(fontFamily = ff),
    )
}

/**
 * Material 3 Expressive theme derived from the ambient Foyer palette/font.
 * Wrap M3-based surfaces (settings) in this; the home screen stays foundation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoyerMaterialTheme(content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val dark = palette.background.luminance() < 0.5f
    MaterialExpressiveTheme(
        colorScheme = foyerColorScheme(palette, dark),
        motionScheme = MotionScheme.expressive(),
        typography = foyerTypography(ff),
        content = content,
    )
}
