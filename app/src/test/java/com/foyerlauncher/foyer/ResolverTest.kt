package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.ai.router.ActionCategory
import com.foyerlauncher.foyer.ai.router.ActionRegistry
import com.foyerlauncher.foyer.ai.router.AiIntent
import com.foyerlauncher.foyer.ai.router.AppCatalog
import com.foyerlauncher.foyer.ai.router.AppIndex
import com.foyerlauncher.foyer.ai.router.Normalizer
import com.foyerlauncher.foyer.ai.router.ResolverConfig
import com.foyerlauncher.foyer.ai.router.allowedActionsBlock
import com.foyerlauncher.foyer.ai.router.fastRoute
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the deterministic, offline layers of the assistant (SRS §14):
 * text normalization, the app resolver, the action registry, and Tier-0 routing.
 * These are pure Kotlin — no Android runtime, no LLM.
 */
class ResolverTest {

    private fun app(label: String, pkg: String = label.lowercase().replace(" ", ".")) =
        AppEntry(label = label, packageName = "com.$pkg", activityName = "Main")

    private val apps = listOf(
        app("Spotify"), app("WhatsApp"), app("Blinkit"),
        app("Google Maps"), app("Google Drive"), app("Camera"), app("Instagram"),
    )
    private val index = AppIndex.of(apps)

    // ---- §5.2 Normalization ----

    @Test fun normalize_collapses_repeated_prefix() {
        assertEquals("open developer options", Normalizer.normalize("open open developer options"))
        assertEquals("developer options", Normalizer.stripLeadingVerb("open developer options"))
    }

    @Test fun normalize_collapses_whitespace_keeps_case() {
        assertEquals("OPEN Spotify", Normalizer.normalize("  OPEN   Spotify "))
    }

    // ---- §14.1 Resolver ----

    @Test fun exact_match_is_confident() {
        val r = index.lookup("spotify")
        assertEquals("Spotify", r.app?.label); assertTrue(r.confident)
    }

    @Test fun typo_resolves_via_subsequence() {
        val r = index.lookup("spotfy")
        assertEquals("Spotify", r.app?.label); assertTrue(r.confident)
    }

    @Test fun spaced_name_resolves() {
        val r = index.lookup("whats app")
        assertEquals("WhatsApp", r.app?.label); assertTrue(r.confident)
    }

    @Test fun blanket_offers_blinkit_but_not_confident() {
        val r = index.lookup("blanket")
        assertEquals("Blinkit", r.app?.label)
        assertTrue(!r.confident)
        assertTrue(r.candidates.any { it.label == "Blinkit" })
    }

    @Test fun uninstalled_app_is_no_match() {
        val r = index.lookup("netflix")
        assertNull(r.app)
    }

    @Test fun short_query_is_not_a_spurious_subsequence() {
        // "amol" is a scattered subsequence of "warmup & cooldown" but must NOT
        // confidently auto-open it (regression for the Amol/AMUL bug).
        val idx = AppIndex.of(listOf(app("Warmup & Cooldown")))
        assertTrue(!idx.lookup("amol").confident)
    }

    @Test fun multiple_google_apps_are_ambiguous() {
        val r = index.lookup("google")
        assertTrue(!r.confident)
        assertTrue(r.candidates.size >= 2)
    }

    @Test fun alias_music_maps_to_spotify() {
        val r = index.lookup("music")
        assertEquals("Spotify", r.app?.label); assertTrue(r.confident)
    }

    // ---- §15 Confidence bands (numeric, centralized) ----

    @Test fun confidence_scores_land_in_expected_bands() {
        val cfg = ResolverConfig.DEFAULT
        assertTrue(index.lookup("spotify").score >= cfg.autoThreshold)      // execute
        val blanket = index.lookup("blanket")
        assertTrue(blanket.score >= cfg.confirmThreshold && blanket.score < cfg.autoThreshold) // confirm
        assertEquals(1, blanket.candidates.size)                            // single "did you mean"
        val google = index.lookup("google")
        assertTrue(google.score in cfg.candidateThreshold..cfg.confirmThreshold) // candidates band
        assertTrue(index.lookup("netflix").score < cfg.candidateThreshold)  // no match
    }

    @Test fun thresholds_are_tunable() {
        // A stricter policy turns an auto-open into a confirmation.
        val strict = ResolverConfig(autoThreshold = 0.99f)
        val r = index.lookup("spotfy", strict) // subsequence scores 0.90
        assertTrue(!r.confident)
    }

    // ---- §5.4 / §12 Action registry ----

    @Test fun registry_resolves_developer_and_wifi() {
        assertEquals("developer", ActionRegistry.settingKey("developer options"))
        assertEquals("developer", ActionRegistry.settingKey("developer mode"))
        assertEquals("developer", ActionRegistry.settingKey("usb debugging"))
        assertEquals("wifi", ActionRegistry.settingKey("wifi settings"))
        assertNull(ActionRegistry.settingKey("spotify"))
    }

    @Test fun registry_exposes_definitions_with_category() {
        val dev = ActionRegistry.definition("developer")!!
        assertEquals(ActionCategory.DEVELOPER, dev.category)
        assertTrue(dev.aliases.contains("developer options"))
        // Every alias round-trips to a real definition.
        ActionRegistry.ACTIONS.forEach { a ->
            a.aliases.forEach { assertEquals(a.id, ActionRegistry.settingKey(it)) }
        }
    }

    // ---- §14.3 Tier-0 routing (deterministic, no LLM) ----

    @Test fun route_open_app_confident() {
        val plan = fastRoute("open spotify", index)!!
        assertEquals(AiIntent.OpenApp(app("Spotify"), true).let { it.app.label to it.confident },
            (plan[0] as AiIntent.OpenApp).let { it.app.label to it.confident })
    }

    @Test fun route_open_open_developer_options_hits_settings() {
        val plan = fastRoute(Normalizer.normalize("open open developer options"), index)!!
        assertEquals("developer", (plan[0] as AiIntent.OpenSetting).key)
    }

    @Test fun route_bare_developer_options_hits_settings() {
        val plan = fastRoute("developer options", index)!!
        assertEquals("developer", (plan[0] as AiIntent.OpenSetting).key)
    }

    @Test fun route_verbed_settings_phrases_are_deterministic() {
        assertEquals("developer", (fastRoute("enable usb debugging", index)!![0] as AiIntent.OpenSetting).key)
        assertEquals("wifi", (fastRoute("take me to wifi settings", index)!![0] as AiIntent.OpenSetting).key)
        assertEquals("accessibility", (fastRoute("open accessibility settings", index)!![0] as AiIntent.OpenSetting).key)
    }

    @Test fun setting_schema_includes_full_registry_keys() {
        // The model's "setting" schema is generated from the registry, so it can
        // emit developer/accessibility/etc. for paraphrased requests.
        val schema = allowedActionsBlock()
        assertTrue(schema.contains("developer"))
        assertTrue(schema.contains("accessibility"))
        assertTrue(ActionRegistry.promptReference().contains("usb debugging"))
    }

    @Test fun route_ambiguous_open_is_not_confident() {
        val plan = fastRoute("open blanket", index)!!
        assertTrue(!(plan[0] as AiIntent.OpenApp).confident)
    }

    @Test fun route_conversational_falls_through_to_llm() {
        assertNull(fastRoute("what's the weather like", index))
    }

    @Test fun route_inventory_question_is_deterministic() {
        assertEquals("music", (fastRoute("how many music apps do i have", index)!![0] as AiIntent.FindApps).query)
        assertEquals("music", (fastRoute("list all music apps", index)!![0] as AiIntent.FindApps).query)
    }

    @Test fun route_usage_question_not_treated_as_inventory() {
        // "…use most today" is a (future) usage question, not an inventory count.
        assertNull(fastRoute("what apps did i use most today", index))
    }

    // ---- §6 Deterministic inventory queries ----

    @Test fun catalog_counts_by_category() {
        val music = listOf(app("Spotify").also { it.category = 1 }, app("Gaana").also { it.category = 1 })
        val other = listOf(app("Chrome").also { it.category = 7 })
        val answer = AppCatalog.describe(music + other, "music")
        assertTrue(answer.contains("2"))
        assertTrue(answer.contains("Spotify"))
        assertTrue(!answer.contains("Chrome"))
    }

    @Test fun catalog_reports_none_when_absent() {
        val answer = AppCatalog.describe(listOf(app("Chrome").also { it.category = 7 }), "music")
        assertTrue(answer.lowercase().contains("don't see any"))
    }
}
