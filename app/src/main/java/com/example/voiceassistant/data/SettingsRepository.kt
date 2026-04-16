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
            .putString(KEY_ASSISTANT_NAME, updated.assistantName)
            .putString(KEY_PROVIDER_TYPE, updated.providerType.name)
            .putString(KEY_BASE_URL, updated.baseUrl)
            .putString(KEY_ENDPOINT_PATH, updated.endpointPath)
            .putString(KEY_MODEL, updated.model)
            .putString(KEY_API_KEY, updated.apiKey)
            .putFloat(KEY_TEMPERATURE, updated.temperature)
            .putString(KEY_SYSTEM_PROMPT, updated.systemPrompt)
            .apply()

        _settings.update { updated }
    }

    private fun readFromPrefs(): AppSettings {
        val provider = runCatching {
            ProviderType.valueOf(
                prefs.getString(KEY_PROVIDER_TYPE, ProviderType.OPENAI_COMPATIBLE.name).orEmpty()
            )
        }.getOrDefault(ProviderType.OPENAI_COMPATIBLE)

        return AppSettings(
            assistantName = prefs.getString(KEY_ASSISTANT_NAME, DEFAULT_ASSISTANT_NAME).orEmpty(),
            providerType = provider,
            baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty(),
            endpointPath = prefs.getString(KEY_ENDPOINT_PATH, DEFAULT_ENDPOINT_PATH).orEmpty(),
            model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            temperature = prefs.getFloat(KEY_TEMPERATURE, 0.7f),
            systemPrompt = prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT).orEmpty()
        )
    }

    companion object {
        private const val PREF_NAME = "aria_settings"
        private const val KEY_ASSISTANT_NAME = "assistant_name"
        private const val KEY_PROVIDER_TYPE = "provider_type"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_ENDPOINT_PATH = "endpoint_path"
        private const val KEY_MODEL = "model"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_SYSTEM_PROMPT = "system_prompt"

        private const val DEFAULT_ASSISTANT_NAME = "Aria"
        private const val DEFAULT_BASE_URL = "https://openrouter.ai"
        private const val DEFAULT_ENDPOINT_PATH = "api/v1/chat/completions"
        private const val DEFAULT_MODEL = "openai/gpt-4.1-mini"
        private const val DEFAULT_SYSTEM_PROMPT =
            "You are Aria, a helpful, concise, and friendly personal assistant."
    }
}

enum class ProviderType(val label: String) {
    OPENAI_COMPATIBLE("OpenAI / OpenRouter"),
    ANTHROPIC("Anthropic Claude"),
    GOOGLE_GEMINI("Google Gemini")
}

data class AppSettings(
    val assistantName: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val endpointPath: String,
    val model: String,
    val apiKey: String,
    val temperature: Float,
    val systemPrompt: String
)

fun presetFor(providerType: ProviderType): AppSettings = when (providerType) {
    ProviderType.OPENAI_COMPATIBLE -> AppSettings(
        assistantName = "Aria",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        baseUrl = "https://openrouter.ai",
        endpointPath = "api/v1/chat/completions",
        model = "openai/gpt-4.1-mini",
        apiKey = "",
        temperature = 0.7f,
        systemPrompt = "You are Aria, a helpful, concise, and friendly personal assistant."
    )

    ProviderType.ANTHROPIC -> AppSettings(
        assistantName = "Aria",
        providerType = ProviderType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com",
        endpointPath = "v1/messages",
        model = "claude-3-5-sonnet-latest",
        apiKey = "",
        temperature = 0.7f,
        systemPrompt = "You are Aria, a helpful, concise, and friendly personal assistant."
    )

    ProviderType.GOOGLE_GEMINI -> AppSettings(
        assistantName = "Aria",
        providerType = ProviderType.GOOGLE_GEMINI,
        baseUrl = "https://generativelanguage.googleapis.com",
        endpointPath = "v1beta/models/gemini-2.0-flash:generateContent",
        model = "gemini-2.0-flash",
        apiKey = "",
        temperature = 0.7f,
        systemPrompt = "You are Aria, a helpful, concise, and friendly personal assistant."
    )
}
