package com.example.rpapp3.data

import android.content.Context
import com.example.rpapp3.data.model.GeminiTTSModels
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

class GeminiTtsService(private val context: Context) {

    private val apiKeyManager = ApiKeyManager.getInstance(context)

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val CONNECT_TIMEOUT_MS = 10000
        private const val READ_TIMEOUT_MS = 30000

        @Volatile
        private var INSTANCE: GeminiTtsService? = null

        fun getInstance(context: Context): GeminiTtsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GeminiTtsService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun initialize() {
        apiKeyManager.initializeDefaults()
    }

    suspend fun textToSpeech(
        text: String,
        voiceId: String,
        modelId: String = GeminiTTSModels.GEMINI_3_1_FLASH_TTS_PREVIEW.modelId
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        textToSpeechWithRetry(text = text, voiceId = voiceId, modelId = modelId)
    }

    private suspend fun textToSpeechWithRetry(
        text: String,
        voiceId: String,
        modelId: String,
        attemptNumber: Int = 0
    ): Result<ByteArray> {
        val keys = apiKeyManager.apiKeys.first()
        if (keys.isEmpty()) {
            return Result.failure(Exception("No Gemini API key available"))
        }

        val maxAttempts = maxOf(keys.size, 2)
        if (attemptNumber >= maxAttempts) {
            return Result.failure(Exception("All Gemini API keys failed or exceeded their quota"))
        }

        val apiKey = apiKeyManager.getCurrentApiKey()
            ?: return Result.failure(Exception("No Gemini API key available"))

        return try {
            Result.success(executeTextToSpeechRequest(text, voiceId, modelId, apiKey))
        } catch (error: CancellationException) {
            throw error
        } catch (error: GeminiTtsHttpException) {
            val canRetry = shouldRetryGeminiTts(error.responseCode, error.responseBody) &&
                attemptNumber + 1 < maxAttempts
            if (canRetry) {
                apiKeyManager.rotateToNextKey()
                delay(500)
                textToSpeechWithRetry(text, voiceId, modelId, attemptNumber + 1)
            } else {
                Result.failure(Exception(geminiTtsFailureMessage(error.responseCode, error.responseBody)))
            }
        } catch (error: IOException) {
            if (attemptNumber + 1 < maxAttempts) {
                apiKeyManager.rotateToNextKey()
                delay(500)
                textToSpeechWithRetry(text, voiceId, modelId, attemptNumber + 1)
            } else {
                Result.failure(error)
            }
        } catch (error: Exception) {
            if (apiKeyManager.isQuotaExhaustedError(error.message) && attemptNumber + 1 < maxAttempts) {
                apiKeyManager.rotateToNextKey()
                delay(500)
                textToSpeechWithRetry(text, voiceId, modelId, attemptNumber + 1)
            } else {
                Result.failure(error)
            }
        }
    }

    private fun executeTextToSpeechRequest(
        text: String,
        voiceId: String,
        modelId: String,
        apiKey: String
    ): ByteArray {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("$BASE_URL/$modelId:generateContent").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(buildGeminiTtsRequestBody(text, voiceId).toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "Unknown error"
                throw GeminiTtsHttpException(responseCode, errorBody)
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val pcmBytes = extractGeminiPcmAudio(responseBody)
            return wrapPcmAsWav(pcmBytes)
        } finally {
            connection?.disconnect()
        }
    }

    private fun shouldRetryGeminiTts(responseCode: Int, responseBody: String): Boolean {
        return responseCode == 408 ||
            responseCode == 429 ||
            responseCode == 500 ||
            responseCode == 502 ||
            responseCode == 503 ||
            responseCode == 504 ||
            apiKeyManager.isQuotaExhaustedError(responseBody)
    }
}

internal class GeminiTtsHttpException(
    val responseCode: Int,
    val responseBody: String
) : Exception("Gemini TTS request failed ($responseCode): $responseBody")

private val geminiTtsJson = Json {
    ignoreUnknownKeys = true
}

internal fun buildGeminiTtsRequestBody(text: String, voiceId: String): String {
    val prompt = buildGeminiTtsPrompt(text)
    return buildString {
        append("{\"contents\":[{\"parts\":[{\"text\":\"")
        append(escapeGeminiTtsJson(prompt))
        append("\"}]}],\"generationConfig\":{\"responseModalities\":[\"AUDIO\"],")
        append("\"speechConfig\":{\"voiceConfig\":{\"prebuiltVoiceConfig\":{\"voiceName\":\"")
        append(escapeGeminiTtsJson(voiceId))
        append("\"}}}}}")
    }
}

internal fun buildGeminiTtsPrompt(text: String): String {
    return buildString {
        appendLine("Synthesize the following transcript as spoken audio.")
        appendLine("Do not read these instructions aloud. Preserve any bracketed audio tags as delivery directions.")
        appendLine()
        appendLine("### TRANSCRIPT")
        append(text)
    }
}

internal fun extractGeminiPcmAudio(responseBody: String): ByteArray {
    return try {
        val root = geminiTtsJson.parseToJsonElement(responseBody).jsonObject
        val output = ByteArrayOutputStream()
        root.arrayOrNull("candidates")
            ?.mapNotNull { it as? JsonObject }
            ?.forEach { candidate ->
                val parts = candidate.objectOrNull("content")
                    ?.arrayOrNull("parts")
                    .orEmpty()
                parts.mapNotNull { it as? JsonObject }.forEach { part ->
                    val inlineData = part.objectOrNull("inlineData") ?: part.objectOrNull("inline_data")
                    val data = inlineData?.string("data")
                    if (!data.isNullOrBlank()) {
                        output.write(Base64.getMimeDecoder().decode(data))
                    }
                }
            }
        val audio = output.toByteArray()
        if (audio.isEmpty()) {
            throw IllegalArgumentException("No audio data returned by Gemini TTS")
        }
        audio
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (error: Exception) {
        throw IllegalArgumentException("Malformed Gemini TTS response", error)
    }
}

internal fun wrapPcmAsWav(
    pcmBytes: ByteArray,
    sampleRate: Int = 24000,
    channels: Int = 1,
    bitsPerSample: Int = 16
): ByteArray {
    val byteRate = sampleRate * channels * bitsPerSample / 8
    val blockAlign = channels * bitsPerSample / 8
    return ByteArrayOutputStream(44 + pcmBytes.size).apply {
        writeAscii("RIFF")
        writeLittleEndianInt(36 + pcmBytes.size)
        writeAscii("WAVE")
        writeAscii("fmt ")
        writeLittleEndianInt(16)
        writeLittleEndianShort(1)
        writeLittleEndianShort(channels)
        writeLittleEndianInt(sampleRate)
        writeLittleEndianInt(byteRate)
        writeLittleEndianShort(blockAlign)
        writeLittleEndianShort(bitsPerSample)
        writeAscii("data")
        writeLittleEndianInt(pcmBytes.size)
        write(pcmBytes)
    }.toByteArray()
}

internal fun geminiTtsFailureMessage(responseCode: Int, responseBody: String): String {
    val detail = extractGeminiTtsErrorDetail(responseBody)
    return when (responseCode) {
        HttpURLConnection.HTTP_BAD_REQUEST -> "Gemini TTS request is invalid: $detail"
        HttpURLConnection.HTTP_UNAUTHORIZED,
        HttpURLConnection.HTTP_FORBIDDEN -> "Gemini TTS credential was rejected: $detail"
        else -> "Gemini TTS request failed ($responseCode): $detail"
    }
}

private fun extractGeminiTtsErrorDetail(responseBody: String): String {
    return try {
        val root = geminiTtsJson.parseToJsonElement(responseBody).jsonObject
        root.objectOrNull("error")?.string("message") ?: responseBody
    } catch (_: Exception) {
        responseBody
    }.take(500)
}

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}

private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
    write((value shr 16) and 0xff)
    write((value shr 24) and 0xff)
}

private fun ByteArrayOutputStream.writeLittleEndianShort(value: Int) {
    write(value and 0xff)
    write((value shr 8) and 0xff)
}

private fun JsonObject.objectOrNull(key: String): JsonObject? {
    return this[key] as? JsonObject
}

private fun JsonObject.arrayOrNull(key: String): JsonArray? {
    return this[key] as? JsonArray
}

private fun JsonObject.string(key: String): String? {
    return (this[key] as? JsonPrimitive)?.contentOrNull
}

private fun escapeGeminiTtsJson(value: String): String {
    return buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character.code < 0x20) {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
    }
}
