package com.foyerlauncher.foyer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val loneVerbs = setOf(
    "open", "launch", "start", "run", "call", "set", "go", "play", "search", "dial",
)

/**
 * Conversational entry point. Android on-device speech-to-text captures the
 * request; Gemini Nano (via [AiIntentEngine]) decides the action; the launcher
 * only executes the model's choice. Fully on-device.
 */
@Composable
fun VoiceScreen(store: LauncherStore, onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current
    val scope = rememberCoroutineScope()

    val sttAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val assistant = rememberAssistant(store)
    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    var status by remember {
        mutableStateOf(if (sttAvailable) "listening…" else "voice unavailable on this device")
    }
    var glyphState by remember { mutableStateOf(if (sttAvailable) GlyphState.LISTENING else GlyphState.ERROR) }
    var micAmp by remember { mutableFloatStateOf(0f) }
    var heard by remember { mutableStateOf<String?>(null) }
    var replyLabel by remember { mutableStateOf<String?>(null) }
    var actionCommand by remember { mutableStateOf<Command?>(null) }
    // Bumped when the assistant asks a question, to re-open the mic for the answer.
    var relistenTick by remember { mutableStateOf(0) }

    val recognizer = remember { if (sttAvailable) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    fun resolve(text: String) {
        heard = text
        replyLabel = null
        actionCommand = null
        // A lone verb ("open", "call") has no target — don't let the model invent one.
        if (text.trim().lowercase() in loneVerbs) {
            status = "say the full command, e.g. “open whatsapp”"
            return
        }
        status = "thinking…"
        glyphState = GlyphState.THINKING
        scope.launch {
            Log.d(AI_TAG, "voice heard='$text' apps=${apps.size}")
            val turn = assistant?.converse(text, apps) ?: AgentTurn.Do(listOf(AiIntent.Web(text)))
            glyphState = GlyphState.DONE
            when (turn) {
                is AgentTurn.Do -> {
                    val command = turn.plan.toCommand(context)
                    when {
                        command.kind == CommandKind.ACTION && !command.confirm -> {
                            command.run() // confident action → just do it
                            onClose()
                        }
                        command.kind == CommandKind.ACTION -> {
                            actionCommand = command // fuzzy → confirm with a button
                            status = "did you mean this?"
                        }
                        else -> {
                            replyLabel = command.title
                            status = "tap anywhere to continue"
                        }
                    }
                }
                is AgentTurn.Say -> {
                    replyLabel = turn.text // the model just talked
                    status = "tap anywhere to reply"
                }
                is AgentTurn.Ask -> {
                    // Disambiguation: show the question + a button per candidate, and
                    // re-open the mic so the user can just say "yes"/"no".
                    actionCommand = Command(
                        title = turn.text,
                        subtitle = null,
                        kind = CommandKind.ACTION,
                        confirm = true,
                        options = turn.candidates.map { app ->
                            Command("open ${app.label.lowercase()}", null, CommandKind.ACTION) { launchApp(context, app) }
                        },
                    ) {}
                    status = "say “yes” or “no” — or tap"
                    relistenTick++
                }
            }
        }
    }

    fun startListening(keepUi: Boolean = false) {
        val r = recognizer ?: return
        status = "listening…"
        glyphState = GlyphState.LISTENING
        // When re-opening the mic to hear a yes/no answer, keep the question and
        // its buttons on screen instead of clearing them.
        if (!keepUi) { heard = null; replyLabel = null; actionCommand = null }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {
                // rms arrives roughly in -2..10 dB; normalize so the glyph's
                // strokes track the real mic and flatline when it closes.
                micAmp = ((rms + 2f) / 12f).coerceIn(0f, 1f)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                micAmp = 0f
            }
            override fun onError(error: Int) {
                Log.w(AI_TAG, "stt onError code=$error")
                status = "didn't catch that — tap to retry"
                glyphState = GlyphState.ERROR
                micAmp = 0f
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let { heard = it }
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim().orEmpty()
                if (text.isEmpty()) status = "didn't catch that — tap to retry" else resolve(text)
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        runCatching { r.startListening(intent) }.onFailure { status = "voice unavailable" }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else status = "microphone permission needed"
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun hasMic() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun onTap() {
        // Tapping empty space starts a new turn — keeps the conversation going.
        when {
            !sttAvailable -> onClose()
            hasMic() -> startListening()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // When the assistant asks a question, re-open the mic to hear the answer while
    // keeping the question + candidate buttons on screen.
    LaunchedEffect(relistenTick) {
        if (relistenTick > 0 && sttAvailable && hasMic()) startListening(keepUi = true)
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
        if (sttAvailable) {
            if (hasMic()) startListening() else micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val tagStyle = TextStyle(color = palette.muted, fontSize = 13.sp, letterSpacing = 2.sp, fontFamily = ff)

    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onTap() }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp),
    ) {
        BasicText(
            text = "foyer",
            modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp),
            style = TextStyle(color = palette.text, fontSize = 48.sp, fontWeight = FontWeight.Thin, fontFamily = ff),
        )
        BasicText(
            text = "close",
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp).clickable { onClose() },
            style = tagStyle,
        )

        Column(Modifier.align(Alignment.CenterStart)) {
            heard?.let {
                BasicText(
                    text = "“$it”",
                    modifier = Modifier.padding(bottom = 16.dp),
                    style = TextStyle(color = palette.muted, fontSize = 20.sp, fontFamily = ff),
                )
            }
            replyLabel?.let {
                BasicText(
                    text = it,
                    style = TextStyle(color = palette.text, fontSize = 26.sp, fontFamily = ff),
                )
            }
            actionCommand?.let { cmd ->
                if (cmd.options.isNotEmpty()) {
                    // Disambiguation: show the question, then each candidate as its own button.
                    BasicText(
                        text = cmd.title,
                        modifier = Modifier.padding(bottom = 12.dp),
                        style = TextStyle(color = palette.text, fontSize = 22.sp, fontFamily = ff),
                    )
                    cmd.options.forEach { opt ->
                        Box(Modifier.padding(bottom = 10.dp)) {
                            ActionButton(label = "${opt.title}${opt.subtitle?.let { "  ·  $it" } ?: ""}") {
                                opt.run(); onClose()
                            }
                        }
                    }
                } else {
                    ActionButton(
                        label = if (cmd.confirm) "did you mean:  ${cmd.title}?" else cmd.title,
                    ) { cmd.run(); onClose() }
                    cmd.subtitle?.let { sub ->
                        BasicText(
                            text = sub,
                            modifier = Modifier.padding(top = 10.dp),
                            style = TextStyle(color = palette.muted, fontSize = 15.sp, fontFamily = ff),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FoyerGlyph(
                state = glyphState,
                amplitude = { micAmp },
                modifier = Modifier.padding(bottom = 14.dp).size(width = 108.dp, height = 40.dp),
            )
            BasicText(
                text = status,
                modifier = Modifier.padding(bottom = 10.dp),
                style = TextStyle(color = palette.muted, fontSize = 16.sp, fontFamily = ff),
            )
            BasicText(
                text = "on-device · private",
                style = TextStyle(color = palette.muted, fontSize = 11.sp, letterSpacing = 1.sp, fontFamily = ff),
            )
        }
    }
}
