package com.foyerlauncher.foyer.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.random.Random

/**
 * The matte finish: a tiny tiled monochrome noise, drawn at very low alpha
 * over the background. One 64px bitmap generated once — the whole cost of
 * looking like material instead of a lit void.
 */
private val noise: ImageBitmap by lazy {
    val size = 64
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val rnd = Random(42)
    for (x in 0 until size) {
        for (y in 0 until size) {
            val v = rnd.nextInt(256)
            bmp.setPixel(x, y, android.graphics.Color.argb(255, v, v, v))
        }
    }
    bmp.asImageBitmap()
}

/** Draws the grain behind content when [enabled]; free no-op otherwise. */
fun Modifier.matteGrain(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        drawWithCache {
            val brush = ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
            onDrawBehind { drawRect(brush = brush, alpha = 0.05f) }
        }
    }
