package com.foyerlauncher.foyer

import com.foyerlauncher.foyer.ai.router.AiIntent
import com.foyerlauncher.foyer.ai.router.AppIndex
import com.foyerlauncher.foyer.ai.router.CAPABILITIES
import com.foyerlauncher.foyer.ai.router.parseIntent
import com.foyerlauncher.foyer.ai.router.parsePlan
import com.foyerlauncher.foyer.data.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the model-output parser (SRS §5): parsePlan must survive the messy
 * replies small models actually produce — code fences, surrounding prose,
 * arrays, out-of-range slots — and never emit an unbuildable intent.
 */
class CommandRouterTest {
    private fun app(label: String) = AppEntry(label = label, packageName = "com.${label.lowercase().replace(" ", ".")}", activityName = "Main")

    private val index = AppIndex.of(listOf(app("Spotify"), app("WhatsApp"), app("Camera")))

    // ---- JSON extraction robustness ----

    @Test fun parses_single_object() {
        val plan = parsePlan("""{"action":"open_app","app":"Spotify"}""", index)
        val open = plan.single() as AiIntent.OpenApp
        assertEquals("Spotify", open.app.label)
        assertTrue(open.confident)
    }

    @Test fun parses_array_of_actions_in_order() {
        val plan =
            parsePlan(
                """[{"action":"setting","key":"wifi"},{"action":"open_app","app":"Spotify"}]""",
                index,
            )
        assertEquals("wifi", (plan[0] as AiIntent.OpenSetting).key)
        assertEquals("Spotify", (plan[1] as AiIntent.OpenApp).app.label)
    }

    @Test fun strips_code_fences() {
        val plan = parsePlan("```json\n{\"action\":\"web\",\"query\":\"weather\"}\n```", index)
        assertEquals("weather", (plan.single() as AiIntent.Web).query)
    }

    @Test fun salvages_json_from_surrounding_prose() {
        val plan =
            parsePlan(
                """Sure! Here is the action: {"action":"timer","seconds":300,"label":"tea"} Hope that helps.""",
                index,
            )
        val timer = plan.single() as AiIntent.SetTimer
        assertEquals(300, timer.seconds)
        assertEquals("tea", timer.label)
    }

    @Test fun pure_prose_yields_empty_plan() {
        assertTrue(parsePlan("I cannot help with that.", index).isEmpty())
        assertEquals(AiIntent.Unknown, parseIntent("I cannot help with that.", index))
    }

    @Test fun unknown_action_is_dropped() {
        assertTrue(parsePlan("""{"action":"self_destruct","seconds":1}""", index).isEmpty())
    }

    // ---- Slot validation ----

    @Test fun alarm_validates_hour_and_minute_ranges() {
        assertTrue(parsePlan("""{"action":"alarm","hour":25,"minute":0}""", index).isEmpty())
        assertTrue(parsePlan("""{"action":"alarm","hour":7,"minute":61}""", index).isEmpty())
        val alarm = parsePlan("""{"action":"alarm","hour":7,"minute":30}""", index).single() as AiIntent.SetAlarm
        assertEquals(7, alarm.hour)
        assertEquals(30, alarm.minute)
    }

    @Test fun timer_rejects_nonpositive_and_over_a_day() {
        assertTrue(parsePlan("""{"action":"timer","seconds":0}""", index).isEmpty())
        assertTrue(parsePlan("""{"action":"timer","seconds":100000}""", index).isEmpty())
    }

    @Test fun call_keeps_digits_and_plus_and_requires_three_digits() {
        val call = parsePlan("""{"action":"call","number":"+91 98x76"}""", index).single() as AiIntent.Call
        assertEquals("+919876", call.number)
        assertTrue(parsePlan("""{"action":"call","number":"ab1"}""", index).isEmpty())
    }

    @Test fun message_requires_recipient_and_text_and_defaults_to_whatsapp() {
        val m = parsePlan("""{"action":"message","to":"mom","text":"omw"}""", index).single() as AiIntent.Message
        assertEquals("whatsapp", m.app)
        assertTrue(parsePlan("""{"action":"message","to":"mom","text":""}""", index).isEmpty())
    }

    @Test fun uninstalled_app_becomes_app_not_found() {
        val plan = parsePlan("""{"action":"open_app","app":"Netflix"}""", index)
        assertEquals("Netflix", (plan.single() as AiIntent.AppNotFound).name)
    }

    // ---- Coalescing ----

    @Test fun open_messaging_app_plus_message_coalesces_to_one_step() {
        val plan =
            parsePlan(
                """[{"action":"open_app","app":"WhatsApp"},{"action":"message","app":"whatsapp","to":"mom","text":"hi"}]""",
                index,
            )
        assertEquals(1, plan.size)
        assertTrue(plan.single() is AiIntent.Message)
    }

    @Test fun independent_steps_are_not_coalesced() {
        val plan =
            parsePlan(
                """[{"action":"open_app","app":"Spotify"},{"action":"message","app":"whatsapp","to":"mom","text":"hi"}]""",
                index,
            )
        assertEquals(2, plan.size)
    }

    // ---- Prompt/parser contract ----

    @Test fun every_few_shot_example_parses_to_a_usable_plan() {
        // The few-shot examples teach the model its output format; if one stops
        // parsing, the prompt is teaching garbage. Keep them in lockstep.
        val idx = AppIndex.of(listOf(app("Camera"), app("WhatsApp")))
        CAPABILITIES.flatMap { it.shots }.forEach { (user, json) ->
            assertTrue("example for \"$user\" no longer parses: $json", parsePlan(json, idx).isNotEmpty())
        }
    }
}
