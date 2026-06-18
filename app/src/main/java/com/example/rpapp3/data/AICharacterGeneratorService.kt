package com.example.rpapp3.data

import android.content.Context
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.World
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionCallingConfig
import com.google.ai.client.generativeai.type.ToolConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Data class representing an AI-generated character before it's saved
 */
data class GeneratedCharacter(
    val name: String,
    val description: String,
    val appearance: String,
    val personality: String,
    val systemInstructions: String
)

/**
 * Result wrapper for character generation
 */
sealed class GenerationResult {
    data class Success(val characters: List<GeneratedCharacter>) : GenerationResult()
    data class Error(val message: String) : GenerationResult()
}

/**
 * Service class for AI-powered character generation using Gemini API.
 * Uses context from worlds and chat sessions to generate character proposals.
 */
class AICharacterGeneratorService(private val context: Context) {
    
    private var apiKeyManager: ApiKeyManager? = null
    
    init {
        apiKeyManager = ApiKeyManager.getInstance(context)
    }
    
    /**
     * Extract characters mentioned in provided context
     * 
     * @param worldDescription Optional world description for context
     * @param aiInstructions Optional AI instructions from the world
     * @param chatMessages Optional list of chat messages for context
     * @param additionalPrompt Optional user prompt for character preferences
     */
    suspend fun generateCharacters(
        worldDescription: String? = null,
        aiInstructions: String? = null,
        chatMessages: List<ChatMessage>? = null,
        additionalPrompt: String? = null
    ): GenerationResult = withContext(Dispatchers.IO) {
        
        val apiKey = apiKeyManager?.getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext GenerationResult.Error("No API key configured. Please add one in Settings.")
        }
        
        val systemPrompt = buildSystemPrompt()
        val userPrompt = buildCharacterExtractionUserPrompt(
            worldDescription = worldDescription,
            aiInstructions = aiInstructions,
            chatMessages = chatMessages,
            additionalPrompt = additionalPrompt
        )
        
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 1.0f
                    topP = 0.95f
                    maxOutputTokens = 8192
                },
                systemInstruction = content { text(systemPrompt) }
            )
            
            val response = generativeModel.generateContent(userPrompt)
            val responseText = response.text ?: return@withContext GenerationResult.Error("No response from AI")
            
            parseCharactersFromResponse(responseText)
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error occurred"
            
            // Check for quota errors and try to rotate keys
            if (apiKeyManager?.isQuotaError(errorMessage) == true) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null) {
                    // Retry with new key
                    return@withContext generateCharacters(
                        worldDescription, aiInstructions, chatMessages, additionalPrompt
                    )
                }
                return@withContext GenerationResult.Error("All API keys have exceeded quota. Please wait or add new keys.")
            }
            
            GenerationResult.Error(errorMessage)
        }
    }
    
    private fun buildSystemPrompt(): String {
        return buildString {
            appendLine("You are a character extraction assistant for roleplay scenarios.")
            appendLine("Your task is to identify and extract characters that are ALREADY MENTIONED in the provided context.")
            appendLine()
            appendLine("CRITICAL RULES:")
            appendLine("1. ONLY extract characters that are explicitly mentioned or named in the context")
            appendLine("2. DO NOT invent or create new characters")
            appendLine("3. ONLY include information that is explicitly stated in the context")
            appendLine("4. If a field is not mentioned in the context, use 'Not specified in context'")
            appendLine("5. Do not embellish or add details beyond what the context provides")
            appendLine()
            appendLine("You MUST respond with ONLY a valid JSON array containing the extracted character(s).")
            appendLine("Each character object must have exactly these fields:")
            appendLine("- \"name\": The character's name as mentioned in context (string)")
            appendLine("- \"description\": Background/role ONLY from context (string, or 'Not specified in context')")
            appendLine("- \"appearance\": Physical description ONLY from context (string, or 'Not specified in context')")
            appendLine("- \"personality\": Personality traits ONLY from context (string, or 'Not specified in context')")
            appendLine("- \"systemInstructions\": How to portray based on context behavior (string)")
            appendLine()
            appendLine("Example response format:")
            appendLine("""[
  {
    "name": "Marcus",
    "description": "A tavern keeper mentioned in the story",
    "appearance": "Not specified in context",
    "personality": "Described as gruff but helpful when the protagonist needed directions",
    "systemInstructions": "Speak in short, direct sentences as shown in the dialogue"
  }
]""")
            appendLine()
            appendLine("If NO characters are mentioned in the context, respond with an empty array: []")
            appendLine("IMPORTANT: Respond with ONLY the JSON array, no other text or formatting.")
        }
    }
    
    private fun parseCharactersFromResponse(responseText: String): GenerationResult {
        return try {
            // Try to extract JSON from the response (handle markdown code blocks)
            val jsonText = extractJsonFromResponse(responseText)
            
            val jsonArray = JSONArray(jsonText)
            val characters = mutableListOf<GeneratedCharacter>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                characters.add(
                    GeneratedCharacter(
                        name = obj.optString("name", "Unnamed Character"),
                        description = obj.optString("description", ""),
                        appearance = obj.optString("appearance", ""),
                        personality = obj.optString("personality", ""),
                        systemInstructions = obj.optString("systemInstructions", "")
                    )
                )
            }
            
            if (characters.isEmpty()) {
                GenerationResult.Error("No characters could be parsed from the response")
            } else {
                GenerationResult.Success(characters)
            }
            
        } catch (e: JSONException) {
            GenerationResult.Error("Failed to parse AI response: ${e.message}")
        }
    }
    
    private fun extractJsonFromResponse(response: String): String {
        // First, try to find JSON array directly
        val trimmed = response.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return trimmed
        }
        
        // Try to extract from markdown code block
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val match = codeBlockPattern.find(response)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        
        // Try to find array brackets anywhere in the response
        val startIdx = response.indexOf('[')
        val endIdx = response.lastIndexOf(']')
        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            return response.substring(startIdx, endIdx + 1)
        }
        
        // Return as-is and let JSON parser handle the error
        return response
    }
}

internal fun buildCharacterExtractionUserPrompt(
    worldDescription: String?,
    aiInstructions: String?,
    chatMessages: List<ChatMessage>?,
    additionalPrompt: String?
): String {
    return buildString {
        appendLine("Extract all characters that are mentioned in the following context.")
        appendLine("Remember: ONLY include characters that are explicitly named or referenced.")
        appendLine()

        if (!worldDescription.isNullOrBlank()) {
            appendLine("=== WORLD CONTEXT ===")
            appendLine(worldDescription)
            appendLine()
        }

        if (!aiInstructions.isNullOrBlank()) {
            appendLine("=== ROLEPLAY STYLE/INSTRUCTIONS ===")
            appendLine(aiInstructions)
            appendLine()
        }

        if (!chatMessages.isNullOrEmpty()) {
            appendLine("=== STORY/CHAT CONTEXT ===")
            appendLine("Extract characters from this roleplay session:")
            appendLine()
            sanitizeChatHistoryForAiContext(chatMessages).takeLast(50).forEach { msg ->
                val speaker = if (msg.isUser) "User" else (msg.characterName ?: "Narrator")
                appendLine("[$speaker]: ${msg.text}")
            }
            appendLine()
        }

        if (!additionalPrompt.isNullOrBlank()) {
            appendLine("=== ADDITIONAL INSTRUCTIONS ===")
            appendLine(additionalPrompt)
            appendLine()
        }

        appendLine("Based on the above context, extract all mentioned characters.")
        appendLine("For each character, only include information that is EXPLICITLY stated in the context.")
        appendLine("If a detail is not mentioned, mark it as 'Not specified in context'.")
        appendLine("Remember to respond with ONLY the JSON array.")
    }
}
