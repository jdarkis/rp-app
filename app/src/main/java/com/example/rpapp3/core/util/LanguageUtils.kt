package com.example.rpapp3.core.util

/**
 * Centralized language utilities for the application.
 * 
 * This object provides consistent language code-to-name mappings used across
 * the app for AI prompts, TTS configuration, and character settings.
 */
object LanguageUtils {
    
    /**
     * Supported language codes and their display names.
     * Used throughout the app for language selection UI and AI prompt generation.
     */
    val SUPPORTED_LANGUAGES: List<Pair<String, String>> = listOf(
        "en" to "English",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "it" to "Italian",
        "pt" to "Portuguese",
        "pl" to "Polish",
        "ru" to "Russian",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese",
        "ar" to "Arabic",
        "hi" to "Hindi",
        "tr" to "Turkish",
        "nl" to "Dutch",
        "sv" to "Swedish",
        "uk" to "Ukrainian",
        "lt" to "Lithuanian"
    )
    
    /**
     * Converts a language code to its human-readable name.
     * 
     * @param code ISO 639-1 language code (e.g., "en", "ru", "ja")
     * @return Human-readable language name, or the original code if not found
     * 
     * Example:
     * ```
     * getLanguageName("en") // Returns "English"
     * getLanguageName("xyz") // Returns "xyz"
     * ```
     */
    fun getLanguageName(code: String): String {
        return SUPPORTED_LANGUAGES.find { it.first == code }?.second ?: code
    }
    
    /**
     * Checks if a language code is supported.
     * 
     * @param code ISO 639-1 language code to check
     * @return true if the language is supported, false otherwise
     */
    fun isSupported(code: String): Boolean {
        return SUPPORTED_LANGUAGES.any { it.first == code }
    }

    fun requiresExplicitLanguageInstructions(
        narratorLanguage: String,
        characterLanguages: Iterable<String>
    ): Boolean {
        return !isEnglish(narratorLanguage) || characterLanguages.any { !isEnglish(it) }
    }

    private fun isEnglish(code: String): Boolean {
        val normalized = code.trim().lowercase()
        return normalized.isEmpty() ||
            normalized == "en" ||
            normalized.startsWith("en-") ||
            normalized.startsWith("en_")
    }
}
