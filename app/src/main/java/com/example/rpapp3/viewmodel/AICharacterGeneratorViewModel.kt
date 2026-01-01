package com.example.rpapp3.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rpapp3.data.AICharacterGeneratorService
import com.example.rpapp3.data.GeneratedCharacter
import com.example.rpapp3.data.GenerationResult
import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.World
import com.example.rpapp3.data.repository.CharacterRepository
import com.example.rpapp3.data.repository.ChatRepository
import com.example.rpapp3.data.repository.WorldRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * Represents the current step in the AI character wizard
 */
enum class WizardStep {
    CONTEXT_SELECTION,  // Step 1: Select context sources
    GENERATION,         // Step 2: AI generates characters
    REVIEW              // Step 3: Review and approve
}

/**
 * Data class for selectable chat items
 */
data class SelectableChat(
    val chat: Chat,
    val worldName: String,
    val isSelected: Boolean = false
)

/**
 * ViewModel for the AI Character Generation Wizard
 */
class AICharacterGeneratorViewModel : ViewModel() {
    
    private val worldRepository = WorldRepository()
    private val chatRepository = ChatRepository()
    private val characterRepository = CharacterRepository()
    
    private var generatorService: AICharacterGeneratorService? = null
    
    // Current wizard step
    private val _currentStep = MutableStateFlow(WizardStep.CONTEXT_SELECTION)
    val currentStep: StateFlow<WizardStep> = _currentStep
    
    // Current world
    private val _currentWorld = MutableStateFlow<World?>(null)
    val currentWorld: StateFlow<World?> = _currentWorld
    
    // All worlds (for cross-world chat selection)
    private val _allWorlds = MutableStateFlow<List<World>>(emptyList())
    val allWorlds: StateFlow<List<World>> = _allWorlds
    
    // Chats from current world
    private val _currentWorldChats = MutableStateFlow<List<SelectableChat>>(emptyList())
    val currentWorldChats: StateFlow<List<SelectableChat>> = _currentWorldChats
    
    // Chats from other worlds
    private val _otherWorldChats = MutableStateFlow<List<SelectableChat>>(emptyList())
    val otherWorldChats: StateFlow<List<SelectableChat>> = _otherWorldChats
    
    // Context selection state
    var useWorldDescription by mutableStateOf(true)
    var useAIInstructions by mutableStateOf(true)
    var additionalPrompt by mutableStateOf("")
    
    // Generated characters
    private val _generatedCharacters = MutableStateFlow<List<GeneratedCharacter>>(emptyList())
    val generatedCharacters: StateFlow<List<GeneratedCharacter>> = _generatedCharacters
    
    // Selected character index for review
    private val _selectedCharacterIndex = MutableStateFlow(0)
    val selectedCharacterIndex: StateFlow<Int> = _selectedCharacterIndex
    
    // Loading state
    var isLoading by mutableStateOf(false)
        private set
    
    // Error state
    var error by mutableStateOf<String?>(null)
        private set
    
    // Upload progress for character creation
    var uploadProgress by mutableStateOf<String?>(null)
        private set
    
    /**
     * Initialize the view model with context
     */
    fun initialize(context: Context, worldId: String) {
        if (generatorService == null) {
            generatorService = AICharacterGeneratorService(context)
        }
        loadCurrentWorld(worldId)
        loadAllWorlds(worldId)
    }
    
    private fun loadCurrentWorld(worldId: String) {
        viewModelScope.launch {
            val world = worldRepository.getWorld(worldId)
            _currentWorld.value = world
            
            // Load chats for current world
            chatRepository.getChatsByWorld(worldId)
                .catch { e -> error = e.message }
                .collect { chats ->
                    _currentWorldChats.value = chats.map { chat ->
                        SelectableChat(
                            chat = chat,
                            worldName = world?.name ?: "Current World"
                        )
                    }
                }
        }
    }
    
    private fun loadAllWorlds(currentWorldId: String) {
        viewModelScope.launch {
            try {
                // First collect worlds once
                worldRepository.getWorlds()
                    .catch { e -> error = e.message }
                    .collect { worlds ->
                        _allWorlds.value = worlds
                        
                        // Load chats from other worlds using one-time fetch (no index required)
                        val otherChats = mutableListOf<SelectableChat>()
                        worlds.filter { it.id != currentWorldId }.forEach { world ->
                            try {
                                val chats = chatRepository.getChatsByWorldOnce(world.id)
                                otherChats.addAll(chats.map { chat ->
                                    SelectableChat(chat = chat, worldName = world.name)
                                })
                            } catch (e: Exception) {
                                // Ignore errors for individual worlds
                            }
                        }
                        _otherWorldChats.value = otherChats
                    }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }
    
    /**
     * Toggle selection of a chat from current world
     */
    fun toggleCurrentWorldChat(chatId: String) {
        _currentWorldChats.value = _currentWorldChats.value.map { selectableChat ->
            if (selectableChat.chat.id == chatId) {
                selectableChat.copy(isSelected = !selectableChat.isSelected)
            } else {
                selectableChat
            }
        }
    }
    
    /**
     * Toggle selection of a chat from other worlds
     */
    fun toggleOtherWorldChat(chatId: String) {
        _otherWorldChats.value = _otherWorldChats.value.map { selectableChat ->
            if (selectableChat.chat.id == chatId) {
                selectableChat.copy(isSelected = !selectableChat.isSelected)
            } else {
                selectableChat
            }
        }
    }
    
    /**
     * Move to the next wizard step
     */
    fun nextStep() {
        when (_currentStep.value) {
            WizardStep.CONTEXT_SELECTION -> {
                _currentStep.value = WizardStep.GENERATION
                generateCharacters()
            }
            WizardStep.GENERATION -> {
                _currentStep.value = WizardStep.REVIEW
            }
            WizardStep.REVIEW -> {
                // Final step - handled by saveCharacter
            }
        }
    }
    
    /**
     * Move to the previous wizard step
     */
    fun previousStep() {
        when (_currentStep.value) {
            WizardStep.CONTEXT_SELECTION -> {
                // Can't go back from first step
            }
            WizardStep.GENERATION -> {
                _currentStep.value = WizardStep.CONTEXT_SELECTION
            }
            WizardStep.REVIEW -> {
                _currentStep.value = WizardStep.GENERATION
            }
        }
    }
    
    /**
     * Generate characters based on selected context
     */
    fun generateCharacters() {
        val service = generatorService ?: run {
            error = "Service not initialized"
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            error = null
            
            // Build context from selections
            val world = _currentWorld.value
            val worldDescription = if (useWorldDescription) world?.description else null
            val aiInstructions = if (useAIInstructions) world?.systemInstructions else null
            
            // Collect messages from selected chats
            val selectedChatIds = _currentWorldChats.value
                .filter { it.isSelected }
                .map { it.chat.id } +
                _otherWorldChats.value
                    .filter { it.isSelected }
                    .map { it.chat.id }
            
            val allMessages = mutableListOf<ChatMessage>()
            selectedChatIds.forEach { chatId ->
                val messages = chatRepository.getMessagesOnce(chatId)
                allMessages.addAll(messages)
            }
            
            val result = service.generateCharacters(
                worldDescription = worldDescription,
                aiInstructions = aiInstructions,
                chatMessages = allMessages.takeIf { it.isNotEmpty() },
                additionalPrompt = additionalPrompt.takeIf { it.isNotBlank() }
            )
            
            when (result) {
                is GenerationResult.Success -> {
                    _generatedCharacters.value = result.characters
                    _selectedCharacterIndex.value = 0
                }
                is GenerationResult.Error -> {
                    error = result.message
                }
            }
            
            isLoading = false
        }
    }
    
    /**
     * Select a character for review
     */
    fun selectCharacter(index: Int) {
        if (index in _generatedCharacters.value.indices) {
            _selectedCharacterIndex.value = index
        }
    }
    
    /**
     * Update a generated character's field
     */
    fun updateGeneratedCharacter(
        index: Int,
        name: String? = null,
        description: String? = null,
        appearance: String? = null,
        personality: String? = null,
        systemInstructions: String? = null
    ) {
        val characters = _generatedCharacters.value.toMutableList()
        if (index in characters.indices) {
            val current = characters[index]
            characters[index] = current.copy(
                name = name ?: current.name,
                description = description ?: current.description,
                appearance = appearance ?: current.appearance,
                personality = personality ?: current.personality,
                systemInstructions = systemInstructions ?: current.systemInstructions
            )
            _generatedCharacters.value = characters
        }
    }
    
    /**
     * Remove a character from the generated list without saving
     */
    fun removeCharacter(index: Int) {
        val characters = _generatedCharacters.value.toMutableList()
        if (index in characters.indices) {
            characters.removeAt(index)
            _generatedCharacters.value = characters
            // Adjust selected index if needed
            if (_selectedCharacterIndex.value >= characters.size) {
                _selectedCharacterIndex.value = (characters.size - 1).coerceAtLeast(0)
            }
        }
    }
    
    /**
     * Save a generated character to the database and remove from list
     */
    fun saveCharacter(
        context: Context,
        index: Int,
        profilePictureUri: Uri? = null,
        photoUris: List<Uri> = emptyList(),
        videoUris: List<Uri> = emptyList(),
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val worldId = _currentWorld.value?.id ?: run {
            onError("No world selected")
            return
        }
        
        val character = _generatedCharacters.value.getOrNull(index) ?: run {
            onError("Character not found")
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            uploadProgress = "Saving ${character.name}..."
            
            // Use CharacterViewModel to create the character
            val characterViewModel = CharacterViewModel()
            characterViewModel.createCharacter(
                context = context,
                worldId = worldId,
                name = character.name,
                description = character.description,
                appearance = character.appearance,
                personality = character.personality,
                systemInstructions = character.systemInstructions,
                profilePictureUri = profilePictureUri,
                photoUris = photoUris,
                nsfwPhotoUris = emptyList(),
                spicyNsfwPhotoUris = emptyList(),
                videoUris = videoUris,
                onSuccess = {
                    // Remove the saved character from the list
                    removeCharacter(index)
                    isLoading = false
                    uploadProgress = null
                    onSuccess()
                },
                onError = { errorMsg ->
                    isLoading = false
                    uploadProgress = null
                    onError(errorMsg)
                }
            )
        }
    }
    
    /**
     * Save all generated characters
     */
    fun saveAllCharacters(
        context: Context,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val worldId = _currentWorld.value?.id ?: run {
            onError("No world selected")
            return
        }
        
        val characters = _generatedCharacters.value
        if (characters.isEmpty()) {
            onError("No characters to save")
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            uploadProgress = "Saving characters..."
            
            var savedCount = 0
            var hasError = false
            
            characters.forEachIndexed { index, character ->
                uploadProgress = "Saving character ${index + 1} of ${characters.size}..."
                
                val result = characterRepository.createCharacter(
                    com.example.rpapp3.data.model.Character(
                        worldId = worldId,
                        name = character.name,
                        description = character.description,
                        appearance = character.appearance,
                        personality = character.personality,
                        systemInstructions = character.systemInstructions
                    )
                )
                
                result.onSuccess { savedCount++ }
                result.onFailure { hasError = true }
            }
            
            isLoading = false
            uploadProgress = null
            
            if (hasError && savedCount == 0) {
                onError("Failed to save characters")
            } else if (hasError) {
                onError("Saved $savedCount of ${characters.size} characters")
            } else {
                onSuccess()
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        error = null
    }
    
    /**
     * Reset wizard to initial state
     */
    fun reset() {
        _currentStep.value = WizardStep.CONTEXT_SELECTION
        _generatedCharacters.value = emptyList()
        _selectedCharacterIndex.value = 0
        useWorldDescription = true
        useAIInstructions = true
        additionalPrompt = ""
        error = null
        
        // Reset chat selections
        _currentWorldChats.value = _currentWorldChats.value.map { it.copy(isSelected = false) }
        _otherWorldChats.value = _otherWorldChats.value.map { it.copy(isSelected = false) }
    }
}
