package com.example.rpapp3.data.model

/**
 * Represents a cached TTS audio segment stored in Cloudinary.
 * Used to avoid regenerating audio for segments that have already been processed.
 */
data class SegmentAudioCache(
    val id: String = "",               // Composite: "{chatId}_{messageId}_{segmentIndex}"
    val chatId: String = "",
    val messageId: String = "",
    val segmentIndex: Int = 0,
    val audioUrl: String = "",
    val textHash: Int = 0,             // Hash of segment text for validation
    val createdAt: Long = System.currentTimeMillis()
) {
    // No-arg constructor for Firestore
    constructor() : this("")
    
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "chatId" to chatId,
        "messageId" to messageId,
        "segmentIndex" to segmentIndex,
        "audioUrl" to audioUrl,
        "textHash" to textHash,
        "createdAt" to createdAt
    )
    
    companion object {
        fun fromMap(map: Map<String, Any?>): SegmentAudioCache {
            return SegmentAudioCache(
                id = map["id"] as? String ?: "",
                chatId = map["chatId"] as? String ?: "",
                messageId = map["messageId"] as? String ?: "",
                segmentIndex = (map["segmentIndex"] as? Long)?.toInt() ?: 0,
                audioUrl = map["audioUrl"] as? String ?: "",
                textHash = (map["textHash"] as? Long)?.toInt() ?: 0,
                createdAt = map["createdAt"] as? Long ?: System.currentTimeMillis()
            )
        }
        
        fun createId(chatId: String, messageId: String, segmentIndex: Int): String {
            return "${chatId}_${messageId}_${segmentIndex}"
        }
    }
}
