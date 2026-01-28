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
    private val summarizerPromptsRepository = com.example.rpapp3.data.repository.SummarizerPromptsRepository()
    
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
        
        // Fetch custom prompts from repository
        val customPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
        
        val systemPrompt = buildSystemPrompt(characters, world, detailLevel, customPrompts)
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
    
    /**
     * Analyze chat history and propose background update for a specific character.
     * Focuses only on character background/story developments.
     */
    suspend fun analyzeCharacterBackground(
        messages: List<ChatMessage>,
        character: Character,
        retryCount: Int = 0,
        lastError: String? = null
    ): Result<String?> = withContext(Dispatchers.IO) {
        
        val maxRetries = 3
        if (retryCount >= maxRetries) {
            return@withContext Result.failure(
                Exception("Failed after $maxRetries retries. Last error: ${lastError ?: "Unknown"}")
            )
        }
        
        val apiKey = apiKeyManager?.getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No API key configured"))
        }
        
        // Fetch custom prompts
        val customPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
        val customPrompt = customPrompts.characterBackgroundPrompt.takeIf { it.isNotBlank() }
        
        val systemPrompt = if (customPrompt != null) {
            // Use custom prompt with character context
            buildString {
                appendLine("CHARACTER: ${character.name}")
                appendLine("CURRENT BACKGROUND: ${character.description.ifBlank { "[empty]" }}")
                appendLine()
                appendLine(customPrompt)
            }
        } else {
            // Use default prompt
            buildString {
                appendLine("You are analyzing a roleplay chat to update a character's BACKGROUND based on story events.")
                appendLine()
                appendLine("CHARACTER: ${character.name}")
                appendLine("CURRENT BACKGROUND: ${character.description.ifBlank { "[empty]" }}")
                appendLine()
                appendLine("TASK:")
                appendLine("Review the chat history and determine if the character's background should be updated.")
                appendLine("The background includes: backstory, life events, experiences, relationships formed, achievements, etc.")
                appendLine()
                appendLine("RULES:")
                appendLine("1. PRESERVE ALL ORIGINAL CONTENT - Start with the existing background text")
                appendLine("2. APPEND new developments from the story events")
                appendLine("3. Only MODIFY if story events directly contradict existing background")
                appendLine("4. Include significant events, relationship developments, major decisions")
                appendLine("5. Use past tense for completed events")
                appendLine()
                appendLine("RESPONSE FORMAT:")
                appendLine("Return ONLY a JSON object with this structure:")
                appendLine("{")
                appendLine("  \"backgroundNew\": \"Updated background text, or null if no changes needed\"")
                appendLine("}")
                appendLine()
                appendLine("If no meaningful background updates occurred in the chat, return: {\"backgroundNew\": null}")
            }
        }
        
        val chatContent = buildChatContent(messages)
        
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topP = 0.95f
                    maxOutputTokens = 8192
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
                ?: return@withContext Result.failure(Exception("No response from AI"))
            
            val json = JSONObject(responseText)
            val backgroundNew = json.optStringOrNull("backgroundNew")
            
            Result.success(backgroundNew)
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
            Log.e(TAG, "Error analyzing character background", e)
            
            val shouldRotateKey = apiKeyManager?.isQuotaError(errorMessage) == true ||
                    errorMessage.lowercase().contains("overloaded") ||
                    errorMessage.lowercase().contains("503") ||
                    errorMessage.lowercase().contains("resource_exhausted")
            
            if (shouldRotateKey) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null && newKey != apiKey) {
                    return@withContext analyzeCharacterBackground(messages, character, retryCount + 1, errorMessage)
                }
                return@withContext Result.failure(Exception("All API keys exhausted. Error: $errorMessage"))
            }
            
            Result.failure(Exception("API Error: $errorMessage"))
        }
    }
    
    /**
     * Analyze chat history and propose appearance update for a specific character.
     * Focuses only on physical appearance changes.
     */
    suspend fun analyzeCharacterAppearance(
        messages: List<ChatMessage>,
        character: Character,
        retryCount: Int = 0,
        lastError: String? = null
    ): Result<String?> = withContext(Dispatchers.IO) {
        
        val maxRetries = 3
        if (retryCount >= maxRetries) {
            return@withContext Result.failure(
                Exception("Failed after $maxRetries retries. Last error: ${lastError ?: "Unknown"}")
            )
        }
        
        val apiKey = apiKeyManager?.getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No API key configured"))
        }
        
        // Fetch custom prompts
        val customPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
        val customPrompt = customPrompts.characterAppearancePrompt.takeIf { it.isNotBlank() }
        
        val systemPrompt = if (customPrompt != null) {
            // Use custom prompt with character context
            buildString {
                appendLine("CHARACTER: ${character.name}")
                appendLine("CURRENT APPEARANCE: ${character.appearance.ifBlank { "[empty]" }}")
                appendLine()
                appendLine(customPrompt)
            }
        } else {
            // Use default prompt
            buildString {
                appendLine("You are analyzing a roleplay chat to update a character's APPEARANCE based on story events.")
                appendLine()
                appendLine("CHARACTER: ${character.name}")
                appendLine("CURRENT APPEARANCE: ${character.appearance.ifBlank { "[empty]" }}")
                appendLine()
                appendLine("TASK:")
                appendLine("Review the chat history and determine if the character's appearance should be updated.")
                appendLine("Appearance includes: physical looks, clothing, hair, body features, scars, injuries, etc.")
                appendLine()
                appendLine("RULES:")
                appendLine("1. PRESERVE ALL ORIGINAL CONTENT - Start with the existing appearance text")
                appendLine("2. APPEND or UPDATE based on physical changes in the story")
                appendLine("3. Only update if appearance was EXPLICITLY changed (new clothes, haircut, injury, etc.)")
                appendLine("4. Do NOT add generic descriptions - only changes from the story")
                appendLine("5. Be specific about what changed")
                appendLine()
                appendLine("RESPONSE FORMAT:")
                appendLine("Return ONLY a JSON object with this structure:")
                appendLine("{")
                appendLine("  \"appearanceNew\": \"Updated appearance text, or null if no changes needed\"")
                appendLine("}")
                appendLine()
                appendLine("If no appearance changes occurred in the chat, return: {\"appearanceNew\": null}")
            }
        }
        
        val chatContent = buildChatContent(messages)
        
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topP = 0.95f
                    maxOutputTokens = 8192
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
                ?: return@withContext Result.failure(Exception("No response from AI"))
            
            val json = JSONObject(responseText)
            val appearanceNew = json.optStringOrNull("appearanceNew")
            
            Result.success(appearanceNew)
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
            Log.e(TAG, "Error analyzing character appearance", e)
            
            val shouldRotateKey = apiKeyManager?.isQuotaError(errorMessage) == true ||
                    errorMessage.lowercase().contains("overloaded") ||
                    errorMessage.lowercase().contains("503") ||
                    errorMessage.lowercase().contains("resource_exhausted")
            
            if (shouldRotateKey) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null && newKey != apiKey) {
                    return@withContext analyzeCharacterAppearance(messages, character, retryCount + 1, errorMessage)
                }
                return@withContext Result.failure(Exception("All API keys exhausted. Error: $errorMessage"))
            }
            
            Result.failure(Exception("API Error: $errorMessage"))
        }
    }
    
    /**
     * Analyze chat history and propose personality update for a specific character.
     * Focuses only on personality traits and character development.
     */
    suspend fun analyzeCharacterPersonality(
        messages: List<ChatMessage>,
        character: Character,
        retryCount: Int = 0,
        lastError: String? = null
    ): Result<String?> = withContext(Dispatchers.IO) {
        
        val maxRetries = 3
        if (retryCount >= maxRetries) {
            return@withContext Result.failure(
                Exception("Failed after $maxRetries retries. Last error: ${lastError ?: "Unknown"}")
            )
        }
        
        val apiKey = apiKeyManager?.getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No API key configured"))
        }
        
        // Fetch custom prompts
        val customPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
        val customPrompt = customPrompts.characterPersonalityPrompt.takeIf { it.isNotBlank() }
        
        val systemPrompt = if (customPrompt != null) {
            // Use custom prompt with character context
            buildString {
                appendLine("CHARACTER: ${character.name}")
                appendLine("CURRENT PERSONALITY: ${character.personality.ifBlank { "[empty]" }}")
                appendLine()
                appendLine(customPrompt)
            }
        } else {
            // Use default prompt
            buildString {
                appendLine("You are analyzing a roleplay chat to update a character's PERSONALITY based on character development.")
                appendLine()
                appendLine("CHARACTER: ${character.name}")
                appendLine("CURRENT PERSONALITY: ${character.personality.ifBlank { "[empty]" }}")
                appendLine()
                appendLine("TASK:")
                appendLine("Review the chat history and determine if the character's personality should be updated.")
                appendLine("Personality includes: traits, behaviors, values, emotional patterns, growth, etc.")
                appendLine()
                appendLine("RULES:")
                appendLine("1. PRESERVE ALL ORIGINAL CONTENT - Start with the existing personality text")
                appendLine("2. APPEND new traits or growth observed in the story")
                appendLine("3. Note significant emotional development, behavioral changes, or value shifts")
                appendLine("4. Include how relationships have affected their personality")
                appendLine("5. Only update if there was meaningful character development")
                appendLine()
                appendLine("RESPONSE FORMAT:")
                appendLine("Return ONLY a JSON object with this structure:")
                appendLine("{")
                appendLine("  \"personalityNew\": \"Updated personality text, or null if no changes needed\"")
                appendLine("}")
                appendLine()
                appendLine("If no personality development occurred in the chat, return: {\"personalityNew\": null}")
            }
        }
        
        val chatContent = buildChatContent(messages)
        
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topP = 0.95f
                    maxOutputTokens = 8192
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
                ?: return@withContext Result.failure(Exception("No response from AI"))
            
            val json = JSONObject(responseText)
            val personalityNew = json.optStringOrNull("personalityNew")
            
            Result.success(personalityNew)
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
            Log.e(TAG, "Error analyzing character personality", e)
            
            val shouldRotateKey = apiKeyManager?.isQuotaError(errorMessage) == true ||
                    errorMessage.lowercase().contains("overloaded") ||
                    errorMessage.lowercase().contains("503") ||
                    errorMessage.lowercase().contains("resource_exhausted")
            
            if (shouldRotateKey) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null && newKey != apiKey) {
                    return@withContext analyzeCharacterPersonality(messages, character, retryCount + 1, errorMessage)
                }
                return@withContext Result.failure(Exception("All API keys exhausted. Error: $errorMessage"))
            }
            
            Result.failure(Exception("API Error: $errorMessage"))
        }
    }
    
    /**
     * Analyze chat history and propose world description update.
     * Focuses only on world/setting changes and new information.
     */
    suspend fun analyzeWorldDescription(
        messages: List<ChatMessage>,
        world: World,
        retryCount: Int = 0,
        lastError: String? = null
    ): Result<String?> = withContext(Dispatchers.IO) {
        
        val maxRetries = 3
        if (retryCount >= maxRetries) {
            return@withContext Result.failure(
                Exception("Failed after $maxRetries retries. Last error: ${lastError ?: "Unknown"}")
            )
        }
        
        val apiKey = apiKeyManager?.getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(Exception("No API key configured"))
        }
        
        // Fetch custom prompts
        val customPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
        val customPrompt = customPrompts.worldDescriptionPrompt.takeIf { it.isNotBlank() }
        
        val systemPrompt = if (customPrompt != null) {
            // Use custom prompt with world context
            buildString {
                appendLine("WORLD: ${world.name}")
                appendLine("CURRENT DESCRIPTION: ${world.description.ifBlank { "[empty]" }}")
                appendLine()
                appendLine(customPrompt)
            }
        } else {
            // Use default prompt
            buildString {
                appendLine("You are analyzing a roleplay chat to update the WORLD DESCRIPTION based on story events.")
                appendLine()
                appendLine("WORLD: ${world.name}")
                appendLine("CURRENT DESCRIPTION: ${world.description.ifBlank { "[empty]" }}")
                appendLine()
                appendLine("TASK:")
                appendLine("Review the chat history and determine if the world description should be updated.")
                appendLine("World description includes: setting details, locations, environment, world state, major events, etc.")
                appendLine()
                appendLine("CRITICAL FORMAT REQUIREMENT:")
                appendLine("The updated description MUST follow this exact structure:")
                appendLine("1. First: Copy the COMPLETE, UNMODIFIED original description text exactly as provided")
                appendLine("2. Then: Add a separator line: \"--- Story Events ---\"")
                appendLine("3. Finally: Paste the generated story summary directly after the separator")
                appendLine()
                appendLine("RULES:")
                appendLine("1. ALWAYS include the COMPLETE original description first - never summarize or shorten it")
                appendLine("2. Copy the story summary as-is after the separator, do not reformat it")
                appendLine("3. Only include world-relevant events in the summary portion")
                appendLine()
                appendLine("RESPONSE FORMAT:")
                appendLine("Return ONLY a JSON object with this structure:")
                appendLine("{")
                appendLine("  \"descriptionNew\": \"The complete original description + separator + story summary, or null if no changes needed\"")
                appendLine("}")
                appendLine()
                appendLine("If no world/setting changes occurred in the chat, return: {\"descriptionNew\": null}")
            }
        }
        
        val chatContent = buildChatContent(messages)
        
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
                    topP = 0.95f
                    maxOutputTokens = 8192
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
                ?: return@withContext Result.failure(Exception("No response from AI"))
            
            val json = JSONObject(responseText)
            val descriptionNew = json.optStringOrNull("descriptionNew")
            
            Result.success(descriptionNew)
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
            Log.e(TAG, "Error analyzing world description", e)
            
            val shouldRotateKey = apiKeyManager?.isQuotaError(errorMessage) == true ||
                    errorMessage.lowercase().contains("overloaded") ||
                    errorMessage.lowercase().contains("503") ||
                    errorMessage.lowercase().contains("resource_exhausted")
            
            if (shouldRotateKey) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null && newKey != apiKey) {
                    return@withContext analyzeWorldDescription(messages, world, retryCount + 1, errorMessage)
                }
                return@withContext Result.failure(Exception("All API keys exhausted. Error: $errorMessage"))
            }
            
            Result.failure(Exception("API Error: $errorMessage"))
        }
    }
    
    private fun buildSystemPrompt(
        characters: List<Character>, 
        world: World?, 
        detailLevel: SummaryDetailLevel,
        customPrompts: com.example.rpapp3.data.repository.SummarizerPrompts? = null
    ): String {
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
                    // Use custom prompt if set, otherwise use default
                    val customHighDetailPrompt = customPrompts?.highDetailSummaryPrompt?.takeIf { it.isNotBlank() }
                    if (customHighDetailPrompt != null) {
                        appendLine(customHighDetailPrompt)
                    } else {
                        appendLine("Provide an EXTREMELY COMPREHENSIVE chronological fact-based summary with NO LENGTH CONSTRAINTS.")
                        appendLine()
                        appendLine("FORMAT REQUIREMENTS:")
                        appendLine("Organize the summary by chronological periods (Day 1, Day 2, etc. OR Event 1, Event 2, etc.)")
                        appendLine("Under each period, list EVERY fact as a separate quoted item:")
                        appendLine()
                        appendLine("Example format:")
                        appendLine("Day 1:")
                        appendLine("- \"Steponas arrived to the apartment\"")
                        appendLine("- \"The apartment was described as being on the third floor\"")
                        appendLine("- \"The living room had a blue couch and large windows\"")
                        appendLine()
                        appendLine("Day 2:")
                        appendLine("- \"Emily went for a job interview at TechCorp\"")
                        appendLine("- \"Emily was feeling distressed after the job interview\"")
                        appendLine("- \"When Emily came home, Steponas offered emotional support to her\"")
                        appendLine("- \"They talked for an hour about career goals\"")
                        appendLine()
                        appendLine("CONTENT TO CAPTURE:")
                        appendLine("- EVERY notable fact, event, interaction, and development that occurred")
                        appendLine("- ALL character moments, dialogue exchanges, emotional shifts, and relationship changes")
                        appendLine("- EVERY piece of world-building, location detail, and environmental description")
                        appendLine("- Subtle moments like facial expressions, tone changes, internal thoughts mentioned")
                        appendLine("- Specific facts: names mentioned, places visited, objects described, actions taken")
                        appendLine("- Time indicators: morning, evening, next day, etc.")
                        appendLine("- Character states: emotions, fatigue, hunger, injuries, etc.")
                        appendLine()
                        appendLine("GUIDELINES:")
                        appendLine("- Each fact should be a COMPLETE, STANDALONE statement")
                        appendLine("- Use past tense for all facts")
                        appendLine("- Include character names in facts for clarity")
                        appendLine("- Do NOT summarize or condense - list EVERYTHING")
                        appendLine("- The summary should be AS LONG AS NECESSARY to capture everything")
                        appendLine("- Think of this as a detailed chronicle or bullet-point transcript")
                        appendLine("- Even minor details like \"X wore a red shirt\" or \"Y ordered coffee\" should be included")
                        appendLine()
                        appendLine("This summary will be APPENDED DIRECTLY to the world description as a story record.")
                        appendLine("Propose updates for ANY character development or world changes, no matter how minor.")
                    }
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
