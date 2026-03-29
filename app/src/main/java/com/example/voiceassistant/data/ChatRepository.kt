package com.example.voiceassistant.data

import com.example.voiceassistant.domain.ChatMessage
import com.example.voiceassistant.domain.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun sendUserMessage(text: String): String {
        chatDao.insert(ChatMessageEntity(role = "user", text = text))

        val promptMessages = chatDao.getRecentMessages(20).map {
            MessagePayload(role = it.role, content = it.text)
        }

        val response = assistantApi.chat(
            ChatRequest(
                model = "gpt-4o-mini",
                messages = promptMessages
            )
        )

        val assistantText = response.choices.firstOrNull()?.message?.content
            ?: "Sorry, I could not generate a response."
        chatDao.insert(ChatMessageEntity(role = "assistant", text = assistantText))
        return assistantText
    }

    suspend fun clearConversation() = chatDao.clearAll()
}
