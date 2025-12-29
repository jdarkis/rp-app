package com.example.rpapp3.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.chatSettingsDataStore by preferencesDataStore(name = "chat_settings")

enum class MessageFilterMode {
    OFF,                // Show full message
    LAST_PARAGRAPH,     // Show only last paragraph
    AFTER_DELIMITER     // Show text after custom delimiter
}

class ChatSettingsManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: ChatSettingsManager? = null

        fun getInstance(context: Context): ChatSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
        
        const val DEFAULT_DELIMITER = "***"
    }

    private val FILTER_MODE_KEY = stringPreferencesKey("message_filter_mode")
    private val CUSTOM_DELIMITER_KEY = stringPreferencesKey("custom_delimiter")

    val filterMode: Flow<MessageFilterMode> = context.chatSettingsDataStore.data
        .map { preferences ->
            val modeString = preferences[FILTER_MODE_KEY] ?: MessageFilterMode.OFF.name
            try {
                MessageFilterMode.valueOf(modeString)
            } catch (e: IllegalArgumentException) {
                MessageFilterMode.OFF
            }
        }

    val customDelimiter: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[CUSTOM_DELIMITER_KEY] ?: DEFAULT_DELIMITER
        }

    suspend fun setFilterMode(mode: MessageFilterMode) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[FILTER_MODE_KEY] = mode.name
        }
    }

    suspend fun setCustomDelimiter(delimiter: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[CUSTOM_DELIMITER_KEY] = delimiter
        }
    }
}
