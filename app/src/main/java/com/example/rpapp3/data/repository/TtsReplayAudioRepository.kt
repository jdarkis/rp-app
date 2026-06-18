package com.example.rpapp3.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64

class TtsReplayAudioRepository(
    private val rootDir: File
) {
    suspend fun saveAudio(
        chatId: String,
        messageId: String,
        segmentIndex: Int,
        audioBytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val messageDir = messageDirectory(chatId, messageId)
            if (!messageDir.exists()) {
                messageDir.mkdirs()
            }

            deleteSegmentFiles(messageDir, segmentIndex)
            val file = File(
                messageDir,
                segmentFileName(segmentIndex, ttsReplayAudioExtension(audioBytes))
            )
            file.writeBytes(audioBytes)

            Result.success(file.toURI().toString())
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun loadAudioUrisForMessage(
        chatId: String,
        messageId: String
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        val messageDir = messageDirectory(chatId, messageId)
        if (!messageDir.exists()) return@withContext emptyMap()

        messageDir.listFiles()
            .orEmpty()
            .mapNotNull { file ->
                segmentIndexFromFileName(file.name)?.let { segmentIndex ->
                    segmentIndex to file
                }
            }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (segmentIndex, files) ->
                files.maxByOrNull { it.lastModified() }?.let { file ->
                    segmentIndex to file.toURI().toString()
                }
            }
            .toMap()
    }

    suspend fun deleteAudioForMessage(
        chatId: String,
        messageId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            messageDirectory(chatId, messageId).deleteRecursively()
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun deleteAudioForMessages(
        chatId: String,
        messageIds: Collection<String>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            messageIds.forEach { messageId ->
                messageDirectory(chatId, messageId).deleteRecursively()
            }
            Result.success(Unit)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun messageDirectory(chatId: String, messageId: String): File {
        return File(File(rootDir, ttsReplayAudioToken(chatId)), ttsReplayAudioToken(messageId))
    }

    private fun deleteSegmentFiles(messageDir: File, segmentIndex: Int) {
        messageDir.listFiles()
            .orEmpty()
            .filter { segmentIndexFromFileName(it.name) == segmentIndex }
            .forEach { it.delete() }
    }
}

internal fun ttsReplayAudioExtension(audioBytes: ByteArray): String {
    return if (
        audioBytes.size >= 12 &&
        audioBytes[0] == 'R'.code.toByte() &&
        audioBytes[1] == 'I'.code.toByte() &&
        audioBytes[2] == 'F'.code.toByte() &&
        audioBytes[3] == 'F'.code.toByte() &&
        audioBytes[8] == 'W'.code.toByte() &&
        audioBytes[9] == 'A'.code.toByte() &&
        audioBytes[10] == 'V'.code.toByte() &&
        audioBytes[11] == 'E'.code.toByte()
    ) {
        "wav"
    } else {
        "mp3"
    }
}

internal fun ttsReplayAudioToken(value: String): String {
    val bytes = value.toByteArray(Charsets.UTF_8)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

internal fun segmentFileName(segmentIndex: Int, extension: String): String {
    return "segment_$segmentIndex.$extension"
}

private fun segmentIndexFromFileName(fileName: String): Int? {
    return Regex("""segment_(\d+)\.(mp3|wav)""")
        .matchEntire(fileName)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
}
