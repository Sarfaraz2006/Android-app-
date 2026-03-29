package com.example.voiceassistant.domain

data class ChatMessage(
    val role: Role,
    val text: String
)

enum class Role { USER, ASSISTANT }
