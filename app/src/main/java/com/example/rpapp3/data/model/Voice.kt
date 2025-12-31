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

/**
 * Predefined custom voices that are always available for selection.
 * These are custom voices added to the user's ElevenLabs account.
 */
object PresetVoices {
    // Preview URLs are fetched from ElevenLabs API when voices are loaded
    val GRANDPA_SPUDS_OXLEY = Voice(
        voiceId = "NOpBlnGInO9m6vDvFkFC",
        name = "Grandpa Spuds Oxley",
        previewUrl = null, // Will be populated from API
        labels = mapOf(
            "gender" to "male",
            "age" to "old",
            "use_case" to "narration"
        )
    )
    
    val EVE = Voice(
        voiceId = "BZgkqPqms7Kj9ulSkVzn",
        name = "Eve",
        previewUrl = null, // Will be populated from API
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )
    
    /**
     * List of all preset voices that should always be available
     */
    val PRESET_VOICES = listOf(
        GRANDPA_SPUDS_OXLEY,
        EVE
    )
}
