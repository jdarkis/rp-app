package com.example.rpapp3.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rpapp3.data.ApiKeyManager
import com.example.rpapp3.data.ChatSettings
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.ElevenLabsService
import com.example.rpapp3.data.PrivateChatSettings
import com.example.rpapp3.data.ResponseLength
import com.example.rpapp3.data.SafetyThreshold
import com.example.rpapp3.data.TTSManager
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.repository.CharacterRepository
import com.example.rpapp3.data.repository.ChatRepository
import com.example.rpapp3.data.repository.SettingsRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.FunctionCallingConfig
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.ToolConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel for private 1-on-1 character chats.
 * Key differences from ChatViewModel:
 * - No narrator - character speaks directly
 * - Single character only
 * - Can include context from world chats
 */
class PrivateChatViewModel : ViewModel() {
    private val chatRepository = ChatRepository()
    private val characterRepository = CharacterRepository()
    private val settingsRepository = SettingsRepository()
    
    private var apiKeyManager: ApiKeyManager? = null
    private var chatSettingsManager: ChatSettingsManager? = null
    private var elevenLabsService: ElevenLabsService? = null
    private var _ttsManager: TTSManager? = null
    private var appContext: Context? = null
    
    private var currentApiKey: String? = null
    private var currentSettings: ChatSettings = ChatSettings()
    private var privateChatSettings: PrivateChatSettings = PrivateChatSettings()
    
    // State
    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat
    
    private val _character = MutableStateFlow<Character?>(null)
    val character: StateFlow<Character?> = _character
    
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages
    
    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value
    
    private val _error = mutableStateOf<String?>(null)
    val error: String? get() = _error.value
    
    // Available world chats for context selection
    private val _availableContextChats = MutableStateFlow<List<Chat>>(emptyList())
    val availableContextChats: StateFlow<List<Chat>> = _availableContextChats
    
    // TTS State
    val ttsPlaybackState: StateFlow<TTSPlaybackState>
        get() = _ttsManager?.playbackState ?: MutableStateFlow(TTSPlaybackState.IDLE)
    val currentPlayingSegmentId: StateFlow<String?>
        get() = _ttsManager?.currentPlayingId ?: MutableStateFlow(null)
    
    // System prompt for viewing
    private val _systemPrompt = MutableStateFlow<String?>(null)
    val systemPrompt: StateFlow<String?> = _systemPrompt
    
    // Display filter settings for UI
    private val _displayFilterSettings = MutableStateFlow(DisplayFilterSettings())
    val displayFilterSettings: StateFlow<DisplayFilterSettings> = _displayFilterSettings
    
    // Pagination state for chat messages
    private val _hasMoreMessages = mutableStateOf(false)
    val hasMoreMessages: Boolean get() = _hasMoreMessages.value
    private val _isLoadingMore = mutableStateOf(false)
    val isLoadingMore: Boolean get() = _isLoadingMore.value
    private var oldestLoadedTimestamp: Long = Long.MAX_VALUE
    
    // Full message history for AI context (full history, not paginated)
    private val _fullMessageHistory = mutableStateListOf<ChatMessage>()
    
    private var generativeModel: GenerativeModel? = null
    private var chatSession: com.google.ai.client.generativeai.Chat? = null
    
    fun initializeWithContext(context: Context) {
        if (apiKeyManager == null) {
            appContext = context.applicationContext
            apiKeyManager = ApiKeyManager.getInstance(context)
            chatSettingsManager = ChatSettingsManager.getInstance(context)
            elevenLabsService = ElevenLabsService.getInstance(context)
            _ttsManager = TTSManager.getInstance(context)
            viewModelScope.launch {
                apiKeyManager?.initializeDefaults()
                apiKeyManager?.resetKeyIndex()
                currentApiKey = apiKeyManager?.getCurrentApiKey()
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
                privateChatSettings = chatSettingsManager?.getPrivateChatSettings() ?: PrivateChatSettings()
                // Update display filter settings for UI
                _displayFilterSettings.value = DisplayFilterSettings(
                    enabled = privateChatSettings.displayFilterEnabled,
                    openBracket = privateChatSettings.displayFilterOpenBracket,
                    closeBracket = privateChatSettings.displayFilterCloseBracket
                )
                elevenLabsService?.initialize()
            }
        }
    }
    
    /**
     * Initialize or get existing private chat for a character
     */
    fun initializePrivateChat(characterId: String, worldId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _messages.clear()
            _fullMessageHistory.clear()
            _hasMoreMessages.value = false
            oldestLoadedTimestamp = Long.MAX_VALUE
            
            // Load available world chats for context FIRST (before blocking calls)
            val worldChats = chatRepository.getWorldChatsForContext(worldId)
            _availableContextChats.value = worldChats
            
            // Load character
            val char = characterRepository.getCharacter(characterId)
            _character.value = char
            
            if (char == null) {
                _error.value = "Character not found"
                _isLoading.value = false
                return@launch
            }
            
            // Get or create private chat
            chatRepository.getOrCreatePrivateChat(characterId, worldId, char.name)
                .onSuccess { chat ->
                    _currentChat.value = chat
                    
                    // Load FULL message history for AI context (all messages)
                    try {
                        val allMessages = chatRepository.getMessagesOnce(chat.id)
                        _fullMessageHistory.addAll(allMessages)
                    } catch (e: Exception) {
                        // Ignore errors loading full history
                    }
                    
                    // Load PAGINATED messages for UI display (only recent messages)
                    try {
                        val (recentMessages, hasMore) = chatRepository.getMessagesPagedInitial(chat.id, 20)
                        _messages.addAll(recentMessages)
                        _hasMoreMessages.value = hasMore
                        if (recentMessages.isNotEmpty()) {
                            oldestLoadedTimestamp = recentMessages.first().timestamp
                        }
                    } catch (e: Exception) {
                        // Fallback: use full history if pagination fails
                        _messages.addAll(_fullMessageHistory)
                    }
                    
                    // Get current API key and initialize AI with FULL history
                    currentApiKey = apiKeyManager?.getCurrentApiKey()
                    initializeAI()
                    
                    _isLoading.value = false
                    
                    // Listen for NEW messages only (real-time updates)
                    val latestTimestamp = _fullMessageHistory.lastOrNull()?.timestamp ?: System.currentTimeMillis()
                    viewModelScope.launch {
                        chatRepository.observeNewMessages(chat.id, latestTimestamp)
                            .catch { e -> _error.value = e.message }
                            .collect { newMessage ->
                                // Check if message is not already in the list (avoid duplicates)
                                if (_messages.none { it.id == newMessage.id }) {
                                    _messages.add(newMessage)
                                }
                                // Also add to full history for AI context
                                if (_fullMessageHistory.none { it.id == newMessage.id }) {
                                    _fullMessageHistory.add(newMessage)
                                }
                            }
                    }
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Failed to create private chat"
                    _isLoading.value = false
                }
        }
    }
    
    /**
     * Load more older messages for UI display (pagination)
     */
    fun loadMoreMessages() {
        if (_isLoadingMore.value || !_hasMoreMessages.value) return
        
        val chatId = _currentChat.value?.id ?: return
        
        viewModelScope.launch {
            _isLoadingMore.value = true
            
            try {
                val (olderMessages, hasMore) = chatRepository.getMessagesOlder(
                    chatId = chatId,
                    beforeTimestamp = oldestLoadedTimestamp,
                    limit = 50
                )
                
                if (olderMessages.isNotEmpty()) {
                    // Prepend older messages to the UI list
                    _messages.addAll(0, olderMessages)
                    oldestLoadedTimestamp = olderMessages.first().timestamp
                }
                
                _hasMoreMessages.value = hasMore
            } catch (e: Exception) {
                _error.value = "Failed to load more messages: ${e.message}"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
    
    private suspend fun initializeAI() {
        val char = _character.value
        val apiKey = currentApiKey
        
        currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
        privateChatSettings = chatSettingsManager?.getPrivateChatSettings() ?: PrivateChatSettings()
        
        // Update display filter settings for UI
        _displayFilterSettings.value = DisplayFilterSettings(
            enabled = privateChatSettings.displayFilterEnabled,
            openBracket = privateChatSettings.displayFilterOpenBracket,
            closeBracket = privateChatSettings.displayFilterCloseBracket
        )
        
        if (apiKey.isNullOrBlank()) {
            _error.value = "No API key configured. Please add one in Settings."
            return
        }
        
        if (char == null) {
            _error.value = "Character not found"
            return
        }
        
        // Build private chat system instructions (no narrator!)
        val baseSystemInstructions = buildPrivateChatSystemInstructions(char)
        
        // Add explicit instructions to avoid external tools for Pro models to mitigate quota issues
        val extraInstructions = if (currentSettings.aiModelId.contains("pro", ignoreCase = true)) {
            "\n\n=== TOOL USAGE ===\nDo NOT use any external tools, search engines, or grounding. Rely ONLY on your internal knowledge and the provided context."
        } else {
            ""
        }
        
        val systemInstructions = baseSystemInstructions + extraInstructions
        _systemPrompt.value = systemInstructions
        
        val safetySettings = buildSafetySettings()
        
        generativeModel = GenerativeModel(
            modelName = currentSettings.aiModelId,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = currentSettings.temperature
                topP = currentSettings.topP
                topK = currentSettings.topK
                maxOutputTokens = currentSettings.maxOutputTokens
            },
            systemInstruction = content { text(systemInstructions) },
            safetySettings = safetySettings
        )
        
        // Start chat with FULL message history (not paginated UI messages)
        val history = _fullMessageHistory.map { message ->
            content(role = if (message.isUser) "user" else "model") {
                text(message.text)
            }
        }
        
        chatSession = generativeModel?.startChat(history = history)
    }
    
    /**
     * Build system instructions for private chat - NO NARRATOR, direct conversation
     */
    private suspend fun buildPrivateChatSystemInstructions(character: Character): String {
        // Load unlock prompt if enabled for private chat
        val unlockPrompt = if (privateChatSettings.unlockPromptEnabled) {
            settingsRepository.getUnlockPromptOnce()
        } else {
            ""
        }
        
        return buildString {
            // Prepend unlock prompt if enabled
            if (unlockPrompt.isNotBlank()) {
                appendLine("=== UNLOCK PROMPT ===")
                appendLine(unlockPrompt)
                appendLine()
            }
            
            appendLine("You ARE ${character.name}. This is a private, direct conversation.")
            appendLine("You are NOT a narrator. You are NOT an AI assistant. You ARE this character.")
            appendLine()
            
            appendLine("=== YOUR CHARACTER ===")
            if (character.description.isNotBlank()) {
                appendLine("Background: ${character.description}")
            }
            if (character.appearance.isNotBlank()) {
                appendLine("Appearance: ${character.appearance}")
            }
            if (character.personality.isNotBlank()) {
                appendLine("Personality: ${character.personality}")
            }
            if (character.systemInstructions.isNotBlank()) {
                appendLine("Special Instructions: ${character.systemInstructions}")
            }
            
            // Language
            val languageName = getLanguageName(character.language)
            appendLine("Language: You MUST speak in $languageName.")
            appendLine()
            
            // Conversation Style - use custom prompt if set, otherwise default
            appendLine("=== CONVERSATION STYLE ===")
            val customConversationStyle = privateChatSettings.conversationStylePrompt
            if (customConversationStyle.isNotBlank()) {
                appendLine(customConversationStyle.replace("{CHARACTER_NAME}", character.name))
            } else {
                appendLine(ChatSettingsManager.DEFAULT_CONVERSATION_STYLE_PROMPT.replace("{CHARACTER_NAME}", character.name))
            }
            appendLine()
            
            // Response length - use custom prompt if set, otherwise default based on enum
            appendLine("=== RESPONSE LENGTH ===")
            val customResponseLength = privateChatSettings.responseLengthPrompt
            if (customResponseLength.isNotBlank()) {
                appendLine(customResponseLength)
            } else {
                when (currentSettings.responseLength) {
                    ResponseLength.SHORT -> appendLine("Keep messages SHORT - 1-3 sentences max, like quick texts.")
                    ResponseLength.MEDIUM -> appendLine("Keep messages MODERATE - a short paragraph, conversational.")
                    ResponseLength.LONG -> appendLine("You can write LONGER messages when appropriate.")
                    ResponseLength.VERY_LONG -> appendLine("Write as much as needed to fully express yourself.")
                }
            }
            
            // Context from world chats (if any)
            val chat = _currentChat.value
            if (chat != null && chat.contextChatIds.isNotEmpty()) {
                appendLine()
                appendLine("=== MEMORIES FROM PAST INTERACTIONS ===")
                appendLine("You have the following memories from previous roleplay sessions:")
                
                for (contextChatId in chat.contextChatIds) {
                    try {
                        val contextMessages = chatRepository.getMessagesOnce(contextChatId)
                        if (contextMessages.isNotEmpty()) {
                            appendLine()
                            appendLine("--- Memory fragment ---")
                            contextMessages.forEach { msg ->
                                val prefix = if (msg.isUser) "User" else msg.characterName ?: "AI"
                                // Strip action/dialogue choices from AI messages
                                val cleanedText = if (!msg.isUser) {
                                    val actionsIndex = msg.text.indexOf("[ACTIONS]")
                                    if (actionsIndex != -1) {
                                        msg.text.substring(0, actionsIndex).trim()
                                    } else {
                                        msg.text
                                    }
                                } else {
                                    msg.text
                                }
                                appendLine("$prefix: $cleanedText")
                            }
                        }
                    } catch (e: Exception) {
                        // Skip this context if loading fails
                    }
                }
                appendLine()
                appendLine("Use these memories to inform your responses, but focus on the current conversation.")
            }
            
            // TTS tags if enabled (using private chat settings)
            if (privateChatSettings.ttsAudioTagsEnabled) {
                appendLine()
                appendLine("=== AUDIO EXPRESSION ===")
                appendLine("You may use audio tags in brackets to express emotion: [laughs], [sighs], [whispers], etc.")
                appendLine("Use sparingly and naturally within your dialogue.")
            }
            
            // Writing style instructions (stored per-chat in Firebase)
            if (chat != null && chat.writingStyle.isNotBlank()) {
                appendLine()
                appendLine("=== WRITING STYLE ===")
                appendLine(chat.writingStyle)
            }
        }
    }
    
    private fun getLanguageName(code: String): String {
        return when (code) {
            "en" -> "English"
            "ru" -> "Russian"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "pt" -> "Portuguese"
            "zh" -> "Chinese"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "ar" -> "Arabic"
            "hi" -> "Hindi"
            "pl" -> "Polish"
            "uk" -> "Ukrainian"
            "lt" -> "Lithuanian"
            "tr" -> "Turkish"
            "nl" -> "Dutch"
            "sv" -> "Swedish"
            else -> code
        }
    }
    
    private fun buildSafetySettings(): List<SafetySetting> {
        fun mapThreshold(threshold: SafetyThreshold): BlockThreshold {
            return when (threshold) {
                SafetyThreshold.BLOCK_NONE -> BlockThreshold.NONE
                SafetyThreshold.BLOCK_ONLY_HIGH -> BlockThreshold.ONLY_HIGH
                SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE -> BlockThreshold.MEDIUM_AND_ABOVE
                SafetyThreshold.BLOCK_LOW_AND_ABOVE -> BlockThreshold.LOW_AND_ABOVE
            }
        }
        
        return listOf(
            SafetySetting(HarmCategory.HARASSMENT, mapThreshold(currentSettings.safetyHarassment)),
            SafetySetting(HarmCategory.HATE_SPEECH, mapThreshold(currentSettings.safetyHateSpeech)),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, mapThreshold(currentSettings.safetySexuallyExplicit)),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, mapThreshold(currentSettings.safetyDangerousContent))
        )
    }
    
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _isLoading.value) return
        
        val chatId = _currentChat.value?.id ?: return
        val char = _character.value ?: return
        
        // Add user message
        val userChatMessage = ChatMessage(
            chatId = chatId,
            text = userMessage,
            isUser = true
        )
        _messages.add(userChatMessage)
        _fullMessageHistory.add(userChatMessage)
        
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            chatRepository.addMessage(userChatMessage)
            sendMessageWithRetry(userMessage, chatId, char)
        }
    }
    
    private suspend fun sendMessageWithRetry(
        userMessage: String,
        chatId: String,
        character: Character,
        keyAttemptNumber: Int = 0,
        rateLimitRetries: Int = 0
    ) {
        val totalKeys = apiKeyManager?.apiKeys?.first()?.size ?: 0
        val maxRateLimitRetries = 3
        
        if (keyAttemptNumber >= totalKeys && totalKeys > 0) {
            _error.value = "All API keys have exceeded their quota."
            
            val errorChatMessage = ChatMessage(
                chatId = chatId,
                text = "Error: All API keys have exceeded their quota. Please add new keys in Settings or wait for quota reset.",
                isUser = false,
                characterId = character.id,
                characterName = character.name
            )
            _messages.add(errorChatMessage)
            
            _isLoading.value = false
            return
        }
        
        try {
            if (chatSession == null) {
                initializeAI()
            }
            
            if (chatSession == null) {
                _error.value = "Failed to initialize AI. Check your API key."
                
                val errorChatMessage = ChatMessage(
                    chatId = chatId,
                    text = "Error: Failed to initialize AI. Check your API key in Settings.",
                    isUser = false,
                    characterId = character.id,
                    characterName = character.name
                )
                _messages.add(errorChatMessage)
                
                _isLoading.value = false
                return
            }
            
            // Refresh chat session with FULL message history
            if (generativeModel != null) {
                val history = _fullMessageHistory.map { message ->
                    content(role = if (message.isUser) "user" else "model") {
                        text(message.text)
                    }
                }
                chatSession = generativeModel?.startChat(history = history)
            }
            
            if (currentSettings.streamingEnabled) {
                // Streaming mode
                val streamingMessage = ChatMessage(
                    chatId = chatId,
                    text = "",
                    isUser = false,
                    characterId = character.id,
                    characterName = character.name
                )
                _messages.add(streamingMessage)
                val messageIndex = _messages.size - 1
                
                val responseFlow = chatSession?.sendMessageStream(userMessage)
                
                if (responseFlow == null) {
                    _messages.removeAt(messageIndex)
                    _error.value = "Failed to get response from AI."
                    
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: Failed to get response from AI. Please try again.",
                        isUser = false,
                        characterId = character.id,
                        characterName = character.name
                    )
                    _messages.add(errorChatMessage)
                    
                    _isLoading.value = false
                    return
                }
                
                val fullResponse = StringBuilder()
                
                responseFlow.collect { chunk ->
                    chunk.text?.let { text ->
                        fullResponse.append(text)
                        _messages[messageIndex] = streamingMessage.copy(text = fullResponse.toString())
                    }
                }
                
                if (fullResponse.isEmpty()) {
                    _messages.removeAt(messageIndex)
                    _error.value = "AI returned an empty response."
                    
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: AI returned an empty response. Please try again.",
                        isUser = false,
                        characterId = character.id,
                        characterName = character.name
                    )
                    _messages.add(errorChatMessage)
                    
                    _isLoading.value = false
                    return
                }
                
                val finalMessage = _messages[messageIndex]
                _fullMessageHistory.add(finalMessage)
                chatRepository.addMessage(finalMessage)
            } else {
                // Non-streaming mode
                val response = chatSession?.sendMessage(userMessage)
                val responseText = response?.text
                
                if (responseText.isNullOrBlank()) {
                    _error.value = "AI returned an empty response."
                    
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: AI returned an empty response. Please try again.",
                        isUser = false,
                        characterId = character.id,
                        characterName = character.name
                    )
                    _messages.add(errorChatMessage)
                    
                    _isLoading.value = false
                    return
                }
                
                val aiMessage = ChatMessage(
                    chatId = chatId,
                    text = responseText.trim(),
                    isUser = false,
                    characterId = character.id,
                    characterName = character.name
                )
                _messages.add(aiMessage)
                _fullMessageHistory.add(aiMessage)
                chatRepository.addMessage(aiMessage)
            }
            
            _isLoading.value = false
            
        } catch (e: Exception) {
            // Extract full error details including cause
            val errorMessage = buildString {
                append(e.localizedMessage ?: e.message ?: "An unknown error occurred")
                e.cause?.let { cause ->
                    append(" Caused by: ${cause.localizedMessage ?: cause.message}")
                }
            }

            
            if (apiKeyManager?.isRateLimitError(errorMessage) == true) {
                if (rateLimitRetries < maxRateLimitRetries) {
                    val delayMs = 2000L * (1 shl rateLimitRetries)
                    delay(delayMs)
                    sendMessageWithRetry(userMessage, chatId, character, keyAttemptNumber, rateLimitRetries + 1)
                    return
                }
            }
            
            if (apiKeyManager?.isQuotaError(errorMessage) == true) {
                val newKey = apiKeyManager?.rotateToNextKey()
                if (newKey != null && keyAttemptNumber + 1 < totalKeys) {
                    currentApiKey = newKey
                    chatSession = null
                    generativeModel = null
                    initializeAI()
                    delay(500L)
                    sendMessageWithRetry(userMessage, chatId, character, keyAttemptNumber + 1, 0)
                    return
                } else {
                    _error.value = "All API keys have exceeded their quota."
                    
                    // Add error as chat message so user sees it in chat
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: All API keys have exceeded their quota. Please wait for the daily quota to reset or add new keys in Settings.",
                        isUser = false,
                        characterId = character.id,
                        characterName = character.name
                    )
                    _messages.add(errorChatMessage)
                    
                    _isLoading.value = false
                    return
                }
            }
            
            _error.value = errorMessage
            
            // Add error as chat message so user sees it in chat
            val errorChatMessage = ChatMessage(
                chatId = chatId,
                text = "Error: $errorMessage",
                isUser = false,
                characterId = character.id,
                characterName = character.name
            )
            _messages.add(errorChatMessage)
            
            _isLoading.value = false
        }
    }
    
    fun deleteMessage(messageId: String) {
        val chatId = _currentChat.value?.id ?: return
        val messageToDelete = _messages.find { it.id == messageId } ?: return
        val messageIndex = _messages.indexOf(messageToDelete)
        _messages.removeAt(messageIndex)
        
        viewModelScope.launch {
            chatRepository.deleteMessage(chatId, messageId)
                .onFailure {
                    if (messageIndex >= 0 && messageIndex <= _messages.size) {
                        _messages.add(messageIndex, messageToDelete)
                    }
                }
        }
    }
    
    fun updateContextChatIds(contextChatIds: List<String>) {
        val chatId = _currentChat.value?.id ?: return
        
        viewModelScope.launch {
            chatRepository.updateContextChatIds(chatId, contextChatIds)
                .onSuccess {
                    _currentChat.value = _currentChat.value?.copy(contextChatIds = contextChatIds)
                    // Reinitialize AI with new context
                    chatSession = null
                    generativeModel = null
                    initializeAI()
                }
        }
    }
    
    fun updateWritingStyle(writingStyle: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        val chatId = _currentChat.value?.id ?: return
        
        viewModelScope.launch {
            chatRepository.updateWritingStyle(chatId, writingStyle)
                .onSuccess {
                    _currentChat.value = _currentChat.value?.copy(writingStyle = writingStyle)
                    // Reinitialize AI with new writing style
                    chatSession = null
                    generativeModel = null
                    initializeAI()
                    onSuccess()
                }
                .onFailure { e ->
                    onError(e.message ?: "Failed to save writing style")
                }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Restart the private chat by clearing all messages
     * The chat record is kept, only messages are deleted
     */
    fun restartChat(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val chatId = _currentChat.value?.id ?: return
        
        viewModelScope.launch {
            chatRepository.restartPrivateChat(chatId)
                .onSuccess {
                    // Clear local messages
                    _messages.clear()
                    // Reinitialize AI session with empty history
                    chatSession = null
                    generativeModel = null
                    initializeAI()
                    onSuccess()
                }
                .onFailure { e ->
                    onError(e.message ?: "Failed to restart chat")
                }
        }
    }
    
    // TTS Functions
    fun speakText(text: String) {
        val char = _character.value ?: return
        
        viewModelScope.launch {
            try {
                val voiceId = char.voiceId ?: currentSettings.narratorVoiceId
                val modelId = currentSettings.ttsModelId
                
                val audioResult = elevenLabsService?.textToSpeech(text, voiceId, modelId)
                audioResult?.getOrNull()?.let { audioData ->
                    _ttsManager?.playFromBytes(audioData, null)
                }
            } catch (e: Exception) {
                Log.e("PrivateChatVM", "TTS error: ${e.message}")
            }
        }
    }
    
    fun stopSpeaking() {
        viewModelScope.launch {
            _ttsManager?.stop()
        }
    }
    
    fun pauseSpeaking() {
        _ttsManager?.pause()
    }
    
    fun resumeSpeaking() {
        _ttsManager?.resume()
    }
}

/**
 * Settings for the display filter feature
 */
data class DisplayFilterSettings(
    val enabled: Boolean = false,
    val openBracket: String = ChatSettingsManager.DEFAULT_DISPLAY_FILTER_OPEN_BRACKET,
    val closeBracket: String = ChatSettingsManager.DEFAULT_DISPLAY_FILTER_CLOSE_BRACKET
)
