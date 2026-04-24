package com.example.voiceassistant.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceSpeaker(context: Context) {
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
                tts?.setSpeechRate(0.95f)
            }
        }
    }

    fun speak(text: String) {
        val clean = sanitizeForSpeech(text)
        if (clean.isBlank()) return
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "assistant-response")
    }

    private fun sanitizeForSpeech(input: String): String {
        return input
            .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "")
            .replace(Regex("[*_`#]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
