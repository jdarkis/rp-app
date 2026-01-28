package com.example.rpapp3.core.constants

/**
 * Centralized application constants.
 * 
 * This object contains magic numbers and configuration values that were previously
 * scattered throughout the codebase. Centralizing these values:
 * - Makes configuration changes trivial (single location)
 * - Improves code readability (named constants vs magic numbers)
 * - Facilitates testing (easier to mock/override)
 */
object AppConstants {
    
    // ==================== Pagination ====================
    
    /** Number of messages to load initially when opening a chat */
    const val INITIAL_PAGE_SIZE = 20
    
    /** Number of older messages to load when scrolling up for more */
    const val LOAD_MORE_PAGE_SIZE = 50
    
    // ==================== API Retry Logic ====================
    
    /** Maximum number of retries when hitting rate limits (429 errors) */
    const val MAX_RATE_LIMIT_RETRIES = 3
    
    /** Initial delay in milliseconds before retrying after rate limit */
    const val INITIAL_RETRY_DELAY_MS = 2000L
    
    /** Delay in milliseconds between API key rotation attempts */
    const val KEY_ROTATION_DELAY_MS = 500L
    
    // ==================== AI Model Configuration ====================
    
    /** Default AI model ID for new chats */
    const val DEFAULT_AI_MODEL = "gemini-3-flash-preview"
    
    // ==================== TTS Configuration ====================
    
    /** Default TTS model for ElevenLabs */
    const val DEFAULT_TTS_MODEL = "eleven_flash_v2_5"
    
    // ==================== UI Constants ====================
    
    /** Maximum height for lazy column dialogs (voice selector, etc.) */
    const val DIALOG_MAX_HEIGHT_DP = 400
    
    /** Standard corner radius for cards and surfaces */
    const val CARD_CORNER_RADIUS_DP = 8
}
