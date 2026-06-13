package com.example.rpapp3.data

import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ElevenLabsVoiceCatalogTest {

    @Test
    fun catalogContainsOnlyDefaultVoicesAvailableToFreeTier() {
        val page = parseFreeTierVoicePage(
            defaultVoicesJson = """
                {
                  "voices": [
                    {
                      "voice_id": "free-default",
                      "name": "Free Default",
                      "category": "premade",
                      "available_for_tiers": ["free", "starter"]
                    },
                    {
                      "voice_id": "paid-default",
                      "name": "Paid Default",
                      "available_for_tiers": ["creator", "pro"]
                    }
                  ]
                }
            """.trimIndent(),
            page = 0
        )

        assertEquals(listOf("free-default"), page.voices.map { it.voiceId })
        assertEquals(ElevenLabsCatalogSource.DEFAULT, page.voices.single().source)
        assertFalse(page.hasMore)
    }

    @Test
    fun defaultVoicesWithoutTierMetadataAreAcceptedFromAuthenticatedUserList() {
        val page = parseFreeTierVoicePage(
            defaultVoicesJson = """
                {
                  "voices": [
                    {
                      "voice_id": "default",
                      "name": "Default Voice",
                      "preview_url": "https://example.com/default.mp3",
                      "category": "premade",
                      "labels": {
                        "gender": "female",
                        "accent": "american"
                      },
                      "verified_languages": [
                        {"language": "en"}
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            page = 0
        )

        val voice = page.voices.single()
        assertEquals("default", voice.voiceId)
        assertEquals("en", voice.language)
        assertEquals("female", voice.gender)
        assertEquals("american", voice.accent)
    }

    @Test
    fun duplicateDefaultVoiceIdsAreDeduplicated() {
        val page = parseFreeTierVoicePage(
            defaultVoicesJson = """
                {
                  "voices": [
                    {"voice_id": "same", "name": "First"},
                    {"voice_id": "same", "name": "Second"}
                  ]
                }
            """.trimIndent(),
            page = 0
        )

        assertEquals(1, page.voices.size)
        assertEquals("First", page.voices.single().name)
    }

    @Test
    fun catalogPathRequestsDefaultVoicesWithEncodedSearch() {
        assertEquals(
            "/v2/voices?page_size=100&voice_type=default&search=Mature+Voice",
            buildDefaultVoicesPath(" Mature Voice ")
        )
    }

    @Test
    fun malformedResponsesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseFreeTierVoicePage(
                defaultVoicesJson = "not-json",
                page = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseFreeTierVoicePage(
                defaultVoicesJson = """{"has_more": false}""",
                page = 0
            )
        }
    }

    @Test
    fun missingPreviewIsPreservedAsUnavailable() {
        val page = parseFreeTierVoicePage(
            defaultVoicesJson = """
                {
                  "voices": [
                    {
                      "voice_id": "no-preview",
                      "name": "No Preview",
                      "preview_url": null
                    }
                  ]
                }
            """.trimIndent(),
            page = 0
        )

        assertNull(page.voices.single().previewUrl)
    }

    @Test
    fun authenticationRateServerAndNetworkFailuresRotateKeys() {
        assertTrue(shouldRotateCatalogKey(401))
        assertTrue(shouldRotateCatalogKey(403))
        assertTrue(shouldRotateCatalogKey(408))
        assertTrue(shouldRotateCatalogKey(429))
        assertTrue(shouldRotateCatalogKey(500))
        assertFalse(shouldRotateCatalogKey(400))
        assertTrue(shouldRotateCatalogKey(IOException("offline")))
        assertFalse(shouldRotateCatalogKey(IllegalArgumentException("bad response")))
    }

    @Test
    fun onlyActivatedDefaultCatalogVoicesAreSelectable() {
        val defaultVoice = ElevenLabsCatalogVoice(
            voiceId = "default-id",
            name = "Default Voice",
            previewUrl = "https://example.com/preview.mp3",
            description = "Warm and clear",
            language = "en",
            gender = "female",
            accent = "british",
            category = "premade",
            source = ElevenLabsCatalogSource.DEFAULT
        ).toVoice()
        val libraryVoice = Voice(
            voiceId = "library-id",
            name = "Library Voice",
            labels = mapOf(
                "catalog_source" to ElevenLabsCatalogSource.SHARED.name,
                "free_users_allowed" to "true"
            ),
            source = VoiceSource.ELEVEN_LABS
        )
        val legacyVoice = Voice(
            voiceId = "legacy-id",
            name = "Legacy Voice",
            source = VoiceSource.ELEVEN_LABS
        )
        val inworldVoice = Voice(
            voiceId = "inworld-id",
            name = "Inworld Voice",
            source = VoiceSource.INWORLD
        )

        assertEquals(
            listOf(defaultVoice),
            selectableElevenLabsVoices(
                listOf(defaultVoice, libraryVoice, legacyVoice, inworldVoice)
            )
        )
        assertEquals("true", defaultVoice.labels["free_api_compatible"])
        assertEquals("DEFAULT", defaultVoice.labels["catalog_source"])
    }

    @Test
    fun paidPlanLibraryErrorHasActionableMessage() {
        val message = elevenLabsTtsFailureMessage(
            """
                {"detail":{"type":"payment_required","code":"paid_plan_required",
                "message":"Free users cannot use library voices via the API."}}
            """.trimIndent()
        )

        assertTrue(message.contains("requires a paid plan"))
        assertTrue(message.contains("Default voice"))
        assertFalse(message.contains("{\"detail\""))
    }
}
