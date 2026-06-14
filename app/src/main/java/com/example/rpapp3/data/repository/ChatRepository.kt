package com.example.rpapp3.data.repository

import com.example.rpapp3.data.ChatSettings

import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.ChatUsageRecord
import com.example.rpapp3.data.model.ChatUsageSummary
import com.example.rpapp3.data.model.ModelRequestDetails
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

    fun observeChat(chatId: String): Flow<Chat?> = callbackFlow {
        val listener = chatsCollection.document(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                trySend(snapshot?.data?.let(Chat::fromMap))
            }

        awaitClose { listener.remove() }
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

    suspend fun updateChatSettings(chatId: String, settings: ChatSettings): Result<Unit> {
        return try {
            chatsCollection.document(chatId)
                .update("settings", settings.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun recordChatUsage(record: ChatUsageRecord): Result<Unit> {
        return try {
            val chatDocument = chatsCollection.document(record.chatId)
            val usageDocument = chatDocument
                .collection("usage_records")
                .document(record.id)

            firestore.runTransaction { transaction ->
                val existingUsage = transaction.get(usageDocument)
                if (!existingUsage.exists()) {
                    val chatSnapshot = transaction.get(chatDocument)
                    check(chatSnapshot.exists()) { "Chat not found" }
                    val currentSummary = ChatUsageSummary.fromMap(chatSnapshot.get("usage"))
                    transaction.set(usageDocument, record.toMap())
                    transaction.update(
                        chatDocument,
                        "usage",
                        currentSummary.plus(record).toMap()
                    )
                }
            }.await()
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
     * Get the last N messages from a chat (for extending chats)
     * Returns messages in chronological order (oldest first)
     * @param chatId The chat to load messages from
     * @param count Maximum number of messages to retrieve
     * @return List of messages, up to 'count' most recent, in chronological order
     */
    suspend fun getLastNMessages(chatId: String, count: Int): List<ChatMessage> {
        return try {
            val docs = chatsCollection
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(count.toLong())
                .get()
                .await()
            
            // Reverse to get chronological order (oldest first)
            docs.documents.mapNotNull { doc ->
                doc.data?.let { ChatMessage.fromMap(it) }
            }.reversed()
        } catch (e: Exception) {
            emptyList()
        }
    }

    
    /**
     * Get the most recent messages for initial UI display (paginated loading)
     * Returns messages in ascending order (oldest first) for display
     * @param chatId The chat to load messages from
     * @param limit Maximum number of messages to load
     * @return Pair of (messages, hasMoreMessages)
     */
    suspend fun getMessagesPagedInitial(chatId: String, limit: Int = 50): Pair<List<ChatMessage>, Boolean> {
        return try {
            // Fetch limit + 1 to check if there are more messages
            val docs = chatsCollection
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong() + 1)
                .get()
                .await()
            
            val messages = docs.documents.mapNotNull { doc ->
                doc.data?.let { ChatMessage.fromMap(it) }
            }
            
            // Check if we got more than limit (meaning there are older messages)
            val hasMore = messages.size > limit
            
            // Take only the limit and reverse to get oldest-first order
            val result = messages.take(limit).reversed()
            
            Pair(result, hasMore)
        } catch (e: Exception) {
            Pair(emptyList(), false)
        }
    }
    
    /**
     * Load older messages before a given timestamp (for "load more" functionality)
     * @param chatId The chat to load messages from
     * @param beforeTimestamp Load messages older than this timestamp
     * @param limit Maximum number of messages to load
     * @return Pair of (messages, hasMoreMessages)
     */
    suspend fun getMessagesOlder(chatId: String, beforeTimestamp: Long, limit: Int = 50): Pair<List<ChatMessage>, Boolean> {
        return try {
            // Fetch limit + 1 to check if there are more messages
            val docs = chatsCollection
                .document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .whereLessThan("timestamp", beforeTimestamp)
                .limit(limit.toLong() + 1)
                .get()
                .await()
            
            val messages = docs.documents.mapNotNull { doc ->
                doc.data?.let { ChatMessage.fromMap(it) }
            }
            
            val hasMore = messages.size > limit
            val result = messages.take(limit).reversed()
            
            Pair(result, hasMore)
        } catch (e: Exception) {
            Pair(emptyList(), false)
        }
    }
    
    /**
     * Observe new messages added after a given timestamp (for real-time updates)
     * Used to get new messages while chat is open without reloading all messages
     */
    fun observeNewMessages(chatId: String, afterTimestamp: Long): Flow<ChatMessage> = callbackFlow {
        val listener = chatsCollection
            .document(chatId)
            .collection("messages")
            .whereGreaterThan("timestamp", afterTimestamp)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                // Only emit newly added documents
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        change.document.data?.let { data ->
                            val message = ChatMessage.fromMap(data)
                            trySend(message)
                        }
                    }
                }
            }
        
        awaitClose { listener.remove() }
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

    suspend fun saveModelRequestDetails(details: ModelRequestDetails): Result<Unit> {
        return try {
            chatsCollection
                .document(details.chatId)
                .collection("request_details")
                .document(details.messageId)
                .set(details.toMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getModelRequestDetails(
        chatId: String,
        messageId: String
    ): Result<ModelRequestDetails?> {
        return try {
            val document = chatsCollection
                .document(chatId)
                .collection("request_details")
                .document(messageId)
                .get()
                .await()
            Result.success(
                document.data?.let(ModelRequestDetails::fromMap)
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getChatUsageRecord(
        chatId: String,
        usageRecordId: String
    ): Result<ChatUsageRecord?> {
        return try {
            val document = chatsCollection
                .document(chatId)
                .collection("usage_records")
                .document(usageRecordId)
                .get()
                .await()
            Result.success(ChatUsageRecord.fromMap(document.data))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestChatUsageForMessage(
        chatId: String,
        messageId: String,
        modelId: String
    ): Result<ChatUsageRecord?> {
        return try {
            val documents = chatsCollection
                .document(chatId)
                .collection("usage_records")
                .whereEqualTo("messageId", messageId)
                .get()
                .await()
            val records = documents.documents.mapNotNull { document ->
                ChatUsageRecord.fromMap(document.data)
            }
            Result.success(selectLatestUsageRecord(records, messageId, modelId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a single message from a chat
     */
    suspend fun deleteMessage(chatId: String, messageId: String): Result<Unit> {
        return try {
            val chatDocument = chatsCollection.document(chatId)
            val batch = firestore.batch()
            batch.delete(
                chatDocument
                    .collection("messages")
                    .document(messageId)
            )
            batch.delete(
                chatDocument
                    .collection("request_details")
                    .document(messageId)
            )
            batch.commit().await()
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
            val chatDocument = chatsCollection.document(chatId)
            messageIds.forEach { messageId ->
                batch.delete(
                    chatDocument
                        .collection("messages")
                        .document(messageId)
                )
                batch.delete(
                    chatDocument
                        .collection("request_details")
                        .document(messageId)
                )
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
            val chatDocument = chatsCollection.document(chatId)
            val messageDocuments = chatDocument
                .collection("messages")
                .get()
                .await()
            val requestDetailDocuments = chatDocument
                .collection("request_details")
                .get()
                .await()
            val usageDocuments = chatDocument
                .collection("usage_records")
                .get()
                .await()
            
            val batch = firestore.batch()
            messageDocuments.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            requestDetailDocuments.documents.forEach { doc ->
                batch.delete(doc.reference)
            }
            usageDocuments.documents.forEach { doc ->
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
                updatedAt = System.currentTimeMillis(),
                usage = ChatUsageSummary()
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
    
    /**
     * Get all private chats for a specific character
     */
    suspend fun getPrivateChatsByCharacter(characterId: String): List<Chat> {
        return try {
            val docs = chatsCollection
                .whereEqualTo("isPrivateChat", true)
                .whereEqualTo("privateCharacterId", characterId)
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
     * Get or create a private chat for a character
     * Returns existing private chat if one exists, otherwise creates a new one
     */
    suspend fun getOrCreatePrivateChat(characterId: String, worldId: String, characterName: String): Result<Chat> {
        return try {
            // Check for existing private chat first
            val existing = getPrivateChatsByCharacter(characterId).firstOrNull()
            if (existing != null) {
                return Result.success(existing)
            }
            
            // Create new private chat
            val docRef = chatsCollection.document()
            val newChat = Chat(
                id = docRef.id,
                worldId = worldId,
                characterIds = listOf(characterId),
                title = "Private: $characterName",
                isPrivateChat = true,
                privateCharacterId = characterId
            )
            docRef.set(newChat.toMap()).await()
            Result.success(newChat)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update the context chat IDs for a private chat
     */
    suspend fun updateContextChatIds(chatId: String, contextChatIds: List<String>): Result<Unit> {
        return try {
            chatsCollection.document(chatId)
                .update(
                    mapOf(
                        "contextChatIds" to contextChatIds,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get non-private chats for a world (for context selection)
     * Note: We filter in-memory because older chats may not have the isPrivateChat field
     */
    suspend fun getWorldChatsForContext(worldId: String): List<Chat> {
        return try {
            val docs = chatsCollection
                .whereEqualTo("worldId", worldId)
                .get()
                .await()
            
            docs.documents.mapNotNull { doc ->
                doc.data?.let { Chat.fromMap(it) }
            }
            .filter { !it.isPrivateChat }  // Filter out private chats in-memory
            .sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Restart a private chat by deleting all its messages
     * The chat itself remains, only messages are cleared
     */
    suspend fun restartPrivateChat(chatId: String): Result<Unit> {
        return try {
            // Delete all messages
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
            
            // Update the chat's updatedAt timestamp
            chatsCollection.document(chatId)
                .update("updatedAt", System.currentTimeMillis())
                .await()
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update the writing style for a private chat
     */
    suspend fun updateWritingStyle(chatId: String, writingStyle: String): Result<Unit> {
        return try {
            chatsCollection.document(chatId)
                .update(
                    mapOf(
                        "writingStyle" to writingStyle,
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

internal fun selectLatestUsageRecord(
    records: List<ChatUsageRecord>,
    messageId: String,
    modelId: String
): ChatUsageRecord? {
    return records
        .filter { it.messageId == messageId && it.modelId == modelId }
        .maxWithOrNull(compareBy<ChatUsageRecord> { it.createdAt }.thenBy { it.id })
}

