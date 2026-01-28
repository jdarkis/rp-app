package com.example.rpapp3.core.util

import com.example.rpapp3.data.ChatSettings
import com.example.rpapp3.data.SafetyThreshold
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting

/**
 * Builder utility for creating Gemini AI safety settings.
 * 
 * This object centralizes the safety settings configuration logic that was previously
 * duplicated across ChatViewModel and PrivateChatViewModel.
 * 
 * Safety settings control what types of content the AI model will generate.
 * Each harm category can have a different blocking threshold.
 */
object SafetySettingsBuilder {
    
    /**
     * Builds a list of safety settings from the user's chat preferences.
     * 
     * @param settings The ChatSettings containing user's safety threshold preferences
     * @return List of SafetySetting configured for the Gemini GenerativeModel
     * 
     * Example:
     * ```
     * val safetySettings = SafetySettingsBuilder.build(currentSettings)
     * GenerativeModel(..., safetySettings = safetySettings)
     * ```
     */
    fun build(settings: ChatSettings): List<SafetySetting> {
        return listOf(
            SafetySetting(HarmCategory.HARASSMENT, mapThreshold(settings.safetyHarassment)),
            SafetySetting(HarmCategory.HATE_SPEECH, mapThreshold(settings.safetyHateSpeech)),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, mapThreshold(settings.safetySexuallyExplicit)),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, mapThreshold(settings.safetyDangerousContent))
        )
    }
    
    /**
     * Maps app-level SafetyThreshold enum to Gemini SDK BlockThreshold.
     * 
     * The app uses its own SafetyThreshold enum to abstract away the SDK implementation,
     * allowing for easier testing and potential SDK changes.
     * 
     * @param threshold The app's SafetyThreshold value
     * @return The corresponding Gemini SDK BlockThreshold
     */
    private fun mapThreshold(threshold: SafetyThreshold): BlockThreshold {
        return when (threshold) {
            SafetyThreshold.BLOCK_NONE -> BlockThreshold.NONE
            SafetyThreshold.BLOCK_ONLY_HIGH -> BlockThreshold.ONLY_HIGH
            SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE -> BlockThreshold.MEDIUM_AND_ABOVE
            SafetyThreshold.BLOCK_LOW_AND_ABOVE -> BlockThreshold.LOW_AND_ABOVE
        }
    }
}
