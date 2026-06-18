package com.example.rpapp3.data

import com.example.rpapp3.data.model.VoiceSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class GeminiTtsServiceTest {

    @Test
    fun requestBodyEscapesTextAndVoiceName() {
        val body = buildGeminiTtsRequestBody(
            text = "She said \"hello\".\\Path\n[whispers] Keep going.",
            voiceId = "Kore\"Test"
        )

        val root = Json.parseToJsonElement(body).jsonObject
        val prompt = root["contents"]!!
            .jsonArray[0]
            .jsonObject["parts"]!!
            .jsonArray[0]
            .jsonObject["text"]!!
            .jsonPrimitive.content
        val voiceName = root["generationConfig"]!!
            .jsonObject["speechConfig"]!!
            .jsonObject["voiceConfig"]!!
            .jsonObject["prebuiltVoiceConfig"]!!
            .jsonObject["voiceName"]!!
            .jsonPrimitive.content

        assertTrue(prompt.startsWith("Synthesize the following transcript as spoken audio."))
        assertTrue(prompt.contains("She said \"hello\".\\Path\n[whispers] Keep going."))
        assertEquals("Kore\"Test", voiceName)
    }

    @Test
    fun extractsAndCombinesInlinePcmAudio() {
        val firstChunk = Base64.getEncoder().encodeToString(byteArrayOf(1, 2))
        val secondChunk = Base64.getEncoder().encodeToString(byteArrayOf(3, 4))
        val response = """
            {
              "candidates": [{
                "content": {
                  "parts": [
                    { "inlineData": { "data": "$firstChunk" } },
                    { "inline_data": { "data": "$secondChunk" } }
                  ]
                }
              }]
            }
        """.trimIndent()

        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            extractGeminiPcmAudio(response)
        )
    }

    @Test
    fun wrapsPcmBytesAsWav() {
        val wav = wrapPcmAsWav(byteArrayOf(10, 20, 30, 40))

        assertEquals("RIFF", wav.ascii(0, 4))
        assertEquals(40, wav.readLittleEndianInt(4))
        assertEquals("WAVE", wav.ascii(8, 4))
        assertEquals("fmt ", wav.ascii(12, 4))
        assertEquals(1, wav.readLittleEndianShort(20))
        assertEquals(1, wav.readLittleEndianShort(22))
        assertEquals(24000, wav.readLittleEndianInt(24))
        assertEquals(48000, wav.readLittleEndianInt(28))
        assertEquals(16, wav.readLittleEndianShort(34))
        assertEquals("data", wav.ascii(36, 4))
        assertEquals(4, wav.readLittleEndianInt(40))
        assertArrayEquals(byteArrayOf(10, 20, 30, 40), wav.copyOfRange(44, 48))
    }

    @Test
    fun geminiVoiceCatalogContainsFreeTierVoices() {
        val voices = GeminiTtsVoices.DEFAULT_VOICES

        assertEquals(30, voices.size)
        assertEquals("Zephyr", voices.first().voiceId)
        assertTrue(voices.any { it.voiceId == "Sulafat" && it.labels["style"] == "Warm" })
        assertTrue(voices.all { it.source == VoiceSource.GEMINI })
        assertTrue(voices.all { it.labels["free_tier"] == "true" })
    }

    private fun ByteArray.ascii(offset: Int, length: Int): String {
        return String(this, offset, length, StandardCharsets.US_ASCII)
    }

    private fun ByteArray.readLittleEndianInt(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun ByteArray.readLittleEndianShort(offset: Int): Int {
        return (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8)
    }
}
