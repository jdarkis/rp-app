package com.example.rpapp3.viewmodel

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rpapp3.data.ApiKeyManager
import com.example.rpapp3.data.ChatSettings
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.SafetyThreshold
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.World
import com.example.rpapp3.data.repository.CharacterRepository
import com.example.rpapp3.data.repository.ChatRepository
import com.example.rpapp3.data.repository.WorldRepository
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

class ChatViewModel : ViewModel() {
    
    private var apiKeyManager: ApiKeyManager? = null
    private var chatSettingsManager: ChatSettingsManager? = null
    private var currentApiKey: String? = null
    private var currentSettings: ChatSettings = ChatSettings()
    
    private val worldRepository = WorldRepository()
    private val characterRepository = CharacterRepository()
    private val chatRepository = ChatRepository()
    
    // State
    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages
    
    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats
    
    private val _currentChat = MutableStateFlow<Chat?>(null)
    val currentChat: StateFlow<Chat?> = _currentChat
    
    private val _characters = MutableStateFlow<List<Character>>(emptyList())
    val characters: StateFlow<List<Character>> = _characters
    
    private val _world = MutableStateFlow<World?>(null)
    val world: StateFlow<World?> = _world
    
    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value
    
    private val _error = mutableStateOf<String?>(null)
    val error: String? get() = _error.value
    
    // Exposed for viewing in settings
    private val _systemPrompt = MutableStateFlow<String?>(null)
    val systemPrompt: StateFlow<String?> = _systemPrompt
    
    private var generativeModel: GenerativeModel? = null
    private var chatSession: com.google.ai.client.generativeai.Chat? = null
    
    /**
     * Initialize the API key manager and chat settings manager with context
     */
    fun initializeWithContext(context: Context) {
        if (apiKeyManager == null) {
            apiKeyManager = ApiKeyManager.getInstance(context)
            chatSettingsManager = ChatSettingsManager.getInstance(context)
            viewModelScope.launch {
                apiKeyManager?.initializeDefaults()
                // Reset to first key on app start - quotas may have reset
                apiKeyManager?.resetKeyIndex()
                currentApiKey = apiKeyManager?.getCurrentApiKey()
                // Load current settings
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
            }
        }
    }
    
    fun loadChats(worldId: String) {
        viewModelScope.launch {
            chatRepository.getChatsByWorld(worldId)
                .catch { e ->
                    _error.value = e.message
                }
                .collect { chatList ->
                    _chats.value = chatList
                }
        }
    }
    
    fun loadCharactersForSelection(worldId: String) {
        viewModelScope.launch {
            characterRepository.getCharactersByWorld(worldId)
                .catch { e ->
                    _error.value = e.message
                }
                .collect { characterList ->
                    _characters.value = characterList
                }
        }
    }
    
    fun createChat(
        worldId: String,
        title: String,
        characterIds: List<String>,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {

        
        viewModelScope.launch {
            _isLoading.value = true
            
            val chat = Chat(
                worldId = worldId,
                title = title.ifBlank { "New Chat" },
                characterIds = characterIds
            )
            
            chatRepository.createChat(chat)
                .onSuccess { createdChat ->
                    _isLoading.value = false
                    onSuccess(createdChat.id)
                }
                .onFailure { e ->
                    _isLoading.value = false
                    onError(e.message ?: "Failed to create chat")
                }
        }
    }
    
    fun initializeChat(chatId: String, worldId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _messages.clear()
            
            // Load world
            _world.value = worldRepository.getWorld(worldId)
            
            // Load chat
            _currentChat.value = chatRepository.getChat(chatId)
            
            // Load characters
            val chat = _currentChat.value
            if (chat != null) {
                val chars = characterRepository.getCharactersByIds(chat.characterIds)
                _characters.value = chars
                
                // Get current API key
                currentApiKey = apiKeyManager?.getCurrentApiKey()
                
                // Initialize AI model with context
                initializeAI()
            }
            
            // Set loading to false after initial setup
            _isLoading.value = false
            
            // Load messages in a separate coroutine (this flow runs indefinitely)
            chatRepository.getMessages(chatId)
                .catch { e ->
                    _error.value = e.message
                }
                .collect { messageList ->
                    _messages.clear()
                    _messages.addAll(messageList)
                }
        }
    }
    
    private suspend fun initializeAI() {
        val world = _world.value
        val characters = _characters.value
        val apiKey = currentApiKey
        
        // Reload settings in case they changed
        currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
        
        // Characters are optional - proceed even with no characters
        if (apiKey.isNullOrBlank()) {
            _error.value = "No API key configured. Please add one in Settings."
            return
        }
        
        // Build system instructions from world and characters
        val systemInstructions = buildSystemInstructions(world, characters)
        _systemPrompt.value = systemInstructions
        
        // Build safety settings from user preferences
        val safetySettings = buildSafetySettings()
        
        generativeModel = GenerativeModel(
            modelName = "gemini-3-flash-preview",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = currentSettings.temperature
                topP = currentSettings.topP
                topK = currentSettings.topK
                maxOutputTokens = currentSettings.maxOutputTokens
            },
            systemInstruction = content { text(systemInstructions) },
            safetySettings = safetySettings,
            // IMPORTANT: Disable all tools including Google Search grounding
            // This prevents quota errors from the search/grounding feature
            tools = emptyList(),
            toolConfig = ToolConfig(
                functionCallingConfig = FunctionCallingConfig(
                    mode = FunctionCallingConfig.Mode.NONE
                )
            )
        )
        
        // Start chat with existing messages as history
        val history = _messages.map { message ->
            content(role = if (message.isUser) "user" else "model") {
                text(message.text)
            }
        }
        
        chatSession = generativeModel?.startChat(history = history)
    }
    
    /**
     * Build safety settings from user preferences
     */
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
    
    private fun buildSystemInstructions(world: World?, characters: List<Character>): String {
        return buildString {
            appendLine("You are a roleplay AI assistant. You will be playing one or more characters in a collaborative story.")
            appendLine()
            
            if (world != null) {
                appendLine("=== WORLD SETTING ===")
                if (world.name.isNotBlank()) {
                    appendLine("World: ${world.name}")
                }
                if (world.description.isNotBlank()) {
                    appendLine("Description: ${world.description}")
                }
                if (world.writingStyle.isNotBlank()) {
                    appendLine("Writing Style: ${world.writingStyle}")
                }
                if (world.systemInstructions.isNotBlank()) {
                    appendLine("Special Instructions: ${world.systemInstructions}")
                }
                appendLine()
            }
            
            appendLine("=== CHARACTERS YOU PLAY ===")
            characters.forEach { character ->
                appendLine()
                appendLine("--- ${character.name} ---")
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
            }
            
            appendLine()
            appendLine("=== ROLEPLAY GUIDELINES ===")
            appendLine("1. Stay in character at all times")
            appendLine("2. Respond as the character(s) would based on their personality and background")
            appendLine("3. Use the specified writing style for your responses")
            appendLine("4. Be creative and engaging while staying consistent with the world setting")
            appendLine("5. If multiple characters are present, you may respond as any or all of them as appropriate")
            appendLine("6. Write in a narrative style, describing actions, dialogue, and scenes naturally")
        }
    }
    
    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank() || _isLoading.value) return
        
        val chatId = _currentChat.value?.id ?: return
        
        // Add user message
        val userChatMessage = ChatMessage(
            chatId = chatId,
            text = userMessage,
            isUser = true
        )
        _messages.add(userChatMessage)
        
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            // Save user message to Firestore
            chatRepository.addMessage(userChatMessage)
            
            // Try sending with retry on quota errors
            sendMessageWithRetry(userMessage, chatId)
        }
    }
    
    private suspend fun sendMessageWithRetry(
        userMessage: String, 
        chatId: String, 
        keyAttemptNumber: Int = 0,
        rateLimitRetries: Int = 0
    ) {
        // Get total number of available keys
        val totalKeys = apiKeyManager?.apiKeys?.first()?.size ?: 0
        val maxRateLimitRetries = 3
        
        // If we've tried all keys, stop
        if (keyAttemptNumber >= totalKeys && totalKeys > 0) {
            _error.value = "All API keys have exceeded their quota. Please add new keys in Settings or wait for quota reset."
            
            val errorChatMessage = ChatMessage(
                chatId = chatId,
                text = "Error: All API keys have exceeded their quota. Please wait for the daily quota to reset or add new keys in Settings.",
                isUser = false
            )
            _messages.add(errorChatMessage)
            _isLoading.value = false
            return
        }
        
        try {
            // Make sure chat session is initialized
            if (chatSession == null) {
                initializeAI()
            }
            
            if (chatSession == null) {
                _error.value = "Failed to initialize AI. Check your API key in Settings."
                _isLoading.value = false
                return
            }
            
            // Check if streaming is enabled
            if (currentSettings.streamingEnabled) {
                // Streaming mode
                val streamingAiMessage = ChatMessage(
                    chatId = chatId,
                    text = "",
                    isUser = false,
                    characterId = null,
                    characterName = "Narrator"
                )
                _messages.add(streamingAiMessage)
                val messageIndex = _messages.size - 1
                
                val responseFlow = chatSession?.sendMessageStream(userMessage)
                val fullResponse = StringBuilder()
                
                responseFlow?.collect { chunk ->
                    chunk.text?.let { text ->
                        fullResponse.append(text)
                        // Update the message in place for streaming effect
                        _messages[messageIndex] = streamingAiMessage.copy(text = fullResponse.toString())
                    }
                }
                
                // Save final message to Firestore
                val finalMessage = _messages[messageIndex]
                chatRepository.addMessage(finalMessage)
            } else {
                // Non-streaming mode (original behavior)
                val response = chatSession?.sendMessage(userMessage)
                
                response?.text?.let { responseText ->
                    // Parse the response to create separate messages for narrator and characters
                    val parsedMessages = parseResponseIntoMessages(responseText, chatId)
                    
                    parsedMessages.forEach { aiMessage ->
                        _messages.add(aiMessage)
                        // Save AI message to Firestore
                        chatRepository.addMessage(aiMessage)
                    }
                }
            }
            
            _isLoading.value = false
            
        } catch (e: Exception) {
            val errorMessage = e.localizedMessage ?: e.message ?: "An error occurred"
            
            // Check if this is a rate limit error (429) - wait and retry with same key
            if (apiKeyManager?.isRateLimitError(errorMessage) == true) {
                if (rateLimitRetries < maxRateLimitRetries) {
                    // Wait with exponential backoff: 2s, 4s, 8s
                    val delayMs = 2000L * (1 shl rateLimitRetries)
                    delay(delayMs)
                    
                    // Retry with same key
                    sendMessageWithRetry(userMessage, chatId, keyAttemptNumber, rateLimitRetries + 1)
                    return
                }
                // Max rate limit retries reached, try next key
            }
            
            // Check if this is a quota exhausted error - switch to next key
            if (apiKeyManager?.isQuotaError(errorMessage) == true) {
                // Rotate to next key
                val newKey = apiKeyManager?.rotateToNextKey()
                
                if (newKey != null && keyAttemptNumber + 1 < totalKeys) {
                    currentApiKey = newKey
                    // Reinitialize AI with new key
                    chatSession = null
                    generativeModel = null
                    initializeAI()
                    
                    // Small delay before trying next key to avoid rapid-fire requests
                    delay(500L)
                    
                    // Retry with new key, reset rate limit counter
                    sendMessageWithRetry(userMessage, chatId, keyAttemptNumber + 1, 0)
                    return
                } else {
                    // All keys exhausted
                    _error.value = "All API keys have exceeded their quota. Please add new keys in Settings or wait for quota reset."
                    
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: All API keys have exceeded their quota. Please wait for the daily quota to reset or add new keys in Settings.",
                        isUser = false
                    )
                    _messages.add(errorChatMessage)
                    _isLoading.value = false
                    return
                }
            }
            
            // Not a quota error - show the actual error
            _error.value = errorMessage
            
            val errorChatMessage = ChatMessage(
                chatId = chatId,
                text = "Error: $errorMessage",
                isUser = false
            )
            _messages.add(errorChatMessage)
            _isLoading.value = false
        }
    }
    
    /**
     * Create a single narrator message from the AI response.
     * All AI responses come from the Narrator.
     */
    private fun parseResponseIntoMessages(responseText: String, chatId: String): List<ChatMessage> {
        val fullText = responseText.trim()
        if (fullText.isEmpty()) {
            return emptyList()
        }
        
        return listOf(
            ChatMessage(
                chatId = chatId,
                text = fullText,
                isUser = false,
                characterId = null,
                characterName = "Narrator"
            )
        )
    }
    
    /**
     * Delete a single message from the chat
     */
    fun deleteMessage(messageId: String) {
        val chatId = _currentChat.value?.id ?: return
        val messageToDelete = _messages.find { it.id == messageId } ?: return
        
        // Optimistically remove from local state
        val messageIndex = _messages.indexOf(messageToDelete)
        _messages.removeAt(messageIndex)
        
        viewModelScope.launch {
            chatRepository.deleteMessage(chatId, messageId)
                .onFailure {
                    // Restore message if deletion failed
                    if (messageIndex >= 0 && messageIndex <= _messages.size) {
                        _messages.add(messageIndex, messageToDelete)
                    }
                }
        }
    }
    
    /**
     * Regenerate AI response for a specific message.
     * This deletes the AI message and all messages after it,
     * then resends the last user message before it.
     */
    fun regenerateResponse(aiMessageId: String) {
        val chatId = _currentChat.value?.id ?: return
        
        // Find the AI message
        val aiMessageIndex = _messages.indexOfFirst { it.id == aiMessageId }
        if (aiMessageIndex < 0) return
        
        val aiMessage = _messages[aiMessageIndex]
        if (aiMessage.isUser) return // Can only regenerate AI messages
        
        // Find the last user message before this AI message
        var lastUserMessage: ChatMessage? = null
        for (i in (aiMessageIndex - 1) downTo 0) {
            if (_messages[i].isUser) {
                lastUserMessage = _messages[i]
                break
            }
        }
        
        if (lastUserMessage == null) return // No user message to regenerate from
        
        // Get all messages to delete (from aiMessage onwards)
        val messagesToDelete = _messages.subList(aiMessageIndex, _messages.size).toList()
        val messageIdsToDelete = messagesToDelete.map { it.id }
        
        // Remove messages from local state
        _messages.removeAll(messagesToDelete.toSet())
        
        // Delete from Firestore and resend
        viewModelScope.launch {
            chatRepository.deleteMessages(chatId, messageIdsToDelete)
            
            // Resend the user message to get new AI response
            _isLoading.value = true
            sendMessageWithRetry(lastUserMessage.text, chatId)
        }
    }
    
    fun deleteChat(chatId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            // Optimistically remove from local state for instant UI update
            val previousChats = _chats.value
            _chats.value = _chats.value.filter { it.id != chatId }
            
            chatRepository.deleteChat(chatId)
                .onSuccess {
                    onSuccess()
                }
                .onFailure { e ->
                    // Restore the chat if deletion failed
                    _chats.value = previousChats
                    onError(e.message ?: "Failed to delete chat")
                }
        }
    }
    
    fun clearError() {
        _error.value = null
    }
}
