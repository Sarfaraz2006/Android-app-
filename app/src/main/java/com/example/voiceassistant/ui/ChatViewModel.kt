package com.example.voiceassistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceassistant.data.ChatRepository
import com.example.voiceassistant.domain.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }
    }

    fun sendMessage(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    inputText = "",
                    partialTranscript = ""
                )
            }

            runCatching { repository.sendUserMessage(cleaned) }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = throwable.message ?: "Unable to send message"
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { state -> state.copy(isLoading = false) }
                }
        }
    }

    fun onInputChanged(value: String) {
        _uiState.update { it.copy(inputText = value) }
    }

    fun onListeningChanged(isListening: Boolean) {
        _uiState.update { it.copy(isListening = isListening) }
    }

    fun onPartialTranscript(text: String) {
        _uiState.update { it.copy(partialTranscript = text) }
    }

    fun onSpeechError(message: String) {
        _uiState.update { it.copy(error = message, isListening = false) }
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
            _uiState.update { it.copy(partialTranscript = "", error = null) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val partialTranscript: String = "",
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val error: String? = null
)
