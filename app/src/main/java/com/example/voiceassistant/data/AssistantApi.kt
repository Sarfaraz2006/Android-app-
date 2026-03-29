package com.example.voiceassistant.data

import retrofit2.http.Body
import retrofit2.http.POST

interface AssistantApi {
    @POST("v1/chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

data class ChatRequest(
    val model: String,
    val messages: List<MessagePayload>
)

data class MessagePayload(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<Choice> = emptyList()
)

data class Choice(
    val message: MessagePayload
)
