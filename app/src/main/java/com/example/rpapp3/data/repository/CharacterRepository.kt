package com.example.rpapp3.data.repository

import com.example.rpapp3.data.model.Character
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CharacterRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val charactersCollection = firestore.collection("characters")
    
    /**
     * Get all characters for a specific world as a Flow
     */
    fun getCharactersByWorld(worldId: String): Flow<List<Character>> = callbackFlow {
        val listener = charactersCollection
            .whereEqualTo("worldId", worldId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val characters = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Character.fromMap(it) }
                } ?: emptyList()
                
                trySend(characters)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get all characters for a specific world (one-time fetch)
     */
    suspend fun getCharactersByWorldOnce(worldId: String): List<Character> {
        return try {
            val docs = charactersCollection
                .whereEqualTo("worldId", worldId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
            
            docs.documents.mapNotNull { doc ->
                doc.data?.let { Character.fromMap(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Get a single character by ID
     */
    suspend fun getCharacter(characterId: String): Character? {
        return try {
            val doc = charactersCollection.document(characterId).get().await()
            doc.data?.let { Character.fromMap(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get multiple characters by their IDs
     */
    suspend fun getCharactersByIds(characterIds: List<String>): List<Character> {
        if (characterIds.isEmpty()) return emptyList()
        
        return try {
            val characters = mutableListOf<Character>()
            // Firestore whereIn has a limit of 10 items
            characterIds.chunked(10).forEach { chunk ->
                val docs = charactersCollection
                    .whereIn("id", chunk)
                    .get()
                    .await()
                
                docs.documents.mapNotNull { doc ->
                    doc.data?.let { Character.fromMap(it) }
                }.let { characters.addAll(it) }
            }
            characters
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Create a new character
     */
    suspend fun createCharacter(character: Character): Result<Character> {
        return try {
            val docRef = charactersCollection.document()
            val newCharacter = character.copy(id = docRef.id)
            docRef.set(newCharacter.toMap()).await()
            Result.success(newCharacter)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing character
     */
    suspend fun updateCharacter(character: Character): Result<Unit> {
        return try {
            val updatedCharacter = character.copy(updatedAt = System.currentTimeMillis())
            charactersCollection.document(character.id).set(updatedCharacter.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a character
     */
    suspend fun deleteCharacter(characterId: String): Result<Unit> {
        return try {
            charactersCollection.document(characterId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete all characters for a world
     */
    suspend fun deleteCharactersByWorld(worldId: String): Result<Unit> {
        return try {
            val docs = charactersCollection
                .whereEqualTo("worldId", worldId)
                .get()
                .await()
            
            val batch = firestore.batch()
            docs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
