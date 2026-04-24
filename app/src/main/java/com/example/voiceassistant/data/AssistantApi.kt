package com.example.voiceassistant.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class AssistantApi @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun chat(
        settings: AppSettings,
        messages: List<MessagePayload>
    ): String = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("model", settings.model)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(
                        JSONObject().apply {
                            put("role", message.role)
                            put("content", message.content)
                        }
                    )
                }
            })
        }

        val request = Request.Builder()
            .url(OPENROUTER_URL)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val raw = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            throw toOpenRouterException(response.code, raw, settings.model)
        }

        val text = parseText(raw)
        if (text.isBlank()) throw OpenRouterException(
            code = 500,
            message = "Empty response from OpenRouter",
            isRateLimited = false
        )
        text
    }

    private fun toOpenRouterException(code: Int, raw: String, model: String): OpenRouterException {
        val parsed = runCatching {
            val root = JSONObject(raw)
            val error = root.optJSONObject("error")
            val message = error?.optString("message").orEmpty()
            val providerRaw = error?.optJSONObject("metadata")?.optString("raw").orEmpty()
            val combined = listOf(message, providerRaw).filter { it.isNotBlank() }.joinToString(" | ")
            combined.ifBlank { raw }
        }.getOrDefault(raw)

        val isRateLimited = code == 429 || parsed.contains("rate-limit", ignoreCase = true)
        val userMessage = if (isRateLimited) {
            "Model '$model' abhi rate-limited hai. Please 1-2 minute baad retry karo ya settings me dusra free model select karo."
        } else {
            "Request failed (${code}). Please try again."
        }

        return OpenRouterException(
            code = code,
            message = userMessage,
            isRateLimited = isRateLimited
        )
    }

    private fun parseText(raw: String): String {
        if (raw.isBlank()) return ""
        val root = JSONObject(raw)
        val choices = root.optJSONArray("choices") ?: JSONArray()
        val content = choices.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        return content.trim()
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions"
    }
}

class OpenRouterException(
    val code: Int,
    override val message: String,
    val isRateLimited: Boolean
) : IllegalStateException(message)

data class MessagePayload(
    val role: String,
    val content: String
)
