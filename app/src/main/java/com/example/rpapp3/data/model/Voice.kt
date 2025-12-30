package com.example.rpapp3.data.model

/**
 * Represents an ElevenLabs voice with metadata
 */
data class Voice(
    val voiceId: String,
    val name: String,
    val previewUrl: String? = null,
    val labels: Map<String, String> = emptyMap()
) {
    val gender: String?
        get() = labels["gender"]
    
    val accent: String?
        get() = labels["accent"]
    
    val age: String?
        get() = labels["age"]
    
    val description: String?
        get() = labels["description"]
    
    val useCase: String?
        get() = labels["use_case"] ?: labels["use case"]
}
