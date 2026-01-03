package com.example.rpapp3.data.repository

import com.example.rpapp3.data.model.Character
import com.example.rpapp3.data.model.VersionHistory
import com.example.rpapp3.data.model.World
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing version history of characters and worlds.
 * Stores snapshots before summarizer updates are applied.
 */
class VersionHistoryRepository {
    
    private val db = FirebaseFirestore.getInstance()
    private val versionsCollection = db.collection("versionHistory")
    
    /**
     * Save a version snapshot before applying updates
     */
    suspend fun saveVersion(version: VersionHistory): Result<Unit> {
        return try {
            versionsCollection.document(version.id).set(version.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Save character state before update
     */
    suspend fun saveCharacterVersion(character: Character, source: String = "summarizer"): Result<VersionHistory> {
        return try {
            val version = VersionHistory.fromCharacter(character, source)
            versionsCollection.document(version.id).set(version.toMap()).await()
            Result.success(version)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Save world state before update
     */
    suspend fun saveWorldVersion(world: World, source: String = "summarizer"): Result<VersionHistory> {
        return try {
            val version = VersionHistory.fromWorld(world, source)
            versionsCollection.document(version.id).set(version.toMap()).await()
            Result.success(version)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all versions for a character, newest first
     */
    fun getCharacterVersions(characterId: String): Flow<List<VersionHistory>> = flow {
        val snapshot = versionsCollection
            .whereEqualTo("entityId", characterId)
            .whereEqualTo("entityType", "character")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
        
        val versions = snapshot.documents.mapNotNull { doc ->
            doc.data?.let { VersionHistory.fromMap(it) }
        }
        emit(versions)
    }
    
    /**
     * Get all versions for a world, newest first
     */
    fun getWorldVersions(worldId: String): Flow<List<VersionHistory>> = flow {
        val snapshot = versionsCollection
            .whereEqualTo("entityId", worldId)
            .whereEqualTo("entityType", "world")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
        
        val versions = snapshot.documents.mapNotNull { doc ->
            doc.data?.let { VersionHistory.fromMap(it) }
        }
        emit(versions)
    }
    
    /**
     * Get versions once (not as flow)
     */
    suspend fun getCharacterVersionsOnce(characterId: String): List<VersionHistory> {
        return try {
            val snapshot = versionsCollection
                .whereEqualTo("entityId", characterId)
                .whereEqualTo("entityType", "character")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { VersionHistory.fromMap(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun getWorldVersionsOnce(worldId: String): List<VersionHistory> {
        return try {
            val snapshot = versionsCollection
                .whereEqualTo("entityId", worldId)
                .whereEqualTo("entityType", "world")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { VersionHistory.fromMap(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Delete a specific version
     */
    suspend fun deleteVersion(versionId: String): Result<Unit> {
        return try {
            versionsCollection.document(versionId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
