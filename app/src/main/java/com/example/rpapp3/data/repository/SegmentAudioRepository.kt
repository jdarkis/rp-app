package com.example.rpapp3.data.repository

import android.util.Log
import com.example.rpapp3.data.model.SegmentAudioCache
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for managing cached TTS audio URLs in Firestore.
 * Stores audio URLs keyed by chatId/messageId/segmentIndex for quick lookup.
 */
class SegmentAudioRepository {
    private val TAG = "SegmentAudioRepository"
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("segmentAudio")
    
    /**
     * Get cached audio URL for a specific segment
     * @return Audio URL if cached, null otherwise
     */
    suspend fun getAudioUrl(
        chatId: String,
        messageId: String,
        segmentIndex: Int
    ): String? = withContext(Dispatchers.IO) {
        try {
            val id = SegmentAudioCache.createId(chatId, messageId, segmentIndex)
            val doc = collection.document(id).get().await()
            
            if (doc.exists()) {
                val cache = doc.data?.let { SegmentAudioCache.fromMap(it) }
                cache?.audioUrl
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cached audio URL: ${e.message}", e)
            null
        }
    }
    
    /**
     * Get all cached audio URLs for a message
     * @return Map of segmentIndex to audioUrl
     */
    suspend fun getAudioUrlsForMessage(
        chatId: String,
        messageId: String
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        try {
            val query = collection
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("messageId", messageId)
                .get()
                .await()
            
            query.documents.mapNotNull { doc ->
                val cache = doc.data?.let { SegmentAudioCache.fromMap(it) }
                cache?.let { it.segmentIndex to it.audioUrl }
            }.toMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cached audio URLs for message: ${e.message}", e)
            emptyMap()
        }
    }
    
    /**
     * Save audio URL for a segment
     */
    suspend fun saveAudioUrl(
        chatId: String,
        messageId: String,
        segmentIndex: Int,
        audioUrl: String,
        textHash: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val id = SegmentAudioCache.createId(chatId, messageId, segmentIndex)
            val cache = SegmentAudioCache(
                id = id,
                chatId = chatId,
                messageId = messageId,
                segmentIndex = segmentIndex,
                audioUrl = audioUrl,
                textHash = textHash
            )
            
            collection.document(id).set(cache.toMap()).await()
            Log.d(TAG, "Saved audio cache: $id -> $audioUrl")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save audio URL: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete cached audio for a message (when message is deleted)
     */
    suspend fun deleteAudioForMessage(
        chatId: String,
        messageId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val query = collection
                .whereEqualTo("chatId", chatId)
                .whereEqualTo("messageId", messageId)
                .get()
                .await()
            
            query.documents.forEach { doc ->
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete audio for message: ${e.message}", e)
            Result.failure(e)
        }
    }
}
