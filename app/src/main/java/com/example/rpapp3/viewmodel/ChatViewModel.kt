package com.example.rpapp3.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import com.example.rpapp3.data.ApiKeyManager
import com.example.rpapp3.data.ChatSettings
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.ElevenLabsService
import com.example.rpapp3.data.SafetyThreshold
import com.example.rpapp3.data.TTSManager
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.ElevenLabsTTSModels
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
    
    // TTS services
    private var elevenLabsService: ElevenLabsService? = null
    private var _ttsManager: TTSManager? = null
    val ttsManager: TTSManager? get() = _ttsManager
    
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
    
    // All characters in the world (for avatar matching in dialogue)
    private val _worldCharacters = MutableStateFlow<List<Character>>(emptyList())
    val worldCharacters: StateFlow<List<Character>> = _worldCharacters
    
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
            elevenLabsService = ElevenLabsService.getInstance(context)
            _ttsManager = TTSManager.getInstance(context)
            viewModelScope.launch {
                apiKeyManager?.initializeDefaults()
                // Reset to first key on app start - quotas may have reset
                apiKeyManager?.resetKeyIndex()
                currentApiKey = apiKeyManager?.getCurrentApiKey()
                // Load current settings
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
                // Initialize ElevenLabs
                elevenLabsService?.initialize()
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
            
            // Load ALL world characters for avatar matching (separate from chat characters)
            try {
                val allChars = characterRepository.getCharactersByWorld(worldId).first()
                _worldCharacters.value = allChars
            } catch (e: Exception) {
                // Ignore errors for world characters loading
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
            
            // Add dialogue format instructions when separateCharacterDialogue is enabled
            if (currentSettings.separateCharacterDialogue) {
                appendLine()
                appendLine("=== DIALOGUE FORMAT ===")
                appendLine("When a character speaks, format their dialogue as:")
                appendLine("[Character Name]:\"What they say\"")
                appendLine()
                appendLine("Example:")
                appendLine("[Eve]:\"What are you doing here?\"")
                appendLine()
                appendLine("Use this format for ALL direct character speech. Narrative descriptions should NOT use this format.")
            }
            
            // Add choices instructions when provideChoicesEnabled is enabled
            if (currentSettings.provideChoicesEnabled) {
                appendLine()
                appendLine("=== CHOICES FORMAT ===")
                appendLine("Provide Choices (Actions & Dialogue): At the end of every response, you MUST provide choices.")
                appendLine("Action Choices: Always provide three distinct, optional actions I could take next.")
                appendLine("Dialogue Choices: Whenever I am in a situation where I would speak, provide three sample dialogue options.")
                appendLine()
                appendLine("Format your choices EXACTLY like this at the END of your response:")
                appendLine("[ACTIONS]")
                appendLine("1. First action option")
                appendLine("2. Second action option")
                appendLine("3. Third action option")
                appendLine("[DIALOGUE]")
                appendLine("a. \"First dialogue option\"")
                appendLine("b. \"Second dialogue option\"")
                appendLine("c. \"Third dialogue option\"")
                appendLine()
                appendLine("IMPORTANT: Always include the [ACTIONS] and [DIALOGUE] markers. If no dialogue is appropriate, you may omit the [DIALOGUE] section.")
            }
            
            // Add audio tag instructions when ttsAudioTagsEnabled is enabled
            if (currentSettings.ttsAudioTagsEnabled) {
                appendLine()
                appendLine("=== AUDIO TAG USAGE FOR TTS ===")
                appendLine("To generate realistic speech, you may use Audio Tags within dialogue. These are performance directions wrapped in square brackets [...].")
                appendLine()
                appendLine("THE GOLDEN RULE - STRATEGIC USAGE:")
                appendLine("• Do NOT overuse tags. Do not tag every sentence or place tags between every few words.")
                appendLine("• Use ONLY when necessary to shift emotion, pace, or volume away from default delivery.")
                appendLine("• Trust the text - for general dialogue, rely on punctuation and words. Use tags only for nuance the text alone cannot convey.")
                appendLine()
                appendLine("CORE TAG CATEGORIES (use sparingly):")
                appendLine("• Emotion: [sad], [excited], [nervous], [sorrowful], [frustrated], [deadpan]")
                appendLine("• Delivery: [whispers], [shouts], [quietly], [dramatic tone], [sarcastically], [matter-of-fact]")
                appendLine("• Pacing: [pause], [rushed], [slows down], [drawn out], [stammers], [hesitates]")
                appendLine("• Non-Verbal: [laughs], [sighs], [clears throat], [gasp], [gulps], [breathing]")
                appendLine("• Character Identity (if required): [French accent], [American accent], [deep voice], [childlike tone]")
                appendLine()
                appendLine("PLACEMENT RULES:")
                appendLine("• Match context - [whispers] fits sneaking, not starting a party")
                appendLine("• Start of line colors entire delivery: [tired] I can't do this anymore.")
                appendLine("• Mid-sentence only for sudden shifts: I was fine, until... [hesitates] until I saw him.")
                appendLine()
                appendLine("EXAMPLES:")
                appendLine("GOOD: [laughing] That was hilarious! I can't believe you did that.")
                appendLine("BAD: [laughing] That [pause] was [excited] hilarious! [breathing] I can't [gasp] believe you did that.")
            }
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
                
                if (responseFlow == null) {
                    // Remove the placeholder message if stream failed to start
                    _messages.removeAt(messageIndex)
                    _error.value = "Failed to get response from AI. Please try again."
                    _isLoading.value = false
                    return
                }
                
                val fullResponse = StringBuilder()
                
                responseFlow.collect { chunk ->
                    chunk.text?.let { text ->
                        fullResponse.append(text)
                        // Update the message in place for streaming effect
                        _messages[messageIndex] = streamingAiMessage.copy(text = fullResponse.toString())
                    }
                }
                
                // Check if we actually got any response content
                if (fullResponse.isEmpty()) {
                    // Remove the empty placeholder message
                    _messages.removeAt(messageIndex)
                    _error.value = "AI returned an empty response. Please try again."
                    _isLoading.value = false
                    return
                }
                
                // Save final message to Firestore
                val finalMessage = _messages[messageIndex]
                chatRepository.addMessage(finalMessage)
            } else {
                // Non-streaming mode (original behavior)
                val response = chatSession?.sendMessage(userMessage)
                val responseText = response?.text
                
                if (responseText.isNullOrBlank()) {
                    _error.value = "AI returned an empty response. Please try again."
                    _isLoading.value = false
                    return
                }
                
                // Parse the response to create separate messages for narrator and characters
                val parsedMessages = parseResponseIntoMessages(responseText, chatId)
                
                parsedMessages.forEach { aiMessage ->
                    _messages.add(aiMessage)
                    // Save AI message to Firestore
                    chatRepository.addMessage(aiMessage)
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
    
    /**
     * Speak a message using TTS
     * Uses character voice if available, otherwise narrator voice from settings
     */
    fun speakMessage(messageId: String) {
        Log.d("ChatViewModel", "speakMessage called with messageId: $messageId")
        Log.d("ChatViewModel", "Messages count: ${_messages.size}")
        
        val message = _messages.find { it.id == messageId }
        if (message == null) {
            Log.e("ChatViewModel", "Message not found for id: $messageId")
            Log.d("ChatViewModel", "Available message IDs: ${_messages.map { it.id }}")
            return
        }
        
        if (message.isUser) {
            Log.d("ChatViewModel", "Skipping user message")
            return
        }
        
        Log.d("ChatViewModel", "Found message: ${message.text.take(50)}...")
        
        viewModelScope.launch {
            try {
                // Reload settings to get latest TTS config
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
                Log.d("ChatViewModel", "TTS enabled: ${currentSettings.ttsEnabled}")
                Log.d("ChatViewModel", "Narrator voice ID: ${currentSettings.narratorVoiceId}")
                Log.d("ChatViewModel", "TTS model ID: ${currentSettings.ttsModelId}")
                
                if (!currentSettings.ttsEnabled) {
                    Log.w("ChatViewModel", "TTS is disabled in settings")
                    return@launch
                }
                
                // Determine voice to use
                var voiceId = currentSettings.narratorVoiceId.takeIf { it.isNotBlank() }
                var language: String? = null
                
                // Check if message has a character with a specific voice
                message.characterId?.let { charId ->
                    val character = _characters.value.find { it.id == charId }
                        ?: _worldCharacters.value.find { it.id == charId }
                    character?.let {
                        if (!it.voiceId.isNullOrBlank()) {
                            voiceId = it.voiceId
                            language = it.language
                            Log.d("ChatViewModel", "Using character voice: $voiceId")
                        }
                    }
                }
                
                if (voiceId.isNullOrBlank()) {
                    Log.e("ChatViewModel", "No voice ID configured - set a narrator voice in Chat Settings")
                    return@launch
                }
                
                val modelId = currentSettings.ttsModelId.takeIf { it.isNotBlank() }
                    ?: ElevenLabsTTSModels.DEFAULT_MODEL_ID
                
                // Clean text - remove dialogue markers and action markers
                val cleanText = message.text
                    .replace(Regex("""\[[^\]]+\]:\s*"""), "") // Remove [Character]: prefixes
                    .replace(Regex("""\[ACTIONS\].*""", RegexOption.DOT_MATCHES_ALL), "") // Remove action choices
                    .trim()
                
                if (cleanText.isBlank()) {
                    Log.w("ChatViewModel", "Message text is empty after cleaning")
                    return@launch
                }
                
                Log.d("ChatViewModel", "Generating TTS: voice=$voiceId, model=$modelId, text=${cleanText.take(100)}...")
                
                val result = elevenLabsService?.textToSpeech(
                    text = cleanText,
                    voiceId = voiceId!!,
                    modelId = modelId,
                    language = language
                )
                
                if (result == null) {
                    Log.e("ChatViewModel", "ElevenLabsService is null!")
                    return@launch
                }
                
                result.onSuccess { audioData ->
                    Log.d("ChatViewModel", "TTS generation successful, playing audio (${audioData.size} bytes)")
                    _ttsManager?.playFromBytes(audioData, messageId)
                }.onFailure { e ->
                    Log.e("ChatViewModel", "TTS generation failed: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exception in speakMessage: ${e.message}", e)
            }
        }
    }
    
    /**
     * Speak text directly using TTS (for per-segment playback)
     * Uses character voice if characterId is provided, otherwise narrator voice
     */
    fun speakText(text: String, characterId: String? = null) {
        Log.d("ChatViewModel", "speakText called with text: ${text.take(50)}..., characterId: $characterId")
        
        viewModelScope.launch {
            try {
                // Reload settings to get latest TTS config
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
                
                if (!currentSettings.ttsEnabled) {
                    Log.w("ChatViewModel", "TTS is disabled in settings")
                    return@launch
                }
                
                // Determine voice to use
                var voiceId = currentSettings.narratorVoiceId.takeIf { it.isNotBlank() }
                var language: String? = null
                
                // Check if we have a character with a specific voice
                characterId?.let { charId ->
                    val character = _characters.value.find { it.id == charId }
                        ?: _worldCharacters.value.find { it.id == charId }
                    character?.let { char ->
                        if (!char.voiceId.isNullOrBlank()) {
                            voiceId = char.voiceId
                            language = char.language
                            Log.d("ChatViewModel", "Using character voice: $voiceId for ${char.name}")
                        }
                    }
                }
                
                if (voiceId.isNullOrBlank()) {
                    Log.e("ChatViewModel", "No voice ID configured - set a narrator voice in Chat Settings")
                    return@launch
                }
                
                val modelId = currentSettings.ttsModelId.takeIf { it.isNotBlank() }
                    ?: ElevenLabsTTSModels.DEFAULT_MODEL_ID
                
                // Clean the text - remove any remaining markers
                val cleanText = text
                    .replace(Regex("""\[[^\]]+\]:\s*"""), "") // Remove [Character]: prefixes
                    .replace(Regex("""\[ACTIONS\].*""", RegexOption.DOT_MATCHES_ALL), "") // Remove action choices
                    .trim()
                
                if (cleanText.isBlank()) {
                    Log.w("ChatViewModel", "Text is empty after cleaning")
                    return@launch
                }
                
                Log.d("ChatViewModel", "Generating TTS: voice=$voiceId, model=$modelId")
                
                // Generate a unique segment ID for tracking
                val segmentId = "${System.currentTimeMillis()}_${text.hashCode()}"
                
                val result = elevenLabsService?.textToSpeech(
                    text = cleanText,
                    voiceId = voiceId!!,
                    modelId = modelId,
                    language = language
                )
                
                if (result == null) {
                    Log.e("ChatViewModel", "ElevenLabsService is null!")
                    return@launch
                }
                
                result.onSuccess { audioData ->
                    Log.d("ChatViewModel", "TTS generation successful, playing audio (${audioData.size} bytes)")
                    _ttsManager?.playFromBytes(audioData, segmentId)
                }.onFailure { e ->
                    Log.e("ChatViewModel", "TTS generation failed: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exception in speakText: ${e.message}", e)
            }
        }
    }
    
    /**
     * Stop any currently playing TTS
     */
    fun stopSpeaking() {
        // Cancel any queued segments first
        ttsSegmentJob?.cancel()
        ttsSegmentJob = null
        _ttsManager?.stop()
    }
    
    /**
     * Pause current TTS playback
     */
    fun pauseSpeaking() {
        _ttsManager?.pause()
    }
    
    /**
     * Resume TTS playback
     */
    fun resumeSpeaking() {
        _ttsManager?.resume()
    }
    
    // Job for sequential segment playback (can be cancelled)
    private var ttsSegmentJob: kotlinx.coroutines.Job? = null
    
    /**
     * Data class for segment info to be spoken
     */
    data class SpeakableSegment(
        val text: String,
        val characterId: String? = null
    )
    
    /**
     * Speak multiple segments sequentially, using appropriate voice for each
     * Pre-fetches next segment's audio while current plays to eliminate gaps
     * Can be cancelled via stopSpeaking()
     */
    fun speakSegmentsSequentially(segments: List<SpeakableSegment>) {
        if (segments.isEmpty()) return
        
        // Cancel any previous sequential playback
        ttsSegmentJob?.cancel()
        
        ttsSegmentJob = viewModelScope.launch {
            var nextAudioDeferred: kotlinx.coroutines.Deferred<ByteArray?>? = null
            var nextSegmentId: String? = null
            
            for (i in segments.indices) {
                if (!isActive) break
                
                val segment = segments[i]
                
                // Get audio for current segment (either pre-fetched or generate now)
                val audioData: ByteArray?
                val segmentId: String
                
                if (nextAudioDeferred != null && nextSegmentId != null) {
                    // Use pre-fetched audio
                    audioData = nextAudioDeferred.await()
                    segmentId = nextSegmentId
                    nextAudioDeferred = null
                    nextSegmentId = null
                } else {
                    // Generate audio for first segment
                    val result = generateAudioForSegment(segment)
                    audioData = result.first
                    segmentId = result.second
                }
                
                if (audioData == null) continue
                
                // Start pre-fetching NEXT segment's audio while this one plays
                if (i + 1 < segments.size && isActive) {
                    val nextSegment = segments[i + 1]
                    nextSegmentId = "${System.currentTimeMillis()}_${nextSegment.text.hashCode()}"
                    nextAudioDeferred = async {
                        generateAudioForSegment(nextSegment).first
                    }
                }
                
                // Play current segment
                _ttsManager?.playFromBytes(audioData, segmentId)
                
                // Wait for playback to complete
                try {
                    _ttsManager?.playbackState?.first { state ->
                        state == TTSPlaybackState.IDLE || state == TTSPlaybackState.ERROR
                    }
                } catch (e: Exception) {
                    Log.w("ChatViewModel", "Error waiting for playback: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Generate audio for a segment and return the audio data and segment ID
     */
    private suspend fun generateAudioForSegment(segment: SpeakableSegment): Pair<ByteArray?, String> {
        try {
            val currentSettings = chatSettingsManager?.getCurrentSettings() 
                ?: return Pair(null, "")
            if (!currentSettings.ttsEnabled) return Pair(null, "")
            
            // Determine voice ID - character-specific or narrator
            var voiceId: String? = null
            var language: String? = null
            
            if (segment.characterId != null) {
                val character = _characters.value.find { it.id == segment.characterId }
                if (character != null && !character.voiceId.isNullOrBlank()) {
                    voiceId = character.voiceId
                    language = character.language
                }
            }
            
            // Fallback to narrator voice
            if (voiceId.isNullOrBlank()) {
                voiceId = currentSettings.narratorVoiceId
            }
            
            if (voiceId.isNullOrBlank()) {
                return Pair(null, "")
            }
            
            val modelId = currentSettings.ttsModelId.takeIf { it.isNotBlank() }
                ?: ElevenLabsTTSModels.DEFAULT_MODEL_ID
            
            // Clean the text
            val cleanText = segment.text
                .replace(Regex("""\\[[^\\]]+\\]:\\s*"""), "")
                .replace(Regex("""\\[ACTIONS\\].*""", RegexOption.DOT_MATCHES_ALL), "")
                .trim()
            
            if (cleanText.isBlank()) return Pair(null, "")
            
            val segmentId = "${System.currentTimeMillis()}_${segment.text.hashCode()}"
            
            val result = elevenLabsService?.textToSpeech(
                text = cleanText,
                voiceId = voiceId,
                modelId = modelId,
                language = language
            ) ?: return Pair(null, segmentId)
            
            return result.fold(
                onSuccess = { audioData -> Pair(audioData, segmentId) },
                onFailure = { e ->
                    Log.e("ChatViewModel", "TTS generation failed: ${e.message}", e)
                    Pair(null, segmentId)
                }
            )
            
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Exception generating audio: ${e.message}", e)
            return Pair(null, "")
        }
    }
    
    /**
     * Speak text and wait for playback to complete (kept for single segment use)
     */
    private suspend fun speakTextAndWait(text: String, characterId: String? = null) {
        val segment = SpeakableSegment(text, characterId)
        val (audioData, segmentId) = generateAudioForSegment(segment)
        
        if (audioData != null) {
            _ttsManager?.playFromBytes(audioData, segmentId)
            
            // Wait for playback to complete
            try {
                _ttsManager?.playbackState?.first { state ->
                    state == TTSPlaybackState.IDLE || state == TTSPlaybackState.ERROR
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Error waiting for playback: ${e.message}")
            }
        }
    }
}
