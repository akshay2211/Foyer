package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.decodeApp
import com.foyerlauncher.foyer.data.encode
import com.foyerlauncher.foyer.data.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip tests for the persisted AppEntry encoding (favorites, shortcuts,
 * hidden…). This format lives in users' SharedPreferences — a change that breaks
 * decoding silently wipes their home screen, so it's pinned here.
 */
class AppEntryCodecTest {
    @Test fun personal_profile_round_trips() {
        val app = AppEntry("Google Maps", "com.google.android.apps.maps", "com.google.android.maps.MapsActivity")
        assertEquals(app, decodeApp(app.encode()))
    }

    @Test fun work_profile_serial_round_trips() {
        val app = AppEntry("Slack", "com.slack", "com.slack.Main", userSerial = 10L)
        val decoded = decodeApp(app.encode())!!
        assertEquals(app, decoded)
        assertEquals(10L, decoded.userSerial)
    }

    @Test fun labels_with_spaces_and_unicode_survive() {
        val app = AppEntry("फ़ोटो — Photos & Videos", "com.photos", "Main")
        assertEquals(app, decodeApp(app.encode()))
    }

    @Test fun garbage_and_blank_decode_to_null() {
        assertNull(decodeApp(null))
        assertNull(decodeApp(""))
        assertNull(decodeApp("just-one-field"))
    }

    @Test fun key_format_is_stable_across_profiles() {
        // Persisted key format: pkg/activity for personal, pkg/activity/serial otherwise.
        assertEquals("com.a/Main", AppEntry("A", "com.a", "Main").key)
        assertEquals("com.a/Main/12", AppEntry("A", "com.a", "Main", 12L).key)
    }
}
