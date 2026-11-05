package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.ai.router.ActionPair
import com.foyerlauncher.foyer.ai.router.AiIntent
import com.foyerlauncher.foyer.ai.router.AppIndex
import com.foyerlauncher.foyer.ai.router.appScope
import com.foyerlauncher.foyer.ai.router.parsePairs
import com.foyerlauncher.foyer.ai.router.planFromPairs
import com.foyerlauncher.foyer.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cycle-2 pair pipeline: the model's pair JSON must parse through noise, and
 * [planFromPairs] must map pairs onto intents — app-scoped messaging collapses
 * to one Message, anything unmappable empties the plan for the fallback.
 */
class PairsTest {
    private fun app(label: String) = AppEntry(label = label, packageName = "com.${label.lowercase().replace(" ", ".")}", activityName = "Main")

    private val index = AppIndex.of(listOf(app("Spotify"), app("WhatsApp"), app("Camera")))

    @Test fun parses_pairs_from_noisy_reply() {
        val pairs = parsePairs("""sure! [{"action":"open","value":"whatsapp"},{"action":"message","value":"mom","text":"hello"}]""")
        assertEquals(listOf(ActionPair("open", "whatsapp"), ActionPair("message", "mom", "hello")), pairs)
    }

    @Test fun scoped_message_collapses_to_one_intent() {
        val plan =
            planFromPairs(
                listOf(ActionPair("open", "whatsapp"), ActionPair("message", "mom", "hello")),
                index,
            )
        val msg = plan.single() as AiIntent.Message
        assertEquals("whatsapp", msg.app)
        assertEquals("mom", msg.to)
        assertEquals("hello", msg.text)
    }

    @Test fun open_pair_resolves_against_installed_apps() {
        val plan = planFromPairs(listOf(ActionPair("open", "spotify")), index)
        val open = plan.single() as AiIntent.OpenApp
        assertEquals("Spotify", open.app.label)
        assertTrue(open.confident)
    }

    @Test fun play_keeps_independent_open() {
        val plan = planFromPairs(listOf(ActionPair("open", "spotify"), ActionPair("play", "jazz")), index)
        assertEquals("Spotify", (plan[0] as AiIntent.OpenApp).app.label)
        assertEquals("jazz", (plan[1] as AiIntent.PlayMedia).query)
    }

    @Test fun alarm_value_parses_meridiem() {
        val plan = planFromPairs(listOf(ActionPair("alarm", "7:30 pm")), index)
        val alarm = plan.single() as AiIntent.SetAlarm
        assertEquals(19, alarm.hour)
        assertEquals(30, alarm.minute)
    }

    @Test fun alarm_value_parses_evening_as_pm() {
        val plan = planFromPairs(listOf(ActionPair("alarm", "7:00 evening")), index)
        assertEquals(19, (plan.single() as AiIntent.SetAlarm).hour)
    }

    @Test fun message_without_text_is_unmappable() {
        assertTrue(planFromPairs(listOf(ActionPair("message", "mom")), index).isEmpty())
    }

    @Test fun unknown_verb_empties_the_plan() {
        assertTrue(planFromPairs(listOf(ActionPair("open", "spotify"), ActionPair("teleport", "home")), index).isEmpty())
    }

    @Test fun unresolved_app_empties_the_plan() {
        assertTrue(planFromPairs(listOf(ActionPair("open", "zzzzz")), index).isEmpty())
    }

    // ---- Deterministic app scope ----

    @Test fun marker_mention_scopes_and_strips() {
        val scope = appScope("message mummy hello on whatsapp", index)!!
        assertEquals("WhatsApp", scope.app.label)
        assertEquals("message mummy hello", scope.cleaned)
    }

    @Test fun bare_exact_label_scopes() {
        val scope = appScope("whatsapp mummy hello", index)!!
        assertEquals("WhatsApp", scope.app.label)
        assertEquals("mummy hello", scope.cleaned)
    }

    @Test fun verb_does_not_scope_to_similar_app_name() {
        val withMessages = AppIndex.of(listOf(app("Messages"), app("WhatsApp")))
        assertEquals(null, appScope("message mummy hello", withMessages))
    }

    @Test fun scoped_message_targets_the_scoped_app() {
        val whatsapp = app("WhatsApp")
        val plan = planFromPairs(listOf(ActionPair("message", "mummy", "hello")), index, whatsapp)
        val msg = plan.single() as AiIntent.Message
        assertEquals("whatsapp", msg.app)
        assertEquals("mummy", msg.to)
        assertEquals("hello", msg.text)
    }

    @Test fun open_phone_is_absorbed_by_a_call() {
        val plan = planFromPairs(listOf(ActionPair("open", "phone"), ActionPair("call", "aman")), index)
        assertEquals("aman", (plan.single() as AiIntent.Call).number)
    }

    @Test fun scope_without_comms_prepends_open() {
        val spotify = app("Spotify")
        val plan = planFromPairs(listOf(ActionPair("play", "jazz")), index, spotify)
        assertEquals("Spotify", (plan[0] as AiIntent.OpenApp).app.label)
        assertEquals("jazz", (plan[1] as AiIntent.PlayMedia).query)
    }

    @Test fun scoped_message_dedupes_an_open_pair_regardless_of_its_casing() {
        // scoped.label.lowercase() is always lowercase ("whatsapp"); the model
        // never sees the app name (it's stripped from its input text), so if it
        // still hallucinates an "open" pair it may use any casing ("WhatsApp").
        // That must still collapse into one Message, not survive as a duplicate.
        val whatsapp = app("WhatsApp")
        val plan =
            planFromPairs(
                listOf(ActionPair("open", "WhatsApp"), ActionPair("message", "mummy", "hello")),
                index,
                whatsapp,
            )
        val msg = plan.single() as AiIntent.Message
        assertEquals("mummy", msg.to)
        assertEquals("hello", msg.text)
    }
}
