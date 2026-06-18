package com.example.rpapp3.data

import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.ElevenLabsTTSModels
import com.example.rpapp3.data.model.GeminiTTSModels
import com.example.rpapp3.data.model.InworldTTSModels
import com.example.rpapp3.data.model.VoiceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TtsRequestResolverTest {

    @Test
    fun elevenLabsCharacterDoesNotInheritInworldNarratorModel() {
        val character = character(
            id = "character-1",
            voiceId = "eleven-voice",
            voiceSource = VoiceSource.ELEVEN_LABS
        )

        val request = resolve(character, InworldTTSModels.INWORLD_TTS_1_5_MAX.modelId)

        assertNotNull(request)
        assertEquals(TtsProvider.ELEVEN_LABS, request?.provider)
        assertEquals("eleven-voice", request?.voiceId)
        assertEquals(ElevenLabsTTSModels.DEFAULT_MODEL_ID, request?.modelId)
    }

    @Test
    fun inworldCharacterDoesNotInheritElevenLabsNarratorModel() {
        val character = character(
            id = "character-1",
            voiceId = "workspaces/test/voices/test",
            voiceSource = VoiceSource.INWORLD
        )

        val request = resolve(character, ElevenLabsTTSModels.ELEVEN_V3.modelId)

        assertNotNull(request)
        assertEquals(TtsProvider.INWORLD, request?.provider)
        assertEquals(InworldTTSModels.INWORLD_TTS_1_5_MAX.modelId, request?.modelId)
    }

    @Test
    fun geminiCharacterDoesNotInheritElevenLabsNarratorModel() {
        val character = character(
            id = "character-1",
            voiceId = "Kore",
            voiceSource = VoiceSource.GEMINI
        )

        val request = resolve(character, ElevenLabsTTSModels.ELEVEN_V3.modelId)

        assertNotNull(request)
        assertEquals(TtsProvider.GEMINI, request?.provider)
        assertEquals("Kore", request?.voiceId)
        assertEquals(GeminiTTSModels.GEMINI_3_1_FLASH_TTS_PREVIEW.modelId, request?.modelId)
    }

    @Test
    fun worldCharacterIsResolvedWhenNotInChatCharacterList() {
        val character = character(
            id = "world-character",
            voiceId = "world-voice",
            voiceSource = VoiceSource.ELEVEN_LABS
        )

        val request = TtsRequestResolver.resolve(
            characterId = character.id,
            chatCharacters = emptyList(),
            worldCharacters = listOf(character),
            narratorVoiceId = "narrator-voice",
            selectedModelId = ElevenLabsTTSModels.ELEVEN_FLASH_V2_5.modelId
        )

        assertNotNull(request)
        assertEquals("world-voice", request?.voiceId)
        assertEquals(ElevenLabsTTSModels.ELEVEN_FLASH_V2_5.modelId, request?.modelId)
    }

    @Test
    fun missingCharacterVoiceFallsBackToNarrator() {
        val character = character(
            id = "character-1",
            voiceId = null,
            voiceSource = VoiceSource.ELEVEN_LABS
        )

        val request = TtsRequestResolver.resolve(
            characterId = character.id,
            chatCharacters = listOf(character),
            worldCharacters = emptyList(),
            narratorVoiceId = "narrator-voice",
            selectedModelId = ElevenLabsTTSModels.ELEVEN_MULTILINGUAL_V2.modelId
        )

        assertNotNull(request)
        assertEquals(TtsProvider.ELEVEN_LABS, request?.provider)
        assertEquals("narrator-voice", request?.voiceId)
        assertEquals(ElevenLabsTTSModels.ELEVEN_MULTILINGUAL_V2.modelId, request?.modelId)
    }

    @Test
    fun geminiNarratorModelRoutesToGemini() {
        val request = TtsRequestResolver.resolve(
            characterId = null,
            chatCharacters = emptyList(),
            worldCharacters = emptyList(),
            narratorVoiceId = "Sulafat",
            selectedModelId = GeminiTTSModels.GEMINI_3_1_FLASH_TTS_PREVIEW.modelId
        )

        assertNotNull(request)
        assertEquals(TtsProvider.GEMINI, request?.provider)
        assertEquals("Sulafat", request?.voiceId)
        assertEquals(GeminiTTSModels.GEMINI_3_1_FLASH_TTS_PREVIEW.modelId, request?.modelId)
    }

    @Test
    fun narratorVoiceNameUsesElevenLabsWhenElevenLabsModelSelected() {
        val request = TtsRequestResolver.resolve(
            characterId = null,
            chatCharacters = emptyList(),
            worldCharacters = emptyList(),
            narratorVoiceId = "Kore",
            selectedModelId = ElevenLabsTTSModels.ELEVEN_FLASH_V2_5.modelId
        )

        assertNotNull(request)
        assertEquals(TtsProvider.ELEVEN_LABS, request?.provider)
        assertEquals("Kore", request?.voiceId)
        assertEquals(ElevenLabsTTSModels.ELEVEN_FLASH_V2_5.modelId, request?.modelId)
    }

    @Test
    fun noCharacterOrNarratorVoiceReturnsNull() {
        val request = TtsRequestResolver.resolve(
            characterId = null,
            chatCharacters = emptyList(),
            worldCharacters = emptyList(),
            narratorVoiceId = "",
            selectedModelId = ElevenLabsTTSModels.DEFAULT_MODEL_ID
        )

        assertNull(request)
    }

    private fun resolve(
        character: Character,
        selectedModelId: String
    ): ResolvedTtsRequest? {
        return TtsRequestResolver.resolve(
            characterId = character.id,
            chatCharacters = listOf(character),
            worldCharacters = emptyList(),
            narratorVoiceId = "narrator-voice",
            selectedModelId = selectedModelId
        )
    }

    private fun character(
        id: String,
        voiceId: String?,
        voiceSource: VoiceSource
    ): Character {
        return Character(
            id = id,
            name = "Test Character",
            voiceId = voiceId,
            voiceSource = voiceSource
        )
    }
}
