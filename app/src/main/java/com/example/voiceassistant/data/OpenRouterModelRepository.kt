package com.example.voiceassistant.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Singleton
class OpenRouterModelRepository @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun getFreeModels(): List<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(MODELS_ENDPOINT)
            .get()
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful || body.isBlank()) return@withContext emptyList()

        val root = JSONObject(body)
        val data = root.optJSONArray("data") ?: return@withContext emptyList()

        buildList {
            for (i in 0 until data.length()) {
                val model = data.optJSONObject(i) ?: continue
                val id = model.optString("id").trim()
                if (id.isBlank()) continue

                val pricing = model.optJSONObject("pricing")
                val prompt = pricing?.optString("prompt").orEmpty()
                val completion = pricing?.optString("completion").orEmpty()
                if (isZeroPrice(prompt) && isZeroPrice(completion)) {
                    add(id)
                }
            }
        }.distinct().sorted()
    }

    private fun isZeroPrice(price: String): Boolean {
        val clean = price.trim()
        return clean == "0" || clean == "0.0" || clean == "0.00"
    }

    companion object {
        private const val MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models"
    }
}
