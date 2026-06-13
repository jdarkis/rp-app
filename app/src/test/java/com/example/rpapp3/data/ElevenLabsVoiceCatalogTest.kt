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
    fun sharedCatalogIncludesOnlyVoicesAllowedForFreeUsers() {
        val page = parseFreeTierVoicePage(
            defaultVoicesJson = null,
            sharedVoicesJson = """
                {
                  "voices": [
                    {
                      "voice_id": "free",
                      "name": "Free Voice",
                      "free_users_allowed": true,
                      "language": "en",
                      "gender": "female"
                    },
                    {
                      "voice_id": "paid",
                      "name": "Paid Voice",
                      "free_users_allowed": false
                    },
                    {
                      "voice_id": "unknown",
                      "name": "Unknown Voice"
                    }
                  ],
                  "has_more": false
                }
            """.trimIndent(),
            page = 0
        )

        assertEquals(listOf("free"), page.voices.map { it.voiceId })
        assertEquals(ElevenLabsCatalogSource.SHARED, page.voices.single().source)
    }

    @Test
    fun defaultAndSharedVoicesAreMergedAndDeduplicatedByVoiceId() {
        val page = parseFreeTierVoicePage(
            defaultVoicesJson = """
                {
                  "voices": [
                    {
                      "voice_id": "same",
                      "name": "Default Name",
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
            sharedVoicesJson = """
                {
                  "voices": [
                    {
                      "voice_id": "same",
                      "name": "Shared Duplicate",
                      "free_users_allowed": true
                    },
                    {
                      "voice_id": "shared",
                      "name": "Shared Voice",
                      "free_users_allowed": true
                    }
                  ],
                  "has_more": true
                }
            """.trimIndent(),
            page = 2
        )

        assertEquals(listOf("same", "shared"), page.voices.map { it.voiceId })
        assertEquals("Default Name", page.voices.first().name)
        assertEquals(ElevenLabsCatalogSource.DEFAULT, page.voices.first().source)
        assertEquals(2, page.page)
        assertTrue(page.hasMore)
    }

    @Test
    fun catalogPathsCarryPaginationAndEncodedSearch() {
        assertEquals(
            "/v2/voices?page_size=100&voice_type=default&search=Mature+Voice",
            buildDefaultVoicesPath(" Mature Voice ")
        )
        assertEquals(
            "/v1/shared-voices?page_size=100&page=3&search=Mature+Voice",
            buildSharedVoicesPath(3, "Mature Voice")
        )
    }

    @Test
    fun malformedResponsesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseFreeTierVoicePage(
                defaultVoicesJson = null,
                sharedVoicesJson = "not-json",
                page = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseFreeTierVoicePage(
                defaultVoicesJson = null,
                sharedVoicesJson = """{"has_more": false}""",
                page = 0
            )
        }
    }

    @Test
    fun missingPreviewIsPreservedAsUnavailable() {
        val page = parseFreeTierVoicePage(
            defaultVoicesJson = null,
            sharedVoicesJson = """
                {
                  "voices": [
                    {
                      "voice_id": "no-preview",
                      "name": "No Preview",
                      "preview_url": null,
                      "free_users_allowed": true
                    }
                  ],
                  "has_more": false
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
    fun activatedCatalogVoiceBecomesSelectableAndDeactivationOnlyRemovesItFromList() {
        val catalogVoice = ElevenLabsCatalogVoice(
            voiceId = "catalog-id",
            name = "Catalog Voice",
            previewUrl = "https://example.com/preview.mp3",
            description = "Warm and clear",
            language = "en",
            gender = "female",
            accent = "british",
            category = "professional",
            source = ElevenLabsCatalogSource.SHARED
        )
        val activatedVoice = catalogVoice.toVoice()
        val inworldVoice = Voice(
            voiceId = "inworld-id",
            name = "Inworld Voice",
            source = VoiceSource.INWORLD
        )

        assertEquals(
            listOf(activatedVoice),
            selectableElevenLabsVoices(listOf(activatedVoice, inworldVoice))
        )
        assertEquals("true", activatedVoice.labels["free_users_allowed"])
        assertEquals("SHARED", activatedVoice.labels["catalog_source"])
        assertTrue(selectableElevenLabsVoices(listOf(inworldVoice)).isEmpty())
    }
}
