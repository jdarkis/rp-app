package com.example.rpapp3.data.model

data class World(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val writingStyle: String = "",
    val systemInstructions: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    // No-arg constructor for Firestore
    constructor() : this("")
    
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "writingStyle" to writingStyle,
        "systemInstructions" to systemInstructions,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): World {
            return World(
                id = map["id"] as? String ?: "",
                name = map["name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                writingStyle = map["writingStyle"] as? String ?: "",
                systemInstructions = map["systemInstructions"] as? String ?: "",
                createdAt = (map["createdAt"] as? Long) ?: System.currentTimeMillis(),
                updatedAt = (map["updatedAt"] as? Long) ?: System.currentTimeMillis()
            )
        }
    }
}
