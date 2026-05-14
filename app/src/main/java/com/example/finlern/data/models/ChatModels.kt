package com.example.finlern.data.models

data class Chat(
    val chatId: String = "",
    val participants: List<String> = emptyList(),
    val lastMessage: Message? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class Message(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT,
    val encryptedContent: String = "",
    val iv: String = "", // Initialization Vector for encryption
    val status: MessageStatus = MessageStatus.SENDING,
    val metadata: Map<String, String> = emptyMap()
)

enum class MessageType {
    TEXT,
    IMAGE,
    FILE
}

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED
}