package com.example.voiceassistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.voiceassistant.data.AppSettings
import com.example.voiceassistant.data.ChatRepository
import com.example.voiceassistant.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.sendUserMessage(text.trim()) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(isLoading = false, error = it.message ?: "Unknown error")
                    }
                }
                .onSuccess {
                    _uiState.update { state -> state.copy(isLoading = false) }
                }
        }
    }

    fun updateSettings(settings: AppSettings) {
        settingsRepository.updateSettings(settings)
    }

    fun clearConversation() {
        viewModelScope.launch {
            repository.clearConversation()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ChatUiState(
    val messages: List<com.example.voiceassistant.domain.ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val settings: AppSettings = AppSettings(
        apiKey = "",
        model = SettingsRepository.DEFAULT_MODEL
    )
)
