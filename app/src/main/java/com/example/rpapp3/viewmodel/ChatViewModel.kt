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
import com.example.rpapp3.data.AiProvider
import com.example.rpapp3.data.BedrockApiKeyManager
import com.example.rpapp3.data.BedrockConverseRequest
import com.example.rpapp3.data.BedrockGenerationException
import com.example.rpapp3.data.BedrockService
import com.example.rpapp3.data.buildBedrockRequestDetails
import com.example.rpapp3.data.buildBedrockMessages
import com.example.rpapp3.data.buildGeminiRequestDetails
import com.example.rpapp3.data.ChatSettings
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.ElevenLabsService
import com.example.rpapp3.data.InworldService
import com.example.rpapp3.data.ResponseLength
import com.example.rpapp3.data.SafetyThreshold
import com.example.rpapp3.data.TTSManager
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.TtsGenerationState
import com.example.rpapp3.data.TtsProvider
import com.example.rpapp3.data.TtsRequestResolver
import com.example.rpapp3.data.ResolvedTtsRequest
import com.example.rpapp3.data.StorySummarizerService
import com.example.rpapp3.data.SummaryDetailLevel
import com.example.rpapp3.data.SummaryResult
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.ModelRequestDetails
import com.example.rpapp3.data.model.ModelRequestStatus
import com.example.rpapp3.data.model.SegmentAudioCache
import com.example.rpapp3.data.model.SummaryProposal
import com.example.rpapp3.data.model.World
import com.example.rpapp3.ui.chat.SelectedUpdates
import com.example.rpapp3.data.repository.CharacterRepository
import com.example.rpapp3.data.repository.ChatRepository
import com.example.rpapp3.data.repository.MediaStorageService
import com.example.rpapp3.data.repository.SegmentAudioRepository
import com.example.rpapp3.data.repository.SettingsRepository
import com.example.rpapp3.data.repository.VersionHistoryRepository
import com.example.rpapp3.data.repository.WorldRepository
import com.example.rpapp3.core.util.LanguageUtils
import com.example.rpapp3.core.util.SafetySettingsBuilder
import com.example.rpapp3.core.constants.AppConstants
import com.example.rpapp3.core.constants.ErrorMessages
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.FunctionCallingConfig
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.ToolConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {
    
    private var apiKeyManager: ApiKeyManager? = null
    private var bedrockApiKeyManager: BedrockApiKeyManager? = null
    private var chatSettingsManager: ChatSettingsManager? = null
    private var currentApiKey: String? = null
    private var currentSettings: ChatSettings = ChatSettings()
    private var currentAiProvider: AiProvider = AiProvider.GEMINI
    private var bedrockReady: Boolean = false
    
    // TTS services
    private var elevenLabsService: ElevenLabsService? = null
    private var inworldService: InworldService? = null
    private var _ttsManager: TTSManager? = null
    val ttsManager: TTSManager? get() = _ttsManager
    private val _ttsGenerationState = MutableStateFlow(TtsGenerationState())
    val ttsGenerationState: StateFlow<TtsGenerationState> = _ttsGenerationState
    private var ttsGenerationJob: Job? = null
    
    private val worldRepository = WorldRepository()
    private val characterRepository = CharacterRepository()
    private val chatRepository = ChatRepository()
    private val settingsRepository = SettingsRepository()
    private val segmentAudioRepository = SegmentAudioRepository()
    private val mediaStorageService = MediaStorageService()
    private val versionHistoryRepository = VersionHistoryRepository()
    
    // Application context for media uploads
    private var appContext: Context? = null
    
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
    
    // Cached audio URLs for current message (segmentIndex -> audioUrl)
    private val _cachedAudioUrls = MutableStateFlow<Map<String, Map<Int, String>>>(emptyMap())
    val cachedAudioUrls: StateFlow<Map<String, Map<Int, String>>> = _cachedAudioUrls
    
    // Story summarizer state
    private var storySummarizerService: StorySummarizerService? = null
    private var bedrockService: BedrockService? = null
    private val _summaryProposal = MutableStateFlow<SummaryProposal?>(null)
    val summaryProposal: StateFlow<SummaryProposal?> = _summaryProposal
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing
    private val _summaryError = MutableStateFlow<String?>(null)
    val summaryError: StateFlow<String?> = _summaryError
    
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
    
    /**
     * Initialize the API key manager and chat settings manager with context
     */
    fun initializeWithContext(context: Context) {
        if (apiKeyManager == null) {
            appContext = context.applicationContext
            apiKeyManager = ApiKeyManager.getInstance(context)
            bedrockApiKeyManager = BedrockApiKeyManager.getInstance(context)
            chatSettingsManager = ChatSettingsManager.getInstance(context)
            elevenLabsService = ElevenLabsService.getInstance(context)
            inworldService = InworldService.getInstance(context)
            _ttsManager = TTSManager.getInstance(context)
            storySummarizerService = StorySummarizerService(context)
            bedrockService = BedrockService()
            viewModelScope.launch {
                apiKeyManager?.initializeDefaults()
                // Reset to first key on app start - quotas may have reset
                apiKeyManager?.resetKeyIndex()
                currentApiKey = apiKeyManager?.getCurrentApiKey()
                // Load current settings
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
                // Initialize ElevenLabs
                elevenLabsService?.initialize()
                inworldService?.initialize()
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
                    // Filter out private chats - they are accessed via character details, not the chat list
                    _chats.value = chatList.filter { !it.isPrivateChat }
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
        onError: (String) -> Unit,
        extendFromChatId: String? = null,
        extendMessageCount: Int = 10
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
                    // If extending from another chat, copy the last N messages
                    if (extendFromChatId != null) {
                        try {
                            val sourceMessages = chatRepository.getLastNMessages(extendFromChatId, extendMessageCount)
                            
                            // Copy each message to the new chat with new IDs and sequential timestamps
                            var baseTimestamp = System.currentTimeMillis() - (sourceMessages.size * 1000L)
                            for (sourceMessage in sourceMessages) {
                                val newMessage = sourceMessage.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    chatId = createdChat.id,
                                    timestamp = baseTimestamp
                                )
                                chatRepository.addMessage(newMessage)
                                baseTimestamp += 1000L // 1 second apart to ensure order
                            }
                        } catch (e: Exception) {
                            // Log error but don't fail the chat creation
                            android.util.Log.e("ChatViewModel", "Failed to copy extend messages: ${e.message}")
                        }
                    }
                    
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
            _fullMessageHistory.clear()
            _hasMoreMessages.value = false
            oldestLoadedTimestamp = Long.MAX_VALUE
            
            // Load world
            _world.value = worldRepository.getWorld(worldId)
            
            // Load chat
            _currentChat.value = chatRepository.getChat(chatId)
            
            // Load FULL message history for AI context (all messages)
            try {
                val allMessages = chatRepository.getMessagesOnce(chatId)
                _fullMessageHistory.addAll(allMessages)
            } catch (e: Exception) {
                // Ignore errors loading full history
            }
            
            // Load PAGINATED messages for UI display (only recent messages)
            try {
                val (recentMessages, hasMore) = chatRepository.getMessagesPagedInitial(chatId, AppConstants.INITIAL_PAGE_SIZE)
                _messages.addAll(recentMessages)
                _hasMoreMessages.value = hasMore
                if (recentMessages.isNotEmpty()) {
                    oldestLoadedTimestamp = recentMessages.first().timestamp
                }
            } catch (e: Exception) {
                // Fallback: use full history if pagination fails
                _messages.addAll(_fullMessageHistory)
            }
            
            // Load characters
            val chat = _currentChat.value
            if (chat != null) {
                val chars = characterRepository.getCharactersByIds(chat.characterIds)
                _characters.value = chars
                
                // Get current API key
                currentApiKey = apiKeyManager?.getCurrentApiKey()
                
                // Initialize AI model with FULL history context
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
            
            // Listen for NEW messages only (real-time updates for new messages)
            val latestTimestamp = _fullMessageHistory.lastOrNull()?.timestamp ?: System.currentTimeMillis()
            chatRepository.observeNewMessages(chatId, latestTimestamp)
                .catch { e ->
                    _error.value = e.message
                }
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
                    limit = AppConstants.LOAD_MORE_PAGE_SIZE
                )
                
                if (olderMessages.isNotEmpty()) {
                    // Prepend older messages to the UI list
                    _messages.addAll(0, olderMessages)
                    oldestLoadedTimestamp = olderMessages.first().timestamp
                }
                
                _hasMoreMessages.value = hasMore
            } catch (e: Exception) {
                _error.value = ErrorMessages.loadMoreFailed(e.message)
            } finally {
                _isLoadingMore.value = false
            }
        }
    }
    
    private suspend fun initializeAI(excludedMessageId: String? = null) {
        val world = _world.value
        val characters = _characters.value
        
        // Reload settings in case they changed
        currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
        currentAiProvider = ChatSettingsManager.aiProviderFor(currentSettings.aiModelId)
        
        val systemInstructions = if (currentSettings.systemPromptEnabled) {
            val unlockPrompt = if (currentSettings.unlockPromptEnabled) {
                settingsRepository.getUnlockPromptOnce()
            } else {
                ""
            }
            val globalSystemPrompt = settingsRepository.getSystemPromptOnce()
            val baseSystemInstructions = buildSystemInstructions(
                world = world,
                characters = characters,
                unlockPrompt = unlockPrompt,
                globalSystemPrompt = globalSystemPrompt
            )
            val extraInstructions = if (currentSettings.aiModelId.contains("pro", ignoreCase = true)) {
                "\n\n=== TOOL USAGE ===\nDo NOT use any external tools, search engines, or grounding. Rely ONLY on your internal knowledge and the provided context."
            } else {
                ""
            }
            baseSystemInstructions + extraInstructions
        } else {
            ""
        }
        _systemPrompt.value = systemInstructions

        if (currentAiProvider == AiProvider.BEDROCK) {
            generativeModel = null
            chatSession = null
            val bedrockApiKey = bedrockApiKeyManager?.getApiKey()
            bedrockReady = !bedrockApiKey.isNullOrBlank()
            if (!bedrockReady) {
                _error.value = "No Bedrock API key configured. Please add one in Settings."
            }
            return
        }

        bedrockReady = false
        val apiKey = currentApiKey ?: apiKeyManager?.getCurrentApiKey()

        // Characters are optional - proceed even with no characters
        if (apiKey.isNullOrBlank()) {
            _error.value = "No API key configured. Please add one in Settings."
            return
        }
        currentApiKey = apiKey
        
        // Build safety settings from user preferences
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
            systemInstruction = systemInstructions
                .takeIf { it.isNotBlank() }
                ?.let { prompt -> content { text(prompt) } },
            safetySettings = safetySettings
        )
        
        // Start chat with FULL message history (not paginated UI messages)
        val history = modelHistory(excludedMessageId).map { message ->
            content(role = if (message.isUser) "user" else "model") {
                text(message.text)
            }
        }
        
        chatSession = generativeModel?.startChat(history = history)
    }
    
    /**
     * Build safety settings from user preferences.
     * @see SafetySettingsBuilder for the centralized implementation.
     */
    private fun buildSafetySettings(): List<SafetySetting> = 
        SafetySettingsBuilder.build(currentSettings)

    private fun isAiReady(): Boolean {
        return when (currentAiProvider) {
            AiProvider.GEMINI -> chatSession != null
            AiProvider.BEDROCK -> bedrockReady
        }
    }

    private fun modelHistory(excludedMessageId: String? = null): List<ChatMessage> {
        return _fullMessageHistory.filter { message ->
            excludedMessageId == null || message.id != excludedMessageId
        }
    }

    private fun buildSystemInstructions(
        world: World?,
        characters: List<Character>,
        unlockPrompt: String = "",
        globalSystemPrompt: String = ""
    ): String {
        return buildString {
            // Prepend unlock prompt at the very top if enabled
            if (unlockPrompt.isNotBlank()) {
                appendLine("=== UNLOCK PROMPT ===")
                appendLine(unlockPrompt)
                appendLine()
            }
            
            if (globalSystemPrompt.isNotBlank()) {
                appendLine(globalSystemPrompt)
                appendLine()
            }
            
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
                // Add language instruction for the character
                val languageName = LanguageUtils.getLanguageName(character.language)
                appendLine("Language: This character MUST speak and respond in $languageName. All dialogue from ${character.name} should be in $languageName.")
            }
            
            appendLine()
            appendLine("=== ROLEPLAY GUIDELINES ===")
            appendLine("1. Stay in character at all times")
            appendLine("2. Respond as the character(s) would based on their personality and background")
            appendLine("3. Use the specified writing style for your responses")
            appendLine("4. Be creative and engaging while staying consistent with the world setting")
            appendLine("5. If multiple characters are present, you may respond as any or all of them as appropriate")
            appendLine("6. Write in a narrative style, describing actions, dialogue, and scenes naturally")
            
            // Add narrator language instructions
            appendLine()
            appendLine("=== NARRATOR LANGUAGE ===")
            val narratorLanguageName = LanguageUtils.getLanguageName(currentSettings.narratorLanguage)
            appendLine("Write all NARRATION (descriptions, actions, scene-setting, internal thoughts exposition) in $narratorLanguageName.")
            appendLine("IMPORTANT: Character DIALOGUE should still be in each character's specified language, NOT the narrator language.")
            appendLine("Only the narrative prose between dialogue should be in $narratorLanguageName.")
            
            // Add response length instructions
            appendLine()
            appendLine("=== RESPONSE LENGTH ===")
            when (currentSettings.responseLength) {
                ResponseLength.SHORT -> {
                    appendLine("Keep your responses SHORT and CONCISE.")
                    appendLine("Aim for 1-2 paragraphs per response.")
                    appendLine("Focus on the most important actions and dialogue, avoiding unnecessary descriptions.")
                    appendLine("Get to the point quickly and let the story move forward efficiently.")
                }
                ResponseLength.MEDIUM -> {
                    appendLine("Keep your responses at a MODERATE length.")
                    appendLine("Aim for 2-3 paragraphs per response.")
                    appendLine("Balance action, dialogue, and description. Include enough detail to be immersive without being overly verbose.")
                }
                ResponseLength.LONG -> {
                    appendLine("Write DETAILED and THOROUGH responses.")
                    appendLine("Aim for 4-5 paragraphs per response.")
                    appendLine("Include rich descriptions of scenes, character emotions, and actions.")
                    appendLine("Take time to develop the narrative and atmosphere.")
                }
                ResponseLength.VERY_LONG -> {
                    appendLine("Write ELABORATE and EXTENSIVE responses.")
                    appendLine("There is no limit on response length - be as detailed as the scene requires.")
                    appendLine("Include comprehensive descriptions, inner thoughts, environmental details, and nuanced character interactions.")
                    appendLine("Fully develop each scene with immersive storytelling.")
                }
            }
            
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
                appendLine("1. I [first action in first person]")
                appendLine("2. I [second action in first person]")
                appendLine("3. I [third action in first person]")
                appendLine()
                appendLine("IMPORTANT: Action choices MUST be written in first person perspective, starting with 'I'. For example: 'I approach the door cautiously', 'I draw my sword', 'I ask her about the artifact'.")
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
                appendLine("CRITICAL FORMATTING RULE:")
                appendLine("Audio tags MUST ALWAYS be placed INSIDE the quotation marks, never outside.")
                appendLine("CORRECT: \"[sighs] Hi, how are you?\"")
                appendLine("INCORRECT: [sighs]\"Hi, how are you?\"")
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
                appendLine("• Audio tags must be inside quotation marks, as part of the spoken dialogue")
                appendLine("• Match context - [whispers] fits sneaking, not starting a party")
                appendLine("• Start of dialogue colors entire delivery: \"[tired] I can't do this anymore.\"")
                appendLine("• Mid-sentence only for sudden shifts: \"I was fine, until... [hesitates] until I saw him.\"")
                appendLine("• ALWAYS end each character's COMPLETE speech with a single [pause] tag at the very end (NOT after every sentence): \"Hello. It is so nice to see you. I can't believe that we haven't seen each other in 5 years. [pause]\"")
                appendLine()
                appendLine("EXAMPLES:")
                appendLine("GOOD: \"[laughing] That was hilarious! I can't believe you did that.\"")
                appendLine("BAD: [laughing]\"That was hilarious! I can't believe you did that.\"")
                appendLine("BAD: \"[laughing] That [pause] was [excited] hilarious! [breathing] I can't [gasp] believe you did that.\"")
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
        _fullMessageHistory.add(userChatMessage)
        
        _isLoading.value = true
        _error.value = null
        
        viewModelScope.launch {
            // Save user message to Firestore
            chatRepository.addMessage(userChatMessage)
            
            // Try sending with retry on quota errors
            sendMessageWithRetry(userChatMessage, chatId)
        }
    }
    
    private suspend fun sendMessageWithRetry(
        userChatMessage: ChatMessage,
        chatId: String, 
        keyAttemptNumber: Int = 0,
        rateLimitRetries: Int = 0,
        requestDetailsRecorded: Boolean = false
    ) {
        val userMessage = userChatMessage.text
        currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
        currentAiProvider = ChatSettingsManager.aiProviderFor(currentSettings.aiModelId)
        var detailsRecorded = requestDetailsRecorded

        // Get total number of available keys
        val totalKeys = if (currentAiProvider == AiProvider.GEMINI) {
            apiKeyManager?.apiKeys?.first()?.size ?: 0
        } else {
            1
        }
        val maxRateLimitRetries = 3
        
        // If we've tried all keys, stop
        if (currentAiProvider == AiProvider.GEMINI && keyAttemptNumber >= totalKeys && totalKeys > 0) {
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
            // Reinitialize with the latest settings and with the pending user message excluded
            // from prior history, so it is sent exactly once to the selected provider.
            initializeAI(excludedMessageId = userChatMessage.id)

            if (!isAiReady()) {
                val setupError = if (currentAiProvider == AiProvider.BEDROCK) {
                    "Failed to initialize Bedrock. Check your Bedrock API key in Settings."
                } else {
                    "Failed to initialize AI. Check your API key in Settings."
                }
                if (!detailsRecorded) {
                    saveRequestDetails(
                        userMessage = userChatMessage,
                        chatId = chatId,
                        status = ModelRequestStatus.NOT_SENT,
                        failureReason = setupError
                    )
                    detailsRecorded = true
                }
                _error.value = setupError
                
                val errorChatMessage = ChatMessage(
                    chatId = chatId,
                    text = "Error: $setupError",
                    isUser = false
                )
                _messages.add(errorChatMessage)
                
                _isLoading.value = false
                return
            }

            if (currentAiProvider == AiProvider.BEDROCK) {
                val apiKey = bedrockApiKeyManager?.getApiKey()
                if (apiKey.isNullOrBlank()) {
                    throw BedrockGenerationException("No Bedrock API key configured. Please add one in Settings.")
                }
                val bedrockRequest = BedrockConverseRequest(
                    modelId = currentSettings.aiModelId,
                    systemPrompt = _systemPrompt.value.orEmpty(),
                    messages = buildBedrockMessages(_fullMessageHistory, userChatMessage),
                    settings = currentSettings
                )
                if (!detailsRecorded) {
                    persistRequestDetails(
                        buildBedrockRequestDetails(
                            chatId = chatId,
                            userMessage = userChatMessage,
                            request = bedrockRequest
                        )
                    )
                    detailsRecorded = true
                }
                val responseText = bedrockService?.converseWithRetry(
                    apiKey = apiKey,
                    request = bedrockRequest
                )?.text

                if (responseText.isNullOrBlank()) {
                    _error.value = "AI returned an empty response. Please try again."

                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: AI returned an empty response. Please try again.",
                        isUser = false
                    )
                    _messages.add(errorChatMessage)

                    _isLoading.value = false
                    return
                }

                val parsedMessages = parseResponseIntoMessages(responseText, chatId)

                parsedMessages.forEach { aiMessage ->
                    _messages.add(aiMessage)
                    _fullMessageHistory.add(aiMessage)
                    chatRepository.addMessage(aiMessage)
                }

                _isLoading.value = false
                return
            }

            if (!detailsRecorded) {
                persistRequestDetails(
                    buildGeminiRequestDetails(
                        chatId = chatId,
                        userMessage = userChatMessage,
                        history = modelHistory(userChatMessage.id),
                        systemPrompt = _systemPrompt.value.orEmpty(),
                        settings = currentSettings
                    )
                )
                detailsRecorded = true
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
                    
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: Failed to get response from AI. Please try again.",
                        isUser = false
                    )
                    _messages.add(errorChatMessage)
                    
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
                    
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: AI returned an empty response. Please try again.",
                        isUser = false
                    )
                    _messages.add(errorChatMessage)
                    
                    _isLoading.value = false
                    return
                }
                
                // Save final message to Firestore and add to full history
                val finalMessage = _messages[messageIndex]
                _fullMessageHistory.add(finalMessage)
                chatRepository.addMessage(finalMessage)
            } else {
                // Non-streaming mode (original behavior)
                val response = chatSession?.sendMessage(userMessage)
                val responseText = response?.text
                
                if (responseText.isNullOrBlank()) {
                    _error.value = "AI returned an empty response. Please try again."
                    
                    val errorChatMessage = ChatMessage(
                        chatId = chatId,
                        text = "Error: AI returned an empty response. Please try again.",
                        isUser = false
                    )
                    _messages.add(errorChatMessage)
                    
                    _isLoading.value = false
                    return
                }
                
                // Parse the response to create separate messages for narrator and characters
                val parsedMessages = parseResponseIntoMessages(responseText, chatId)
                
                parsedMessages.forEach { aiMessage ->
                    _messages.add(aiMessage)
                    _fullMessageHistory.add(aiMessage)
                    // Save AI message to Firestore
                    chatRepository.addMessage(aiMessage)
                }
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
            if (!detailsRecorded) {
                saveRequestDetails(
                    userMessage = userChatMessage,
                    chatId = chatId,
                    status = ModelRequestStatus.NOT_SENT,
                    failureReason = errorMessage
                )
                detailsRecorded = true
            }

            if (currentAiProvider == AiProvider.BEDROCK) {
                val bedrockErrorMessage = BedrockService.userFacingErrorMessage(e)
                _error.value = bedrockErrorMessage

                val errorChatMessage = ChatMessage(
                    chatId = chatId,
                    text = "Error: $bedrockErrorMessage",
                    isUser = false
                )
                _messages.add(errorChatMessage)
                _isLoading.value = false
                return
            }
            
            // Check if this is a rate limit error (429) - wait and retry with same key
            if (apiKeyManager?.isRateLimitError(errorMessage) == true) {
                if (rateLimitRetries < maxRateLimitRetries) {
                    // Wait with exponential backoff: 2s, 4s, 8s
                    val delayMs = 2000L * (1 shl rateLimitRetries)
                    delay(delayMs)
                    
                    // Retry with same key
                    sendMessageWithRetry(
                        userChatMessage,
                        chatId,
                        keyAttemptNumber,
                        rateLimitRetries + 1,
                        detailsRecorded
                    )
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
                    initializeAI(excludedMessageId = userChatMessage.id)
                    
                    // Small delay before trying next key to avoid rapid-fire requests
                    delay(500L)
                    
                    // Retry with new key, reset rate limit counter
                    sendMessageWithRetry(
                        userChatMessage,
                        chatId,
                        keyAttemptNumber + 1,
                        0,
                        detailsRecorded
                    )
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
            _fullMessageHistory.removeAll(messagesToDelete.toSet())
            sendMessageWithRetry(lastUserMessage, chatId)
        }
    }

    private suspend fun saveRequestDetails(
        userMessage: ChatMessage,
        chatId: String,
        status: ModelRequestStatus,
        failureReason: String
    ) {
        val details = when (currentAiProvider) {
            AiProvider.GEMINI -> buildGeminiRequestDetails(
                chatId = chatId,
                userMessage = userMessage,
                history = modelHistory(userMessage.id),
                systemPrompt = _systemPrompt.value.orEmpty(),
                settings = currentSettings,
                status = status,
                failureReason = failureReason
            )
            AiProvider.BEDROCK -> buildBedrockRequestDetails(
                chatId = chatId,
                userMessage = userMessage,
                request = BedrockConverseRequest(
                    modelId = currentSettings.aiModelId,
                    systemPrompt = _systemPrompt.value.orEmpty(),
                    messages = buildBedrockMessages(_fullMessageHistory, userMessage),
                    settings = currentSettings
                ),
                status = status,
                failureReason = failureReason
            )
        }
        persistRequestDetails(details)
    }

    private suspend fun persistRequestDetails(details: ModelRequestDetails) {
        chatRepository.saveModelRequestDetails(details)
            .onFailure { error ->
                Log.w("ChatViewModel", "Failed to save model request details", error)
            }
    }

    suspend fun getModelRequestDetails(messageId: String): Result<ModelRequestDetails?> {
        val chatId = _currentChat.value?.id
            ?: return Result.failure(IllegalStateException("Chat is not loaded"))
        return chatRepository.getModelRequestDetails(chatId, messageId)
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
    
    fun duplicateChat(chatId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            chatRepository.duplicateChat(chatId)
                .onSuccess {
                    _isLoading.value = false
                    onSuccess()
                }
                .onFailure { e ->
                    _isLoading.value = false
                    onError(e.message ?: "Failed to duplicate chat")
                }
        }
    }
    
    fun updateChatCharacters(
        chatId: String,
        characterIds: List<String>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            chatRepository.updateChatCharacters(chatId, characterIds)
                .onSuccess {
                    // Update local state if this is the current chat
                    _currentChat.value?.let { chat ->
                        if (chat.id == chatId) {
                            _currentChat.value = chat.copy(characterIds = characterIds)
                        }
                    }
                    onSuccess()
                }
                .onFailure { e ->
                    onError(e.message ?: "Failed to update chat characters")
                }
        }
    }
    
    fun clearError() {
        _error.value = null
    }

    fun clearTtsError() {
        _ttsGenerationState.value = _ttsGenerationState.value.copy(errorMessage = null)
    }
    
    // ==================== STORY SUMMARIZER METHODS ====================
    
    /**
     * Generate a story summary and proposed character/world updates
     */
    fun generateStorySummary(detailLevel: SummaryDetailLevel = SummaryDetailLevel.MEDIUM) {
        if (_isSummarizing.value) return
        
        viewModelScope.launch {
            _isSummarizing.value = true
            _summaryError.value = null
            _summaryProposal.value = null
            
            // Use full message history, not just paginated UI messages
            val messages = _fullMessageHistory.toList()
            val characters = _characters.value
            val world = _world.value
            
            when (val result = storySummarizerService?.summarizeStory(messages, characters, world, detailLevel)) {
                is SummaryResult.Success -> {
                    _summaryProposal.value = result.proposal
                }
                is SummaryResult.Error -> {
                    _summaryError.value = result.message
                }
                null -> {
                    _summaryError.value = "Summarizer service not initialized"
                }
            }
            
            _isSummarizing.value = false
        }
    }
    
    /**
     * Apply selected updates from the summary proposal
     */
    fun applySummaryUpdates(
        selectedUpdates: SelectedUpdates,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val proposal = _summaryProposal.value ?: return
        
        viewModelScope.launch {
            try {
                // Apply character updates
                proposal.characterUpdates.forEach { charUpdate ->
                    val character = _characters.value.find { it.id == charUpdate.characterId } ?: return@forEach
                    
                    var updatedCharacter = character
                    var hasChanges = false
                    
                    // Apply background if selected
                    if (selectedUpdates.characterBackgrounds[charUpdate.characterId] == true && charUpdate.backgroundNew != null) {
                        updatedCharacter = updatedCharacter.copy(description = charUpdate.backgroundNew)
                        hasChanges = true
                    }
                    
                    // Apply appearance if selected
                    if (selectedUpdates.characterAppearances[charUpdate.characterId] == true && charUpdate.appearanceNew != null) {
                        updatedCharacter = updatedCharacter.copy(appearance = charUpdate.appearanceNew)
                        hasChanges = true
                    }
                    
                    // Apply personality if selected
                    if (selectedUpdates.characterPersonalities[charUpdate.characterId] == true && charUpdate.personalityNew != null) {
                        updatedCharacter = updatedCharacter.copy(personality = charUpdate.personalityNew)
                        hasChanges = true
                    }
                    
                    // Save version history before updating, then apply changes
                    if (hasChanges) {
                        versionHistoryRepository.saveCharacterVersion(character, "summarizer")
                        characterRepository.updateCharacter(updatedCharacter)
                    }
                }
                
                // Apply world update if selected
                if (selectedUpdates.worldDescription && proposal.worldUpdateProposal?.descriptionNew != null) {
                    val world = _world.value
                    if (world != null) {
                        // Save version history before updating
                        versionHistoryRepository.saveWorldVersion(world, "summarizer")
                        val updatedWorld = world.copy(description = proposal.worldUpdateProposal.descriptionNew)
                        worldRepository.updateWorld(updatedWorld)
                        _world.value = updatedWorld
                    }
                }
                
                // Refresh characters state
                val currentChat = _currentChat.value
                if (currentChat != null) {
                    val updatedChars = characterRepository.getCharactersByIds(currentChat.characterIds)
                    _characters.value = updatedChars
                }
                
                // Clear the proposal
                _summaryProposal.value = null
                
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to apply updates")
            }
        }
    }
    
    /**
     * Dismiss the summary proposal without applying changes
     */
    fun dismissSummaryProposal() {
        _summaryProposal.value = null
        _summaryError.value = null
    }
    
    /**
     * Clear summary error
     */
    fun clearSummaryError() {
        _summaryError.value = null
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
        
        ttsGenerationJob?.cancel()
        ttsGenerationJob = viewModelScope.launch {
            startTtsGeneration(messageId)
            try {
                // Reload settings to get latest TTS config
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
                Log.d("ChatViewModel", "TTS enabled: ${currentSettings.ttsEnabled}")
                Log.d("ChatViewModel", "Narrator voice ID: ${currentSettings.narratorVoiceId}")
                Log.d("ChatViewModel", "TTS model ID: ${currentSettings.ttsModelId}")
                
                if (!currentSettings.ttsEnabled) {
                    Log.w("ChatViewModel", "TTS is disabled in settings")
                    finishTtsGeneration(messageId)
                    return@launch
                }

                val request = resolveTtsRequest(message.characterId, currentSettings)
                if (request == null) {
                    failTtsGeneration(messageId, "No TTS voice is configured")
                    return@launch
                }
                
                // Clean text - remove dialogue markers and action markers
                val cleanText = message.text
                    .replace(Regex("""\[[^\]]+\]:\s*"""), "") // Remove [Character]: prefixes
                    .replace(Regex("""\[ACTIONS\].*""", RegexOption.DOT_MATCHES_ALL), "") // Remove action choices
                    .trim()
                
                if (cleanText.isBlank()) {
                    Log.w("ChatViewModel", "Message text is empty after cleaning")
                    failTtsGeneration(messageId, "There is no speakable text")
                    return@launch
                }
                
                Log.d("ChatViewModel", "Generating TTS: provider=${request.provider}, voice=${request.voiceId}, model=${request.modelId}")
                val result = synthesizeSpeech(cleanText, request)
                
                if (result == null) {
                    failTtsGeneration(messageId, "TTS service is unavailable")
                    return@launch
                }
                
                result.onSuccess { audioData ->
                    Log.d("ChatViewModel", "TTS generation successful, playing audio (${audioData.size} bytes)")
                    finishTtsGeneration(messageId)
                    _ttsManager?.playFromBytes(audioData, messageId)
                }.onFailure { e ->
                    Log.e("ChatViewModel", "TTS generation failed: ${e.message}", e)
                    failTtsGeneration(messageId, ttsFailureMessage(e))
                }
            } catch (error: CancellationException) {
                finishTtsGeneration(messageId)
                throw error
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exception in speakMessage: ${e.message}", e)
                failTtsGeneration(messageId, ttsFailureMessage(e))
            }
        }
    }
    
    /**
     * Speak text directly using TTS (for per-segment playback)
     * Uses character voice if characterId is provided, otherwise narrator voice
     */
    fun speakText(text: String, characterId: String? = null) {
        Log.d("ChatViewModel", "speakText called with text: ${text.take(50)}..., characterId: $characterId")

        val segmentId = "${System.currentTimeMillis()}_${text.hashCode()}"
        ttsGenerationJob?.cancel()
        ttsGenerationJob = viewModelScope.launch {
            startTtsGeneration(segmentId)
            try {
                // Reload settings to get latest TTS config
                currentSettings = chatSettingsManager?.getCurrentSettings() ?: ChatSettings()
                
                if (!currentSettings.ttsEnabled) {
                    Log.w("ChatViewModel", "TTS is disabled in settings")
                    finishTtsGeneration(segmentId)
                    return@launch
                }

                val request = resolveTtsRequest(characterId, currentSettings)
                if (request == null) {
                    failTtsGeneration(segmentId, "No TTS voice is configured")
                    return@launch
                }
                
                // Clean the text - remove any remaining markers
                val cleanText = text
                    .replace(Regex("""\[[^\]]+\]:\s*"""), "") // Remove [Character]: prefixes
                    .replace(Regex("""\[ACTIONS\].*""", RegexOption.DOT_MATCHES_ALL), "") // Remove action choices
                    .trim()
                
                if (cleanText.isBlank()) {
                    Log.w("ChatViewModel", "Text is empty after cleaning")
                    failTtsGeneration(segmentId, "There is no speakable text")
                    return@launch
                }
                
                Log.d("ChatViewModel", "Generating TTS: provider=${request.provider}, voice=${request.voiceId}, model=${request.modelId}")
                val result = synthesizeSpeech(cleanText, request)
                
                if (result == null) {
                    failTtsGeneration(segmentId, "TTS service is unavailable")
                    return@launch
                }
                
                result.onSuccess { audioData ->
                    Log.d("ChatViewModel", "TTS generation successful, playing audio (${audioData.size} bytes)")
                    finishTtsGeneration(segmentId)
                    _ttsManager?.playFromBytes(audioData, segmentId)
                }.onFailure { e ->
                    Log.e("ChatViewModel", "TTS generation failed: ${e.message}", e)
                    failTtsGeneration(segmentId, ttsFailureMessage(e))
                }
            } catch (error: CancellationException) {
                finishTtsGeneration(segmentId)
                throw error
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exception in speakText: ${e.message}", e)
                failTtsGeneration(segmentId, ttsFailureMessage(e))
            }
        }
    }
    
    /**
     * Speak text directly using TTS with caching support.
     * Generates audio, plays it, and uploads to Cloudinary for future playback.
     */
    fun speakTextWithCaching(
        text: String,
        characterId: String? = null,
        messageId: String,
        segmentIndex: Int
    ) {
        Log.d("ChatViewModel", "speakTextWithCaching called: messageId=$messageId, segment=$segmentIndex")

        val playId = "${messageId}_$segmentIndex"
        ttsGenerationJob?.cancel()
        ttsGenerationJob = viewModelScope.launch {
            startTtsGeneration(playId)
            try {
                val segment = SpeakableSegment(text, characterId)
                val generationResult = generateAudioForSegment(
                    segment = segment,
                    messageId = messageId,
                    segmentIndex = segmentIndex
                )

                if (generationResult.audioData != null) {
                    finishTtsGeneration(playId)
                    _ttsManager?.playFromBytes(generationResult.audioData, playId)
                } else {
                    failTtsGeneration(
                        playId,
                        generationResult.errorMessage ?: "Unable to generate speech"
                    )
                }
            } catch (error: CancellationException) {
                finishTtsGeneration(playId)
                throw error
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Exception in speakTextWithCaching: ${e.message}", e)
                failTtsGeneration(playId, ttsFailureMessage(e))
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
        ttsGenerationJob?.cancel()
        ttsGenerationJob = null
        _ttsGenerationState.value = TtsGenerationState()
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
    
    /**
     * Replay TTS from the beginning (after playback completed)
     */
    fun replaySpeaking() {
        _ttsManager?.replay()
    }
    
    // Job for sequential segment playback (can be cancelled)
    private var ttsSegmentJob: Job? = null
    
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
     * @param messageId Optional message ID to enable audio caching for each segment
     */
    fun speakSegmentsSequentially(segments: List<SpeakableSegment>, messageId: String? = null) {
        if (segments.isEmpty()) return
        
        // Cancel any previous sequential playback
        ttsSegmentJob?.cancel()
        
        ttsSegmentJob = viewModelScope.launch {
            var nextAudioDeferred: kotlinx.coroutines.Deferred<SegmentAudioResult>? = null
            var nextSegmentId: String? = null
            
            for (i in segments.indices) {
                if (!isActive) break
                
                val segment = segments[i]
                
                // Get audio for current segment (either pre-fetched or generate now)
                val generationResult: SegmentAudioResult
                val playId = "${messageId}_$i"
                
                if (nextAudioDeferred != null && nextSegmentId != null) {
                    // Use pre-fetched audio
                    startTtsGeneration(playId)
                    generationResult = nextAudioDeferred.await()
                    nextAudioDeferred = null
                    nextSegmentId = null
                } else {
                    // Generate audio for first segment (with caching if messageId provided)
                    startTtsGeneration(playId)
                    generationResult = generateAudioForSegment(segment, messageId, i)
                }
                
                val audioData = generationResult.audioData
                if (audioData == null) {
                    failTtsGeneration(
                        playId,
                        generationResult.errorMessage ?: "Unable to generate speech"
                    )
                    break
                }
                finishTtsGeneration(playId)
                
                // Start pre-fetching NEXT segment's audio while this one plays
                if (i + 1 < segments.size && isActive) {
                    val nextSegment = segments[i + 1]
                    val nextIndex = i + 1
                    nextSegmentId = "${messageId}_$nextIndex"
                    nextAudioDeferred = async {
                        generateAudioForSegment(nextSegment, messageId, nextIndex)
                    }
                }
                
                // Play current segment
                _ttsManager?.playFromBytes(audioData, "${messageId}_$i")
                
                // Wait for playback to complete
                // IMPORTANT: First wait for PLAYING state (playFromBytes calls stop() which briefly sets IDLE)
                // Then wait for completion, otherwise we catch the transient IDLE state
                try {
                    // Wait for playback to actually start
                    _ttsManager?.playbackState?.first { state ->
                        state == TTSPlaybackState.PLAYING || state == TTSPlaybackState.ERROR
                    }
                    
                    // Now wait for playback to finish
                    _ttsManager?.playbackState?.first { state ->
                        state == TTSPlaybackState.IDLE || state == TTSPlaybackState.ERROR || state == TTSPlaybackState.COMPLETED
                    }
                    // Add a small delay after completion to ensure audio buffer is fully flushed
                    // This prevents cutting off the end of dialogue when switching segments
                    if (i + 1 < segments.size) {
                        delay(150L) // 150ms buffer to allow audio to fully finish
                    }
                } catch (e: Exception) {
                    Log.w("ChatViewModel", "Error waiting for playback: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Generate audio for a segment and return the audio data and segment ID.
     * Optionally uploads to Cloudinary and caches the URL if messageId and segmentIndex are provided.
     */
    private data class SegmentAudioResult(
        val audioData: ByteArray?,
        val segmentId: String,
        val errorMessage: String? = null
    )

    private suspend fun generateAudioForSegment(
        segment: SpeakableSegment,
        messageId: String? = null,
        segmentIndex: Int? = null
    ): SegmentAudioResult {
        try {
            val currentSettings = chatSettingsManager?.getCurrentSettings() 
                ?: return SegmentAudioResult(null, "", "TTS settings are unavailable")
            if (!currentSettings.ttsEnabled) {
                return SegmentAudioResult(null, "", "TTS is disabled")
            }
            
            // Clean the text
            val cleanText = segment.text
                .replace(Regex("""\[[^\]]+\]:\s*"""), "")
                .replace(Regex("""\[ACTIONS\].*""", RegexOption.DOT_MATCHES_ALL), "")
                .trim()
            
            if (cleanText.isBlank()) {
                return SegmentAudioResult(null, "", "There is no speakable text")
            }
            
            val segmentId = "${System.currentTimeMillis()}_${segment.text.hashCode()}"

            val request = resolveTtsRequest(segment.characterId, currentSettings)
                ?: return SegmentAudioResult(null, segmentId, "No TTS voice is configured")
            val result = synthesizeSpeech(cleanText, request)
                ?: return SegmentAudioResult(null, segmentId, "TTS service is unavailable")
            
            return result.fold(
                onSuccess = { audioData ->
                    // Upload to Cloudinary and cache if message info provided
                    if (messageId != null && segmentIndex != null && appContext != null) {
                        val chatId = _currentChat.value?.id
                        if (chatId != null) {
                            viewModelScope.launch {
                                try {
                                    // Upload to Cloudinary
                                    val uploadResult = mediaStorageService.uploadAudioBytes(
                                        context = appContext!!,
                                        chatId = chatId,
                                        messageId = messageId,
                                        segmentIndex = segmentIndex,
                                        audioBytes = audioData
                                    )
                                    
                                    uploadResult.onSuccess { audioUrl ->
                                        // Save URL to Firestore
                                        segmentAudioRepository.saveAudioUrl(
                                            chatId = chatId,
                                            messageId = messageId,
                                            segmentIndex = segmentIndex,
                                            audioUrl = audioUrl,
                                            textHash = cleanText.hashCode()
                                        )
                                        
                                        // Update local cache state
                                        val currentCache = _cachedAudioUrls.value.toMutableMap()
                                        val messageCache = currentCache[messageId]?.toMutableMap() ?: mutableMapOf()
                                        messageCache[segmentIndex] = audioUrl
                                        currentCache[messageId] = messageCache
                                        _cachedAudioUrls.value = currentCache
                                        
                                        Log.d("ChatViewModel", "Audio cached for message=$messageId, segment=$segmentIndex")
                                    }.onFailure { e ->
                                        Log.e("ChatViewModel", "Failed to upload audio: ${e.message}")
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatViewModel", "Error caching audio: ${e.message}")
                                }
                            }
                        }
                    }
                    SegmentAudioResult(audioData, segmentId)
                },
                onFailure = { e ->
                    Log.e("ChatViewModel", "TTS generation failed: ${e.message}", e)
                    SegmentAudioResult(null, segmentId, ttsFailureMessage(e))
                }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Exception generating audio: ${e.message}", e)
            return SegmentAudioResult(null, "", ttsFailureMessage(e))
        }
    }
    
    /**
     * Play cached audio from URL
     */
    fun playCachedAudio(audioUrl: String, segmentId: String) {
        _ttsManager?.playFromUrl(audioUrl, segmentId)
    }
    
    /**
     * Get cached audio URL for a segment if available
     */
    fun getCachedAudioUrl(messageId: String, segmentIndex: Int): String? {
        return _cachedAudioUrls.value[messageId]?.get(segmentIndex)
    }
    
    /**
     * Get all cached audio URLs for a message (used by UI)
     */
    fun getCachedAudioUrlsForMessage(messageId: String): Map<Int, String> {
        return _cachedAudioUrls.value[messageId] ?: emptyMap()
    }
    
    /**
     * Load cached audio URLs for all messages in the current chat
     */
    fun loadCachedAudioUrlsForMessage(messageId: String) {
        val chatId = _currentChat.value?.id ?: return
        
        viewModelScope.launch {
            try {
                val cachedUrls = segmentAudioRepository.getAudioUrlsForMessage(chatId, messageId)
                if (cachedUrls.isNotEmpty()) {
                    val currentCache = _cachedAudioUrls.value.toMutableMap()
                    currentCache[messageId] = cachedUrls
                    _cachedAudioUrls.value = currentCache
                    Log.d("ChatViewModel", "Loaded ${cachedUrls.size} cached audio URLs for message $messageId")
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error loading cached audio URLs: ${e.message}")
            }
        }
    }
    
    /**
     * Speak text and wait for playback to complete (kept for single segment use)
     */
    private suspend fun speakTextAndWait(text: String, characterId: String? = null) {
        val segment = SpeakableSegment(text, characterId)
        val generationResult = generateAudioForSegment(segment)
        
        if (generationResult.audioData != null) {
            _ttsManager?.playFromBytes(generationResult.audioData, generationResult.segmentId)
            
            // Wait for playback to complete
            try {
                _ttsManager?.playbackState?.first { state ->
                    state == TTSPlaybackState.IDLE || state == TTSPlaybackState.ERROR || state == TTSPlaybackState.COMPLETED
                }
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Error waiting for playback: ${e.message}")
            }
        }
    }

    private fun resolveTtsRequest(
        characterId: String?,
        settings: ChatSettings
    ): ResolvedTtsRequest? {
        return TtsRequestResolver.resolve(
            characterId = characterId,
            chatCharacters = _characters.value,
            worldCharacters = _worldCharacters.value,
            narratorVoiceId = settings.narratorVoiceId,
            selectedModelId = settings.ttsModelId
        )
    }

    private suspend fun synthesizeSpeech(
        text: String,
        request: ResolvedTtsRequest
    ): Result<ByteArray>? {
        return when (request.provider) {
            TtsProvider.ELEVEN_LABS -> elevenLabsService?.textToSpeech(
                text = text,
                voiceId = request.voiceId,
                modelId = request.modelId
            )
            TtsProvider.INWORLD -> inworldService?.textToSpeech(
                text = text,
                voiceId = request.voiceId,
                modelId = request.modelId
            )
        }
    }

    private fun startTtsGeneration(segmentId: String) {
        _ttsGenerationState.value = TtsGenerationState(
            activeSegmentId = segmentId,
            isGenerating = true
        )
    }

    private fun finishTtsGeneration(segmentId: String) {
        if (_ttsGenerationState.value.activeSegmentId == segmentId) {
            _ttsGenerationState.value = TtsGenerationState()
        }
    }

    private fun failTtsGeneration(segmentId: String, message: String) {
        _ttsGenerationState.value = TtsGenerationState(
            activeSegmentId = segmentId,
            isGenerating = false,
            errorMessage = message
        )
    }

    private fun ttsFailureMessage(error: Throwable): String {
        return "TTS failed: ${error.message?.take(300) ?: "Unknown error"}"
    }
}
