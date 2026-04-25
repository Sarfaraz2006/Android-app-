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
            .putString(KEY_USER_NAME, updated.userName)
            .putString(KEY_LANGUAGE, updated.preferredLanguage)
            .apply()
        _settings.update { updated }
    }

    private fun readFromPrefs(): AppSettings = AppSettings(
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty(),
        userName = prefs.getString(KEY_USER_NAME, "").orEmpty(),
        preferredLanguage = prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE).orEmpty()
    )

    companion object {
        private const val PREF_NAME = "aria_settings"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LANGUAGE = "preferred_language"

        const val DEFAULT_MODEL = "openai/gpt-4o-mini"
        const val DEFAULT_LANGUAGE = "Hinglish"
    }
}

data class AppSettings(
    val apiKey: String,
    val model: String,
    val userName: String,
    val preferredLanguage: String
)

val OPENROUTER_MODELS = listOf(
    "meta-llama/llama-3.1-70b-instruct",
    "google/gemini-flash-1.5",
    "anthropic/claude-3-haiku",
    "mistralai/mistral-7b-instruct"
)

val SUPPORTED_LANGUAGES = listOf("Hinglish", "Hindi", "English")
