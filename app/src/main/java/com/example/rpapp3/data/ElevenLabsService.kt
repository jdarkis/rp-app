package com.example.rpapp3.data

import android.content.Context
import android.util.Log
import com.example.rpapp3.data.model.ElevenLabsTTSModels
import com.example.rpapp3.data.model.TTSModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ElevenLabsService(private val context: Context) {
    
    private val apiKeyManager = ElevenLabsApiKeyManager.getInstance(context)
    
    companion object {
        private const val BASE_URL = "https://api.elevenlabs.io"
        private const val TTS_ENDPOINT = "/v1/text-to-speech"
        private const val SUBSCRIPTION_ENDPOINT = "/v1/user/subscription"
        private const val CATALOG_CONNECT_TIMEOUT_MS = 10000
        private const val CATALOG_READ_TIMEOUT_MS = 15000
        
        @Volatile
        private var INSTANCE: ElevenLabsService? = null
        
        fun getInstance(context: Context): ElevenLabsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ElevenLabsService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Subscription info data class
     */
    data class SubscriptionInfo(
        val tier: String,
        val characterCount: Int,
        val characterLimit: Int,
        val remainingCharacters: Int
    )
    
    /**
     * Initialize the service - call this on app startup
     */
    suspend fun initialize() {
        apiKeyManager.initializeDefaults()
    }
    
    /**
     * Get subscription info for a specific API key
     * @param apiKey The ElevenLabs API key to check
     * @return Result containing subscription info or error
     */
    suspend fun getSubscriptionInfoForKey(apiKey: String): Result<SubscriptionInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL$SUBSCRIPTION_ENDPOINT")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("xi-api-key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                connection.disconnect()
                return@withContext Result.failure(Exception("Failed to get subscription info: $errorBody"))
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            val jsonObject = JSONObject(response)
            val tier = jsonObject.optString("tier", "unknown")
            val characterCount = jsonObject.optInt("character_count", 0)
            val characterLimit = jsonObject.optInt("character_limit", 0)
            val remaining = characterLimit - characterCount
            
            Result.success(SubscriptionInfo(
                tier = tier,
                characterCount = characterCount,
                characterLimit = characterLimit,
                remainingCharacters = remaining
            ))
        } catch (e: Exception) {
            Log.e("ElevenLabsService", "Failed to get subscription info: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get available TTS models
     * Returns predefined models since API response format may vary
     */
    fun getModels(): List<TTSModel> {
        return ElevenLabsTTSModels.DEFAULT_MODELS
    }
    
    /**
     * Convert text to speech and return audio stream
     * @param text The text to convert
     * @param voiceId The ElevenLabs voice ID
     * @param modelId The TTS model ID (default: eleven_v3)
     */
    suspend fun textToSpeech(
        text: String,
        voiceId: String,
        modelId: String = ElevenLabsTTSModels.DEFAULT_MODEL_ID
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        textToSpeechWithRetry(text, voiceId, modelId)
    }

    internal suspend fun listFreeTierVoices(
        page: Int = 0,
        search: String = ""
    ): Result<ElevenLabsVoicePage> = withContext(Dispatchers.IO) {
        try {
            require(page >= 0) { "Page must not be negative" }
            val defaultVoicesJson = if (page == 0) {
                makeCatalogRequest(buildDefaultVoicesPath(search))
            } else {
                null
            }
            val sharedVoicesJson = makeCatalogRequest(buildSharedVoicesPath(page, search))
            Result.success(
                parseFreeTierVoicePage(
                    defaultVoicesJson = defaultVoicesJson,
                    sharedVoicesJson = sharedVoicesJson,
                    page = page
                )
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("ElevenLabsService", "Failed to load free-tier voice catalog: ${error.message}", error)
            Result.failure(error)
        }
    }
    
    private suspend fun textToSpeechWithRetry(
        text: String,
        voiceId: String,
        modelId: String,
        keyAttemptNumber: Int = 0
    ): Result<ByteArray> {
        val apiKey = apiKeyManager.getCurrentApiKey()
            ?: return Result.failure(Exception("No ElevenLabs API key available"))
        
        val totalKeys = apiKeyManager.apiKeys.first().size
        
        if (keyAttemptNumber >= totalKeys) {
            return Result.failure(Exception("All ElevenLabs API keys have exceeded their quota"))
        }
        
        return try {
            val url = URL("$BASE_URL$TTS_ENDPOINT/$voiceId")
            val connection = url.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("xi-api-key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "audio/mpeg")
            connection.doOutput = true
            
            val requestBody = buildElevenLabsRequestBody(text, modelId)
            
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == 429 || responseCode == 503) {
                // Rate limit or quota exceeded - try next key
                connection.disconnect()
                apiKeyManager.rotateToNextKey()
                delay(500)
                return textToSpeechWithRetry(text, voiceId, modelId, keyAttemptNumber + 1)
            }
            
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                connection.disconnect()
                
                if (apiKeyManager.isQuotaExhaustedError(errorBody)) {
                    apiKeyManager.rotateToNextKey()
                    delay(500)
                    return textToSpeechWithRetry(text, voiceId, modelId, keyAttemptNumber + 1)
                }
                
                return Result.failure(Exception("TTS request failed: $errorBody"))
            }
            
            val audioData = connection.inputStream.readBytes()
            connection.disconnect()
            Result.success(audioData)
            
        } catch (e: Exception) {
            if (apiKeyManager.isQuotaExhaustedError(e.message)) {
                apiKeyManager.rotateToNextKey()
                delay(500)
                return textToSpeechWithRetry(text, voiceId, modelId, keyAttemptNumber + 1)
            }
            Result.failure(e)
        }
    }

    private suspend fun makeCatalogRequest(path: String): String {
        val keys = apiKeyManager.apiKeys.first()
        if (keys.isEmpty()) {
            throw IllegalStateException("No ElevenLabs API key available")
        }

        var lastError: Throwable? = null
        repeat(keys.size) { attempt ->
            val apiKey = apiKeyManager.getCurrentApiKey()
                ?: throw IllegalStateException("No ElevenLabs API key available")
            try {
                return executeCatalogRequest(path, apiKey)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                lastError = error
                val canRetry = attempt + 1 < keys.size && shouldRotateCatalogKey(error)
                if (!canRetry) throw error
                apiKeyManager.rotateToNextKey()
            }
        }
        throw lastError ?: IllegalStateException("Unable to load ElevenLabs voice catalog")
    }

    private fun executeCatalogRequest(path: String, apiKey: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("$BASE_URL$path").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("xi-api-key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = CATALOG_CONNECT_TIMEOUT_MS
            connection.readTimeout = CATALOG_READ_TIMEOUT_MS

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "Unknown error"
                throw ElevenLabsCatalogHttpException(responseCode, errorBody)
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (error: ElevenLabsCatalogHttpException) {
            throw error
        } catch (error: IOException) {
            throw error
        } finally {
            connection?.disconnect()
        }
    }
    
}

internal fun buildElevenLabsRequestBody(text: String, modelId: String): String {
    return buildString {
        append("{\"text\":\"")
        append(escapeJsonString(text))
        append("\",\"model_id\":\"")
        append(escapeJsonString(modelId))
        append("\"}")
    }
}

private fun escapeJsonString(value: String): String {
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
