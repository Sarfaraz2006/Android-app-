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

        val promptMessages = buildList {
            add(MessagePayload(role = "system", content = createSystemPrompt(settings)))
            addAll(
                chatDao.getRecentMessages(20).map {
                    MessagePayload(role = it.role, content = it.text)
                }
            )
        }

        val assistantText = runCatching {
            assistantApi.chat(settings = settings, messages = promptMessages)
        }.recoverCatching { err ->
            if (err is OpenRouterException && err.isRateLimited) {
                val fallbackModel = OPENROUTER_MODELS.firstOrNull { it != settings.model }
                    ?: throw err
                val fallbackSettings = settings.copy(model = fallbackModel)
                val fallbackResponse = assistantApi.chat(settings = fallbackSettings, messages = promptMessages)
                settingsRepository.updateSettings(fallbackSettings)
                fallbackResponse
            } else {
                throw err
            }
        }.getOrElse {
            throw it
        }.ifBlank { "I could not generate a response. Please try again." }

        chatDao.insert(ChatMessageEntity(role = "assistant", text = assistantText))
        return assistantText
    }

    private fun createSystemPrompt(settings: AppSettings): String {
        val nameSection = if (settings.userName.isBlank()) {
            "Address the user respectfully."
        } else {
            "User name is ${settings.userName}. Address them by name naturally."
        }

        return """
            You are Elix, a calm and helpful personal assistant.
            $nameSection
            Preferred language: ${settings.preferredLanguage}. Reply naturally in that language style.
            Be concise, practical, and friendly.
            Do not mention model names, OpenRouter, or that you are an AI model.
            Never say you are Bard/Google AI/LLM.
        """.trimIndent()
    }

    suspend fun clearConversation() = chatDao.clearAll()
}
