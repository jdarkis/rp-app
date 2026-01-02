package com.example.rpapp3.data.repository

import com.example.rpapp3.data.model.Voice
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for storing user's custom Elevenlabs voices in Firestore.
 * Voices are stored in the "settings/elevenlabs_voices/voices" subcollection.
 */
class VoiceRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val voicesCollection = firestore.collection("settings")
        .document("elevenlabs_voices")
        .collection("voices")
    
    /**
     * Get all custom voices as a Flow for reactive updates
     */
    fun getCustomVoices(): Flow<List<Voice>> = callbackFlow {
        val listener = voicesCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            
            val voices = snapshot?.documents?.mapNotNull { doc ->
                try {
                    Voice(
                        voiceId = doc.getString("voiceId") ?: return@mapNotNull null,
                        name = doc.getString("name") ?: return@mapNotNull null,
                        previewUrl = doc.getString("previewUrl"),
                        labels = (doc.get("labels") as? Map<String, String>) ?: emptyMap()
                    )
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()
            
            trySend(voices)
        }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Add a voice to the user's custom voice list
     */
    suspend fun addVoice(voice: Voice): Result<Unit> {
        return try {
            val voiceData = mapOf(
                "voiceId" to voice.voiceId,
                "name" to voice.name,
                "previewUrl" to voice.previewUrl,
                "labels" to voice.labels
            )
            voicesCollection.document(voice.voiceId).set(voiceData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Remove a voice from the user's custom voice list
     */
    suspend fun removeVoice(voiceId: String): Result<Unit> {
        return try {
            voicesCollection.document(voiceId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if a voice exists in the user's custom voice list
     */
    suspend fun hasVoice(voiceId: String): Boolean {
        return try {
            val doc = voicesCollection.document(voiceId).get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }
}
