package com.example.rpapp3.data

import android.content.Context
import android.util.Log
import com.example.rpapp3.data.model.ElevenLabsTTSModels
import com.example.rpapp3.data.model.PresetVoices
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
        private const val SUBSCRIPTION_ENDPOINT = "/v1/user/subscription"
        private const val SHARED_VOICES_ENDPOINT = "/v1/shared-voices"
        private const val ADD_VOICE_ENDPOINT = "/v1/voices/add"
        
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
     * Shared voice info from Voice Library
     */
    data class SharedVoiceInfo(
        val voiceId: String,
        val publicUserId: String,
        val name: String,
        val previewUrl: String?
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
     * Search for a voice in the ElevenLabs Voice Library
     * @param voiceId The voice ID to search for
     * @return SharedVoiceInfo if found, null otherwise
     */
    private suspend fun searchSharedVoice(voiceId: String): SharedVoiceInfo? {
        return try {
            val apiKey = apiKeyManager.getCurrentApiKey() ?: return null
            val searchUrl = URL("$BASE_URL$SHARED_VOICES_ENDPOINT?search=$voiceId&page_size=5")
            val connection = searchUrl.openConnection() as HttpURLConnection
            
            connection.requestMethod = "GET"
            connection.setRequestProperty("xi-api-key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                connection.disconnect()
                return null
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            val jsonObject = JSONObject(response)
            val voicesArray = jsonObject.optJSONArray("voices") ?: return null
            
            // Find the matching voice by voice_id
            for (i in 0 until voicesArray.length()) {
                val voiceObj = voicesArray.getJSONObject(i)
                if (voiceObj.getString("voice_id") == voiceId) {
                    return SharedVoiceInfo(
                        voiceId = voiceObj.getString("voice_id"),
                        publicUserId = voiceObj.getString("public_owner_id"),
                        name = voiceObj.getString("name"),
                        previewUrl = voiceObj.optString("preview_url").takeIf { it.isNotBlank() }
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e("ElevenLabsService", "Failed to search shared voices: ${e.message}", e)
            null
        }
    }
    
    /**
     * Add a shared voice from the Voice Library to the user's account
     * @param sharedVoice The shared voice info (from searchSharedVoice)
     * @return true if successfully added, false otherwise
     */
    private suspend fun addSharedVoice(sharedVoice: SharedVoiceInfo): Boolean {
        return try {
            val apiKey = apiKeyManager.getCurrentApiKey() ?: return false
            val addUrl = URL("$BASE_URL$ADD_VOICE_ENDPOINT/${sharedVoice.publicUserId}/${sharedVoice.voiceId}")
            val connection = addUrl.openConnection() as HttpURLConnection
            
            connection.requestMethod = "POST"
            connection.setRequestProperty("xi-api-key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            // Request body with the voice name
            val requestBody = """{"new_name":"${sharedVoice.name}"}"""
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            if (responseCode == 200) {
                Log.i("ElevenLabsService", "Successfully added voice '${sharedVoice.name}' to account")
                true
            } else {
                Log.w("ElevenLabsService", "Failed to add voice '${sharedVoice.name}': HTTP $responseCode")
                false
            }
        } catch (e: Exception) {
            Log.e("ElevenLabsService", "Failed to add shared voice: ${e.message}", e)
            false
        }
    }
    
    /**
     * Ensures preset voices are added to the user's account if missing.
     * Searches the Voice Library and adds any missing preset voices.
     * @param existingVoiceIds Set of voice IDs already in the user's account
     * @return true if any voices were added
     */
    private suspend fun ensurePresetVoicesInAccount(existingVoiceIds: Set<String>): Boolean {
        var anyAdded = false
        
        for (preset in PresetVoices.PRESET_VOICES) {
            if (preset.voiceId !in existingVoiceIds) {
                Log.i("ElevenLabsService", "Preset voice '${preset.name}' not in account, searching Voice Library...")
                
                val sharedVoice = searchSharedVoice(preset.voiceId)
                if (sharedVoice != null) {
                    val added = addSharedVoice(sharedVoice)
                    if (added) {
                        anyAdded = true
                    }
                } else {
                    Log.w("ElevenLabsService", "Preset voice '${preset.name}' not found in Voice Library")
                }
            }
        }
        
        return anyAdded
    }
    
    /**
     * Get all available voices from ElevenLabs.
     * Merges preset custom voices with API results, with presets appearing first.
     * Automatically adds missing preset voices from the Voice Library.
     */
    suspend fun getVoices(): Result<List<Voice>> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest(VOICES_ENDPOINT)
            var apiVoices = parseVoicesResponse(response)
            
            // Get IDs of voices already in user's account
            val existingVoiceIds = apiVoices.map { it.voiceId }.toSet()
            
            // Auto-add missing preset voices from Voice Library
            val voicesAdded = ensurePresetVoicesInAccount(existingVoiceIds)
            
            // If voices were added, re-fetch to get the updated list with preview URLs
            if (voicesAdded) {
                Log.i("ElevenLabsService", "Voices were added, re-fetching voice list...")
                val refreshedResponse = makeRequest(VOICES_ENDPOINT)
                apiVoices = parseVoicesResponse(refreshedResponse)
            }
            
            // Get preset voice IDs to filter out duplicates from API response
            val presetVoiceIds = PresetVoices.PRESET_VOICES.map { it.voiceId }.toSet()
            
            // Update preset voices with actual data from API if available
            val updatedPresetVoices = PresetVoices.PRESET_VOICES.map { preset ->
                val apiMatch = apiVoices.find { it.voiceId == preset.voiceId }
                if (apiMatch != null) {
                    // Use preview URL and labels from API
                    preset.copy(
                        previewUrl = apiMatch.previewUrl ?: preset.previewUrl,
                        labels = preset.labels + apiMatch.labels
                    )
                } else {
                    preset
                }
            }
            
            val filteredApiVoices = apiVoices.filter { it.voiceId !in presetVoiceIds }
            
            // Combine preset voices (at the top) with API voices
            val allVoices = updatedPresetVoices + filteredApiVoices
            
            Result.success(allVoices)
        } catch (e: Exception) {
            Log.e("ElevenLabsService", "Failed to get voices: ${e.message}", e)
            // Even if API fails, return preset voices
            Result.success(PresetVoices.PRESET_VOICES)
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
