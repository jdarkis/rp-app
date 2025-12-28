package com.example.rpapp3.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rpapp3.data.model.World
import com.example.rpapp3.data.repository.CharacterRepository
import com.example.rpapp3.data.repository.ChatRepository
import com.example.rpapp3.data.repository.WorldRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class WorldViewModel : ViewModel() {
    private val worldRepository = WorldRepository()
    private val characterRepository = CharacterRepository()
    private val chatRepository = ChatRepository()
    
    private val _worlds = MutableStateFlow<List<World>>(emptyList())
    val worlds: StateFlow<List<World>> = _worlds
    
    private val _currentWorld = MutableStateFlow<World?>(null)
    val currentWorld: StateFlow<World?> = _currentWorld
    
    var isLoading by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    init {
        loadWorlds()
    }
    
    private fun loadWorlds() {
        viewModelScope.launch {
            worldRepository.getWorlds()
                .catch { e ->
                    error = e.message
                }
                .collect { worldList ->
                    _worlds.value = worldList
                }
        }
    }
    
    fun loadWorld(worldId: String) {
        viewModelScope.launch {
            isLoading = true
            _currentWorld.value = worldRepository.getWorld(worldId)
            isLoading = false
        }
    }
    
    fun createWorld(
        name: String,
        description: String,
        writingStyle: String,
        systemInstructions: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.isBlank()) {
            onError("Name is required")
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            error = null
            
            val world = World(
                name = name,
                description = description,
                writingStyle = writingStyle,
                systemInstructions = systemInstructions
            )
            
            worldRepository.createWorld(world)
                .onSuccess { createdWorld ->
                    isLoading = false
                    onSuccess(createdWorld.id)
                }
                .onFailure { e ->
                    isLoading = false
                    error = e.message
                    onError(e.message ?: "Failed to create world")
                }
        }
    }
    
    fun updateWorld(
        world: World,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (world.name.isBlank()) {
            onError("Name is required")
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            error = null
            
            worldRepository.updateWorld(world)
                .onSuccess {
                    isLoading = false
                    onSuccess()
                }
                .onFailure { e ->
                    isLoading = false
                    error = e.message
                    onError(e.message ?: "Failed to update world")
                }
        }
    }
    
    fun deleteWorld(
        worldId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            try {
                // Delete all chats first
                chatRepository.deleteChatsByWorld(worldId)
                // Delete all characters
                characterRepository.deleteCharactersByWorld(worldId)
                // Delete the world
                worldRepository.deleteWorld(worldId)
                    .onSuccess {
                        isLoading = false
                        onSuccess()
                    }
                    .onFailure { e ->
                        isLoading = false
                        error = e.message
                        onError(e.message ?: "Failed to delete world")
                    }
            } catch (e: Exception) {
                isLoading = false
                error = e.message
                onError(e.message ?: "Failed to delete world")
            }
        }
    }
    
    fun clearError() {
        error = null
    }
}
