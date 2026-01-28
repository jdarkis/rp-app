package com.example.rpapp3.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class InworldService(private val context: Context) {
    
    private val apiKeyManager = InworldApiKeyManager.getInstance(context)
    
    companion object {
        private const val BASE_URL = "https://api.inworld.ai/v1"
        // Using Studio API for listing, but for interaction we might need a different one?
        // Assuming Studio API allows simpleSendText for testing/interaction.
        
        @Volatile
        private var INSTANCE: InworldService? = null
        
        fun getInstance(context: Context): InworldService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InworldService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Cache access token
    private var accessToken: String? = null
    private var tokenExpiration: Long = 0
    
    // Cache for workspace ID map (Character ID -> Workspace ID)
    // We need this because we need workspace ID to call simpleSendText
    private val characterWorkspaceMap = mutableMapOf<String, String>()
    
    /**
     * Initialize the service
     */
    suspend fun initialize() {
        apiKeyManager.initializeDefaults()
    }
    
    /**
     * Get access token using the API key (Basic Auth)
     */
    private suspend fun getAccessToken(): String {
        // storage for current key attempt
        // For simplicity, we just use the current key
        val apiKey = apiKeyManager.getCurrentApiKey() 
            ?: throw Exception("No Inworld API key available")
        
        if (accessToken != null && System.currentTimeMillis() < tokenExpiration) {
            return accessToken!!
        }
        
        // The apiKey stored is expected to be the Base64 "key:secret" string as provided by user
        // Or "key:secret" raw? The user provided Base64.
        // Let's assume the stored key is the Base64 string to be put in Auth header.
        // If the user inputs "key:secret", we should base64 encode it.
        // But the user prompt said "Here's the api key (base 64): ..."
        // So we assume the stored key is ALREADY base64 or suitable for Basic Auth.
        // PRO TIP: Basic Auth header is "Basic <base64(user:pass)>".
        
        // Verify if the key needs "Basic " prefix. 
        val authHeaderValue = if (apiKey.startsWith("Basic ")) apiKey else "Basic $apiKey"
        
        // Note: Inworld Auth endpoint might be slightly different.
        // Actually, for Studio API interactions, many endpoints use the Basic Auth directly.
        // But getting a session token is better.
        // Let's try to just use Basic Auth for requests if possible, or get a token.
        // Inworld V1 typically uses "authorization: Basic ..." to get a token from /v1/auth (or /studio/v1/auth/token ?)
        
        // Let's try to get a Session Token for the workspace interactions.
        // However, generic "studio" interactions often work with the keys directly.
        // To be safe, I'll return the apiKey to be dealing with Basic Auth directly for now for listing.
        
        // Wait, for simpleSendText, we usually need a session.
        // But let's assume valid Basic Auth works for management APIs.
        
        return authHeaderValue
    }
    
    /**
     * Get available voices from Inworld using the Voices API
     */
    suspend fun getVoices(): Result<List<Voice>> = withContext(Dispatchers.IO) {
        try {
            val authHeader = getAccessToken()
            
            // Use the Voices API without workspace prefix to list voices for the workspace tied to the API key
            val voicesUrl = URL("https://api.inworld.ai/voices/v1/voices")
            Log.d("InworldService", "Fetching voices from: $voicesUrl")
            val conn = voicesUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("Accept", "application/json")
            
            val responseCode = conn.responseCode
            Log.d("InworldService", "Voices response code: $responseCode")
            
            if (responseCode != 200) {
                val errorText = conn.errorStream?.bufferedReader()?.readText() ?: "No error body"
                Log.e("InworldService", "Failed to list voices: $responseCode, Error: $errorText")
                return@withContext Result.failure(Exception("Failed to list voices: $responseCode. $errorText"))
            }
            
            val response = conn.inputStream.bufferedReader().readText()
            Log.d("InworldService", "Voices response received")
            val resultObj = JSONObject(response)
            val voicesArray = resultObj.optJSONArray("voices") ?: JSONArray()
            
            val allVoices = mutableListOf<Voice>()
            
            for (i in 0 until voicesArray.length()) {
                val voiceObj = voicesArray.getJSONObject(i)
                val displayName = voiceObj.optString("displayName", "Unnamed Voice")
                val voiceId = voiceObj.optString("voiceId").takeIf { !it.isNullOrBlank() } 
                    ?: voiceObj.getString("name") // Fallback to name if voiceId missing
                
                // Extract metadata if available
                val labels = mutableMapOf<String, String>()
                labels["gender"] = voiceObj.optString("gender", "unknown")
                labels["age"] = voiceObj.optString("age", "unknown")
                
                // New fields from user request
                val langCode = voiceObj.optString("langCode")
                if (langCode.isNotEmpty()) {
                    labels["language"] = langCode
                } else {
                    // Fallback to old languages array if generic one used previously
                    val languages = voiceObj.optJSONArray("languages")
                    if (languages != null && languages.length() > 0) {
                        labels["language"] = languages.getString(0)
                    }
                }
                
                val description = voiceObj.optString("description")
                if (description.isNotEmpty()) {
                    labels["description"] = description
                }
                
                val tagsArray = voiceObj.optJSONArray("tags")
                if (tagsArray != null && tagsArray.length() > 0) {
                    val tagsList = mutableListOf<String>()
                    for (j in 0 until tagsArray.length()) {
                        tagsList.add(tagsArray.getString(j))
                    }
                    labels["tags"] = tagsList.joinToString(", ")
                }
                
                allVoices.add(Voice(
                    voiceId = voiceId,
                    name = displayName,
                    previewUrl = null,
                    labels = labels,
                    source = VoiceSource.INWORLD
                ))
            }
            
            Result.success(allVoices)
        } catch (e: Exception) {
            Log.e("InworldService", "Error getting voices", e)
            Result.failure(e)
        }
    }
    
    /**
     * Convert text to speech using Inworld Voices API
     */
    suspend fun textToSpeech(text: String, voiceId: String, modelId: String? = null): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val authHeader = getAccessToken()
            
            // Check if it's a character or a voice
            val isCharacter = voiceId.contains("/characters/")
            
            // Start with the base TTS URL (using 'synthesize' for non-streaming response)
            // Documentation shows ':stream', but we want a single byte array, so we try ':synthesize' 
            // If synthesis is not available on this endpoint, we might need to use stream and buffer it.
            // Let's stick thereto the docs: often 'stream' endpoint works for non-streaming if we just read it all.
            // But usually there is a batch/synthesize endpoint.
            // Based on typical Inworld/Google APIs, if ':stream' is the only one, we use that.
            // However, the user provided 'voice:stream'. Let's try to match the pattern but maybe without stream 
            // if we want a single block. But maybe 'voice:synthesize' exists?
            // Let's try the exact URL structure from the user example but with 'synthesize' if possible, 
            // or just use 'stream' and read the full stream.
            // Safer bet: use the EXACT URL structure the user gave (voice:stream) but read the whole stream.
            // UPDATE: Typically standard HTTP clients buffering input stream effectively "wait" for the stream.
            
            // Let's assume voice:synthesize exists similar to other APIs, or use voice:stream and read all bytes.
            // The 404s suggest the previous URL structure was wrong.
            // The provided example uses `https://api.inworld.ai/tts/v1/voice:stream`.
            // We'll use `https://api.inworld.ai/tts/v1/voice:synthesize` hoping it exists for non-streaming,
            // OR just use `...:stream` and read fully. Let's try `synthesize` first as it's standard for atomic requests.
            // If that fails, we can fallback or the user can correct us.
            
            // Actually, let's look at the structure: "tts/v1/voice" is the RESOURCE, and ":stream" is the METHOD.
            // The "voiceId" is passed in the BODY.
            
            val url = URL("https://api.inworld.ai/tts/v1/voice:stream")
            
            Log.d("InworldService", "Calling TTS at: $url")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val jsonBody = JSONObject()
            jsonBody.put("text", text)
            
            // Extract pure voice ID if it contains paths (like voices/Hades -> Hades) 
            // since the example shows just "Dennis"
            val simpleVoiceId = voiceId.substringAfterLast("/")
            jsonBody.put("voiceId", simpleVoiceId)
            
            jsonBody.put("modelId", modelId ?: "inworld-tts-1.5-max")
            // Include timestampType as per doc example
            jsonBody.put("timestampType", "WORD") 
            
            conn.outputStream.write(jsonBody.toString().toByteArray())
            
            if (conn.responseCode != 200) {
                val error = conn.errorStream?.bufferedReader()?.readText()
                Log.e("InworldService", "TTS Failed: ${conn.responseCode}, Error: $error")
                return@withContext Result.failure(Exception("Inworld TTS failed ($url): $error"))
            }
            
            if (isCharacter) {
                // Character response parsing (as before)
                val response = conn.inputStream.bufferedReader().readText()
                val responseJson = JSONObject(response)
                val audioBase64 = responseJson.optString("audio")
                if (audioBase64.isNotEmpty()) {
                    return@withContext Result.success(Base64.decode(audioBase64, Base64.DEFAULT))
                }
                Result.failure(Exception("No audio data in character response"))
            } else {
                // Voice API synthesis
                Log.d("InworldService", "Response Content-Type: ${conn.contentType}, Length: ${conn.contentLength}")
                
                // Read the full response as text first, since it's JSON (application/json)
                val responseString = conn.inputStream.bufferedReader().readText()
                Log.d("InworldService", "Read ${responseString.length} chars from stream")
                
                // The response is likely a stream of JSON objects (NDJSON) or a single JSON wrapper
                // We need to parse it and extract the audio chunks (Base64)
                
                val combinedAudio = java.io.ByteArrayOutputStream()
                
                // Split by newlines to handle streaming JSON (NDJSON)
                val lines = responseString.split("\n")
                
                var chunkCount = 0
                
                for (line in lines) {
                    if (line.isBlank()) continue
                    
                    try {
                        val jsonObject = JSONObject(line)
                        
                        // Structure might be { "audioChunk": "base64..." } or { "packet": { "audioChunk": "..." } }
                        // NEW FINDING: { "result": { "audioContent": "base64..." } }
                        var audioBase64: String? = null
                        
                        if (jsonObject.has("result")) {
                            val resultObj = jsonObject.getJSONObject("result")
                            if (resultObj.has("audioContent")) {
                                audioBase64 = resultObj.getString("audioContent")
                            }
                        } else if (jsonObject.has("audioChunk")) {
                            audioBase64 = jsonObject.getString("audioChunk")
                        } else if (jsonObject.has("audio")) {
                            val audioObj = jsonObject.get("audio")
                            if (audioObj is String) {
                                audioBase64 = audioObj
                            } else if (audioObj is JSONObject && audioObj.has("chunk")) {
                                audioBase64 = audioObj.getString("chunk")
                            }
                        }
                        
                        if (!audioBase64.isNullOrBlank()) {
                            val decodedBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                            combinedAudio.write(decodedBytes)
                            chunkCount++
                        }
                    } catch (e: Exception) {
                        Log.w("InworldService", "Failed to parse JSON line: ${e.message}")
                    }
                }
                
                val finalAudioBytes = combinedAudio.toByteArray()
                Log.d("InworldService", "Extracted $chunkCount chunks, total audio size: ${finalAudioBytes.size} bytes")
                
                if (finalAudioBytes.isNotEmpty()) {
                    Result.success(finalAudioBytes)
                } else {
                    // Fallback: If parsing failed effectively but we have data, maybe it was raw bytes?
                    // But Content-Type was application/json. 
                    // Let's log a snippet of the response to help debug if this fails.
                    Log.e("InworldService", "Failed to extract audio from JSON. First 500 chars: ${responseString.take(500)}")
                    Result.failure(Exception("No audio data extracted from JSON stream"))
                }
            }
        } catch (e: Exception) {
            Log.e("InworldService", "TTS Failed", e)
            Result.failure(e)
        }
    }
}
