package com.example.rpapp3.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "theme_settings")

class ThemeManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: ThemeManager? = null

        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ThemeManager(context).also { INSTANCE = it }
            }
        }
        
        // Available Themes
        const val THEME_MODERN_DARK = "MODERN_DARK"
        const val THEME_CYBERPUNK = "CYBERPUNK"
        const val THEME_NATURE = "NATURE"
        const val THEME_CLASSIC = "CLASSIC"
        const val THEME_ECLIPSE = "ECLIPSE"
        const val THEME_CLOUD = "CLOUD"
    }

    private val THEME_KEY = stringPreferencesKey("app_theme")

    val currentTheme: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[THEME_KEY] ?: THEME_MODERN_DARK
        }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[THEME_KEY] = theme
        }
    }
}
