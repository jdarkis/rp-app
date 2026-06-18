package com.example.rpapp3.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsReplayAudioTest {

    @Test
    fun successfulGenerationImmediatelyCreatesPlayableBytesEntry() {
        val audioData = byteArrayOf(1, 2, 3)
        val state = putTtsReplayAudioBytes(
            state = emptyMap(),
            messageId = "message-1",
            segmentIndex = 0,
            generationId = 1L,
            audioData = audioData
        )

        val entry = state["message-1"]!![0]!!
        assertTrue(entry.isPlayable)
        assertEquals(1L, entry.generationId)
        assertArrayEquals(audioData, entry.audioData)
        assertNull(entry.localAudioUri)
        assertNull(entry.audioUrl)
    }

    @Test
    fun staleUploadDoesNotReplaceNewerGeneratedAudio() {
        val firstState = putTtsReplayAudioBytes(
            state = emptyMap(),
            messageId = "message-1",
            segmentIndex = 0,
            generationId = 1L,
            audioData = byteArrayOf(1)
        )
        val newerState = putTtsReplayAudioBytes(
            state = firstState,
            messageId = "message-1",
            segmentIndex = 0,
            generationId = 2L,
            audioData = byteArrayOf(2)
        )

        val afterStaleUpload = putTtsReplayAudioUrlIfCurrent(
            state = newerState,
            messageId = "message-1",
            segmentIndex = 0,
            generationId = 1L,
            audioUrl = "https://example.com/old.mp3"
        )

        assertSame(newerState, afterStaleUpload)
        val entry = afterStaleUpload["message-1"]!![0]!!
        assertEquals(2L, entry.generationId)
        assertArrayEquals(byteArrayOf(2), entry.audioData)
        assertNull(entry.audioUrl)
    }

    @Test
    fun localUriEntriesArePlayable() {
        val state = mergeLocalTtsReplayAudioUris(
            state = emptyMap(),
            messageId = "message-1",
            localAudioUris = mapOf(0 to "file:/local/audio.mp3")
        )

        val entry = state["message-1"]!![0]!!
        assertTrue(entry.isPlayable)
        assertEquals(0L, entry.generationId)
        assertEquals("file:/local/audio.mp3", entry.localAudioUri)
        assertNull(entry.audioData)
        assertNull(entry.audioUrl)
    }

    @Test
    fun matchingUploadReplacesBytesWithPersistedUrl() {
        val state = putTtsReplayAudioBytes(
            state = emptyMap(),
            messageId = "message-1",
            segmentIndex = 0,
            generationId = 5L,
            audioData = byteArrayOf(1, 2, 3),
            localAudioUri = "file:/local/audio.mp3"
        )

        val uploadedState = putTtsReplayAudioUrlIfCurrent(
            state = state,
            messageId = "message-1",
            segmentIndex = 0,
            generationId = 5L,
            audioUrl = "https://example.com/new.mp3"
        )

        val entry = uploadedState["message-1"]!![0]!!
        assertTrue(entry.isPlayable)
        assertEquals(5L, entry.generationId)
        assertEquals("file:/local/audio.mp3", entry.localAudioUri)
        assertEquals("https://example.com/new.mp3", entry.audioUrl)
        assertNull(entry.audioData)
    }

    @Test
    fun loadedPersistedUrlEntriesArePlayable() {
        val state = mergePersistedTtsReplayAudioUrls(
            state = emptyMap(),
            messageId = "message-1",
            audioUrls = mapOf(0 to "https://example.com/audio.mp3")
        )

        val entry = state["message-1"]!![0]!!
        assertTrue(entry.isPlayable)
        assertEquals(0L, entry.generationId)
        assertEquals("https://example.com/audio.mp3", entry.audioUrl)
        assertNull(entry.localAudioUri)
        assertNull(entry.audioData)
    }

    @Test
    fun loadedPersistedUrlDoesNotClearExistingGeneratedAudio() {
        val generatedState = putTtsReplayAudioBytes(
            state = emptyMap(),
            messageId = "message-1",
            segmentIndex = 0,
            generationId = 3L,
            audioData = byteArrayOf(9, 8),
            localAudioUri = "file:/local/newer.mp3"
        )

        val loadedState = mergePersistedTtsReplayAudioUrls(
            state = generatedState,
            messageId = "message-1",
            audioUrls = mapOf(0 to "https://example.com/older.mp3")
        )

        assertSame(generatedState, loadedState)
        val entry = loadedState["message-1"]!![0]!!
        assertArrayEquals(byteArrayOf(9, 8), entry.audioData)
        assertEquals("file:/local/newer.mp3", entry.localAudioUri)
        assertNull(entry.audioUrl)
    }

    @Test
    fun loadedPersistedUrlAugmentsLoadedLocalEntry() {
        val localState = mergeLocalTtsReplayAudioUris(
            state = emptyMap(),
            messageId = "message-1",
            localAudioUris = mapOf(0 to "file:/local/audio.mp3")
        )

        val mergedState = mergePersistedTtsReplayAudioUrls(
            state = localState,
            messageId = "message-1",
            audioUrls = mapOf(0 to "https://example.com/audio.mp3")
        )

        val entry = mergedState["message-1"]!![0]!!
        assertEquals("file:/local/audio.mp3", entry.localAudioUri)
        assertEquals("https://example.com/audio.mp3", entry.audioUrl)
        assertNull(entry.audioData)
    }

    @Test
    fun removingMessagesDropsReplayAudioEntries() {
        val state = mergeLocalTtsReplayAudioUris(
            state = mergeLocalTtsReplayAudioUris(
                state = emptyMap(),
                messageId = "message-1",
                localAudioUris = mapOf(0 to "file:/local/one.mp3")
            ),
            messageId = "message-2",
            localAudioUris = mapOf(0 to "file:/local/two.mp3")
        )

        val updatedState = removeTtsReplayAudioForMessages(
            state = state,
            messageIds = listOf("message-1")
        )

        assertNull(updatedState["message-1"])
        assertTrue(updatedState["message-2"]!![0]!!.isPlayable)
    }
}
