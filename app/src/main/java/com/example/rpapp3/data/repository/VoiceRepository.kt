package com.example.rpapp3.data.repository

import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Base64

internal fun voiceDocumentId(voice: Voice): String {
    if (voice.source == VoiceSource.ELEVEN_LABS) {
        return voice.voiceId
    }

    val encodedVoiceId = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(voice.voiceId.toByteArray(Charsets.UTF_8))
    return "${voice.source.name.lowercase()}_$encodedVoiceId"
}

/**
 * Repository for storing the user's activated TTS voices in Firestore.
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
                        labels = (doc.get("labels") as? Map<String, String>) ?: emptyMap(),
                        source = try {
                            val sourceStr = doc.getString("source")
                            if (sourceStr != null) {
                                com.example.rpapp3.data.model.VoiceSource.valueOf(sourceStr)
                            } else {
                                com.example.rpapp3.data.model.VoiceSource.ELEVEN_LABS
                            }
                        } catch (e: Exception) {
                            com.example.rpapp3.data.model.VoiceSource.ELEVEN_LABS
                        }
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
                "labels" to voice.labels,
                "source" to voice.source.name
            )
            voicesCollection.document(voiceDocumentId(voice)).set(voiceData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setVoiceActive(voice: Voice, active: Boolean): Result<Unit> {
        return if (active) {
            addVoice(voice)
        } else {
            removeVoice(voice)
        }
    }
    
    /**
     * Remove a voice from the user's custom voice list
     */
    suspend fun removeVoice(voice: Voice): Result<Unit> {
        return try {
            voicesCollection.document(voiceDocumentId(voice)).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Check if a voice exists in the user's custom voice list
     */
    suspend fun hasVoice(voice: Voice): Boolean {
        return try {
            val doc = voicesCollection.document(voiceDocumentId(voice)).get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }
}
