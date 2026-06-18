package com.example.rpapp3.data

import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.ElevenLabsTTSModels
import com.example.rpapp3.data.model.GeminiTTSModels
import com.example.rpapp3.data.model.InworldTTSModels
import com.example.rpapp3.data.model.VoiceSource

internal enum class TtsProvider {
    ELEVEN_LABS,
    INWORLD,
    GEMINI
}

internal data class ResolvedTtsRequest(
    val provider: TtsProvider,
    val voiceId: String,
    val modelId: String
)

data class TtsGenerationState(
    val activeSegmentId: String? = null,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

internal object TtsRequestResolver {

    fun resolve(
        characterId: String?,
        chatCharacters: List<Character>,
        worldCharacters: List<Character>,
        narratorVoiceId: String,
        selectedModelId: String
    ): ResolvedTtsRequest? {
        val character = characterId?.let { id ->
            chatCharacters.find { it.id == id }
                ?: worldCharacters.find { it.id == id }
        }

        val characterVoiceId = character?.voiceId?.trim()?.takeIf { it.isNotEmpty() }
        if (character != null && characterVoiceId != null) {
            return when (character.voiceSource) {
                VoiceSource.ELEVEN_LABS -> ResolvedTtsRequest(
                    provider = TtsProvider.ELEVEN_LABS,
                    voiceId = characterVoiceId,
                    modelId = selectedModelId.takeIf(::isElevenLabsModel)
                        ?: ElevenLabsTTSModels.DEFAULT_MODEL_ID
                )
                VoiceSource.INWORLD -> ResolvedTtsRequest(
                    provider = TtsProvider.INWORLD,
                    voiceId = characterVoiceId,
                    modelId = InworldTTSModels.INWORLD_TTS_1_5_MAX.modelId
                )
                VoiceSource.GEMINI -> ResolvedTtsRequest(
                    provider = TtsProvider.GEMINI,
                    voiceId = characterVoiceId,
                    modelId = GeminiTTSModels.GEMINI_3_1_FLASH_TTS_PREVIEW.modelId
                )
            }
        }

        val narratorVoice = narratorVoiceId.trim().takeIf { it.isNotEmpty() } ?: return null
        val narratorUsesInworld =
            selectedModelId == InworldTTSModels.INWORLD_TTS_1_5_MAX.modelId ||
                narratorVoice.startsWith("workspaces/") ||
                narratorVoice.startsWith("voices/")

        return if (narratorUsesInworld) {
            ResolvedTtsRequest(
                provider = TtsProvider.INWORLD,
                voiceId = narratorVoice,
                modelId = InworldTTSModels.INWORLD_TTS_1_5_MAX.modelId
            )
        } else if (isGeminiModel(selectedModelId)) {
            ResolvedTtsRequest(
                provider = TtsProvider.GEMINI,
                voiceId = narratorVoice,
                modelId = GeminiTTSModels.GEMINI_3_1_FLASH_TTS_PREVIEW.modelId
            )
        } else {
            ResolvedTtsRequest(
                provider = TtsProvider.ELEVEN_LABS,
                voiceId = narratorVoice,
                modelId = selectedModelId.takeIf(::isElevenLabsModel)
                    ?: ElevenLabsTTSModels.DEFAULT_MODEL_ID
            )
        }
    }

    private fun isElevenLabsModel(modelId: String): Boolean {
        return ElevenLabsTTSModels.DEFAULT_MODELS.any { it.modelId == modelId }
    }

    private fun isGeminiModel(modelId: String): Boolean {
        return GeminiTTSModels.DEFAULT_MODELS.any { it.modelId == modelId }
    }
}
