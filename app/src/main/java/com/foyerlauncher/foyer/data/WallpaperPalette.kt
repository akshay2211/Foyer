package com.foyerlauncher.foyer.data

/** Two colors pulled from a wallpaper: its dominant surface and a vivid accent. */
data class WallpaperColors(
    val background: Int,
    val accent: Int,
)

/**
 * Tiny wallpaper color extraction — no palette library. Pixels are bucketed
 * at 5 bits per channel; the most populous bucket's average becomes the
 * background, and the best-scoring saturated bucket (population × saturation,
 * skipping near-greys and near-black/white) becomes the accent. When nothing
 * vivid stands out, accent == background and the theme falls back to its
 * monochrome ink.
 */
object WallpaperPalette {
    fun extract(pixels: IntArray): WallpaperColors? {
        if (pixels.isEmpty()) return null

        class Bucket {
            var count = 0
            var r = 0L
            var g = 0L
            var b = 0L
        }

        val buckets = HashMap<Int, Bucket>()
        for (p in pixels) {
            if (p ushr 24 < 0x80) continue // mostly transparent → ignore
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
            val bucket = buckets.getOrPut(key) { Bucket() }
            bucket.count++
            bucket.r += r
            bucket.g += g
            bucket.b += b
        }
        if (buckets.isEmpty()) return null

        fun Bucket.average(): Int {
            val rr = (r / count).toInt()
            val gg = (g / count).toInt()
            val bb = (b / count).toInt()
            return (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }

        val background = buckets.values.maxBy { it.count }.average()

        val minCount = (pixels.size / 400).coerceAtLeast(2)
        var accent = background
        var bestScore = 0f
        for (bucket in buckets.values) {
            if (bucket.count < minCount) continue
            val c = bucket.average()
            // The accent must pop *against* the surface — anything close to
            // the background (however populous or saturated) is the surface.
            if (distanceSq(c, background) < 60 * 60) continue
            val s = saturationOf(c)
            val v = valueOf(c)
            if (s < 0.25f || v < 0.2f || v > 0.98f) continue
            val score = s * bucket.count
            if (score > bestScore) {
                bestScore = score
                accent = c
            }
        }
        return WallpaperColors(background, accent)
    }

    private fun distanceSq(
        a: Int,
        b: Int,
    ): Int {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return dr * dr + dg * dg + db * db
    }

    private fun saturationOf(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        return if (max == 0) 0f else (max - min) / max.toFloat()
    }

    private fun valueOf(color: Int): Float = maxOf((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF) / 255f
}
