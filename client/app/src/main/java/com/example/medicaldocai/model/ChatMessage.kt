package com.example.medicaldocai.model


data class ChatMessage(
    val id: String,
    val text: String,
    val sender: String, // "user" or "assistant"
    val timestamp: Long,
    val isTyping: Boolean = false
)