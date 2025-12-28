package com.example.rpapp3.data.model

data class Character(
    val id: String = "",
    val worldId: String = "",
    val name: String = "",
    val description: String = "",
    val appearance: String = "",
    val personality: String = "",
    val systemInstructions: String = "",
    val photoUrls: List<String> = emptyList(),
    val videoUrls: List<String> = emptyList(),
    val profilePictureUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // No-arg constructor for Firestore
    constructor() : this("")
    
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "worldId" to worldId,
        "name" to name,
        "description" to description,
        "appearance" to appearance,
        "personality" to personality,
        "systemInstructions" to systemInstructions,
        "photoUrls" to photoUrls,
        "videoUrls" to videoUrls,
        "profilePictureUrl" to profilePictureUrl,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
    
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): Character {
            return Character(
                id = map["id"] as? String ?: "",
                worldId = map["worldId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                appearance = map["appearance"] as? String ?: "",
                personality = map["personality"] as? String ?: "",
                systemInstructions = map["systemInstructions"] as? String ?: "",
                photoUrls = (map["photoUrls"] as? List<String>) ?: emptyList(),
                videoUrls = (map["videoUrls"] as? List<String>) ?: emptyList(),
                profilePictureUrl = map["profilePictureUrl"] as? String,
                createdAt = (map["createdAt"] as? Long) ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Long) ?: System.currentTimeMillis()
            )
        }
    }
    
    /**
     * Generates system instructions for AI based on character data
     */
    fun generateAIInstructions(): String {
        return buildString {
            appendLine("You are roleplaying as $name.")
            if (description.isNotBlank()) {
                appendLine("\nBackground: $description")
            }
            if (appearance.isNotBlank()) {
                appendLine("\nAppearance: $appearance")
            }
            if (personality.isNotBlank()) {
                appendLine("\nPersonality: $personality")
            }
            if (systemInstructions.isNotBlank()) {
                appendLine("\nAdditional instructions: $systemInstructions")
            }
        }
    }
}
