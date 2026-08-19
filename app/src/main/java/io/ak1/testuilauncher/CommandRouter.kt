package io.ak1.testuilauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import kotlin.math.abs

/**
 * The command bar routes a typed query to actionable results. Everything here is
 * 100% on-device and offline — no network, no telemetry.
 *
 * This is the extension seam for local AI: [CommandProvider] is the single
 * interface an on-device model (Gemini Nano via AICore, or a small model via the
 * MediaPipe LLM Inference API) would implement. A future `AiCommandProvider` can
 * sit behind the same contract, and [routeCommands] can merge/rank it with the
 * heuristic provider, falling back to heuristics when no accelerator is present.
 */
enum class CommandKind { MATH, ACTION }

data class Command(
    val title: String,
    val subtitle: String?,
    val kind: CommandKind,
    val run: () -> Unit,
)

interface CommandProvider {
    fun suggest(context: Context, query: String): List<Command>
}

/**
 * Rule-based provider: instant, deterministic, offline. Handles the high-intent
 * cases a small on-device model would otherwise cover (math, calling, alarms,
 * quick settings), so the AI layer can start focused and optional.
 */
class HeuristicCommandProvider : CommandProvider {
    override fun suggest(context: Context, query: String): List<Command> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val out = mutableListOf<Command>()

        // Calculator: "12*(3+4)" -> "= 84"
        evalExpression(q)?.let { result ->
            val text = formatNumber(result)
            out += Command(
                title = "= $text",
                subtitle = "tap to copy",
                kind = CommandKind.MATH,
                run = { copyToClipboard(context, text) },
            )
        }

        // Phone number -> call
        if (looksLikePhoneNumber(q)) {
            out += Command(
                title = "call $q",
                subtitle = "dialer",
                kind = CommandKind.ACTION,
                run = { startNewTask(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + q.filter { it.isDigit() || it == '+' }))) },
            )
        }

        // Alarm: "alarm 7:30"
        parseAlarm(q)?.let { (hour, minute) ->
            out += Command(
                title = "set alarm ${"%d:%02d".format(hour, minute)}",
                subtitle = "clock",
                kind = CommandKind.ACTION,
                run = {
                    startNewTask(
                        context,
                        Intent(AlarmClock.ACTION_SET_ALARM)
                            .putExtra(AlarmClock.EXTRA_HOUR, hour)
                            .putExtra(AlarmClock.EXTRA_MINUTES, minute),
                    )
                },
            )
        }

        // Quick settings by keyword: "wifi", "bluetooth", ...
        quickSetting(q)?.let { (label, action) ->
            out += Command(
                title = label,
                subtitle = "settings",
                kind = CommandKind.ACTION,
                run = { startNewTask(context, Intent(action)) },
            )
        }

        return out
    }
}

/**
 * Single entry point the UI calls. Today it's just the heuristic provider; when an
 * on-device AI provider lands, merge and rank here (AI results first, deduped),
 * with a graceful fallback to heuristics on unsupported devices.
 */
private val heuristics = HeuristicCommandProvider()
private val ai = AiCommandProvider()

fun routeCommands(context: Context, query: String, useAi: Boolean = false): List<Command> {
    val base = heuristics.suggest(context, query)
    if (!useAi || !ai.available(context)) return base
    // AI results first, then heuristics, de-duplicated by title.
    return (ai.suggest(context, query) + base).distinctBy { it.title }
}

// ---- Heuristics -----------------------------------------------------------

private fun startNewTask(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("result", text))
    }
}

private fun looksLikePhoneNumber(q: String): Boolean {
    if (!q.matches(Regex("^\\+?[0-9][0-9 ()\\-]{4,}$"))) return false
    return q.count { it.isDigit() } >= 5
}

private val alarmRegex = Regex("(?:alarm|wake).*?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)

private fun parseAlarm(q: String): Pair<Int, Int>? {
    if (!q.contains("alarm", ignoreCase = true) && !q.contains("wake", ignoreCase = true)) return null
    val m = alarmRegex.find(q) ?: return null
    var hour = m.groupValues[1].toIntOrNull() ?: return null
    val minute = m.groupValues[2].toIntOrNull() ?: 0
    val ampm = m.groupValues[3].lowercase()
    if (ampm == "pm" && hour < 12) hour += 12
    if (ampm == "am" && hour == 12) hour = 0
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour to minute
}

private fun quickSetting(q: String): Pair<String, String>? {
    return when (q.lowercase().trim()) {
        "wifi", "wi-fi" -> "wi-fi settings" to Settings.ACTION_WIFI_SETTINGS
        "bluetooth", "bt" -> "bluetooth settings" to Settings.ACTION_BLUETOOTH_SETTINGS
        "airplane", "flight mode", "airplane mode" -> "airplane mode" to Settings.ACTION_AIRPLANE_MODE_SETTINGS
        "battery" -> "battery settings" to Settings.ACTION_BATTERY_SAVER_SETTINGS
        "display", "brightness" -> "display settings" to Settings.ACTION_DISPLAY_SETTINGS
        "sound", "volume" -> "sound settings" to Settings.ACTION_SOUND_SETTINGS
        "data", "mobile data" -> "data usage" to Settings.ACTION_DATA_USAGE_SETTINGS
        "location", "gps" -> "location settings" to Settings.ACTION_LOCATION_SOURCE_SETTINGS
        else -> null
    }
}

private fun formatNumber(d: Double): String {
    if (!d.isFinite()) return d.toString()
    return if (d % 1.0 == 0.0 && abs(d) < 1e15) d.toLong().toString() else d.toString()
}

/** Evaluates a basic arithmetic expression; returns null if it isn't one. */
private fun evalExpression(expr: String): Double? {
    // Must look like math (digits + operators) and contain at least one operator.
    if (!expr.matches(Regex("[0-9+\\-*/%^(). ]+"))) return null
    if (!expr.any { it in "+-*/%^" }) return null
    if (!expr.any { it.isDigit() }) return null
    return runCatching { Parser(expr).parse() }.getOrNull()?.takeIf { it.isFinite() }
}

/** Tiny recursive-descent evaluator: + - * / % ^, parentheses, unary minus. */
private class Parser(private val s: String) {
    private var pos = 0

    fun parse(): Double {
        val v = parseExpr()
        skipWs()
        if (pos != s.length) throw IllegalArgumentException("trailing input")
        return v
    }

    private fun skipWs() { while (pos < s.length && s[pos] == ' ') pos++ }

    private fun peek(): Char? { skipWs(); return if (pos < s.length) s[pos] else null }

    private fun parseExpr(): Double {
        var v = parseTerm()
        while (true) {
            when (peek()) {
                '+' -> { pos++; v += parseTerm() }
                '-' -> { pos++; v -= parseTerm() }
                else -> return v
            }
        }
    }

    private fun parseTerm(): Double {
        var v = parsePower()
        while (true) {
            when (peek()) {
                '*' -> { pos++; v *= parsePower() }
                '/' -> { pos++; v /= parsePower() }
                '%' -> { pos++; v %= parsePower() }
                else -> return v
            }
        }
    }

    private fun parsePower(): Double {
        val base = parseUnary()
        return if (peek() == '^') {
            pos++
            Math.pow(base, parsePower())
        } else {
            base
        }
    }

    private fun parseUnary(): Double {
        return when (peek()) {
            '-' -> { pos++; -parseUnary() }
            '+' -> { pos++; parseUnary() }
            else -> parseAtom()
        }
    }

    private fun parseAtom(): Double {
        val c = peek() ?: throw IllegalArgumentException("unexpected end")
        if (c == '(') {
            pos++
            val v = parseExpr()
            if (peek() != ')') throw IllegalArgumentException("expected )")
            pos++
            return v
        }
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
        if (pos == start) throw IllegalArgumentException("expected number")
        return s.substring(start, pos).toDouble()
    }
}
