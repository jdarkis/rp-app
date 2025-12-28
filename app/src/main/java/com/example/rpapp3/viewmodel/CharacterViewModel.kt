package com.example.rpapp3.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.repository.CharacterRepository
import com.example.rpapp3.data.repository.MediaStorageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import android.util.Log
import kotlinx.coroutines.launch

class CharacterViewModel : ViewModel() {
    private val characterRepository = CharacterRepository()
    private val mediaStorageService = MediaStorageService()
    
    private val _characters = MutableStateFlow<List<Character>>(emptyList())
    val characters: StateFlow<List<Character>> = _characters
    
    private val _currentCharacter = MutableStateFlow<Character?>(null)
    val currentCharacter: StateFlow<Character?> = _currentCharacter
    
    var isLoading by mutableStateOf(false)
        private set
    
    var uploadProgress by mutableStateOf<String?>(null)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set
    
    fun loadCharacters(worldId: String) {
        viewModelScope.launch {
            characterRepository.getCharactersByWorld(worldId)
                .catch { e ->
                    error = e.message
                }
                .collect { characterList ->
                    _characters.value = characterList
                }
        }
    }
    
    fun loadCharacter(characterId: String) {
        viewModelScope.launch {
            isLoading = true
            _currentCharacter.value = characterRepository.getCharacter(characterId)
            isLoading = false
        }
    }
    
    fun createCharacter(
        context: Context,
        worldId: String,
        name: String,
        description: String,
        appearance: String,
        personality: String,
        systemInstructions: String,
        profilePictureUri: Uri?,
        photoUris: List<Uri>,
        videoUris: List<Uri>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.isBlank()) {
            onError("Name is required")
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            error = null
            
            try {
                // First create the character to get the ID
                val character = Character(
                    worldId = worldId,
                    name = name,
                    description = description,
                    appearance = appearance,
                    personality = personality,
                    systemInstructions = systemInstructions
                )
                
                val createdCharacter = characterRepository.createCharacter(character).getOrThrow()
                Log.d("CharacterViewModel", "Created character with ID: ${createdCharacter.id}")
                
                // Upload profile picture if provided
                var profilePictureUrl: String? = null
                profilePictureUri?.let { uri ->
                    uploadProgress = "Uploading profile picture..."
                    Log.d("CharacterViewModel", "Attempting to upload profile picture from URI: $uri")
                    mediaStorageService.uploadCharacterPhoto(context, createdCharacter.id, uri)
                        .onSuccess { url ->
                            profilePictureUrl = url
                            Log.d("CharacterViewModel", "Successfully uploaded profile picture: $url")
                        }
                        .onFailure { e ->
                            Log.e("CharacterViewModel", "Failed to upload profile picture: ${e.message}", e)
                        }
                }
                
                // Upload photos
                val photoUrls = mutableListOf<String>()
                val photoUploadErrors = mutableListOf<String>()
                photoUris.forEachIndexed { index, uri ->
                    uploadProgress = "Uploading photo ${index + 1}/${photoUris.size}..."
                    Log.d("CharacterViewModel", "Attempting to upload photo from URI: $uri")
                    mediaStorageService.uploadCharacterPhoto(context, createdCharacter.id, uri)
                        .onSuccess { url -> 
                            photoUrls.add(url)
                            Log.d("CharacterViewModel", "Successfully uploaded photo: $url")
                        }
                        .onFailure { e -> 
                            Log.e("CharacterViewModel", "Failed to upload photo: ${e.message}", e)
                            photoUploadErrors.add("Photo ${index + 1}: ${e.message}")
                        }
                }
                
                // Upload videos
                val videoUrls = mutableListOf<String>()
                val videoUploadErrors = mutableListOf<String>()
                videoUris.forEachIndexed { index, uri ->
                    uploadProgress = "Uploading video ${index + 1}/${videoUris.size}..."
                    Log.d("CharacterViewModel", "Attempting to upload video from URI: $uri")
                    mediaStorageService.uploadCharacterVideo(context, createdCharacter.id, uri)
                        .onSuccess { url -> 
                            videoUrls.add(url)
                            Log.d("CharacterViewModel", "Successfully uploaded video: $url")
                        }
                        .onFailure { e -> 
                            Log.e("CharacterViewModel", "Failed to upload video: ${e.message}", e)
                            videoUploadErrors.add("Video ${index + 1}: ${e.message}")
                        }
                }
                
                // Log summary
                Log.d("CharacterViewModel", "Upload summary: ${photoUrls.size}/${photoUris.size} photos, ${videoUrls.size}/${videoUris.size} videos, profile: ${profilePictureUrl != null}")
                
                // Update character with media URLs if any were uploaded
                if (profilePictureUrl != null || photoUrls.isNotEmpty() || videoUrls.isNotEmpty()) {
                    val updatedCharacter = createdCharacter.copy(
                        profilePictureUrl = profilePictureUrl,
                        photoUrls = photoUrls,
                        videoUrls = videoUrls
                    )
                    characterRepository.updateCharacter(updatedCharacter).getOrThrow()
                    Log.d("CharacterViewModel", "Successfully saved character with profile picture and ${photoUrls.size} photos and ${videoUrls.size} videos")
                }
                
                isLoading = false
                uploadProgress = null
                
                // Show warning if some uploads failed
                val allErrors = photoUploadErrors + videoUploadErrors
                if (allErrors.isNotEmpty()) {
                    error = "Some media failed to upload:\n${allErrors.joinToString("\n")}"
                }
                
                onSuccess()
            } catch (e: Exception) {
                isLoading = false
                uploadProgress = null
                error = e.message
                onError(e.message ?: "Failed to create character")
            }
        }
    }
    
    fun updateCharacter(
        context: Context,
        character: Character,
        newProfilePictureUri: Uri?,
        newPhotoUris: List<Uri>,
        newVideoUris: List<Uri>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (character.name.isBlank()) {
            onError("Name is required")
            return
        }
        
        viewModelScope.launch {
            isLoading = true
            error = null
            
            try {
                var updatedCharacter = character
                
                // Upload new profile picture if provided
                newProfilePictureUri?.let { uri ->
                    uploadProgress = "Uploading profile picture..."
                    mediaStorageService.uploadCharacterPhoto(context, character.id, uri)
                        .onSuccess { url ->
                            updatedCharacter = updatedCharacter.copy(profilePictureUrl = url)
                        }
                        .onFailure { e ->
                            Log.e("CharacterViewModel", "Failed to upload profile picture: ${e.message}", e)
                        }
                }
                
                // Upload new photos
                val newPhotoUrls = mutableListOf<String>()
                newPhotoUris.forEachIndexed { index, uri ->
                    uploadProgress = "Uploading photo ${index + 1}/${newPhotoUris.size}..."
                    mediaStorageService.uploadCharacterPhoto(context, character.id, uri)
                        .onSuccess { url -> newPhotoUrls.add(url) }
                        .onFailure { e -> Log.e("CharacterViewModel", "Failed to upload media: ${e.message}", e) }
                }
                
                // Upload new videos
                val newVideoUrls = mutableListOf<String>()
                newVideoUris.forEachIndexed { index, uri ->
                    uploadProgress = "Uploading video ${index + 1}/${newVideoUris.size}..."
                    mediaStorageService.uploadCharacterVideo(context, character.id, uri)
                        .onSuccess { url -> newVideoUrls.add(url) }
                        .onFailure { e -> Log.e("CharacterViewModel", "Failed to upload media: ${e.message}", e) }
                }
                
                // Add new URLs to existing ones
                updatedCharacter = updatedCharacter.copy(
                    photoUrls = character.photoUrls + newPhotoUrls,
                    videoUrls = character.videoUrls + newVideoUrls
                )
                
                characterRepository.updateCharacter(updatedCharacter)
                    .onSuccess {
                        isLoading = false
                        uploadProgress = null
                        onSuccess()
                    }
                    .onFailure { e ->
                        isLoading = false
                        uploadProgress = null
                        error = e.message
                        onError(e.message ?: "Failed to update character")
                    }
            } catch (e: Exception) {
                isLoading = false
                uploadProgress = null
                error = e.message
                onError(e.message ?: "Failed to update character")
            }
        }
    }
    
    fun deleteCharacter(
        characterId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            isLoading = true
            error = null
            
            try {
                // Delete media first
                mediaStorageService.deleteAllCharacterMedia(characterId)
                
                // Delete the character
                characterRepository.deleteCharacter(characterId)
                    .onSuccess {
                        isLoading = false
                        onSuccess()
                    }
                    .onFailure { e ->
                        isLoading = false
                        error = e.message
                        onError(e.message ?: "Failed to delete character")
                    }
            } catch (e: Exception) {
                isLoading = false
                error = e.message
                onError(e.message ?: "Failed to delete character")
            }
        }
    }
    
    fun removePhoto(character: Character, photoUrl: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            mediaStorageService.deleteMedia(photoUrl)
            // Also clear profile picture if it matches the removed photo
            val updatedProfilePictureUrl = if (character.profilePictureUrl == photoUrl) null else character.profilePictureUrl
            val updatedCharacter = character.copy(
                photoUrls = character.photoUrls.filter { it != photoUrl },
                profilePictureUrl = updatedProfilePictureUrl
            )
            characterRepository.updateCharacter(updatedCharacter)
            _currentCharacter.value = updatedCharacter
            onComplete()
        }
    }
    
    fun removeVideo(character: Character, videoUrl: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            mediaStorageService.deleteMedia(videoUrl)
            val updatedCharacter = character.copy(
                videoUrls = character.videoUrls.filter { it != videoUrl }
            )
            characterRepository.updateCharacter(updatedCharacter)
            _currentCharacter.value = updatedCharacter
            onComplete()
        }
    }
    
    fun clearError() {
        error = null
    }
}
