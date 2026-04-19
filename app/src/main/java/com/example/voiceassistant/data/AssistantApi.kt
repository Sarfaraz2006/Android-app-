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
            throw IllegalStateException("OpenRouter error ${response.code}: ${raw.ifBlank { "Unknown error" }}")
        }

        val text = parseText(raw)
        if (text.isBlank()) throw IllegalStateException("Empty response from OpenRouter")
        text
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

data class MessagePayload(
    val role: String,
    val content: String
)
