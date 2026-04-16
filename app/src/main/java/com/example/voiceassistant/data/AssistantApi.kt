package com.example.voiceassistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantApi @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun chat(
        settings: AppSettings,
        messages: List<MessagePayload>
    ): String = withContext(Dispatchers.IO) {
        val endpoint = buildEndpoint(settings)
        val payload = when (settings.providerType) {
            ProviderType.OPENAI_COMPATIBLE -> buildOpenAiPayload(settings, messages)
            ProviderType.ANTHROPIC -> buildAnthropicPayload(settings, messages)
            ProviderType.GOOGLE_GEMINI -> buildGeminiPayload(settings, messages)
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")

        applyProviderHeaders(requestBuilder, settings)

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
            throw IllegalStateException("API error ${response.code}: ${responseBody.ifBlank { "Unknown network error" }}")
        }

        parseAssistantText(responseBody, settings.providerType)
    }

    private fun buildEndpoint(settings: AppSettings): String {
        val base = settings.baseUrl.trim().trimEnd('/')
        val path = settings.endpointPath.trim().trimStart('/')
        val full = "$base/$path"

        return if (settings.providerType == ProviderType.GOOGLE_GEMINI) {
            val url = full.toHttpUrlOrNull() ?: throw IllegalStateException("Invalid Gemini URL")
            url.newBuilder().addQueryParameter("key", settings.apiKey).build().toString()
        } else {
            full
        }
    }

    private fun applyProviderHeaders(builder: Request.Builder, settings: AppSettings) {
        when (settings.providerType) {
            ProviderType.OPENAI_COMPATIBLE -> {
                builder.header("Authorization", "Bearer ${settings.apiKey}")
                if (settings.baseUrl.contains("openrouter.ai")) {
                    builder.header("HTTP-Referer", "https://github.com/")
                    builder.header("X-Title", "Aria Android")
                }
            }

            ProviderType.ANTHROPIC -> {
                builder.header("x-api-key", settings.apiKey)
                builder.header("anthropic-version", "2023-06-01")
            }

            ProviderType.GOOGLE_GEMINI -> Unit
        }
    }

    private fun buildOpenAiPayload(settings: AppSettings, messages: List<MessagePayload>): JSONObject {
        return JSONObject().apply {
            put("model", settings.model)
            put("temperature", settings.temperature)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                    })
                }
            })
        }
    }

    private fun buildAnthropicPayload(settings: AppSettings, messages: List<MessagePayload>): JSONObject {
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content.orEmpty()
        val userAssistantMessages = messages.filter { it.role != "system" }

        return JSONObject().apply {
            put("model", settings.model)
            put("max_tokens", 1024)
            put("temperature", settings.temperature)
            if (systemPrompt.isNotBlank()) put("system", systemPrompt)
            put("messages", JSONArray().apply {
                userAssistantMessages.forEach { message ->
                    put(
                        JSONObject().apply {
                            put("role", if (message.role == "assistant") "assistant" else "user")
                            put(
                                "content",
                                JSONArray().put(
                                    JSONObject().apply {
                                        put("type", "text")
                                        put("text", message.content)
                                    }
                                )
                            )
                        }
                    )
                }
            })
        }
    }

    private fun buildGeminiPayload(settings: AppSettings, messages: List<MessagePayload>): JSONObject {
        val systemPrompt = messages.firstOrNull { it.role == "system" }?.content.orEmpty()
        val filtered = messages.filter { it.role != "system" }

        return JSONObject().apply {
            if (systemPrompt.isNotBlank()) {
                put(
                    "systemInstruction",
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", systemPrompt))
                    )
                )
            }
            put("generationConfig", JSONObject().put("temperature", settings.temperature))
            put("contents", JSONArray().apply {
                filtered.forEach { message ->
                    put(
                        JSONObject().apply {
                            put("role", if (message.role == "assistant") "model" else "user")
                            put(
                                "parts",
                                JSONArray().put(
                                    JSONObject().put("text", message.content)
                                )
                            )
                        }
                    )
                }
            })
        }
    }

    private fun parseAssistantText(rawJson: String, providerType: ProviderType): String {
        if (rawJson.isBlank()) return "Sorry, I could not generate a response."
        val root = JSONObject(rawJson)
        return when (providerType) {
            ProviderType.OPENAI_COMPATIBLE -> parseOpenAi(root)
            ProviderType.ANTHROPIC -> parseAnthropic(root)
            ProviderType.GOOGLE_GEMINI -> parseGemini(root)
        }
    }

    private fun parseOpenAi(root: JSONObject): String {
        val choices = root.optJSONArray("choices") ?: JSONArray()
        val content = choices.optJSONObject(0)?.optJSONObject("message")?.opt("content")
        return when (content) {
            is String -> content
            is JSONArray -> extractTextArray(content)
            else -> "Sorry, I could not generate a response."
        }.ifBlank { "Sorry, I could not generate a response." }
    }

    private fun parseAnthropic(root: JSONObject): String {
        val content = root.optJSONArray("content") ?: JSONArray()
        return extractTextArray(content).ifBlank { "Sorry, I could not generate a response." }
    }

    private fun parseGemini(root: JSONObject): String {
        val candidates = root.optJSONArray("candidates") ?: JSONArray()
        val parts = candidates.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts") ?: JSONArray()
        return extractTextArray(parts).ifBlank { "Sorry, I could not generate a response." }
    }

    private fun extractTextArray(array: JSONArray): String {
        return buildString {
            for (i in 0 until array.length()) {
                val part = array.optJSONObject(i)
                val text = part?.optString("text").orEmpty()
                if (text.isNotBlank()) append(text)
            }
        }
    }
}

data class MessagePayload(
    val role: String,
    val content: String
)
