package com.example.rpapp3.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TTSModel(
    @SerialName("model_id")
    val modelId: String,
    val name: String,
    val description: String = "",
    @SerialName("can_do_text_to_speech")
    val canDoTextToSpeech: Boolean = true,
    val languages: List<LanguageInfo> = emptyList()
)

@Serializable
data class LanguageInfo(
    @SerialName("language_id")
    val languageId: String,
    val name: String
)

@Serializable
data class ModelsResponse(
    val models: List<TTSModel> = emptyList()
)

/**
 * Predefined TTS models for selection UI
 */
object ElevenLabsTTSModels {
    val ELEVEN_V3 = TTSModel(
        modelId = "eleven_v3",
        name = "Eleven V3",
        description = "Emotionally rich and expressive in 70+ languages",
        canDoTextToSpeech = true
    )
    
    val ELEVEN_MULTILINGUAL_V2 = TTSModel(
        modelId = "eleven_multilingual_v2",
        name = "Eleven Multilingual V2",
        description = "Most lifelike model, 29 languages, rich emotion",
        canDoTextToSpeech = true
    )
    
    val ELEVEN_FLASH_V2_5 = TTSModel(
        modelId = "eleven_flash_v2_5",
        name = "Eleven Flash V2.5",
        description = "Ultra-low latency (~75ms), 32 languages",
        canDoTextToSpeech = true
    )
    
    val ELEVEN_TURBO_V2_5 = TTSModel(
        modelId = "eleven_turbo_v2_5",
        name = "Eleven Turbo V2.5",
        description = "Balanced quality and speed with low latency",
        canDoTextToSpeech = true
    )
    
    val DEFAULT_MODELS = listOf(
        ELEVEN_V3,
        ELEVEN_MULTILINGUAL_V2,
        ELEVEN_FLASH_V2_5,
        ELEVEN_TURBO_V2_5
    )
    
    const val DEFAULT_MODEL_ID = "eleven_v3"
}

object InworldTTSModels {
    val INWORLD_TTS_1_5_MAX = TTSModel(
        modelId = "inworld-tts-1.5-max",
        name = "Inworld TTS 1.5 Max",
        description = "Highest quality Inworld speech synthesis",
        canDoTextToSpeech = true
    )
    
    val DEFAULT_MODELS = listOf(
        INWORLD_TTS_1_5_MAX
    )
}

object GeminiTTSModels {
    val GEMINI_3_1_FLASH_TTS_PREVIEW = TTSModel(
        modelId = "gemini-3.1-flash-tts-preview",
        name = "Gemini 3.1 Flash TTS Preview",
        description = "Low-latency, controllable Gemini speech generation",
        canDoTextToSpeech = true
    )

    val DEFAULT_MODELS = listOf(
        GEMINI_3_1_FLASH_TTS_PREVIEW
    )
}

object AllTTSModels {
    val DEFAULT_MODELS = ElevenLabsTTSModels.DEFAULT_MODELS +
        InworldTTSModels.DEFAULT_MODELS +
        GeminiTTSModels.DEFAULT_MODELS
}
