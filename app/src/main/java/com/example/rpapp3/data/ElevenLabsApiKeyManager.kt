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
            "sk_d1c7496e62f9befa199cfb2a8fbb34b25eb7c4703dd8fe0c",
            "sk_8452724c390214d6f6f3a2d7a15d475d7a88b9f102a83aa3",
            "sk_e5a4c92c5696c66a520d33b58b52ff779e28ee6bf9691bb5",
            "sk_07f70f96f143b3afa288117c8c2c4616cf7324437712f856",
            "sk_ee74c711fb1815729f42af9fb2e4e9e2e0234879f02daca6",
            "sk_86cea6633ec80a0c9752c82789d8c38f97cf07ab578326ea",
            "sk_e5946aba965167bf69b71155f08639210693d2f5631b9ccc",
            "sk_37a5206a40da6d4ac941a1989960d77d26135a2f0e8dc78d",
            "sk_d018a069b43756f3772383fe4f81e3f1c89fd085a5fd47a2",
            "sk_0c94f52354cfe6b56e90958e0fe0698834b35a61c392c788",
            "sk_f05b202f8cd38214c3e955df22968d7cd5de8338fb860c20"
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
     * Set the active API key by index
     * Allows manual selection of which key to use
     */
    suspend fun setActiveKeyIndex(index: Int) {
        val keys = apiKeys.first()
        if (index >= 0 && index < keys.size) {
            context.elevenLabsDataStore.edit { preferences ->
                preferences[CURRENT_INDEX_KEY] = index
            }
        }
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
