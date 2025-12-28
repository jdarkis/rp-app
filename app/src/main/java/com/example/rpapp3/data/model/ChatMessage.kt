package com.example.rpapp3.data.model

import java.util.UUID

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String = "",
    val text: String = "",
    val isUser: Boolean = true,
    val characterId: String? = null,
    val characterName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    // No-arg constructor for Firestore
    constructor() : this(UUID.randomUUID().toString())
    
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "chatId" to chatId,
        "text" to text,
        "isUser" to isUser,
        "characterId" to characterId,
        "characterName" to characterName,
        "timestamp" to timestamp
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): ChatMessage {
            return ChatMessage(
                id = map["id"] as? String ?: UUID.randomUUID().toString(),
                chatId = map["chatId"] as? String ?: "",
                text = map["text"] as? String ?: "",
                isUser = map["isUser"] as? Boolean ?: true,
                characterId = map["characterId"] as? String,
                characterName = map["characterName"] as? String,
                timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis()
            )
        }
    }
}

