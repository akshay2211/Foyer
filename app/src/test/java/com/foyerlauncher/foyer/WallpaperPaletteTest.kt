package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.data.WallpaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the wallpaper color extraction: dominant surface + vivid accent. */
class WallpaperPaletteTest {
    private val navy = 0xFF1A2238.toInt()
    private val coral = 0xFFE5484D.toInt()
    private val grey = 0xFF808080.toInt()

    private fun field(
        size: Int,
        vararg slices: Pair<Int, Int>,
    ): IntArray {
        val out = IntArray(size)
        var i = 0
        slices.forEach { (color, count) ->
            repeat(count) { out[i++] = color }
        }
        while (i < size) out[i++] = slices.first().first
        return out
    }

    @Test fun dominant_color_becomes_the_background() {
        val colors = WallpaperPalette.extract(field(4096, navy to 3800, coral to 296))
        assertNotNull(colors)
        assertEquals(navy, colors!!.background)
    }

    @Test fun a_vivid_minority_color_becomes_the_accent() {
        val colors = WallpaperPalette.extract(field(4096, navy to 3800, coral to 296))!!
        assertEquals(coral, colors.accent)
    }

    @Test fun all_grey_image_falls_back_to_accent_equals_background() {
        val colors = WallpaperPalette.extract(field(4096, grey to 4096))!!
        assertEquals(grey, colors.background)
        assertEquals(colors.background, colors.accent)
    }

    @Test fun tiny_vivid_speckles_below_threshold_are_ignored() {
        // 4 coral pixels in 4096 (< 1/400 of the image) must not win accent.
        val colors = WallpaperPalette.extract(field(4096, navy to 4092, coral to 4))!!
        assertEquals(colors.background, colors.accent)
    }

    @Test fun empty_and_transparent_input_yield_null() {
        assertNull(WallpaperPalette.extract(IntArray(0)))
        assertNull(WallpaperPalette.extract(IntArray(64) { 0x00FFFFFF }))
    }

    @Test fun background_wins_even_when_it_is_also_the_most_vivid() {
        val colors = WallpaperPalette.extract(field(4096, coral to 4096))!!
        assertEquals(coral, colors.background)
        // A vivid background may also be the accent — contrast handling
        // happens at palette-derivation time, not here.
        assertTrue(colors.accent == coral)
    }
}
