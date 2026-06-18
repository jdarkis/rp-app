package com.example.rpapp3.data

import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import com.example.rpapp3.data.repository.voiceDocumentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSelectionTest {
    @Test
    fun providerSafeDocumentIdsPreserveElevenLabsAndEncodeInworld() {
        val elevenLabsVoice = Voice(
            voiceId = "legacy-eleven-id",
            name = "ElevenLabs",
            source = VoiceSource.ELEVEN_LABS
        )
        val inworldVoice = Voice(
            voiceId = "workspaces/demo/voices/voice-1",
            name = "Inworld",
            source = VoiceSource.INWORLD
        )
        val sameRawIdInworldVoice = Voice(
            voiceId = elevenLabsVoice.voiceId,
            name = "Inworld collision",
            source = VoiceSource.INWORLD
        )
        val geminiVoice = Voice(
            voiceId = "Kore",
            name = "Kore",
            source = VoiceSource.GEMINI
        )

        assertEquals("legacy-eleven-id", voiceDocumentId(elevenLabsVoice))
        assertTrue(voiceDocumentId(inworldVoice).startsWith("inworld_"))
        assertTrue(voiceDocumentId(geminiVoice).startsWith("gemini_"))
        assertFalse(voiceDocumentId(inworldVoice).contains("/"))
        assertFalse(voiceDocumentId(sameRawIdInworldVoice) == voiceDocumentId(elevenLabsVoice))
        assertFalse(voiceDocumentId(geminiVoice) == voiceDocumentId(elevenLabsVoice))
    }

    @Test
    fun selectableVoicesIncludeActivatedGeneratedVoicesAndEligibleElevenLabs() {
        val eligibleElevenLabs = Voice(
            voiceId = "eleven-default",
            name = "Default",
            labels = mapOf("catalog_source" to ElevenLabsCatalogSource.DEFAULT.name),
            source = VoiceSource.ELEVEN_LABS
        )
        val legacyElevenLabs = Voice(
            voiceId = "eleven-legacy",
            name = "Legacy",
            source = VoiceSource.ELEVEN_LABS
        )
        val activeInworld = Voice(
            voiceId = "inworld-active",
            name = "Inworld",
            source = VoiceSource.INWORLD
        )
        val activeGemini = Voice(
            voiceId = "Kore",
            name = "Kore",
            source = VoiceSource.GEMINI
        )

        assertEquals(
            listOf(eligibleElevenLabs, activeInworld, activeGemini),
            selectableTtsVoices(listOf(eligibleElevenLabs, legacyElevenLabs, activeInworld, activeGemini))
        )
    }
}
