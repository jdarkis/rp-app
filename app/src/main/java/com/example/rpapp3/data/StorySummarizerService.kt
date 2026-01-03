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
 * Detail level for story summarization
 */
enum class SummaryDetailLevel {
    LOW,    // Only major events and significant changes
    MEDIUM, // Balanced summary with important developments
    HIGH    // Detailed summary including minor events
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
        detailLevel: SummaryDetailLevel = SummaryDetailLevel.MEDIUM,
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
        
        val systemPrompt = buildSystemPrompt(characters, world, detailLevel)
        val chatContent = buildChatContent(messages)
        
        try {
            // Adjust max tokens based on detail level
            val maxTokens = when (detailLevel) {
                SummaryDetailLevel.LOW -> 8192
                SummaryDetailLevel.MEDIUM -> 16384
                SummaryDetailLevel.HIGH -> 65536 // Much higher limit for comprehensive summaries
            }
            
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topP = 0.95f
                    maxOutputTokens = maxTokens
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
                    return@withContext summarizeStory(messages, characters, world, detailLevel, retryCount + 1, errorMessage)
                }
                return@withContext SummaryResult.Error("All API keys exhausted. Error: $errorMessage")
            }
            
            SummaryResult.Error("API Error: $errorMessage")
        }
    }
    
    private fun buildSystemPrompt(characters: List<Character>, world: World?, detailLevel: SummaryDetailLevel): String {
        return buildString {
            appendLine("You are a story analyst for a roleplay application.")
            appendLine("Your task is to analyze the chat history and provide:")
            appendLine("1. A summary of what happened in the story so far")
            appendLine("2. Analysis of how each character has developed")
            appendLine("3. Suggested updates to character and world descriptions based on story events")
            appendLine()
            
            // Detail level instructions
            appendLine("=== DETAIL LEVEL: ${detailLevel.name} ===")
            when (detailLevel) {
                SummaryDetailLevel.LOW -> {
                    appendLine("Focus ONLY on MAJOR events and significant turning points.")
                    appendLine("Ignore minor interactions and small talk.")
                    appendLine("Summary should be 1-2 paragraphs maximum.")
                    appendLine("Only propose character/world updates for dramatic changes.")
                }
                SummaryDetailLevel.MEDIUM -> {
                    appendLine("Provide a balanced summary covering important developments.")
                    appendLine("Include key events and notable character moments.")
                    appendLine("Summary should be 2-3 paragraphs.")
                    appendLine("Propose updates for meaningful character or world developments.")
                }
                SummaryDetailLevel.HIGH -> {
                    appendLine("Provide an EXTREMELY COMPREHENSIVE and DETAILED summary with NO LENGTH CONSTRAINTS.")
                    appendLine("Include EVERY notable fact, event, interaction, and development that occurred.")
                    appendLine("Document ALL character moments, dialogue exchanges, emotional shifts, and relationship changes.")
                    appendLine("Capture every piece of world-building, location detail, and environmental description.")
                    appendLine("Include subtle moments like facial expressions, tone changes, internal thoughts mentioned.")
                    appendLine("List specific facts: names mentioned, places visited, objects described, actions taken.")
                    appendLine("Organize chronologically but ensure NOTHING is omitted.")
                    appendLine("The summary should be AS LONG AS NECESSARY to capture everything - do not summarize or condense.")
                    appendLine("Think of this as a detailed chronicle or transcript summary, not a brief overview.")
                    appendLine("Propose updates for ANY character development or world changes, no matter how minor.")
                }
            }
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
            appendLine("CRITICAL RULES FOR UPDATES:")
            appendLine("1. PRESERVE ALL ORIGINAL INFORMATION - Never remove or lose existing content")
            appendLine("2. APPEND new story developments to the existing text, don't replace it")
            appendLine("3. Start each update by copying the original text, then add new information at the end")
            appendLine("4. Only MODIFY existing text if the story directly contradicted or changed that specific detail")
            appendLine("5. Format: '[Original content...] [New additions from story events...]'")
            appendLine()
            appendLine("EXAMPLE:")
            appendLine("- Original: 'A brave knight from the northern kingdom.'")
            appendLine("- After story events where the knight saved a village:")
            appendLine("- Updated: 'A brave knight from the northern kingdom. Has since proven their valor by saving the village of Millbrook from bandits.'")
            appendLine()
            appendLine("OTHER RULES:")
            appendLine("- Only suggest updates if there were SIGNIFICANT story developments")
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
