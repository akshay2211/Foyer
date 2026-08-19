package io.ak1.testuilauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Conversational entry point. Uses Android's on-device speech recognition
 * (offline-preferred) and routes the transcript through [routeCommands] — the
 * same seam a local LLM would plug into for richer replies.
 */
@Composable
fun VoiceScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val ff = LocalFontFamily.current

    val available = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    var status by remember {
        mutableStateOf(if (available) "listening…" else "voice unavailable on this device")
    }
    var heard by remember { mutableStateOf<String?>(null) }
    var replyLabel by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val recognizer = remember { if (available) SpeechRecognizer.createSpeechRecognizer(context) else null }
    DisposableEffect(Unit) { onDispose { recognizer?.destroy() } }

    fun resolve(text: String) {
        heard = text
        val cmd = routeCommands(context, text).firstOrNull()
        if (cmd != null) {
            replyLabel = "→ ${cmd.title}"
            pendingAction = { cmd.run(); onClose() }
            status = "tap to run"
        } else {
            replyLabel = "search web for “$text”"
            pendingAction = { webSearch(context, text); onClose() }
            status = "tap to search"
        }
    }

    fun startListening() {
        val r = recognizer ?: return
        status = "listening…"; heard = null; replyLabel = null; pendingAction = null
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rms: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status = "thinking…" }
            override fun onError(error: Int) { status = "didn't catch that — tap to retry" }
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

    fun hasMic() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun onTap() {
        val action = pendingAction
        when {
            action != null -> action()
            !available -> onClose()
            hasMic() -> startListening()
            else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        if (available) {
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
            text = "un",
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
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
