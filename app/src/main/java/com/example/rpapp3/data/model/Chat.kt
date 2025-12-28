package com.example.rpapp3.data.model

data class Chat(
    val id: String = "",
    val worldId: String = "",
    val characterIds: List<String> = emptyList(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // No-arg constructor for Firestore
    constructor() : this("")
    
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "worldId" to worldId,
        "characterIds" to characterIds,
        "title" to title,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
    
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): Chat {
            return Chat(
                id = map["id"] as? String ?: "",
                worldId = map["worldId"] as? String ?: "",
                characterIds = (map["characterIds"] as? List<String>) ?: emptyList(),
                title = map["title"] as? String ?: "",
                createdAt = (map["createdAt"] as? Long) ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Long) ?: System.currentTimeMillis()
            )
        }
    }
}
