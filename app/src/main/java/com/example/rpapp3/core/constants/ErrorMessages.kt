package com.example.rpapp3.core.constants

/**
 * Centralized user-facing error messages.
 * 
 * This object contains all error strings displayed to users, previously hardcoded
 * throughout ViewModels. Centralizing these messages:
 * - Ensures consistency in error messaging
 * - Simplifies localization (all strings in one place)
 * - Makes error message updates trivial
 */
object ErrorMessages {
    
    // ==================== API Key Errors ====================
    
    /** Displayed when no API key is configured */
    const val NO_API_KEY = "No API key configured. Please add one in Settings."
    
    /** Displayed when all API keys have exceeded their quota */
    const val ALL_KEYS_EXHAUSTED = "All API keys have exceeded their quota. Please add new keys in Settings or wait for quota reset."
    
    /** Detailed message for quota exceeded shown in chat */
    const val QUOTA_EXCEEDED_CHAT = "Error: All API keys have exceeded their quota. Please wait for the daily quota to reset or add new keys in Settings."
    
    // ==================== AI Initialization Errors ====================
    
    /** Displayed when AI model fails to initialize */
    const val AI_INIT_FAILED = "Failed to initialize AI. Check your API key in Settings."
    
    /** Displayed when AI returns no response */
    const val EMPTY_RESPONSE = "AI returned an empty response. Please try again."
    
    /** Displayed when stream fails to start */
    const val STREAM_FAILED = "Failed to get response from AI. Please try again."
    
    // ==================== Chat Errors ====================
    
    /** Displayed when character is not found */
    const val CHARACTER_NOT_FOUND = "Character not found"
    
    /** Displayed when private chat creation fails */
    const val PRIVATE_CHAT_FAILED = "Failed to create private chat"
    
    /** Displayed when loading more messages fails */
    fun loadMoreFailed(message: String?) = "Failed to load more messages: ${message ?: "Unknown error"}"
    
    // ==================== General Errors ====================
    
    /** Template for displaying errors in chat */
    fun errorPrefix(message: String) = "Error: $message"
}
