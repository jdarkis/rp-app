package com.example.rpapp3.data

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.FunctionCallingConfig
import com.google.ai.client.generativeai.type.ToolConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Field types that can be AI-enhanced
 */
enum class CharacterFieldType {
    NAME,
    DESCRIPTION,
    APPEARANCE,
    PERSONALITY,
    SYSTEM_INSTRUCTIONS
}

/**
 * Result wrapper for text elaboration
 */
sealed class ElaborationResult {
    data class Success(val text: String) : ElaborationResult()
    data class Error(val message: String) : ElaborationResult()
}

/**
 * Service class for AI-powered text field elaboration using Gemini API.
 * Expands short prompts into detailed content suitable for character creation.
 */
class AITextFieldService(private val context: Context) {
    
    private var apiKeyManager: ApiKeyManager? = null
    
    init {
        apiKeyManager = ApiKeyManager.getInstance(context)
    }
    
    /**
     * Elaborate a user prompt into detailed text for a specific field type.
     * 
     * @param prompt The user's short prompt to elaborate
     * @param fieldType The type of field being filled (affects the style of elaboration)
     */
    suspend fun elaboratePrompt(
        prompt: String,
        fieldType: CharacterFieldType,
        retryCount: Int = 0
    ): ElaborationResult = withContext(Dispatchers.IO) {
        
        // Limit retries to prevent infinite loops
        val maxRetries = 3
        if (retryCount >= maxRetries) {
            return@withContext ElaborationResult.Error("Failed after $maxRetries retries. Please try again later.")
        }
        
        val apiKey = apiKeyManager?.getCurrentApiKey()
        if (apiKey.isNullOrBlank()) {
            return@withContext ElaborationResult.Error("No API key configured. Please add one in Settings.")
        }
        
        val systemPrompt = buildSystemPrompt(fieldType)
        
        try {
            val generativeModel = GenerativeModel(
                modelName = "gemini-3-flash-preview",
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.9f
                    topP = 0.95f
                    maxOutputTokens = 512
                },
                systemInstruction = content { text(systemPrompt) },
                tools = emptyList(),
                toolConfig = ToolConfig(
                    functionCallingConfig = FunctionCallingConfig(
                        mode = FunctionCallingConfig.Mode.NONE
                    )
                )
            )
            
            val response = generativeModel.generateContent(prompt)
            val responseText = response.text?.trim() ?: return@withContext ElaborationResult.Error("No response from AI")
            
            ElaborationResult.Success(responseText)
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "Unknown error occurred"
            val lowerError = errorMessage.lowercase()
            
            // Check for errors that should trigger key rotation
            val shouldRotateKey = apiKeyManager?.isQuotaError(errorMessage) == true ||
                    lowerError.contains("overloaded") ||
                    lowerError.contains("503") ||
                    lowerError.contains("model is overloaded") ||
                    lowerError.contains("max_tokens") ||
                    lowerError.contains("resource_exhausted")
            
            if (shouldRotateKey) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null && newKey != apiKey) {
                    // Retry with new key, increment retry count
                    return@withContext elaboratePrompt(prompt, fieldType, retryCount + 1)
                }
                return@withContext ElaborationResult.Error("All API keys exhausted. Error: $errorMessage")
            }
            
            ElaborationResult.Error(errorMessage)
        }
    }
    
    private fun buildSystemPrompt(fieldType: CharacterFieldType): String {
        return when (fieldType) {
            CharacterFieldType.NAME -> buildString {
                appendLine("You are a creative character name generator for roleplay.")
                appendLine("Generate a single, memorable character name based on the user's prompt.")
                appendLine("The name should fit the theme or setting described.")
                appendLine("Respond with ONLY the name, nothing else. No quotes, no explanation.")
            }
            
            CharacterFieldType.DESCRIPTION -> buildString {
                appendLine("You are a creative writer helping to create character backgrounds for roleplay.")
                appendLine("Based on the user's prompt, write a detailed character background and description.")
                appendLine("Include:")
                appendLine("- Origin and history")
                appendLine("- Role or occupation")
                appendLine("- Key life events or motivations")
                appendLine("- Relationships or affiliations")
                appendLine("Write in third person. Be creative and detailed but concise (2-4 paragraphs).")
                appendLine("Respond with ONLY the description, no quotes or meta-commentary.")
            }
            
            CharacterFieldType.APPEARANCE -> buildString {
                appendLine("You are a creative writer helping to describe character appearances for roleplay.")
                appendLine("Based on the user's prompt, write a vivid physical description of the character.")
                appendLine("Include:")
                appendLine("- Physical features (height, build, hair, eyes, skin)")
                appendLine("- Distinctive marks or features")
                appendLine("- Typical clothing and accessories")
                appendLine("- Overall presence and demeanor")
                appendLine("Write in third person. Be vivid and descriptive (1-3 paragraphs).")
                appendLine("Respond with ONLY the appearance description, no quotes or meta-commentary.")
            }
            
            CharacterFieldType.PERSONALITY -> buildString {
                appendLine("You are a creative writer helping to develop character personalities for roleplay.")
                appendLine("Based on the user's prompt, describe the character's personality traits.")
                appendLine("Include:")
                appendLine("- Core personality traits")
                appendLine("- Mannerisms and habits")
                appendLine("- Speech patterns or verbal quirks")
                appendLine("- Strengths and flaws")
                appendLine("- Likes, dislikes, fears")
                appendLine("Write in third person. Be detailed but concise (1-3 paragraphs).")
                appendLine("Respond with ONLY the personality description, no quotes or meta-commentary.")
            }
            
            CharacterFieldType.SYSTEM_INSTRUCTIONS -> buildString {
                appendLine("You are helping to create AI roleplay instructions for portraying a character.")
                appendLine("Based on the user's prompt, write clear instructions for how an AI should portray this character.")
                appendLine("Include:")
                appendLine("- Voice and tone to use")
                appendLine("- Speech patterns or vocabulary")
                appendLine("- Typical reactions and behaviors")
                appendLine("- Things the character would/wouldn't do")
                appendLine("- Any special roleplay notes")
                appendLine("Write as direct instructions. Be specific and actionable (1-2 paragraphs).")
                appendLine("Respond with ONLY the instructions, no quotes or meta-commentary.")
            }
        }
    }
}
