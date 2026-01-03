package com.example.rpapp3.data.model

/**
 * Represents a version snapshot of character or world data,
 * stored before summarizer updates are applied.
 */
data class VersionHistory(
    val id: String = "",
    val entityId: String = "",           // Character or World ID
    val entityType: String = "",         // "character" or "world"
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "summarizer",   // Source of the update (e.g., "summarizer", "manual")
    
    // Character fields (only populated for character versions)
    val description: String? = null,
    val appearance: String? = null,
    val personality: String? = null,
    
    // World fields (only populated for world versions)
    val worldDescription: String? = null
) {
    constructor() : this("")
    
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "entityId" to entityId,
        "entityType" to entityType,
        "timestamp" to timestamp,
        "source" to source,
        "description" to description,
        "appearance" to appearance,
        "personality" to personality,
        "worldDescription" to worldDescription
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): VersionHistory {
            return VersionHistory(
                id = map["id"] as? String ?: "",
                entityId = map["entityId"] as? String ?: "",
                entityType = map["entityType"] as? String ?: "",
                timestamp = (map["timestamp"] as? Long) ?: System.currentTimeMillis(),
                source = map["source"] as? String ?: "summarizer",
                description = map["description"] as? String,
                appearance = map["appearance"] as? String,
                personality = map["personality"] as? String,
                worldDescription = map["worldDescription"] as? String
            )
        }
        
        /**
         * Create a version snapshot from a Character
         */
        fun fromCharacter(character: Character, source: String = "summarizer"): VersionHistory {
            return VersionHistory(
                id = java.util.UUID.randomUUID().toString(),
                entityId = character.id,
                entityType = "character",
                timestamp = System.currentTimeMillis(),
                source = source,
                description = character.description,
                appearance = character.appearance,
                personality = character.personality
            )
        }
        
        /**
         * Create a version snapshot from a World
         */
        fun fromWorld(world: World, source: String = "summarizer"): VersionHistory {
            return VersionHistory(
                id = java.util.UUID.randomUUID().toString(),
                entityId = world.id,
                entityType = "world",
                timestamp = System.currentTimeMillis(),
                source = source,
                worldDescription = world.description
            )
        }
    }
}
