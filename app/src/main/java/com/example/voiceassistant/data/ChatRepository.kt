package com.example.voiceassistant.data

import com.example.voiceassistant.domain.ChatMessage
import com.example.voiceassistant.domain.Role
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val assistantApi: AssistantApi
) {
    fun observeMessages(): Flow<List<ChatMessage>> =
        chatDao.observeMessages().map { list ->
            list.map {
                ChatMessage(
                    role = if (it.role == "user") Role.USER else Role.ASSISTANT,
                    text = it.text
                )
            }
        }

    suspend fun sendUserMessage(text: String): String = withContext(Dispatchers.IO) {
        chatDao.insert(ChatMessageEntity(role = "user", text = text))

        val promptMessages = chatDao
            .getRecentMessages(20)
            .asReversed()
            .map { MessagePayload(role = it.role, content = it.text) }

        val response = assistantApi.chat(
            ChatRequest(
                model = "gpt-4o-mini",
                messages = promptMessages
            )
        )

        val assistantText = response.choices.firstOrNull()?.message?.content
            ?.trim()
            ?.ifBlank { null }
            ?: "Sorry, I could not generate a response."

        chatDao.insert(ChatMessageEntity(role = "assistant", text = assistantText))
        assistantText
    }

    suspend fun clearConversation() = withContext(Dispatchers.IO) {
        chatDao.clearAll()
    }
}
