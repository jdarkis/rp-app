package com.example.rpapp3.data

import android.content.Context
import android.util.Log
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.CharacterUpdateProposal
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.SummaryProposal
import com.example.rpapp3.data.model.World
import com.example.rpapp3.data.model.WorldUpdateProposal
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Result wrapper for story summarization
 */
sealed class SummaryResult {
    data class Success(val proposal: SummaryProposal) : SummaryResult()
    data class Error(val message: String) : SummaryResult()
}

/**
 * Service for AI-powered story summarization and character/world update proposals.
 * Analyzes chat history to identify character development and story progression.
 */
class StorySummarizerService(private val context: Context) {
    
    companion object {
        private const val TAG = "StorySummarizerService"
    }
    
    private var apiKeyManager: ApiKeyManager? = null
    
    init {
        apiKeyManager = ApiKeyManager.getInstance(context)
    }
    
    /**
     * Generate a story summary and propose character/world updates based on chat history.
     */
    suspend fun summarizeStory(
        messages: List<ChatMessage>,
        characters: List<Character>,
        world: World?,
        retryCount: Int = 0,
        lastError: String? = null
    ): SummaryResult = withContext(Dispatchers.IO) {
        
        val maxRetries = 3
        if (retryCount >= maxRetries) {
            return@withContext SummaryResult.Error(
                "Failed after $maxRetries retries. Last error: ${lastError ?: "Unknown"}"
            )
        }
        
        if (messages.isEmpty()) {
            return@withContext SummaryResult.Error("No messages to summarize")
        }
        
        val apiKey = apiKeyManager?.getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext SummaryResult.Error("No API key configured")
        }
        
        val systemPrompt = buildSystemPrompt(characters, world)
        val chatContent = buildChatContent(messages)
        
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topP = 0.95f
                    maxOutputTokens = 16384
                    responseMimeType = "application/json"
                },
                systemInstruction = content { text(systemPrompt) },
                safetySettings = listOf(
                    SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                    SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                    SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                    SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE)
                )
            )
            
            val response = generativeModel.generateContent(chatContent)
            val responseText = response.text?.trim() 
                ?: return@withContext SummaryResult.Error("No response from AI")
            
            Log.d(TAG, "Raw AI response: $responseText")
            
            val proposal = parseResponse(responseText, characters, world)
            SummaryResult.Success(proposal)
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
            Log.e(TAG, "Error summarizing story", e)
            
            val shouldRotateKey = apiKeyManager?.isQuotaError(errorMessage) == true ||
                    errorMessage.lowercase().contains("overloaded") ||
                    errorMessage.lowercase().contains("503") ||
                    errorMessage.lowercase().contains("resource_exhausted")
            
            if (shouldRotateKey) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null && newKey != apiKey) {
                    return@withContext summarizeStory(messages, characters, world, retryCount + 1, errorMessage)
                }
                return@withContext SummaryResult.Error("All API keys exhausted. Error: $errorMessage")
            }
            
            SummaryResult.Error("API Error: $errorMessage")
        }
    }
    
    private fun buildSystemPrompt(characters: List<Character>, world: World?): String {
        return buildString {
            appendLine("You are a story analyst for a roleplay application.")
            appendLine("Your task is to analyze the chat history and provide:")
            appendLine("1. A summary of what happened in the story so far")
            appendLine("2. Analysis of how each character has developed")
            appendLine("3. Suggested updates to character and world descriptions based on story events")
            appendLine()
            appendLine("=== CURRENT CHARACTER DATA ===")
            characters.forEach { char ->
                appendLine()
                appendLine("Character: ${char.name} (ID: ${char.id})")
                appendLine("Current Background: ${char.description.ifBlank { "[empty]" }}")
                appendLine("Current Appearance: ${char.appearance.ifBlank { "[empty]" }}")
                appendLine("Current Personality: ${char.personality.ifBlank { "[empty]" }}")
            }
            appendLine()
            world?.let {
                appendLine("=== CURRENT WORLD DATA ===")
                appendLine("World: ${it.name} (ID: ${it.id})")
                appendLine("Current Description: ${it.description.ifBlank { "[empty]" }}")
                appendLine()
            }
            appendLine("=== RESPONSE FORMAT ===")
            appendLine("Respond with a JSON object in this exact structure:")
            appendLine("""
{
  "storySummary": "A comprehensive summary of the story events so far...",
  "characterUpdates": [
    {
      "characterId": "character_id_here",
      "backgroundNew": "Updated background incorporating story events, or null if no changes",
      "appearanceNew": "Updated appearance if character changed appearance, or null if no changes",
      "personalityNew": "Updated personality if character developed/changed, or null if no changes"
    }
  ],
  "worldUpdate": {
    "descriptionNew": "Updated world description if world changed, or null if no changes"
  }
}
            """.trimIndent())
            appendLine()
            appendLine("IMPORTANT RULES:")
            appendLine("- Only suggest updates if there were SIGNIFICANT changes in the story")
            appendLine("- Preserve the original style and essential information")
            appendLine("- ADD new information rather than replacing everything")
            appendLine("- Set fields to null if no meaningful updates are needed")
            appendLine("- The summary should be 2-4 paragraphs covering key events")
        }
    }
    
    private fun buildChatContent(messages: List<ChatMessage>): String {
        return buildString {
            appendLine("=== CHAT HISTORY TO ANALYZE ===")
            appendLine()
            messages.forEach { msg ->
                val sender = if (msg.isUser) "User" else (msg.characterName ?: "Narrator")
                appendLine("[$sender]: ${msg.text}")
                appendLine()
            }
        }
    }
    
    private fun parseResponse(
        jsonText: String, 
        characters: List<Character>, 
        world: World?
    ): SummaryProposal {
        return try {
            val json = JSONObject(jsonText)
            
            val storySummary = json.optString("storySummary", "No summary generated")
            
            val characterUpdates = mutableListOf<CharacterUpdateProposal>()
            val updatesArray = json.optJSONArray("characterUpdates")
            
            if (updatesArray != null) {
                for (i in 0 until updatesArray.length()) {
                    val updateObj = updatesArray.getJSONObject(i)
                    val charId = updateObj.getString("characterId")
                    val character = characters.find { it.id == charId } ?: continue
                    
                    characterUpdates.add(CharacterUpdateProposal(
                        characterId = charId,
                        characterName = character.name,
                        backgroundOld = character.description,
                        backgroundNew = updateObj.optStringOrNull("backgroundNew"),
                        appearanceOld = character.appearance,
                        appearanceNew = updateObj.optStringOrNull("appearanceNew"),
                        personalityOld = character.personality,
                        personalityNew = updateObj.optStringOrNull("personalityNew")
                    ))
                }
            }
            
            // Add characters with no updates proposed
            characters.filter { char -> characterUpdates.none { it.characterId == char.id } }
                .forEach { char ->
                    characterUpdates.add(CharacterUpdateProposal(
                        characterId = char.id,
                        characterName = char.name,
                        backgroundOld = char.description,
                        backgroundNew = null,
                        appearanceOld = char.appearance,
                        appearanceNew = null,
                        personalityOld = char.personality,
                        personalityNew = null
                    ))
                }
            
            val worldUpdate = if (world != null) {
                val worldObj = json.optJSONObject("worldUpdate")
                WorldUpdateProposal(
                    worldId = world.id,
                    worldName = world.name,
                    descriptionOld = world.description,
                    descriptionNew = worldObj?.optStringOrNull("descriptionNew")
                )
            } else null
            
            SummaryProposal(storySummary, characterUpdates, worldUpdate)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON response", e)
            // Return a basic proposal with the raw text as summary
            SummaryProposal(
                storySummary = jsonText.take(2000),
                characterUpdates = characters.map { char ->
                    CharacterUpdateProposal(
                        characterId = char.id,
                        characterName = char.name,
                        backgroundOld = char.description,
                        backgroundNew = null,
                        appearanceOld = char.appearance,
                        appearanceNew = null,
                        personalityOld = char.personality,
                        personalityNew = null
                    )
                },
                worldUpdateProposal = world?.let {
                    WorldUpdateProposal(it.id, it.name, it.description, null)
                }
            )
        }
    }
    
    // Extension to handle null JSON strings properly
    private fun JSONObject.optStringOrNull(key: String): String? {
        if (this.isNull(key)) return null
        val value = this.optString(key, "")
        return if (value.isBlank() || value == "null") null else value
    }
}
