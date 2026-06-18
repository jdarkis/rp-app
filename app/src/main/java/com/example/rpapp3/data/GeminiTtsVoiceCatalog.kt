package com.example.rpapp3.data

import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource

internal data class GeminiTtsVoice(
    val voiceId: String,
    val style: String
) {
    fun toVoice(): Voice {
        return Voice(
            voiceId = voiceId,
            name = voiceId,
            labels = mapOf(
                "description" to "$style Gemini TTS voice",
                "style" to style,
                "language" to "Auto",
                "free_tier" to "true"
            ),
            source = VoiceSource.GEMINI
        )
    }
}

internal object GeminiTtsVoices {
    val DEFAULT_VOICES = listOf(
        GeminiTtsVoice("Zephyr", "Bright"),
        GeminiTtsVoice("Puck", "Upbeat"),
        GeminiTtsVoice("Charon", "Informative"),
        GeminiTtsVoice("Kore", "Firm"),
        GeminiTtsVoice("Fenrir", "Excitable"),
        GeminiTtsVoice("Leda", "Youthful"),
        GeminiTtsVoice("Orus", "Firm"),
        GeminiTtsVoice("Aoede", "Breezy"),
        GeminiTtsVoice("Callirrhoe", "Easy-going"),
        GeminiTtsVoice("Autonoe", "Bright"),
        GeminiTtsVoice("Enceladus", "Breathy"),
        GeminiTtsVoice("Iapetus", "Clear"),
        GeminiTtsVoice("Umbriel", "Easy-going"),
        GeminiTtsVoice("Algieba", "Smooth"),
        GeminiTtsVoice("Despina", "Smooth"),
        GeminiTtsVoice("Erinome", "Clear"),
        GeminiTtsVoice("Algenib", "Gravelly"),
        GeminiTtsVoice("Rasalgethi", "Informative"),
        GeminiTtsVoice("Laomedeia", "Upbeat"),
        GeminiTtsVoice("Achernar", "Soft"),
        GeminiTtsVoice("Alnilam", "Firm"),
        GeminiTtsVoice("Schedar", "Even"),
        GeminiTtsVoice("Gacrux", "Mature"),
        GeminiTtsVoice("Pulcherrima", "Forward"),
        GeminiTtsVoice("Achird", "Friendly"),
        GeminiTtsVoice("Zubenelgenubi", "Casual"),
        GeminiTtsVoice("Vindemiatrix", "Gentle"),
        GeminiTtsVoice("Sadachbia", "Lively"),
        GeminiTtsVoice("Sadaltager", "Knowledgeable"),
        GeminiTtsVoice("Sulafat", "Warm")
    ).map { it.toVoice() }
}
