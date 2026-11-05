package com.foyerlauncher.foyer.ai.router

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.foyerlauncher.foyer.ai.engine.AI_TAG
import com.foyerlauncher.foyer.data.AppEntry
import com.foyerlauncher.foyer.data.key
import com.foyerlauncher.foyer.data.launchApp
import com.foyerlauncher.foyer.data.webSearch
import org.json.JSONObject

/**
 * A resolved thing the user can run — produced by the AI, rendered by the UI.
 * The launcher does no intent *parsing*; the model decides, this executes.
 */
enum class CommandKind { ANSWER, ACTION }

data class Command(
    val title: String,
    val subtitle: String?,
    val kind: CommandKind,
    val confirm: Boolean = false,
    /**
     * When non-empty, this command is a *disambiguation*: the UI shows these as a
     * "did you mean…" choice list instead of running [run]. Used when a contact
     * name doesn't map to a single confident match.
     */
    val options: List<Command> = emptyList(),
    val run: () -> Unit,
)

/**
 * The structured decision the on-device model returns — one per capability in
 * the catalog. [leaving] marks actions that hand off to another app (and thus
 * background the launcher); the plan executor uses it to decide what can run
 * inline vs. what can only be the terminal step. See [Plan].
 */
sealed interface AiIntent {
    /** Does performing this send the user into another app / foreground UI? */
    val leaving: Boolean

    data class OpenApp(
        val app: AppEntry,
        val confident: Boolean = true,
        /** Other close matches, shown as a "did you mean…" list when not confident. */
        val alternatives: List<AppEntry> = emptyList(),
    ) : AiIntent {
        override val leaving get() = true
    }

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
    ) : AiIntent {
        override val leaving get() = false // set silently via SKIP_UI
    }

    data class SetTimer(
        val seconds: Int,
        val label: String?,
    ) : AiIntent {
        override val leaving get() = false
    }

    data class Call(
        val number: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class OpenSetting(
        val key: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class Web(
        val query: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class Message(
        val app: String,
        val to: String,
        val text: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class Email(
        val to: String,
        val subject: String,
        val text: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class Navigate(
        val destination: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class ShowMap(
        val query: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class PlayMedia(
        val query: String,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class Capture(
        val video: Boolean,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class CreateEvent(
        val title: String,
        val whenText: String?,
    ) : AiIntent {
        override val leaving get() = true
    }

    data class CreateNote(
        val text: String,
    ) : AiIntent {
        override val leaving get() = false
    }

    data class Answer(
        val text: String,
    ) : AiIntent {
        override val leaving get() = false
    }

    /** The user named an app that isn't installed — tell them, don't guess. */
    data class AppNotFound(
        val name: String,
    ) : AiIntent {
        override val leaving get() = false
    }

    /** A question about installed apps ("how many music apps") — answered locally, not by the LLM. */
    data class FindApps(
        val query: String,
    ) : AiIntent {
        override val leaving get() = false
    }

    data object Unknown : AiIntent {
        override val leaving get() = false
    }
}

/** An ordered list of actions the model wants performed for one command. */
typealias Plan = List<AiIntent>

// ---- Parsing --------------------------------------------------------------

/**
 * Parses the model's reply into a [Plan]. Robust by design: it accepts a single
 * object or a JSON array, salvages objects with a regex if the surrounding text
 * is noisy, drops anything that doesn't map to a real capability, and coalesces
 * redundant steps (e.g. "open whatsapp and message mom" → just the message).
 */
fun parsePlan(
    raw: String,
    index: AppIndex,
): Plan {
    val cleaned = raw.replace("```json", "").replace("```", "")
    val intents =
        Regex("\\{[^{}]*\\}")
            .findAll(cleaned)
            .mapNotNull { runCatching { JSONObject(it.value) }.getOrNull() }
            .map { toIntent(it, index) }
            .filter { it != AiIntent.Unknown }
            .toList()
    if (intents.isEmpty()) Log.w(AI_TAG, "parse: no usable action in reply")
    return coalesce(intents)
}

/** Single-intent parse kept for callers that only want the first action. */
fun parseIntent(
    raw: String,
    index: AppIndex,
): AiIntent = parsePlan(raw, index).firstOrNull() ?: AiIntent.Unknown

/**
 * Removes an explicit "open <app>" when a later step already targets that app
 * (messaging opens WhatsApp itself, so "open whatsapp and message mom" is one
 * action, not two). Keeps genuinely independent steps intact.
 */
private fun coalesce(intents: Plan): Plan {
    if (intents.size <= 1) return intents
    val hasMessaging = intents.any { it is AiIntent.Message || it is AiIntent.Email }
    return intents.filterNot { intent ->
        intent is AiIntent.OpenApp &&
            hasMessaging &&
            run {
                val l = intent.app.label.lowercase()
                l.contains("whatsapp") ||
                    l.contains("message") ||
                    l.contains("sms") ||
                    l.contains("mail") ||
                    l.contains("gmail")
            }
    }
}

private fun toIntent(
    obj: JSONObject,
    index: AppIndex,
): AiIntent =
    when (obj.optString("action").lowercase()) {
        "open_app" -> {
            val name = obj.optString("app").trim()
            val r = index.lookup(name)
            when {
                r.app == null -> {
                    Log.w(AI_TAG, "parse: open_app '$name' not installed")
                    AiIntent.AppNotFound(name)
                }
                r.confident -> AiIntent.OpenApp(r.app, true)
                else -> AiIntent.OpenApp(r.app, false, r.candidates.ifEmpty { listOf(r.app) })
            }
        }
        "alarm" -> {
            val h = obj.optInt("hour", -1)
            val m = obj.optInt("minute", 0)
            if (h in 0..23 && m in 0..59) AiIntent.SetAlarm(h, m) else AiIntent.Unknown
        }
        "timer" -> {
            val s = obj.optInt("seconds", -1)
            if (s in 1..86_400) AiIntent.SetTimer(s, obj.optString("label").trim().ifBlank { null }) else AiIntent.Unknown
        }
        "call" ->
            obj
                .optString("number")
                .filter { it.isDigit() || it == '+' }
                .takeIf { it.count { c -> c.isDigit() } >= 3 }
                ?.let { AiIntent.Call(it) } ?: AiIntent.Unknown
        "setting" ->
            obj
                .optString("key")
                .lowercase()
                .takeIf { it.isNotEmpty() }
                ?.let { AiIntent.OpenSetting(it) } ?: AiIntent.Unknown
        "web" -> obj.optString("query").takeIf { it.isNotBlank() }?.let { AiIntent.Web(it) } ?: AiIntent.Unknown
        "message", "send_message" -> {
            val to = obj.optString("to").trim()
            val text = obj.optString("text").trim()
            val app = obj.optString("app").lowercase().ifBlank { "whatsapp" }
            if (to.isNotEmpty() && text.isNotEmpty()) AiIntent.Message(app, to, text) else AiIntent.Unknown
        }
        "email" -> {
            val to = obj.optString("to").trim()
            val text = obj.optString("text").trim()
            val subject = obj.optString("subject").trim()
            if (text.isNotEmpty() || subject.isNotEmpty()) AiIntent.Email(to, subject, text) else AiIntent.Unknown
        }
        "navigate", "navigation" -> obj.optString("to").takeIf { it.isNotBlank() }?.let { AiIntent.Navigate(it.trim()) } ?: AiIntent.Unknown
        "map" -> obj.optString("query").takeIf { it.isNotBlank() }?.let { AiIntent.ShowMap(it.trim()) } ?: AiIntent.Unknown
        "play" -> obj.optString("query").takeIf { it.isNotBlank() }?.let { AiIntent.PlayMedia(it.trim()) } ?: AiIntent.Unknown
        "capture" -> AiIntent.Capture(video = obj.optString("kind").lowercase().contains("video"))
        "event" ->
            obj
                .optString("title")
                .takeIf { it.isNotBlank() }
                ?.let { AiIntent.CreateEvent(it.trim(), obj.optString("when").trim().ifBlank { null }) } ?: AiIntent.Unknown
        "note" -> obj.optString("text").takeIf { it.isNotBlank() }?.let { AiIntent.CreateNote(it.trim()) } ?: AiIntent.Unknown
        "answer" -> obj.optString("text").takeIf { it.isNotBlank() }?.let { AiIntent.Answer(it) } ?: AiIntent.Unknown
        "find_apps", "list_apps", "count_apps" -> AiIntent.FindApps(obj.optString("query").trim())
        else -> AiIntent.Unknown
    }

// ---- Building & the validation gate ---------------------------------------

/** One built, device-validated step of a plan. */
private class Step(
    val title: String,
    val subtitle: String?,
    val kind: CommandKind,
    val leaving: Boolean,
    val confirm: Boolean,
    val options: List<Command> = emptyList(),
    val run: () -> Unit,
)

/**
 * Turns a whole [Plan] into a single runnable [Command] the existing UI renders.
 *
 * Execution respects Android's reality that only *one* app hand-off can happen
 * per command: non-leaving steps (silent alarm/timer, an answer, a saved note)
 * run inline, then the leaving steps run. A plan with more than one leaving step
 * (or any low-confidence / sensitive step) is marked [Command.confirm] so the
 * user sees and approves it before anything fires.
 */
fun Plan.toCommand(context: Context): Command {
    val steps = mapNotNull { it.build(context) }
    if (steps.isEmpty()) return Command("didn't get that", null, CommandKind.ANSWER) {}

    if (steps.size == 1) {
        val s = steps[0]
        return Command(s.title, s.subtitle, s.kind, s.confirm, s.options, s.run)
    }

    val ordered = steps.sortedBy { it.leaving } // non-leaving first
    val leavingCount = steps.count { it.leaving }
    val confirm = steps.any { it.confirm } || leavingCount > 1
    val subtitle = ordered.firstNotNullOfOrNull { it.subtitle }
    return Command(
        title = ordered.joinToString("  ·  ") { it.title },
        subtitle = subtitle,
        kind = CommandKind.ACTION,
        confirm = confirm,
    ) {
        ordered.forEach { step -> runCatching { step.run() } }
    }
}

/** Single-intent convenience for callers that don't build a plan. */
fun AiIntent.toCommand(context: Context): Command = listOf(this).toCommand(context)

/**
 * Builds and *validates* one intent. Returns null only when nothing on the
 * device can satisfy it and there's no sensible fallback — the plan then simply
 * omits that step rather than firing a dead intent.
 */
private fun AiIntent.build(context: Context): Step? =
    when (this) {
        is AiIntent.OpenApp ->
            when {
                confident -> action("open ${app.label.lowercase()}", "app", leaving = true) { launchApp(context, app) }
                // One close guess — a simple "did you mean X?" confirm.
                alternatives.size <= 1 ->
                    action("open ${app.label.lowercase()}", "app", leaving = true, confirm = true) { launchApp(context, app) }
                // Several close guesses — offer the choice list.
                else ->
                    Step(
                        title = "which app?",
                        subtitle = "app",
                        kind = CommandKind.ACTION,
                        leaving = true,
                        confirm = true,
                        options =
                            alternatives.map { a ->
                                Command("open ${a.label.lowercase()}", "app", CommandKind.ACTION) { launchApp(context, a) }
                            },
                    ) { /* disambiguation only */ }
            }
        is AiIntent.SetAlarm ->
            Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .validated(context)
                ?.let { i -> action("set alarm ${"%d:%02d".format(hour, minute)}", "clock", leaving = false) { startNewTask(context, i) } }

        is AiIntent.SetTimer ->
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .apply { label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }
                .validated(context)
                ?.let { i -> action("timer ${seconds / 60}m", "clock", leaving = false) { startNewTask(context, i) } }

        is AiIntent.Call -> buildCallStep(context, number)

        is AiIntent.OpenSetting -> {
            // Prefer the specific screen; if the OEM/version doesn't expose it, fall
            // back to the root Settings screen rather than failing silently (§7).
            val specific = settingAction(key)?.let { Intent(it) }?.validated(context)
            val intent = specific ?: Intent(Settings.ACTION_SETTINGS).validated(context)
            intent?.let { i -> action(settingLabel(key), "settings", leaving = true) { startNewTask(context, i) } }
        }

        is AiIntent.Web -> action("search web for “$query”", null, leaving = true) { webSearch(context, query) }

        is AiIntent.Message -> buildMessageStep(context, app, to, text)

        is AiIntent.Email ->
            emailIntent(to, subject, text)
                .validated(context)
                ?.let { i -> action("email${if (to.isNotBlank()) " $to" else ""}", subject.ifBlank { text }, leaving = true) { startNewTask(context, i) } }
                ?: action("search web for “$subject $text”", null, leaving = true) { webSearch(context, "$subject $text") }

        is AiIntent.Navigate ->
            (navIntent(destination).validated(context) ?: geoIntent(destination).validated(context))
                ?.let { i -> action("navigate to $destination", "maps", leaving = true) { startNewTask(context, i) } }
                ?: action("search web for “directions to $destination”", null, leaving = true) { webSearch(context, "directions to $destination") }

        is AiIntent.ShowMap ->
            geoIntent(query)
                .validated(context)
                ?.let { i -> action("map: $query", "maps", leaving = true) { startNewTask(context, i) } }
                ?: action("search web for “$query”", null, leaving = true) { webSearch(context, query) }

        is AiIntent.PlayMedia ->
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                .putExtra(SearchExtras.QUERY, query)
                .validated(context)
                ?.let { i -> action("play $query", "media", leaving = true) { startNewTask(context, i) } }
                ?: action("search web for “$query”", null, leaving = true) { webSearch(context, query) }

        is AiIntent.Capture ->
            Intent(if (video) MediaStore.ACTION_VIDEO_CAPTURE else MediaStore.ACTION_IMAGE_CAPTURE)
                .validated(context)
                ?.let { i -> action(if (video) "record video" else "take photo", "camera", leaving = true) { startNewTask(context, i) } }

        is AiIntent.CreateEvent ->
            eventIntent(title, whenText)
                .validated(context)
                ?.let { i -> action("add “$title” to calendar", whenText, leaving = true) { startNewTask(context, i) } }

        is AiIntent.CreateNote ->
            noteIntent(text)
                ?.validated(context)
                ?.let { i -> action("new note", "“$text”", leaving = false) { startNewTask(context, i) } }
                ?: Step("note: $text", null, CommandKind.ANSWER, leaving = false, confirm = false) {}

        is AiIntent.Answer -> Step(text, null, CommandKind.ANSWER, leaving = false, confirm = false) {}

        is AiIntent.AppNotFound -> Step("can't find $name on your phone", null, CommandKind.ANSWER, leaving = false, confirm = false) {}

        // Normally rewritten to an Answer by the Assistant (which holds the app list); null is a safe no-op.
        is AiIntent.FindApps -> null

        AiIntent.Unknown -> null
    }

private fun action(
    title: String,
    subtitle: String?,
    leaving: Boolean,
    confirm: Boolean = false,
    run: () -> Unit,
) = Step(title, subtitle, CommandKind.ACTION, leaving, confirm, run = run)

// ---- Intent construction (the only place raw Intents are made) ------------

/** Search extras key, spelled out to avoid the android.app.SearchManager import. */
private object SearchExtras {
    const val QUERY = "query"
}

private fun navIntent(destination: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(destination)))

private fun geoIntent(query: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))

private fun emailIntent(
    to: String,
    subject: String,
    text: String,
): Intent {
    val isEmail = to.contains("@")
    return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + if (isEmail) Uri.encode(to) else ""))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
}

private fun eventIntent(
    title: String,
    whenText: String?,
): Intent =
    Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, title)
        .apply { whenText?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) } }

private fun noteIntent(text: String): Intent? =
    if (android.os.Build.VERSION.SDK_INT >= 34) {
        Intent("android.intent.action.CREATE_NOTE").putExtra(Intent.EXTRA_TEXT, text)
    } else {
        null
    }

/** The validation gate: an intent only becomes runnable if the device can handle it. */
private fun Intent.validated(context: Context): Intent? = if (context.packageManager.resolveActivity(this, PackageManager.MATCH_DEFAULT_ONLY) != null) this else null

private fun startNewTask(
    context: Context,
    intent: Intent,
) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Log.w(AI_TAG, "execute FAILED: ${intent.action} — ${it.message}") }
}

/**
 * Resolves [who] against contacts and branches three ways — shared by
 * [buildCallStep] and [buildMessageStep], which differ only in what each
 * branch actually does (dial vs. compose) and how many digits count as
 * "this is already a number, skip contact lookup entirely" ([minDigits] — a
 * bare number is more likely typed/spoken for a call than for a message, so
 * the call path accepts a shorter run before trusting it's a real number).
 */
private fun resolveContactStep(
    context: Context,
    who: String,
    minDigits: Int,
    bareNumber: (digits: String) -> Step,
    noMatch: () -> Step,
    oneConfidentMatch: (ContactMatch) -> Step,
    disambiguate: (List<ContactMatch>) -> Step,
): Step {
    val digits = who.filter { it.isDigit() || it == '+' }
    if (digits.count { it.isDigit() } >= minDigits) return bareNumber(digits)

    val matches = resolveContacts(context, who)
    return when {
        matches.isEmpty() -> noMatch()
        matches.size == 1 && matches[0].confident -> oneConfidentMatch(matches[0])
        else -> disambiguate(matches)
    }
}

/** A "who did you mean?" disambiguation step shared by call and message. */
private fun disambiguationStep(
    subtitle: String,
    matches: List<ContactMatch>,
    optionTitle: (ContactMatch) -> String,
    onPick: (ContactMatch) -> Unit,
): Step {
    val options =
        matches.take(4).map { c ->
            Command(optionTitle(c), c.number, CommandKind.ACTION) { onPick(c) }
        }
    return Step(
        title = "who did you mean?",
        subtitle = subtitle,
        kind = CommandKind.ACTION,
        leaving = true,
        confirm = true,
        options = options,
    ) { /* disambiguation only — the chosen option runs */ }
}

/**
 * Builds the call step, resolving a contact name the same way messaging does:
 *  - a bare number goes straight to the dialer (prefilled; the user taps call),
 *  - a name resolves to ranked candidates — one confident match dials it, an
 *    ambiguous match becomes a "who did you mean…" choice list,
 *  - no contacts / no permission opens the dialer to pick manually.
 */
private fun buildCallStep(
    context: Context,
    who: String,
): Step =
    resolveContactStep(
        context,
        who,
        minDigits = 3,
        bareNumber = { digits -> action("call $who", "dialer", leaving = true) { startNewTask(context, dialIntent(digits)) } },
        noMatch = {
            action("call $who", "find them in the dialer", leaving = true) {
                startNewTask(context, Intent(Intent.ACTION_DIAL))
            }
        },
        oneConfidentMatch = { m ->
            action("call ${m.name}", m.number, leaving = true) { startNewTask(context, dialIntent(m.number)) }
        },
        disambiguate = { matches ->
            disambiguationStep(
                "call $who",
                matches,
                optionTitle = { c -> "call ${c.name}" },
            ) { c -> startNewTask(context, dialIntent(c.number)) }
        },
    )

private fun dialIntent(number: String): Intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))

/**
 * Builds the messaging step, disambiguating the recipient first:
 *  - a bare phone number is used as-is (no lookup),
 *  - a contact name is resolved to ranked candidates; one confident match sends
 *    straight through, but an ambiguous or fuzzy match becomes a "did you mean…"
 *    [Command.options] choice list so we never open the wrong person's chat,
 *  - no contacts / no permission falls back to the app's own contact picker.
 */
private fun buildMessageStep(
    context: Context,
    app: String,
    to: String,
    text: String,
): Step =
    resolveContactStep(
        context,
        to,
        minDigits = 5,
        bareNumber = { digits -> action("message $to", "“$text”", leaving = true) { composeMessage(context, app, digits, text) } },
        noMatch = { action("message $to", "“$text”", leaving = true) { composeMessage(context, app, null, text) } },
        oneConfidentMatch = { m ->
            action("message ${m.name}", "“$text”", leaving = true) { composeMessage(context, app, m.number, text) }
        },
        disambiguate = { matches ->
            disambiguationStep(
                "“$text”",
                matches,
                optionTitle = { c -> "message ${c.name}" },
            ) { c -> composeMessage(context, app, c.number, text) }
        },
    )

/**
 * Opens a chat with the given [number] (or the app's picker when null) with
 * [text] pre-filled. Android does not allow auto-send, so the user taps send.
 */
private fun composeMessage(
    context: Context,
    app: String,
    number: String?,
    text: String,
) {
    val wantsWhatsApp = app.contains("whatsapp")
    val whatsAppInstalled = context.packageManager.getLaunchIntentForPackage("com.whatsapp") != null

    if (wantsWhatsApp && whatsAppInstalled) {
        val intent =
            if (number != null) {
                val e164 = number.filter { it.isDigit() }
                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$e164?text=" + Uri.encode(text)))
            } else {
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .setPackage("com.whatsapp")
                    .putExtra(Intent.EXTRA_TEXT, text)
            }
        startNewTask(context, intent)
        return
    }
    val smsUri = Uri.parse("smsto:" + (number ?: ""))
    startNewTask(context, Intent(Intent.ACTION_SENDTO, smsUri).putExtra("sms_body", text))
}

/** A contact candidate for a name query, with whether it's a confident match. */
private data class ContactMatch(
    val name: String,
    val number: String,
    val confident: Boolean,
)

/**
 * Resolves a spoken/typed name to ranked contact candidates. Reads the whole
 * phone book once and scores by tiers (exact → token → prefix → substring →
 * fuzzy), mirroring [AppIndex.resolve]. "Confident" means an exact/token-level
 * hit that clearly beats the runner-up; everything else is offered for
 * disambiguation. Returns empty without READ_CONTACTS permission.
 */
private fun resolveContacts(
    context: Context,
    query: String,
    limit: Int = 4,
): List<ContactMatch> =
    runCatching {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()

        val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val nameCol = android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        val numCol = android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        val seen = HashMap<String, Pair<String, Int>>() // number -> (name, score)

        context.contentResolver.query(uri, arrayOf(nameCol, numCol), null, null, null)?.use { c ->
            val ni = c.getColumnIndex(nameCol)
            val pi = c.getColumnIndex(numCol)
            while (c.moveToNext()) {
                val name = c.getString(ni)?.trim() ?: continue
                val number = c.getString(pi)?.trim() ?: continue
                if (name.isEmpty() || number.isEmpty()) continue
                val score = contactScore(q, name.lowercase())
                if (score <= 0) continue
                val key = number.filter { it.isDigit() }
                val prev = seen[key]
                if (prev == null || score > prev.second) seen[key] = name to score
            }
        }

        val ranked =
            seen.entries
                .map { Triple(it.value.first, it.key, it.value.second) } // (name, digits, score)
                .filter { it.second.isNotEmpty() }
                .sortedByDescending { it.third }
        if (ranked.isEmpty()) return emptyList()

        val top = ranked[0]
        val runnerUp = ranked.getOrNull(1)?.third ?: 0
        // Confident only when the best is a strong tier AND clearly ahead of the next.
        val confident = top.third >= STRONG_CONTACT_SCORE && top.third - runnerUp >= 2
        ranked.take(limit).mapIndexed { i, (name, number, score) ->
            ContactMatch(name, number, confident = i == 0 && confident)
        }
    }.getOrDefault(emptyList())

private const val STRONG_CONTACT_SCORE = 6

/** Scores a contact name against the query; higher is better, 0 means no match. */
private fun contactScore(
    q: String,
    name: String,
): Int {
    val tokens = name.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    return when {
        name == q -> 10
        tokens.any { it == q } -> 8 // matches a full first/last name
        tokens.any { it.startsWith(q) } && q.length >= 3 -> 6
        name.startsWith(q) -> 6
        name.contains(q) && q.length >= 3 -> 4
        // Fuzzy: close to any single name token (typos / mishearings).
        tokens.any { AppIndex.levenshtein(q, it) <= maxOf(1, it.length / 4) } -> 3
        else -> 0
    }
}

private fun settingAction(key: String): String? =
    when (key) {
        "root" -> Settings.ACTION_SETTINGS
        "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
        // No stable public constant across all versions; validated() + root fallback handle absence.
        "notification" -> "android.settings.NOTIFICATION_SETTINGS"
        "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
        "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
        "airplane" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        "battery" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
        "display", "brightness" -> Settings.ACTION_DISPLAY_SETTINGS
        "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
        "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
        "data" -> Settings.ACTION_DATA_USAGE_SETTINGS
        else -> null
    }

private fun settingLabel(key: String): String =
    when (key) {
        "root" -> "settings"
        "developer" -> "developer options"
        else -> "$key settings"
    }
