package com.example.voiceassistant.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        val endpoint = buildEndpoint(settings.baseUrl, settings.endpointPath)
        val body = JSONObject().apply {
            put("model", settings.model)
            put("temperature", settings.temperature)
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

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")

        if (settings.apiKey.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer ${settings.apiKey}")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty().ifBlank { "Unknown network error" }
            throw IllegalStateException("API error ${response.code}: $errorBody")
        }

        val responseBody = response.body?.string().orEmpty()
        parseAssistantText(responseBody)
    }

    private fun buildEndpoint(baseUrl: String, endpointPath: String): String {
        val sanitizedBase = baseUrl.trim().trimEnd('/')
        val sanitizedPath = endpointPath.trim().trimStart('/')
        return "$sanitizedBase/$sanitizedPath"
    }

    private fun parseAssistantText(rawJson: String): String {
        if (rawJson.isBlank()) return "Sorry, I could not generate a response."
        val root = JSONObject(rawJson)

        val choices = root.optJSONArray("choices") ?: JSONArray()
        if (choices.length() == 0) return "Sorry, I could not generate a response."

        val message = choices.optJSONObject(0)?.optJSONObject("message")
        val content = message?.opt("content")

        return when (content) {
            is String -> content.ifBlank { "Sorry, I could not generate a response." }
            is JSONArray -> {
                buildString {
                    for (i in 0 until content.length()) {
                        val part = content.optJSONObject(i)
                        val text = part?.optString("text").orEmpty()
                        if (text.isNotBlank()) append(text)
                    }
                }.ifBlank { "Sorry, I could not generate a response." }
            }
            else -> "Sorry, I could not generate a response."
        }
    }
}

data class MessagePayload(
    val role: String,
    val content: String
)
