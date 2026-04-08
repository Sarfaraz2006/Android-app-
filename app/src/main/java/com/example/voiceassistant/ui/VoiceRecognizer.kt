package com.example.voiceassistant.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceRecognizer(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) {
    private val appContext = context.applicationContext
    private val speechRecognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(appContext)) {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        } else {
            null
        }

    init {
        if (speechRecognizer == null) {
            onError("Speech recognition is not available on this device")
            return
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                onListeningStateChanged(true)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                onListeningStateChanged(false)
            }

            override fun onError(error: Int) {
                onListeningStateChanged(false)
                onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()

                if (text.isNotBlank()) {
                    onPartialResult("")
                    onResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                if (text.isNotBlank()) onPartialResult(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    fun startListening(locale: Locale = Locale.getDefault()) {
        val recognizer = speechRecognizer
        if (recognizer == null) {
            onError("Speech recognition is not available")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        recognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        onListeningStateChanged(false)
    }

    fun release() {
        speechRecognizer?.destroy()
    }

    private fun errorMessage(code: Int): String {
        return when (code) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side recognition error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing microphone permission"
            SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "Could not understand speech"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Recognition server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Speech recognition failed ($code)"
        }
    }
}
