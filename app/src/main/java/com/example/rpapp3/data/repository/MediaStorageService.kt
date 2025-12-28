package com.example.rpapp3.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.example.rpapp3.data.CloudinaryConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.resume

class MediaStorageService {
    private val TAG = "MediaStorageService"
    private var isInitialized = false
    
    /**
     * Initialize Cloudinary MediaManager if not already initialized
     */
    private fun ensureInitialized(context: Context) {
        if (!isInitialized) {
            try {
                val config = mapOf(
                    "cloud_name" to CloudinaryConfig.CLOUD_NAME
                )
                MediaManager.init(context.applicationContext, config)
                isInitialized = true
                Log.d(TAG, "Cloudinary MediaManager initialized")
            } catch (e: Exception) {
                // Already initialized
                if (e.message?.contains("already been initialized") == true) {
                    isInitialized = true
                } else {
                    Log.e(TAG, "Failed to initialize MediaManager: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Upload a photo for a character to Cloudinary
     * @return URL of the uploaded photo
     */
    suspend fun uploadCharacterPhoto(
        context: Context,
        characterId: String,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!CloudinaryConfig.isConfigured()) {
                return@withContext Result.failure(Exception("Cloudinary is not configured. Please set your cloud name and upload preset in CloudinaryConfig.kt"))
            }
            
            ensureInitialized(context)
            
            val publicId = "characters/${characterId}/photos/${UUID.randomUUID()}"
            
            Log.d(TAG, "Uploading photo from URI: $imageUri to publicId: $publicId")
            
            suspendCancellableCoroutine { continuation ->
                MediaManager.get().upload(imageUri)
                    .unsigned(CloudinaryConfig.UPLOAD_PRESET)
                    .option("public_id", publicId)
                    .option("resource_type", "image")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "Upload started: $requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            Log.d(TAG, "Upload progress: $bytes / $totalBytes")
                        }
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val secureUrl = resultData["secure_url"] as? String
                            Log.d(TAG, "Upload successful! URL: $secureUrl")
                            if (secureUrl != null) {
                                continuation.resume(Result.success(secureUrl))
                            } else {
                                continuation.resume(Result.failure(Exception("No URL in response")))
                            }
                        }
                        
                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "Upload failed: ${error.description}")
                            continuation.resume(Result.failure(Exception(error.description)))
                        }
                        
                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w(TAG, "Upload rescheduled: ${error.description}")
                        }
                    })
                    .dispatch()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload photo: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a video for a character to Cloudinary
     * @return URL of the uploaded video
     */
    suspend fun uploadCharacterVideo(
        context: Context,
        characterId: String,
        videoUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!CloudinaryConfig.isConfigured()) {
                return@withContext Result.failure(Exception("Cloudinary is not configured. Please set your cloud name and upload preset in CloudinaryConfig.kt"))
            }
            
            ensureInitialized(context)
            
            val publicId = "characters/${characterId}/videos/${UUID.randomUUID()}"
            
            Log.d(TAG, "Uploading video from URI: $videoUri to publicId: $publicId")
            
            suspendCancellableCoroutine { continuation ->
                MediaManager.get().upload(videoUri)
                    .unsigned(CloudinaryConfig.UPLOAD_PRESET)
                    .option("public_id", publicId)
                    .option("resource_type", "video")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {
                            Log.d(TAG, "Upload started: $requestId")
                        }
                        
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                            Log.d(TAG, "Upload progress: $bytes / $totalBytes")
                        }
                        
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val secureUrl = resultData["secure_url"] as? String
                            Log.d(TAG, "Upload successful! URL: $secureUrl")
                            if (secureUrl != null) {
                                continuation.resume(Result.success(secureUrl))
                            } else {
                                continuation.resume(Result.failure(Exception("No URL in response")))
                            }
                        }
                        
                        override fun onError(requestId: String, error: ErrorInfo) {
                            Log.e(TAG, "Upload failed: ${error.description}")
                            continuation.resume(Result.failure(Exception(error.description)))
                        }
                        
                        override fun onReschedule(requestId: String, error: ErrorInfo) {
                            Log.w(TAG, "Upload rescheduled: ${error.description}")
                        }
                    })
                    .dispatch()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload video: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a media file by URL (Cloudinary doesn't support deletion with unsigned uploads)
     * For now, this is a no-op since unsigned uploads can't delete
     */
    suspend fun deleteMedia(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Note: Cloudinary unsigned uploads don't support deletion
        // To enable deletion, you would need server-side signed requests
        Log.w(TAG, "Delete not supported with unsigned uploads. URL: $url")
        Result.success(Unit)
    }
    
    /**
     * Delete all media for a character (no-op for unsigned uploads)
     */
    suspend fun deleteAllCharacterMedia(characterId: String): Result<Unit> = withContext(Dispatchers.IO) {
        // Note: Cloudinary unsigned uploads don't support deletion
        Log.w(TAG, "Delete not supported with unsigned uploads. CharacterId: $characterId")
        Result.success(Unit)
    }
}
