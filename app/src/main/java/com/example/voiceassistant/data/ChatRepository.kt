package com.example.voiceassistant.data

import com.example.voiceassistant.domain.ChatMessage
import com.example.voiceassistant.domain.Role
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao,
    private val assistantApi: AssistantApi,
    private val settingsRepository: SettingsRepository
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
        val settings = settingsRepository.currentSettings()
        if (settings.apiKey.isBlank()) {
            throw IllegalStateException("Please add your OpenRouter API key in Settings.")
        }

        chatDao.insert(ChatMessageEntity(role = "user", text = text))

        val promptMessages = chatDao.getRecentMessages(20).map {
            MessagePayload(role = it.role, content = it.text)
        }

        val assistantText = assistantApi.chat(settings = settings, messages = promptMessages)
            .ifBlank { "I could not generate a response. Please try again." }

        chatDao.insert(ChatMessageEntity(role = "assistant", text = assistantText))
        return assistantText
    }

    suspend fun clearConversation() = chatDao.clearAll()
}
