package com.plantora.billing.ui.billing.voice

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Mic button that opens the system voice-input dialog (the familiar full-screen
 * "Speak now" UI) and returns the recognised phrases. Using the Intent — rather
 * than an in-app SpeechRecognizer — is far more reliable across devices and
 * needs no separate mic-permission flow (the dialog handles it). The big,
 * spoken dialog is also the most accessible option for low-literacy users.
 *
 * Delivers ALL alternative transcripts so the caller can snap to the closest
 * available product name.
 */
@Composable
fun VoiceSearchButton(
    onResults: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    onUnavailable: () -> Unit = {},
) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val phrases = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!phrases.isNullOrEmpty()) onResults(phrases)
        }
    }

    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say the plant name")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 6)
            }
            try {
                launcher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                onUnavailable()
            }
        },
        modifier = modifier,
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = "Voice search", tint = MaterialTheme.colorScheme.primary)
    }
}
