package com.example.rpapp3.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.chatSettingsDataStore by preferencesDataStore(name = "chat_settings")

enum class MessageFilterMode {
    OFF,                // Show full message
    LAST_N_PARAGRAPHS,  // Show only last N paragraphs
    AFTER_DELIMITER     // Show text after custom delimiter
}

enum class SafetyThreshold {
    BLOCK_NONE,          // Allow all content
    BLOCK_ONLY_HIGH,     // Block only high probability harmful content
    BLOCK_MEDIUM_AND_ABOVE, // Block medium and above
    BLOCK_LOW_AND_ABOVE  // Block low and above (strictest)
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
        const val DEFAULT_PARAGRAPH_COUNT = 1
        
        // Default values
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_TOP_P = 0.95f
        const val DEFAULT_TOP_K = 64
        const val DEFAULT_MAX_OUTPUT_TOKENS = 16384
        const val DEFAULT_PRESENCE_PENALTY = 0f
        const val DEFAULT_FREQUENCY_PENALTY = 0f
        
        // TTS Defaults
        const val DEFAULT_TTS_MODEL_ID = "eleven_v3"
        const val DEFAULT_NARRATOR_VOICE_ID = ""
    }

    // Preference Keys
    private val FILTER_MODE_KEY = stringPreferencesKey("message_filter_mode")
    private val CUSTOM_DELIMITER_KEY = stringPreferencesKey("custom_delimiter")
    private val PARAGRAPH_COUNT_KEY = intPreferencesKey("paragraph_count")
    private val STREAMING_ENABLED_KEY = booleanPreferencesKey("streaming_enabled")
    private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
    private val TOP_P_KEY = floatPreferencesKey("top_p")
    private val TOP_K_KEY = intPreferencesKey("top_k")
    private val MAX_OUTPUT_TOKENS_KEY = intPreferencesKey("max_output_tokens")
    private val PRESENCE_PENALTY_KEY = floatPreferencesKey("presence_penalty")
    private val FREQUENCY_PENALTY_KEY = floatPreferencesKey("frequency_penalty")
    private val THINKING_ENABLED_KEY = booleanPreferencesKey("thinking_enabled")
    private val SAFETY_HARASSMENT_KEY = stringPreferencesKey("safety_harassment")
    private val SAFETY_HATE_SPEECH_KEY = stringPreferencesKey("safety_hate_speech")
    private val SAFETY_SEXUALLY_EXPLICIT_KEY = stringPreferencesKey("safety_sexually_explicit")
    private val SAFETY_DANGEROUS_CONTENT_KEY = stringPreferencesKey("safety_dangerous_content")
    private val SEPARATE_CHARACTER_DIALOGUE_KEY = booleanPreferencesKey("separate_character_dialogue")
    private val PROVIDE_CHOICES_ENABLED_KEY = booleanPreferencesKey("provide_choices_enabled")
    
    // TTS Settings Keys
    private val TTS_ENABLED_KEY = booleanPreferencesKey("tts_enabled")
    private val AUTO_TTS_ENABLED_KEY = booleanPreferencesKey("auto_tts_enabled")
    private val TTS_AUDIO_TAGS_ENABLED_KEY = booleanPreferencesKey("tts_audio_tags_enabled")
    private val NARRATOR_VOICE_ID_KEY = stringPreferencesKey("narrator_voice_id")
    private val TTS_MODEL_ID_KEY = stringPreferencesKey("tts_model_id")

    // Message Display Settings
    val filterMode: Flow<MessageFilterMode> = context.chatSettingsDataStore.data
        .map { preferences ->
            val modeString = preferences[FILTER_MODE_KEY] ?: MessageFilterMode.OFF.name
            try {
                // Handle legacy LAST_PARAGRAPH value
                if (modeString == "LAST_PARAGRAPH") {
                    MessageFilterMode.LAST_N_PARAGRAPHS
                } else {
                    MessageFilterMode.valueOf(modeString)
                }
            } catch (e: IllegalArgumentException) {
                MessageFilterMode.OFF
            }
        }

    val customDelimiter: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[CUSTOM_DELIMITER_KEY] ?: DEFAULT_DELIMITER
        }

    val paragraphCount: Flow<Int> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PARAGRAPH_COUNT_KEY] ?: DEFAULT_PARAGRAPH_COUNT
        }

    // Generation Settings
    val streamingEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[STREAMING_ENABLED_KEY] ?: false
        }

    val temperature: Flow<Float> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[TEMPERATURE_KEY] ?: DEFAULT_TEMPERATURE
        }

    val topP: Flow<Float> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[TOP_P_KEY] ?: DEFAULT_TOP_P
        }

    val topK: Flow<Int> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[TOP_K_KEY] ?: DEFAULT_TOP_K
        }

    val maxOutputTokens: Flow<Int> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[MAX_OUTPUT_TOKENS_KEY] ?: DEFAULT_MAX_OUTPUT_TOKENS
        }

    val presencePenalty: Flow<Float> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRESENCE_PENALTY_KEY] ?: DEFAULT_PRESENCE_PENALTY
        }

    val frequencyPenalty: Flow<Float> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[FREQUENCY_PENALTY_KEY] ?: DEFAULT_FREQUENCY_PENALTY
        }

    val thinkingEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[THINKING_ENABLED_KEY] ?: false
        }

    // Safety Settings
    val safetyHarassment: Flow<SafetyThreshold> = context.chatSettingsDataStore.data
        .map { preferences ->
            val value = preferences[SAFETY_HARASSMENT_KEY] ?: SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            try {
                SafetyThreshold.valueOf(value)
            } catch (e: IllegalArgumentException) {
                SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE
            }
        }

    val safetyHateSpeech: Flow<SafetyThreshold> = context.chatSettingsDataStore.data
        .map { preferences ->
            val value = preferences[SAFETY_HATE_SPEECH_KEY] ?: SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            try {
                SafetyThreshold.valueOf(value)
            } catch (e: IllegalArgumentException) {
                SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE
            }
        }

    val safetySexuallyExplicit: Flow<SafetyThreshold> = context.chatSettingsDataStore.data
        .map { preferences ->
            val value = preferences[SAFETY_SEXUALLY_EXPLICIT_KEY] ?: SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            try {
                SafetyThreshold.valueOf(value)
            } catch (e: IllegalArgumentException) {
                SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE
            }
        }

    val safetyDangerousContent: Flow<SafetyThreshold> = context.chatSettingsDataStore.data
        .map { preferences ->
            val value = preferences[SAFETY_DANGEROUS_CONTENT_KEY] ?: SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            try {
                SafetyThreshold.valueOf(value)
            } catch (e: IllegalArgumentException) {
                SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE
            }
        }

    // Character Dialogue Separation Setting
    val separateCharacterDialogue: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[SEPARATE_CHARACTER_DIALOGUE_KEY] ?: true // Default ON
        }

    // Provide Choices Setting (Action & Dialogue choices at end of AI messages)
    val provideChoicesEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PROVIDE_CHOICES_ENABLED_KEY] ?: true // Default ON
        }

    // TTS Settings
    val ttsEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[TTS_ENABLED_KEY] ?: false // Default OFF
        }

    val autoTtsEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[AUTO_TTS_ENABLED_KEY] ?: false // Default OFF
        }

    val ttsAudioTagsEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[TTS_AUDIO_TAGS_ENABLED_KEY] ?: false // Default OFF
        }

    val narratorVoiceId: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[NARRATOR_VOICE_ID_KEY] ?: DEFAULT_NARRATOR_VOICE_ID
        }

    val ttsModelId: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[TTS_MODEL_ID_KEY] ?: DEFAULT_TTS_MODEL_ID
        }

    // Setters for Message Display
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

    suspend fun setParagraphCount(count: Int) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PARAGRAPH_COUNT_KEY] = count.coerceIn(1, 20)
        }
    }

    // Setters for Generation Settings
    suspend fun setStreamingEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[STREAMING_ENABLED_KEY] = enabled
        }
    }

    suspend fun setTemperature(value: Float) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[TEMPERATURE_KEY] = value.coerceIn(0f, 2f)
        }
    }

    suspend fun setTopP(value: Float) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[TOP_P_KEY] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun setTopK(value: Int) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[TOP_K_KEY] = value.coerceIn(1, 100)
        }
    }

    suspend fun setMaxOutputTokens(value: Int) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[MAX_OUTPUT_TOKENS_KEY] = value.coerceIn(1, 65536)
        }
    }

    suspend fun setPresencePenalty(value: Float) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRESENCE_PENALTY_KEY] = value.coerceIn(-2f, 2f)
        }
    }

    suspend fun setFrequencyPenalty(value: Float) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[FREQUENCY_PENALTY_KEY] = value.coerceIn(-2f, 2f)
        }
    }

    suspend fun setThinkingEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[THINKING_ENABLED_KEY] = enabled
        }
    }

    // Setters for Safety Settings
    suspend fun setSafetyHarassment(threshold: SafetyThreshold) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[SAFETY_HARASSMENT_KEY] = threshold.name
        }
    }

    suspend fun setSafetyHateSpeech(threshold: SafetyThreshold) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[SAFETY_HATE_SPEECH_KEY] = threshold.name
        }
    }

    suspend fun setSafetySexuallyExplicit(threshold: SafetyThreshold) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[SAFETY_SEXUALLY_EXPLICIT_KEY] = threshold.name
        }
    }

    suspend fun setSafetyDangerousContent(threshold: SafetyThreshold) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[SAFETY_DANGEROUS_CONTENT_KEY] = threshold.name
        }
    }

    suspend fun setSeparateCharacterDialogue(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[SEPARATE_CHARACTER_DIALOGUE_KEY] = enabled
        }
    }

    suspend fun setProvideChoicesEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PROVIDE_CHOICES_ENABLED_KEY] = enabled
        }
    }

    // TTS Setters
    suspend fun setTtsEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[TTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setNarratorVoiceId(voiceId: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[NARRATOR_VOICE_ID_KEY] = voiceId
        }
    }

    suspend fun setTtsModelId(modelId: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[TTS_MODEL_ID_KEY] = modelId
        }
    }

    suspend fun setAutoTtsEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[AUTO_TTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setTtsAudioTagsEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[TTS_AUDIO_TAGS_ENABLED_KEY] = enabled
        }
    }

    /**
     * Restore all settings to their default values
     */
    suspend fun restoreDefaults() {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[FILTER_MODE_KEY] = MessageFilterMode.OFF.name
            preferences[CUSTOM_DELIMITER_KEY] = DEFAULT_DELIMITER
            preferences[PARAGRAPH_COUNT_KEY] = DEFAULT_PARAGRAPH_COUNT
            preferences[STREAMING_ENABLED_KEY] = false
            preferences[TEMPERATURE_KEY] = DEFAULT_TEMPERATURE
            preferences[TOP_P_KEY] = DEFAULT_TOP_P
            preferences[TOP_K_KEY] = DEFAULT_TOP_K
            preferences[MAX_OUTPUT_TOKENS_KEY] = DEFAULT_MAX_OUTPUT_TOKENS
            preferences[PRESENCE_PENALTY_KEY] = DEFAULT_PRESENCE_PENALTY
            preferences[FREQUENCY_PENALTY_KEY] = DEFAULT_FREQUENCY_PENALTY
            preferences[THINKING_ENABLED_KEY] = false
            preferences[SAFETY_HARASSMENT_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SAFETY_HATE_SPEECH_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SAFETY_SEXUALLY_EXPLICIT_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SAFETY_DANGEROUS_CONTENT_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SEPARATE_CHARACTER_DIALOGUE_KEY] = true
            preferences[PROVIDE_CHOICES_ENABLED_KEY] = true
            // TTS defaults
            preferences[TTS_ENABLED_KEY] = false
            preferences[AUTO_TTS_ENABLED_KEY] = false
            preferences[TTS_AUDIO_TAGS_ENABLED_KEY] = false
            preferences[NARRATOR_VOICE_ID_KEY] = DEFAULT_NARRATOR_VOICE_ID
            preferences[TTS_MODEL_ID_KEY] = DEFAULT_TTS_MODEL_ID
        }
    }

    // Utility to get all settings at once (for ViewModel initialization)
    suspend fun getCurrentSettings(): ChatSettings {
        return ChatSettings(
            filterMode = filterMode.first(),
            customDelimiter = customDelimiter.first(),
            paragraphCount = paragraphCount.first(),
            streamingEnabled = streamingEnabled.first(),
            temperature = temperature.first(),
            topP = topP.first(),
            topK = topK.first(),
            maxOutputTokens = maxOutputTokens.first(),
            presencePenalty = presencePenalty.first(),
            frequencyPenalty = frequencyPenalty.first(),
            thinkingEnabled = thinkingEnabled.first(),
            safetyHarassment = safetyHarassment.first(),
            safetyHateSpeech = safetyHateSpeech.first(),
            safetySexuallyExplicit = safetySexuallyExplicit.first(),
            safetyDangerousContent = safetyDangerousContent.first(),
            separateCharacterDialogue = separateCharacterDialogue.first(),
            provideChoicesEnabled = provideChoicesEnabled.first(),
            // TTS
            ttsEnabled = ttsEnabled.first(),
            autoTtsEnabled = autoTtsEnabled.first(),
            ttsAudioTagsEnabled = ttsAudioTagsEnabled.first(),
            narratorVoiceId = narratorVoiceId.first(),
            ttsModelId = ttsModelId.first()
        )
    }
}

data class ChatSettings(
    val filterMode: MessageFilterMode = MessageFilterMode.OFF,
    val customDelimiter: String = ChatSettingsManager.DEFAULT_DELIMITER,
    val paragraphCount: Int = ChatSettingsManager.DEFAULT_PARAGRAPH_COUNT,
    val streamingEnabled: Boolean = false,
    val temperature: Float = ChatSettingsManager.DEFAULT_TEMPERATURE,
    val topP: Float = ChatSettingsManager.DEFAULT_TOP_P,
    val topK: Int = ChatSettingsManager.DEFAULT_TOP_K,
    val maxOutputTokens: Int = ChatSettingsManager.DEFAULT_MAX_OUTPUT_TOKENS,
    val presencePenalty: Float = ChatSettingsManager.DEFAULT_PRESENCE_PENALTY,
    val frequencyPenalty: Float = ChatSettingsManager.DEFAULT_FREQUENCY_PENALTY,
    val thinkingEnabled: Boolean = false,
    val safetyHarassment: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetyHateSpeech: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetySexuallyExplicit: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetyDangerousContent: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val separateCharacterDialogue: Boolean = true,
    val provideChoicesEnabled: Boolean = true,
    // TTS Settings
    val ttsEnabled: Boolean = false,
    val autoTtsEnabled: Boolean = false,
    val ttsAudioTagsEnabled: Boolean = false,
    val narratorVoiceId: String = ChatSettingsManager.DEFAULT_NARRATOR_VOICE_ID,
    val ttsModelId: String = ChatSettingsManager.DEFAULT_TTS_MODEL_ID
)
