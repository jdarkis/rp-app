package com.example.rpapp3.data

import android.content.Context
import android.util.Log
import com.example.rpapp3.data.model.ElevenLabsTTSModels
import com.example.rpapp3.data.model.TTSModel
import com.example.rpapp3.data.model.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ElevenLabsService(private val context: Context) {
    
    private val apiKeyManager = ElevenLabsApiKeyManager.getInstance(context)
    
    companion object {
        private const val BASE_URL = "https://api.elevenlabs.io"
        private const val VOICES_ENDPOINT = "/v2/voices"
        private const val MODELS_ENDPOINT = "/v1/models"
        private const val TTS_ENDPOINT = "/v1/text-to-speech"
        
        @Volatile
        private var INSTANCE: ElevenLabsService? = null
        
        fun getInstance(context: Context): ElevenLabsService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ElevenLabsService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Initialize the service - call this on app startup
     */
    suspend fun initialize() {
        apiKeyManager.initializeDefaults()
    }
    
    /**
     * Get all available voices from ElevenLabs
     */
    suspend fun getVoices(): Result<List<Voice>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest(VOICES_ENDPOINT)
            val voicesResponse = parseVoicesResponse(response)
            Result.success(voicesResponse)
        } catch (e: Exception) {
            Log.e("ElevenLabsService", "Failed to get voices: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    private fun parseVoicesResponse(jsonString: String): List<Voice> {
        val voices = mutableListOf<Voice>()
        try {
            val jsonObject = JSONObject(jsonString)
            val voicesArray = jsonObject.getJSONArray("voices")
            for (i in 0 until voicesArray.length()) {
                val voiceObj = voicesArray.getJSONObject(i)
                val labels = mutableMapOf<String, String>()
                if (voiceObj.has("labels")) {
                    val labelsObj = voiceObj.getJSONObject("labels")
                    labelsObj.keys().forEach { key ->
                        labels[key] = labelsObj.optString(key, "")
                    }
                }
                voices.add(Voice(
                    voiceId = voiceObj.getString("voice_id"),
                    name = voiceObj.getString("name"),
                    previewUrl = voiceObj.optString("preview_url").takeIf { it.isNotBlank() },
                    labels = labels
                ))
            }
        } catch (e: Exception) {
            Log.e("ElevenLabsService", "Failed to parse voices: ${e.message}", e)
        }
        return voices
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
     * @param language Optional language code (ISO 639-1)
     */
    suspend fun textToSpeech(
        text: String,
        voiceId: String,
        modelId: String = ElevenLabsTTSModels.DEFAULT_MODEL_ID,
        language: String? = null
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        textToSpeechWithRetry(text, voiceId, modelId, language)
    }
    
    private suspend fun textToSpeechWithRetry(
        text: String,
        voiceId: String,
        modelId: String,
        language: String?,
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
            
            // Build request body - properly escape JSON special characters
            val escapedText = text
                .replace("\\", "\\\\")  // Escape backslashes first
                .replace("\"", "\\\"")  // Escape quotes
                .replace("\n", "\\n")   // Escape newlines
                .replace("\r", "\\r")   // Escape carriage returns
                .replace("\t", "\\t")   // Escape tabs
            
            val requestBody = buildString {
                append("{")
                append("\"text\":\"$escapedText\"")
                append(",\"model_id\":\"$modelId\"")
                language?.let { append(",\"language_code\":\"$it\"") }
                append("}")
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }
            
            val responseCode = connection.responseCode
            
            if (responseCode == 429 || responseCode == 503) {
                // Rate limit or quota exceeded - try next key
                connection.disconnect()
                apiKeyManager.rotateToNextKey()
                delay(500)
                return textToSpeechWithRetry(text, voiceId, modelId, language, keyAttemptNumber + 1)
            }
            
            if (responseCode != 200) {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                connection.disconnect()
                
                if (apiKeyManager.isQuotaExhaustedError(errorBody)) {
                    apiKeyManager.rotateToNextKey()
                    delay(500)
                    return textToSpeechWithRetry(text, voiceId, modelId, language, keyAttemptNumber + 1)
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
                return textToSpeechWithRetry(text, voiceId, modelId, language, keyAttemptNumber + 1)
            }
            Result.failure(e)
        }
    }
    
    /**
     * Get audio preview URL for a voice (for playback)
     */
    fun getVoicePreviewUrl(voice: Voice): String? {
        return voice.previewUrl
    }
    
    private suspend fun makeRequest(endpoint: String): String {
        val apiKey = apiKeyManager.getCurrentApiKey()
            ?: throw Exception("No ElevenLabs API key available")
        
        return makeRequestWithRetry(endpoint, apiKey, 0)
    }
    
    private suspend fun makeRequestWithRetry(
        endpoint: String, 
        apiKey: String, 
        attemptNumber: Int
    ): String {
        val totalKeys = apiKeyManager.apiKeys.first().size
        
        if (attemptNumber >= totalKeys) {
            throw Exception("All ElevenLabs API keys have exceeded their quota")
        }
        
        val currentKey = if (attemptNumber == 0) apiKey else apiKeyManager.getCurrentApiKey()
            ?: throw Exception("No ElevenLabs API key available")
        
        val url = URL("$BASE_URL$endpoint")
        val connection = url.openConnection() as HttpURLConnection
        
        connection.requestMethod = "GET"
        connection.setRequestProperty("xi-api-key", currentKey)
        connection.setRequestProperty("Accept", "application/json")
        
        val responseCode = connection.responseCode
        
        if (responseCode == 429 || responseCode == 503) {
            connection.disconnect()
            apiKeyManager.rotateToNextKey()
            delay(500)
            return makeRequestWithRetry(endpoint, apiKey, attemptNumber + 1)
        }
        
        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            connection.disconnect()
            
            if (apiKeyManager.isQuotaExhaustedError(errorBody)) {
                apiKeyManager.rotateToNextKey()
                delay(500)
                return makeRequestWithRetry(endpoint, apiKey, attemptNumber + 1)
            }
            
            throw Exception("API request failed: $errorBody")
        }
        
        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        return response
    }
}
