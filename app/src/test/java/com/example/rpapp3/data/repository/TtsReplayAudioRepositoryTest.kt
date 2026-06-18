package com.example.rpapp3.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtsReplayAudioRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun writesGeneratedAudioToDeterministicMessageSegmentFile() = runBlocking {
        val repository = TtsReplayAudioRepository(temporaryFolder.root)
        val audioBytes = byteArrayOf(1, 2, 3)

        val uri = repository.saveAudio(
            chatId = "chat/1",
            messageId = "message:1",
            segmentIndex = 2,
            audioBytes = audioBytes
        ).getOrThrow()

        val files = temporaryFolder.root.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, files.size)
        assertEquals(segmentFileName(2, "mp3"), files.single().name)
        assertArrayEquals(audioBytes, files.single().readBytes())
        assertEquals(files.single().toURI().toString(), uri)
    }

    @Test
    fun loadsLocalReplayEntriesForReopenedMessage() = runBlocking {
        val repository = TtsReplayAudioRepository(temporaryFolder.root)
        val savedUri = repository.saveAudio(
            chatId = "chat-1",
            messageId = "message-1",
            segmentIndex = 0,
            audioBytes = byteArrayOf(4, 5, 6)
        ).getOrThrow()

        val loadedUris = repository.loadAudioUrisForMessage(
            chatId = "chat-1",
            messageId = "message-1"
        )

        assertEquals(mapOf(0 to savedUri), loadedUris)
    }

    @Test
    fun deletesLocalReplayFilesForRemovedMessages() = runBlocking {
        val repository = TtsReplayAudioRepository(temporaryFolder.root)
        repository.saveAudio("chat-1", "deleted-message", 0, byteArrayOf(1)).getOrThrow()
        repository.saveAudio("chat-1", "deleted-message", 1, byteArrayOf(2)).getOrThrow()
        repository.saveAudio("chat-1", "kept-message", 0, byteArrayOf(3)).getOrThrow()

        repository.deleteAudioForMessage("chat-1", "deleted-message").getOrThrow()

        assertTrue(repository.loadAudioUrisForMessage("chat-1", "deleted-message").isEmpty())
        assertEquals(
            1,
            repository.loadAudioUrisForMessage("chat-1", "kept-message").size
        )
    }

    @Test
    fun detectsWavAndMp3Extensions() {
        val wavHeader = byteArrayOf(
            'R'.code.toByte(),
            'I'.code.toByte(),
            'F'.code.toByte(),
            'F'.code.toByte(),
            0,
            0,
            0,
            0,
            'W'.code.toByte(),
            'A'.code.toByte(),
            'V'.code.toByte(),
            'E'.code.toByte()
        )

        assertEquals("wav", ttsReplayAudioExtension(wavHeader))
        assertEquals("mp3", ttsReplayAudioExtension(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun replacesPreviousFormatForSameSegment() = runBlocking {
        val repository = TtsReplayAudioRepository(temporaryFolder.root)
        val wavHeader = byteArrayOf(
            'R'.code.toByte(),
            'I'.code.toByte(),
            'F'.code.toByte(),
            'F'.code.toByte(),
            0,
            0,
            0,
            0,
            'W'.code.toByte(),
            'A'.code.toByte(),
            'V'.code.toByte(),
            'E'.code.toByte()
        )

        repository.saveAudio("chat-1", "message-1", 0, wavHeader).getOrThrow()
        repository.saveAudio("chat-1", "message-1", 0, byteArrayOf(1, 2, 3)).getOrThrow()

        val files = temporaryFolder.root.walkTopDown().filter { it.isFile }.toList()
        assertEquals(1, files.size)
        assertEquals(segmentFileName(0, "mp3"), files.single().name)
    }
}
