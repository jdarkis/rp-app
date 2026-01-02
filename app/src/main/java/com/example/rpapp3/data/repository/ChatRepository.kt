package com.example.rpapp3.data.repository

import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val chatsCollection = firestore.collection("chats")
    
    /**
     * Get all chats for a specific world as a Flow
     */
    fun getChatsByWorld(worldId: String): Flow<List<Chat>> = callbackFlow {
        val listener = chatsCollection
            .whereEqualTo("worldId", worldId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val chats = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { Chat.fromMap(it) }
                } ?: emptyList()
                
                trySend(chats)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get a single chat by ID
     */
    suspend fun getChat(chatId: String): Chat? {
        return try {
            val doc = chatsCollection.document(chatId).get().await()
            doc.data?.let { Chat.fromMap(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get all chats for a specific world (one-time fetch, no index required)
     * Useful for AI character generation context loading
     */
    suspend fun getChatsByWorldOnce(worldId: String): List<Chat> {
        return try {
            val docs = chatsCollection
                .whereEqualTo("worldId", worldId)
                .get()
                .await()
            
            docs.documents.mapNotNull { doc ->
                doc.data?.let { Chat.fromMap(it) }
            }.sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Create a new chat
     */
    suspend fun createChat(chat: Chat): Result<Chat> {
        return try {
            val docRef = chatsCollection.document()
            val newChat = chat.copy(id = docRef.id)
            docRef.set(newChat.toMap()).await()
            Result.success(newChat)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update a chat
     */
    suspend fun updateChat(chat: Chat): Result<Unit> {
        return try {
            val updatedChat = chat.copy(updatedAt = System.currentTimeMillis())
            chatsCollection.document(chat.id).set(updatedChat.toMap()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a chat and all its messages
     */
    suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            // Delete all messages first
            deleteMessagesByChat(chatId)
            // Then delete the chat
            chatsCollection.document(chatId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // Messages subcollection
    
    /**
     * Get messages for a chat as a Flow
     */
    fun getMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = chatsCollection
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { ChatMessage.fromMap(it) }
                } ?: emptyList()
                
                trySend(messages)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Get messages for a chat (one-time fetch, not a Flow)
     * Useful for loading context for AI character generation
     */
    suspend fun getMessagesOnce(chatId: String): List<ChatMessage> {
        return try {
            val docs = chatsCollection
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
            
            docs.documents.mapNotNull { doc ->
                doc.data?.let { ChatMessage.fromMap(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Add a message to a chat
     */
    suspend fun addMessage(message: ChatMessage): Result<ChatMessage> {
        return try {
            val messagesCollection = chatsCollection
                .document(message.chatId)
                .collection("messages")
            
            messagesCollection.document(message.id).set(message.toMap()).await()
            
            // Update chat's updatedAt timestamp
            chatsCollection.document(message.chatId)
                .update("updatedAt", System.currentTimeMillis())
                .await()
            
            Result.success(message)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a single message from a chat
     */
    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            chatsCollection
                .document(chatId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete multiple messages from a chat
     */
    suspend fun deleteMessages(chatId: String, messageIds: List<String>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            messageIds.forEach { messageId ->
                val docRef = chatsCollection
                    .document(chatId)
                    .collection("messages")
                    .document(messageId)
                batch.delete(docRef)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete all messages for a chat
     */
    private suspend fun deleteMessagesByChat(chatId: String) {
        try {
            val docs = chatsCollection
                .document(chatId)
                .collection("messages")
                .get()
                .await()
            
            val batch = firestore.batch()
            docs.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            // Ignore errors when deleting messages
        }
    }
    
    /**
     * Delete all chats for a world
     */
    suspend fun deleteChatsByWorld(worldId: String): Result<Unit> {
        return try {
            val docs = chatsCollection
                .whereEqualTo("worldId", worldId)
                .get()
                .await()
            
            docs.documents.forEach { doc ->
                deleteChat(doc.id)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Duplicate a chat and all its messages
     */
    suspend fun duplicateChat(chatId: String): Result<Chat> {
        return try {
            // Get the original chat
            val originalChat = getChat(chatId) ?: return Result.failure(Exception("Chat not found"))
            
            // Create a new chat with a copy suffix
            val newDocRef = chatsCollection.document()
            val newChat = originalChat.copy(
                id = newDocRef.id,
                title = "${originalChat.title} (Copy)",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            newDocRef.set(newChat.toMap()).await()
            
            // Copy all messages
            val messages = getMessagesOnce(chatId)
            val batch = firestore.batch()
            messages.forEach { message ->
                val newMessageId = java.util.UUID.randomUUID().toString()
                val newMessage = message.copy(
                    id = newMessageId,
                    chatId = newChat.id
                )
                val messageDocRef = chatsCollection
                    .document(newChat.id)
                    .collection("messages")
                    .document(newMessageId)
                batch.set(messageDocRef, newMessage.toMap())
            }
            batch.commit().await()
            
            Result.success(newChat)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update the character list for a chat
     */
    suspend fun updateChatCharacters(chatId: String, characterIds: List<String>): Result<Unit> {
        return try {
            chatsCollection.document(chatId)
                .update(
                    mapOf(
                        "characterIds" to characterIds,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
