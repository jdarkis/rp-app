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

    val ADELINE = Voice(
        voiceId = "5l5f8iK3YPeGga21rQIX",
        name = "Adeline",
        previewUrl = null,
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    val ALEXANDRA = Voice(
        voiceId = "kdmDKE6EkgrWrrykO9Qt",
        name = "Alexandra",
        previewUrl = null,
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    val ARABELLA = Voice(
        voiceId = "aEO01A4wXwd1O8GPgGlF",
        name = "Arabella",
        previewUrl = null,
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    val ANIKA = Voice(
        voiceId = "Sm1seazb4gs7RSlUVw7c",
        name = "Anika",
        previewUrl = null,
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    val ANA = Voice(
        voiceId = "rCmVtv8cYU60uhlsOo1M",
        name = "Ana",
        previewUrl = null,
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    val IVANNA = Voice(
        voiceId = "yM93hbw8Qtvdma2wCnJG",
        name = "Ivanna",
        previewUrl = null,
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    // Default ElevenLabs voices - these have static preview URLs
    val CHRIS = Voice(
        voiceId = "iP95p4xoKVk53GoZ742B",
        name = "Chris",
        previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/iP95p4xoKVk53GoZ742B/3f4bde72-cc48-40dd-829f-57fbf906f4d7.mp3",
        labels = mapOf(
            "gender" to "male",
            "use_case" to "characters"
        )
    )

    val CHARLOTTE = Voice(
        voiceId = "XB0fDUnXU5powFXDhCwa",
        name = "Charlotte",
        previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/XB0fDUnXU5powFXDhCwa/942356dc-f10d-4d89-bda5-4f8505ee038b.mp3",
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    val JESSICA = Voice(
        voiceId = "cgSgspJ2msm6clMCkdW9",
        name = "Jessica",
        previewUrl = "https://storage.googleapis.com/eleven-public-prod/premade/voices/cgSgspJ2msm6clMCkdW9/56a97bf8-b69b-448f-846c-c3a11683d45a.mp3",
        labels = mapOf(
            "gender" to "female",
            "use_case" to "characters"
        )
    )

    /**
     * List of all preset voices that should always be available.
     * Custom voices (null previewUrl) will be fetched from Voice Library.
     * Default ElevenLabs voices have hardcoded preview URLs.
     */
    val PRESET_VOICES = listOf(
        GRANDPA_SPUDS_OXLEY,
        EVE,
        ADELINE,
        ALEXANDRA,
        ARABELLA,
        ANIKA,
        ANA,
        IVANNA,
        CHRIS,
        CHARLOTTE,
        JESSICA
    )
}
