package com.example.rpapp3.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for app-level settings stored in Firestore.
 * Uses a single document "app_settings" in the "settings" collection.
 */
class SettingsRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val settingsDocument = firestore.collection("settings").document("app_settings")
    
    companion object {
        private const val UNLOCK_PROMPT_KEY = "unlock_prompt"
        private const val SYSTEM_PROMPT_KEY = "system_prompt"

        const val DEFAULT_SYSTEM_PROMPT =
            "You are a roleplay AI assistant. You will be playing one or more characters in a collaborative story."
    }
    
    /**
     * Get the unlock prompt as a Flow for reactive updates
     */
    fun getUnlockPrompt(): Flow<String> = callbackFlow {
        val listener = settingsDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend("")
                return@addSnapshotListener
            }
            
            val unlockPrompt = snapshot?.getString(UNLOCK_PROMPT_KEY) ?: ""
            trySend(unlockPrompt)
        }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get the unlock prompt synchronously
     */
    suspend fun getUnlockPromptOnce(): String {
        return try {
            val doc = settingsDocument.get().await()
            doc.getString(UNLOCK_PROMPT_KEY) ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Set the unlock prompt
     */
    suspend fun setUnlockPrompt(prompt: String): Result<Unit> {
        return try {
            settingsDocument
                .set(mapOf(UNLOCK_PROMPT_KEY to prompt), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the global system prompt as a Flow for reactive updates.
     */
    fun getSystemPrompt(): Flow<String> = callbackFlow {
        val listener = settingsDocument.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(DEFAULT_SYSTEM_PROMPT)
                return@addSnapshotListener
            }

            val systemPrompt = snapshot?.getString(SYSTEM_PROMPT_KEY) ?: DEFAULT_SYSTEM_PROMPT
            trySend(systemPrompt)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Get the global system prompt synchronously.
     */
    suspend fun getSystemPromptOnce(): String {
        return try {
            val doc = settingsDocument.get().await()
            doc.getString(SYSTEM_PROMPT_KEY) ?: DEFAULT_SYSTEM_PROMPT
        } catch (e: Exception) {
            DEFAULT_SYSTEM_PROMPT
        }
    }

    /**
     * Set the global system prompt.
     */
    suspend fun setSystemPrompt(prompt: String): Result<Unit> {
        return try {
            settingsDocument
                .set(mapOf(SYSTEM_PROMPT_KEY to prompt), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
