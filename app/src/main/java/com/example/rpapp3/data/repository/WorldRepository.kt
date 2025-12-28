package com.example.rpapp3.data.repository

import com.example.rpapp3.data.model.World
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class WorldRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val worldsCollection = firestore.collection("worlds")
    
    /**
     * Get all worlds as a Flow
     */
    fun getWorlds(): Flow<List<World>> = callbackFlow {
        val listener = worldsCollection
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val worlds = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { World.fromMap(it) }
                } ?: emptyList()
                
                trySend(worlds)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get a single world by ID
     */
    suspend fun getWorld(worldId: String): World? {
        return try {
            val doc = worldsCollection.document(worldId).get().await()
            doc.data?.let { World.fromMap(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create a new world
     */
    suspend fun createWorld(world: World): Result<World> {
        return try {
            val docRef = worldsCollection.document()
            val newWorld = world.copy(id = docRef.id)
            docRef.set(newWorld.toMap()).await()
            Result.success(newWorld)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing world
     */
    suspend fun updateWorld(world: World): Result<Unit> {
        return try {
            val updatedWorld = world.copy(updatedAt = System.currentTimeMillis())
            worldsCollection.document(world.id).set(updatedWorld.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a world
     */
    suspend fun deleteWorld(worldId: String): Result<Unit> {
        return try {
            worldsCollection.document(worldId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
