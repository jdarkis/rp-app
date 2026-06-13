package com.example.rpapp3.data.model

import com.example.rpapp3.data.ChatSettings

data class Chat(
    val id: String = "",
    val worldId: String = "",
    val characterIds: List<String> = emptyList(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Private chat fields
    val isPrivateChat: Boolean = false,
    val privateCharacterId: String? = null,
    // Context knowledge: list of chat IDs to include as context
    val contextChatIds: List<String> = emptyList(),
    // Writing style instructions for private chats (stored in Firebase)
    val writingStyle: String = "",
    // Normal chat settings. Private chats keep using their existing settings path.
    val settings: ChatSettings = ChatSettings()
) {
    // No-arg constructor for Firestore
    constructor() : this("")
    
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "worldId" to worldId,
        "characterIds" to characterIds,
        "title" to title,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "isPrivateChat" to isPrivateChat,
        "privateCharacterId" to privateCharacterId,
        "contextChatIds" to contextChatIds,
        "writingStyle" to writingStyle,
        "settings" to settings.toMap()
    )
    
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): Chat {
            return Chat(
                id = map["id"] as? String ?: "",
                worldId = map["worldId"] as? String ?: "",
                characterIds = (map["characterIds"] as? List<String>) ?: emptyList(),
                title = map["title"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                isPrivateChat = map["isPrivateChat"] as? Boolean ?: false,
                privateCharacterId = map["privateCharacterId"] as? String,
                contextChatIds = (map["contextChatIds"] as? List<String>) ?: emptyList(),
                writingStyle = map["writingStyle"] as? String ?: "",
                settings = ChatSettings.fromMap(map["settings"].asStringKeyMap())
            )
        }

        private fun Any?.asStringKeyMap(): Map<String, Any?>? {
            val rawMap = this as? Map<*, *> ?: return null
            return rawMap.entries.mapNotNull { (key, value) ->
                (key as? String)?.let { it to value }
            }.toMap()
        }
    }
}
