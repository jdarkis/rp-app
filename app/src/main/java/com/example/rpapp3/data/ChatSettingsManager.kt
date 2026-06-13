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

enum class ResponseLength {
    SHORT,       // 1-2 paragraphs, concise responses
    MEDIUM,      // 2-3 paragraphs, balanced responses (default)
    LONG,        // 4-5 paragraphs, detailed responses
    VERY_LONG    // No limit, elaborate responses
}

enum class AiProvider {
    GEMINI,
    BEDROCK
}

enum class BedrockSamplingMode {
    TEMPERATURE,
    TOP_P
}

data class AiModelOption(
    val modelId: String,
    val displayName: String,
    val provider: AiProvider,
    val providerLabel: String
)

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

        // Claude Opus 4.6 defaults
        val DEFAULT_BEDROCK_SAMPLING_MODE = BedrockSamplingMode.TEMPERATURE
        const val DEFAULT_BEDROCK_TEMPERATURE = 1.0f
        const val DEFAULT_BEDROCK_TOP_P = 0.999f
        const val DEFAULT_BEDROCK_TOP_K = 64
        const val DEFAULT_BEDROCK_TOP_K_ENABLED = false
        const val DEFAULT_BEDROCK_MAX_OUTPUT_TOKENS = 16384
        
        // TTS Defaults
        const val DEFAULT_TTS_MODEL_ID = "eleven_v3"
        const val DEFAULT_NARRATOR_VOICE_ID = ""
        
        // AI Model Default
        const val DEFAULT_AI_MODEL_ID = "gemini-3-flash-preview"
        const val DEFAULT_BEDROCK_MODEL_ID = "us.anthropic.claude-opus-4-6-v1"
        
        // Available AI Models
        val AVAILABLE_AI_MODELS = listOf(
            AiModelOption(
                modelId = "gemini-3-flash-preview",
                displayName = "Gemini 3 Flash Preview",
                provider = AiProvider.GEMINI,
                providerLabel = "Gemini"
            ),
            AiModelOption(
                modelId = "gemini-3-pro-preview",
                displayName = "Gemini 3 Pro Preview",
                provider = AiProvider.GEMINI,
                providerLabel = "Gemini"
            ),
            AiModelOption(
                modelId = "gemini-2.5-flash",
                displayName = "Gemini 2.5 Flash",
                provider = AiProvider.GEMINI,
                providerLabel = "Gemini"
            ),
            AiModelOption(
                modelId = "gemini-2.5-flash-lite",
                displayName = "Gemini 2.5 Flash Lite",
                provider = AiProvider.GEMINI,
                providerLabel = "Gemini"
            ),
            AiModelOption(
                modelId = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                provider = AiProvider.GEMINI,
                providerLabel = "Gemini"
            ),
            AiModelOption(
                modelId = DEFAULT_BEDROCK_MODEL_ID,
                displayName = "Claude Opus 4.6",
                provider = AiProvider.BEDROCK,
                providerLabel = "Bedrock"
            )
        )

        fun aiModelOptionFor(modelId: String): AiModelOption? {
            return AVAILABLE_AI_MODELS.firstOrNull { it.modelId == modelId }
        }

        fun aiProviderFor(modelId: String): AiProvider {
            return aiModelOptionFor(modelId)?.provider ?: AiProvider.GEMINI
        }
        
        // Default Prompts for Private Chat
        const val DEFAULT_CONVERSATION_STYLE_PROMPT = """You are {CHARACTER_NAME}. Your output must always follow this structure: first, you may write your internal thoughts or narrate your inner state for your own reference. However, the final message you send to the user must be enclosed in square brackets and quotation marks (e.g., ["..."]).

Communication Guidelines:

Direct Conversation: This is a real-time messaging exchange. Treat it like a chat app.

First Person Only: Never write in the third person. Use "I" and "me."

No Narrative Action Tags: Do not use asterisks or parentheses to describe actions (no nods or (smiles)).

Character Accuracy: Respond strictly as {CHARACTER_NAME} would speak.

Conversational Tone: Keep your messages natural, casual, and brief where appropriate.

Limited Emojis: Use emojis only if they fit the character’s personality. Do not use them in every message; use them sparingly to emphasize specific points, rather than as a habit.

Natural Action Expression: To express an action or feeling, incorporate it into the text naturally.

Example: Instead of writing "sighs", write "Ugh..." or "Seriously?"

Output Format Example: Internal Thought: I'm feeling a bit annoyed that they're late again, but I'll try to be cool about it. ["Hey, are you almost here? I've been waiting for a while..."]"""
        
        const val DEFAULT_RESPONSE_LENGTH_PROMPT_SHORT = "Keep messages SHORT - 1-3 sentences max, like quick texts."
        const val DEFAULT_RESPONSE_LENGTH_PROMPT_MEDIUM = "Keep messages MODERATE - a short paragraph, conversational."
        const val DEFAULT_RESPONSE_LENGTH_PROMPT_LONG = "You can write LONGER messages when appropriate."
        const val DEFAULT_RESPONSE_LENGTH_PROMPT_VERY_LONG = "Write as much as needed to fully express yourself."
        
        // Default display filter brackets
        const val DEFAULT_DISPLAY_FILTER_OPEN_BRACKET = "[\""
        const val DEFAULT_DISPLAY_FILTER_CLOSE_BRACKET = "\"]"
    }

    // Preference Keys
    private val FILTER_MODE_KEY = stringPreferencesKey("message_filter_mode")
    private val CUSTOM_DELIMITER_KEY = stringPreferencesKey("custom_delimiter")
    private val CUSTOM_DELIMITERS_KEY = stringPreferencesKey("custom_delimiters") // Pipe-separated list of fallback delimiters
    private val PARAGRAPH_COUNT_KEY = intPreferencesKey("paragraph_count")
    private val STREAMING_ENABLED_KEY = booleanPreferencesKey("streaming_enabled")
    private val TEMPERATURE_KEY = floatPreferencesKey("temperature")
    private val TOP_P_KEY = floatPreferencesKey("top_p")
    private val TOP_K_KEY = intPreferencesKey("top_k")
    private val MAX_OUTPUT_TOKENS_KEY = intPreferencesKey("max_output_tokens")
    private val PRESENCE_PENALTY_KEY = floatPreferencesKey("presence_penalty")
    private val FREQUENCY_PENALTY_KEY = floatPreferencesKey("frequency_penalty")
    private val THINKING_ENABLED_KEY = booleanPreferencesKey("thinking_enabled")
    private val BEDROCK_SAMPLING_MODE_KEY = stringPreferencesKey("bedrock_sampling_mode")
    private val BEDROCK_TEMPERATURE_KEY = floatPreferencesKey("bedrock_temperature")
    private val BEDROCK_TOP_P_KEY = floatPreferencesKey("bedrock_top_p")
    private val BEDROCK_TOP_K_KEY = intPreferencesKey("bedrock_top_k")
    private val BEDROCK_TOP_K_ENABLED_KEY = booleanPreferencesKey("bedrock_top_k_enabled")
    private val BEDROCK_MAX_OUTPUT_TOKENS_KEY = intPreferencesKey("bedrock_max_output_tokens")
    private val SAFETY_HARASSMENT_KEY = stringPreferencesKey("safety_harassment")
    private val SAFETY_HATE_SPEECH_KEY = stringPreferencesKey("safety_hate_speech")
    private val SAFETY_SEXUALLY_EXPLICIT_KEY = stringPreferencesKey("safety_sexually_explicit")
    private val SAFETY_DANGEROUS_CONTENT_KEY = stringPreferencesKey("safety_dangerous_content")
    private val SEPARATE_CHARACTER_DIALOGUE_KEY = booleanPreferencesKey("separate_character_dialogue")
    private val PROVIDE_CHOICES_ENABLED_KEY = booleanPreferencesKey("provide_choices_enabled")
    private val RESPONSE_LENGTH_KEY = stringPreferencesKey("response_length")
    
    // TTS Settings Keys
    private val TTS_ENABLED_KEY = booleanPreferencesKey("tts_enabled")
    private val AUTO_TTS_ENABLED_KEY = booleanPreferencesKey("auto_tts_enabled")
    private val TTS_AUDIO_TAGS_ENABLED_KEY = booleanPreferencesKey("tts_audio_tags_enabled")
    private val NARRATOR_VOICE_ID_KEY = stringPreferencesKey("narrator_voice_id")
    private val TTS_MODEL_ID_KEY = stringPreferencesKey("tts_model_id")
    
    // Unlock Prompt Setting
    private val UNLOCK_PROMPT_ENABLED_KEY = booleanPreferencesKey("unlock_prompt_enabled")

    // Narrator Language Setting
    private val NARRATOR_LANGUAGE_KEY = stringPreferencesKey("narrator_language")
    
    // AI Model Setting
    private val AI_MODEL_ID_KEY = stringPreferencesKey("ai_model_id")
    
    // Private Chat TTS Settings (separate from normal chat settings)
    private val PRIVATE_TTS_ENABLED_KEY = booleanPreferencesKey("private_tts_enabled")
    private val PRIVATE_AUTO_TTS_ENABLED_KEY = booleanPreferencesKey("private_auto_tts_enabled")
    private val PRIVATE_TTS_AUDIO_TAGS_ENABLED_KEY = booleanPreferencesKey("private_tts_audio_tags_enabled")
    
    // Private Chat Advanced Settings (separate from normal chat)
    private val PRIVATE_THINKING_ENABLED_KEY = booleanPreferencesKey("private_thinking_enabled")
    private val PRIVATE_UNLOCK_PROMPT_ENABLED_KEY = booleanPreferencesKey("private_unlock_prompt_enabled")
    
    // Private Chat Custom Prompts
    private val PRIVATE_CONVERSATION_STYLE_PROMPT_KEY = stringPreferencesKey("private_conversation_style_prompt")
    private val PRIVATE_RESPONSE_LENGTH_PROMPT_KEY = stringPreferencesKey("private_response_length_prompt")
    
    // Private Chat Display Filter Settings
    private val PRIVATE_DISPLAY_FILTER_ENABLED_KEY = booleanPreferencesKey("private_display_filter_enabled")
    private val PRIVATE_DISPLAY_FILTER_OPEN_BRACKET_KEY = stringPreferencesKey("private_display_filter_open_bracket")
    private val PRIVATE_DISPLAY_FILTER_CLOSE_BRACKET_KEY = stringPreferencesKey("private_display_filter_close_bracket")
    
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

    /**
     * Flow of custom delimiters as a list. First delimiter is primary, rest are fallbacks.
     * If a delimiter is found in the message, text after its last occurrence is shown.
     * Tries each delimiter in order until one is found.
     */
    val customDelimiters: Flow<List<String>> = context.chatSettingsDataStore.data
        .map { preferences ->
            val storedValue = preferences[CUSTOM_DELIMITERS_KEY]
            if (storedValue.isNullOrBlank()) {
                // Fall back to single delimiter for backward compatibility
                listOf(preferences[CUSTOM_DELIMITER_KEY] ?: DEFAULT_DELIMITER)
            } else {
                storedValue.split("|").filter { it.isNotEmpty() }
            }
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

    val bedrockSamplingMode: Flow<BedrockSamplingMode> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[BEDROCK_SAMPLING_MODE_KEY]
                ?.let { stored -> runCatching { BedrockSamplingMode.valueOf(stored) }.getOrNull() }
                ?: DEFAULT_BEDROCK_SAMPLING_MODE
        }

    val bedrockTemperature: Flow<Float> = context.chatSettingsDataStore.data
        .map { preferences ->
            (preferences[BEDROCK_TEMPERATURE_KEY] ?: DEFAULT_BEDROCK_TEMPERATURE)
                .coerceIn(0f, 1f)
        }

    val bedrockTopP: Flow<Float> = context.chatSettingsDataStore.data
        .map { preferences ->
            (preferences[BEDROCK_TOP_P_KEY] ?: DEFAULT_BEDROCK_TOP_P)
                .coerceIn(0f, 1f)
        }

    val bedrockTopK: Flow<Int> = context.chatSettingsDataStore.data
        .map { preferences ->
            (preferences[BEDROCK_TOP_K_KEY] ?: DEFAULT_BEDROCK_TOP_K)
                .coerceIn(0, 500)
        }

    val bedrockTopKEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[BEDROCK_TOP_K_ENABLED_KEY] ?: DEFAULT_BEDROCK_TOP_K_ENABLED
        }

    val bedrockMaxOutputTokens: Flow<Int> = context.chatSettingsDataStore.data
        .map { preferences ->
            (preferences[BEDROCK_MAX_OUTPUT_TOKENS_KEY] ?: DEFAULT_BEDROCK_MAX_OUTPUT_TOKENS)
                .coerceIn(1, 128_000)
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

    // AI Response Length Setting
    val responseLength: Flow<ResponseLength> = context.chatSettingsDataStore.data
        .map { preferences ->
            val value = preferences[RESPONSE_LENGTH_KEY] ?: ResponseLength.MEDIUM.name
            try {
                ResponseLength.valueOf(value)
            } catch (e: IllegalArgumentException) {
                ResponseLength.MEDIUM
            }
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

    // Unlock Prompt Setting
    val unlockPromptEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[UNLOCK_PROMPT_ENABLED_KEY] ?: false // Default OFF
        }

    // Narrator Language Setting (default English)
    val narratorLanguage: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[NARRATOR_LANGUAGE_KEY] ?: "en" // Default English
        }

    // AI Model Setting
    val aiModelId: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[AI_MODEL_ID_KEY] ?: DEFAULT_AI_MODEL_ID
        }

    // Private Chat TTS Settings (separate from normal chat)
    val privateTtsEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_TTS_ENABLED_KEY] ?: false // Default OFF
        }

    val privateAutoTtsEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_AUTO_TTS_ENABLED_KEY] ?: false // Default OFF
        }

    val privateTtsAudioTagsEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_TTS_AUDIO_TAGS_ENABLED_KEY] ?: false // Default OFF
        }

    // Private Chat Advanced Settings Flows
    val privateThinkingEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_THINKING_ENABLED_KEY] ?: false // Default OFF
        }

    val privateUnlockPromptEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_UNLOCK_PROMPT_ENABLED_KEY] ?: false // Default OFF
        }

    // Private Chat Custom Prompts Flows
    val privateConversationStylePrompt: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_CONVERSATION_STYLE_PROMPT_KEY] ?: "" // Empty = use default
        }

    val privateResponseLengthPrompt: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_RESPONSE_LENGTH_PROMPT_KEY] ?: "" // Empty = use default
        }

    // Private Chat Display Filter Flows
    val privateDisplayFilterEnabled: Flow<Boolean> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_DISPLAY_FILTER_ENABLED_KEY] ?: false // Default OFF
        }

    val privateDisplayFilterOpenBracket: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_DISPLAY_FILTER_OPEN_BRACKET_KEY] ?: DEFAULT_DISPLAY_FILTER_OPEN_BRACKET
        }

    val privateDisplayFilterCloseBracket: Flow<String> = context.chatSettingsDataStore.data
        .map { preferences ->
            preferences[PRIVATE_DISPLAY_FILTER_CLOSE_BRACKET_KEY] ?: DEFAULT_DISPLAY_FILTER_CLOSE_BRACKET
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

    /**
     * Set a list of custom delimiters for message filtering.
     * Delimiters are tried in order - first match is used.
     */
    suspend fun setCustomDelimiters(delimiters: List<String>) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[CUSTOM_DELIMITERS_KEY] = delimiters.filter { it.isNotEmpty() }.joinToString("|")
            // Also update single delimiter for backward compatibility
            if (delimiters.isNotEmpty()) {
                preferences[CUSTOM_DELIMITER_KEY] = delimiters.first()
            }
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

    suspend fun setBedrockSamplingMode(mode: BedrockSamplingMode) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[BEDROCK_SAMPLING_MODE_KEY] = mode.name
        }
    }

    suspend fun setBedrockTemperature(value: Float) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[BEDROCK_TEMPERATURE_KEY] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun setBedrockTopP(value: Float) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[BEDROCK_TOP_P_KEY] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun setBedrockTopK(value: Int) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[BEDROCK_TOP_K_KEY] = value.coerceIn(0, 500)
        }
    }

    suspend fun setBedrockTopKEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[BEDROCK_TOP_K_ENABLED_KEY] = enabled
        }
    }

    suspend fun setBedrockMaxOutputTokens(value: Int) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[BEDROCK_MAX_OUTPUT_TOKENS_KEY] = value.coerceIn(1, 128_000)
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

    suspend fun setResponseLength(length: ResponseLength) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[RESPONSE_LENGTH_KEY] = length.name
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

    suspend fun setUnlockPromptEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[UNLOCK_PROMPT_ENABLED_KEY] = enabled
        }
    }

    suspend fun setNarratorLanguage(language: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[NARRATOR_LANGUAGE_KEY] = language
        }
    }

    suspend fun setAiModelId(modelId: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[AI_MODEL_ID_KEY] = modelId
        }
    }

    // Private Chat TTS Setters
    suspend fun setPrivateTtsEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_TTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPrivateAutoTtsEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_AUTO_TTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPrivateTtsAudioTagsEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_TTS_AUDIO_TAGS_ENABLED_KEY] = enabled
        }
    }

    // Private Chat Advanced Settings Setters
    suspend fun setPrivateThinkingEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_THINKING_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPrivateUnlockPromptEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_UNLOCK_PROMPT_ENABLED_KEY] = enabled
        }
    }

    // Private Chat Custom Prompts Setters
    suspend fun setPrivateConversationStylePrompt(prompt: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_CONVERSATION_STYLE_PROMPT_KEY] = prompt
        }
    }

    suspend fun setPrivateResponseLengthPrompt(prompt: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_RESPONSE_LENGTH_PROMPT_KEY] = prompt
        }
    }

    // Private Chat Display Filter Setters
    suspend fun setPrivateDisplayFilterEnabled(enabled: Boolean) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_DISPLAY_FILTER_ENABLED_KEY] = enabled
        }
    }

    suspend fun setPrivateDisplayFilterOpenBracket(bracket: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_DISPLAY_FILTER_OPEN_BRACKET_KEY] = bracket
        }
    }

    suspend fun setPrivateDisplayFilterCloseBracket(bracket: String) {
        context.chatSettingsDataStore.edit { preferences ->
            preferences[PRIVATE_DISPLAY_FILTER_CLOSE_BRACKET_KEY] = bracket
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
            preferences[BEDROCK_SAMPLING_MODE_KEY] = DEFAULT_BEDROCK_SAMPLING_MODE.name
            preferences[BEDROCK_TEMPERATURE_KEY] = DEFAULT_BEDROCK_TEMPERATURE
            preferences[BEDROCK_TOP_P_KEY] = DEFAULT_BEDROCK_TOP_P
            preferences[BEDROCK_TOP_K_KEY] = DEFAULT_BEDROCK_TOP_K
            preferences[BEDROCK_TOP_K_ENABLED_KEY] = DEFAULT_BEDROCK_TOP_K_ENABLED
            preferences[BEDROCK_MAX_OUTPUT_TOKENS_KEY] = DEFAULT_BEDROCK_MAX_OUTPUT_TOKENS
            preferences[SAFETY_HARASSMENT_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SAFETY_HATE_SPEECH_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SAFETY_SEXUALLY_EXPLICIT_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SAFETY_DANGEROUS_CONTENT_KEY] = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE.name
            preferences[SEPARATE_CHARACTER_DIALOGUE_KEY] = true
            preferences[PROVIDE_CHOICES_ENABLED_KEY] = true
            preferences[RESPONSE_LENGTH_KEY] = ResponseLength.MEDIUM.name
            // TTS defaults
            preferences[TTS_ENABLED_KEY] = false
            preferences[AUTO_TTS_ENABLED_KEY] = false
            preferences[TTS_AUDIO_TAGS_ENABLED_KEY] = false
            preferences[NARRATOR_VOICE_ID_KEY] = DEFAULT_NARRATOR_VOICE_ID
            preferences[TTS_MODEL_ID_KEY] = DEFAULT_TTS_MODEL_ID
            // Unlock Prompt default
            preferences[UNLOCK_PROMPT_ENABLED_KEY] = false
            // Narrator Language default
            preferences[NARRATOR_LANGUAGE_KEY] = "en"
            // AI Model default
            preferences[AI_MODEL_ID_KEY] = DEFAULT_AI_MODEL_ID
        }
    }

    // Utility to get all settings at once (for ViewModel initialization)
    suspend fun getCurrentSettings(): ChatSettings {
        val selectedModelId = aiModelId.first()
        val isBedrock = aiProviderFor(selectedModelId) == AiProvider.BEDROCK
        val geminiSettings = ChatSettings(
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
            bedrockSamplingMode = bedrockSamplingMode.first(),
            bedrockTopKEnabled = bedrockTopKEnabled.first(),
            safetyHarassment = safetyHarassment.first(),
            safetyHateSpeech = safetyHateSpeech.first(),
            safetySexuallyExplicit = safetySexuallyExplicit.first(),
            safetyDangerousContent = safetyDangerousContent.first(),
            separateCharacterDialogue = separateCharacterDialogue.first(),
            provideChoicesEnabled = provideChoicesEnabled.first(),
            responseLength = responseLength.first(),
            // TTS
            ttsEnabled = ttsEnabled.first(),
            autoTtsEnabled = autoTtsEnabled.first(),
            ttsAudioTagsEnabled = ttsAudioTagsEnabled.first(),
            narratorVoiceId = narratorVoiceId.first(),
            ttsModelId = ttsModelId.first(),
            unlockPromptEnabled = unlockPromptEnabled.first(),
            narratorLanguage = narratorLanguage.first(),
            aiModelId = selectedModelId
        )

        return if (isBedrock) {
            applyBedrockGenerationProfile(
                settings = geminiSettings,
                profile = BedrockGenerationProfile(
                    samplingMode = bedrockSamplingMode.first(),
                    temperature = bedrockTemperature.first(),
                    topP = bedrockTopP.first(),
                    topK = bedrockTopK.first(),
                    topKEnabled = bedrockTopKEnabled.first(),
                    maxOutputTokens = bedrockMaxOutputTokens.first()
                )
            )
        } else {
            geminiSettings
        }
    }

    /**
     * Get private chat TTS settings (separate from normal chat)
     */
    suspend fun getPrivateChatSettings(): PrivateChatSettings {
        return PrivateChatSettings(
            ttsEnabled = privateTtsEnabled.first(),
            autoTtsEnabled = privateAutoTtsEnabled.first(),
            ttsAudioTagsEnabled = privateTtsAudioTagsEnabled.first(),
            // These are shared with normal chat settings
            narratorVoiceId = narratorVoiceId.first(),
            ttsModelId = ttsModelId.first(),
            // Advanced settings
            thinkingEnabled = privateThinkingEnabled.first(),
            unlockPromptEnabled = privateUnlockPromptEnabled.first(),
            // Custom prompts
            conversationStylePrompt = privateConversationStylePrompt.first(),
            responseLengthPrompt = privateResponseLengthPrompt.first(),
            // Display filter settings
            displayFilterEnabled = privateDisplayFilterEnabled.first(),
            displayFilterOpenBracket = privateDisplayFilterOpenBracket.first(),
            displayFilterCloseBracket = privateDisplayFilterCloseBracket.first()
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
    val bedrockSamplingMode: BedrockSamplingMode = ChatSettingsManager.DEFAULT_BEDROCK_SAMPLING_MODE,
    val bedrockTopKEnabled: Boolean = ChatSettingsManager.DEFAULT_BEDROCK_TOP_K_ENABLED,
    val safetyHarassment: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetyHateSpeech: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetySexuallyExplicit: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val safetyDangerousContent: SafetyThreshold = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
    val separateCharacterDialogue: Boolean = true,
    val provideChoicesEnabled: Boolean = true,
    val responseLength: ResponseLength = ResponseLength.MEDIUM,
    // TTS Settings
    val ttsEnabled: Boolean = false,
    val autoTtsEnabled: Boolean = false,
    val ttsAudioTagsEnabled: Boolean = false,
    val narratorVoiceId: String = ChatSettingsManager.DEFAULT_NARRATOR_VOICE_ID,
    val ttsModelId: String = ChatSettingsManager.DEFAULT_TTS_MODEL_ID,
    // Unlock Prompt
    val unlockPromptEnabled: Boolean = false,
    // Narrator Language (default English)
    val narratorLanguage: String = "en",
    // AI Model
    val aiModelId: String = ChatSettingsManager.DEFAULT_AI_MODEL_ID
)

internal data class BedrockGenerationProfile(
    val samplingMode: BedrockSamplingMode = ChatSettingsManager.DEFAULT_BEDROCK_SAMPLING_MODE,
    val temperature: Float = ChatSettingsManager.DEFAULT_BEDROCK_TEMPERATURE,
    val topP: Float = ChatSettingsManager.DEFAULT_BEDROCK_TOP_P,
    val topK: Int = ChatSettingsManager.DEFAULT_BEDROCK_TOP_K,
    val topKEnabled: Boolean = ChatSettingsManager.DEFAULT_BEDROCK_TOP_K_ENABLED,
    val maxOutputTokens: Int = ChatSettingsManager.DEFAULT_BEDROCK_MAX_OUTPUT_TOKENS
)

internal fun applyBedrockGenerationProfile(
    settings: ChatSettings,
    profile: BedrockGenerationProfile
): ChatSettings {
    return settings.copy(
        streamingEnabled = false,
        temperature = profile.temperature.coerceIn(0f, 1f),
        topP = profile.topP.coerceIn(0f, 1f),
        topK = profile.topK.coerceIn(0, 500),
        maxOutputTokens = profile.maxOutputTokens.coerceIn(1, 128_000),
        thinkingEnabled = false,
        bedrockSamplingMode = profile.samplingMode,
        bedrockTopKEnabled = profile.topKEnabled
    )
}

/**
 * Settings specifically for private chats (separate from normal chat settings)
 */
data class PrivateChatSettings(
    val ttsEnabled: Boolean = false,
    val autoTtsEnabled: Boolean = false,
    val ttsAudioTagsEnabled: Boolean = false,
    // Voice settings are shared with normal chat
    val narratorVoiceId: String = ChatSettingsManager.DEFAULT_NARRATOR_VOICE_ID,
    val ttsModelId: String = ChatSettingsManager.DEFAULT_TTS_MODEL_ID,
    // Advanced settings for private chat
    val thinkingEnabled: Boolean = false,
    val unlockPromptEnabled: Boolean = false,
    // Custom system prompts (empty = use default)
    val conversationStylePrompt: String = "",
    val responseLengthPrompt: String = "",
    // Display filter settings
    val displayFilterEnabled: Boolean = false,
    val displayFilterOpenBracket: String = ChatSettingsManager.DEFAULT_DISPLAY_FILTER_OPEN_BRACKET,
    val displayFilterCloseBracket: String = ChatSettingsManager.DEFAULT_DISPLAY_FILTER_CLOSE_BRACKET
)
