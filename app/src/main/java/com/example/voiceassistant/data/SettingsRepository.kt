package com.example.voiceassistant.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readFromPrefs())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun currentSettings(): AppSettings = _settings.value

    fun updateSettings(updated: AppSettings) {
        prefs.edit()
            .putString(KEY_API_KEY, updated.apiKey)
            .putString(KEY_MODEL, updated.model)
            .apply()
        _settings.update { updated }
    }

    private fun readFromPrefs(): AppSettings = AppSettings(
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty()
    )

    companion object {
        private const val PREF_NAME = "aria_settings"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"

        const val DEFAULT_MODEL = "openai/gpt-4o-mini"
    }
}

data class AppSettings(
    val apiKey: String,
    val model: String
)

val OPENROUTER_MODELS = listOf(
    "google/gemma-3n-e4b-it:free",
    "google/gemma-3-27b-it:free",
    "meta-llama/llama-3.3-8b-instruct:free",
    "mistralai/mistral-small-3.1-24b-instruct:free",
    "deepseek/deepseek-r1:free"
)
