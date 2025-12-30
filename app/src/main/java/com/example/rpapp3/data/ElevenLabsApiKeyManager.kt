package com.example.rpapp3.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.elevenLabsDataStore: DataStore<Preferences> by preferencesDataStore(name = "elevenlabs_api_keys")

class ElevenLabsApiKeyManager(private val context: Context) {
    
    companion object {
        private val API_KEYS_KEY = stringPreferencesKey("elevenlabs_api_keys")
        private val CURRENT_INDEX_KEY = intPreferencesKey("elevenlabs_current_key_index")
        private const val SEPARATOR = "|||"
        
        // Default API keys
        private val DEFAULT_API_KEYS = listOf(
            "sk_b8012c97f04c7b55e11db450f18e1679784ec9e621c592dd",
            "sk_d1c7496e62f9befa199cfb2a8fbb34b25eb7c4703dd8fe0c"
        )
        
        @Volatile
        private var INSTANCE: ElevenLabsApiKeyManager? = null
        
        fun getInstance(context: Context): ElevenLabsApiKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ElevenLabsApiKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Flow of all API keys in the pool
     */
    val apiKeys: Flow<List<String>> = context.elevenLabsDataStore.data.map { preferences ->
        val keysString = preferences[API_KEYS_KEY] ?: ""
        if (keysString.isEmpty()) {
            emptyList()
        } else {
            keysString.split(SEPARATOR)
        }
    }
    
    /**
     * Flow of the current key index
     */
    val currentKeyIndex: Flow<Int> = context.elevenLabsDataStore.data.map { preferences ->
        preferences[CURRENT_INDEX_KEY] ?: 0
    }
    
    /**
     * Initialize with default API keys if not already set.
     * Also syncs any new default keys that were added to DEFAULT_API_KEYS.
     */
    suspend fun initializeDefaults() {
        val currentKeys = apiKeys.first()
        if (currentKeys.isEmpty()) {
            // No keys stored, initialize with all default keys
            context.elevenLabsDataStore.edit { preferences ->
                preferences[API_KEYS_KEY] = DEFAULT_API_KEYS.joinToString(SEPARATOR)
                preferences[CURRENT_INDEX_KEY] = 0
            }
        } else {
            // Sync: Add any new default keys that aren't already in the stored list
            val newDefaultKeys = DEFAULT_API_KEYS.filter { defaultKey -> 
                !currentKeys.contains(defaultKey) 
            }
            if (newDefaultKeys.isNotEmpty()) {
                context.elevenLabsDataStore.edit { preferences ->
                    val updatedKeys = currentKeys + newDefaultKeys
                    preferences[API_KEYS_KEY] = updatedKeys.joinToString(SEPARATOR)
                }
            }
        }
    }
    
    /**
     * Get the currently active API key
     */
    suspend fun getCurrentApiKey(): String? {
        val keys = apiKeys.first()
        val index = currentKeyIndex.first()
        return keys.getOrNull(index)
    }
    
    /**
     * Add a new API key to the pool
     */
    suspend fun addApiKey(key: String) {
        if (key.isBlank()) return
        context.elevenLabsDataStore.edit { preferences ->
            val currentKeys = preferences[API_KEYS_KEY] ?: ""
            val keysList = if (currentKeys.isEmpty()) {
                mutableListOf()
            } else {
                currentKeys.split(SEPARATOR).toMutableList()
            }
            
            // Don't add duplicates
            if (!keysList.contains(key.trim())) {
                keysList.add(key.trim())
                preferences[API_KEYS_KEY] = keysList.joinToString(SEPARATOR)
                preferences[CURRENT_INDEX_KEY] = 0
            }
        }
    }
    
    /**
     * Remove an API key from the pool
     */
    suspend fun removeApiKey(key: String) {
        context.elevenLabsDataStore.edit { preferences ->
            val currentKeys = preferences[API_KEYS_KEY] ?: ""
            if (currentKeys.isEmpty()) return@edit
            
            val keysList = currentKeys.split(SEPARATOR).toMutableList()
            val removedIndex = keysList.indexOf(key)
            keysList.remove(key)
            
            preferences[API_KEYS_KEY] = keysList.joinToString(SEPARATOR)
            
            // Adjust current index if needed
            val currentIndex = preferences[CURRENT_INDEX_KEY] ?: 0
            if (keysList.isEmpty()) {
                preferences[CURRENT_INDEX_KEY] = 0
            } else if (removedIndex <= currentIndex) {
                preferences[CURRENT_INDEX_KEY] = maxOf(0, currentIndex - 1) % keysList.size
            }
        }
    }
    
    /**
     * Rotate to the next API key in the pool
     * Returns the new current key, or null if no keys available
     */
    suspend fun rotateToNextKey(): String? {
        context.elevenLabsDataStore.edit { preferences ->
            val currentKeys = preferences[API_KEYS_KEY] ?: ""
            if (currentKeys.isEmpty()) return@edit
            
            val keysList = currentKeys.split(SEPARATOR)
            if (keysList.isEmpty()) return@edit
            
            val currentIndex = preferences[CURRENT_INDEX_KEY] ?: 0
            val nextIndex = (currentIndex + 1) % keysList.size
            preferences[CURRENT_INDEX_KEY] = nextIndex
        }
        
        return getCurrentApiKey()
    }
    
    /**
     * Reset the key index to 0
     */
    suspend fun resetKeyIndex() {
        context.elevenLabsDataStore.edit { preferences ->
            preferences[CURRENT_INDEX_KEY] = 0
        }
    }
    
    /**
     * Check if an error message indicates quota exceeded - should switch to next key
     */
    fun isQuotaExhaustedError(errorMessage: String?): Boolean {
        if (errorMessage == null) return false
        val lowerMessage = errorMessage.lowercase()
        return lowerMessage.contains("429") ||
                lowerMessage.contains("quota") ||
                lowerMessage.contains("rate limit") ||
                lowerMessage.contains("too many requests") ||
                lowerMessage.contains("exceeded")
    }
}
