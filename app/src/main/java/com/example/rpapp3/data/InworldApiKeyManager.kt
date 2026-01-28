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

private val Context.inworldDataStore: DataStore<Preferences> by preferencesDataStore(name = "inworld_api_keys")

class InworldApiKeyManager(private val context: Context) {
    
    companion object {
        private val API_KEYS_KEY = stringPreferencesKey("inworld_api_keys")
        private val CURRENT_INDEX_KEY = intPreferencesKey("inworld_current_key_index")
        private const val SEPARATOR = "|||"
        
        // Default API keys (empty for now, user must add them)
        private val DEFAULT_API_KEYS = listOf<String>()
        
        @Volatile
        private var INSTANCE: InworldApiKeyManager? = null
        
        fun getInstance(context: Context): InworldApiKeyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: InworldApiKeyManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Flow of all API keys in the pool
     */
    val apiKeys: Flow<List<String>> = context.inworldDataStore.data.map { preferences ->
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
    val currentKeyIndex: Flow<Int> = context.inworldDataStore.data.map { preferences ->
        preferences[CURRENT_INDEX_KEY] ?: 0
    }
    
    /**
     * Initialize with default API keys if not already set.
     */
    suspend fun initializeDefaults() {
        val currentKeys = apiKeys.first()
        if (currentKeys.isEmpty() && DEFAULT_API_KEYS.isNotEmpty()) {
            context.inworldDataStore.edit { preferences ->
                preferences[API_KEYS_KEY] = DEFAULT_API_KEYS.joinToString(SEPARATOR)
                preferences[CURRENT_INDEX_KEY] = 0
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
        context.inworldDataStore.edit { preferences ->
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
                // If it's the first key, ensure index is 0
                if (keysList.size == 1) {
                    preferences[CURRENT_INDEX_KEY] = 0
                }
            }
        }
    }
    
    /**
     * Remove an API key from the pool
     */
    suspend fun removeApiKey(key: String) {
        context.inworldDataStore.edit { preferences ->
            val currentKeys = preferences[API_KEYS_KEY] ?: ""
            if (currentKeys.isEmpty()) return@edit
            
            val keysList = currentKeys.split(SEPARATOR).toMutableList()
            val removedIndex = keysList.indexOf(key)
            if (removedIndex == -1) return@edit
            
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
     */
    suspend fun rotateToNextKey(): String? {
        context.inworldDataStore.edit { preferences ->
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
     */
    suspend fun setActiveKeyIndex(index: Int) {
        val keys = apiKeys.first()
        if (index >= 0 && index < keys.size) {
            context.inworldDataStore.edit { preferences ->
                preferences[CURRENT_INDEX_KEY] = index
            }
        }
    }
    
    /**
     * Reset the key index to 0
     */
    suspend fun resetKeyIndex() {
        context.inworldDataStore.edit { preferences ->
            preferences[CURRENT_INDEX_KEY] = 0
        }
    }
}
