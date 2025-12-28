package com.example.rpapp3.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.rpapp3.data.SupabaseConfig
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class MediaStorageService {
    private val TAG = "MediaStorageService"
    
    /**
     * Upload a photo for a character to Supabase Storage
     * @return URL of the uploaded photo
     */
    suspend fun uploadCharacterPhoto(
        context: Context,
        characterId: String,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!SupabaseConfig.isConfigured()) {
                return@withContext Result.failure(Exception("Supabase is not configured. Please set your project URL and anon key in SupabaseConfig.kt"))
            }
            
            val fileName = "${UUID.randomUUID()}.jpg"
            val path = "characters/$characterId/photos/$fileName"
            
            Log.d(TAG, "Opening image from URI: $imageUri")
            val inputStream = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Could not open image from URI: $imageUri"))
            
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            Log.d(TAG, "Uploading ${bytes.size} bytes to path: $path")
            
            val bucket = SupabaseConfig.client.storage.from(SupabaseConfig.BUCKET_NAME)
            bucket.upload(path, bytes) {
                upsert = true
            }
            
            // Get the public URL
            val publicUrl = bucket.publicUrl(path)
            Log.d(TAG, "Upload successful! Public URL: $publicUrl")
            
            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload photo: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a video for a character to Supabase Storage
     * @return URL of the uploaded video
     */
    suspend fun uploadCharacterVideo(
        context: Context,
        characterId: String,
        videoUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!SupabaseConfig.isConfigured()) {
                return@withContext Result.failure(Exception("Supabase is not configured. Please set your project URL and anon key in SupabaseConfig.kt"))
            }
            
            val fileName = "${UUID.randomUUID()}.mp4"
            val path = "characters/$characterId/videos/$fileName"
            
            Log.d(TAG, "Opening video from URI: $videoUri")
            val inputStream = context.contentResolver.openInputStream(videoUri)
                ?: return@withContext Result.failure(Exception("Could not open video from URI: $videoUri"))
            
            val bytes = inputStream.readBytes()
            inputStream.close()
            
            Log.d(TAG, "Uploading ${bytes.size} bytes to path: $path")
            
            val bucket = SupabaseConfig.client.storage.from(SupabaseConfig.BUCKET_NAME)
            bucket.upload(path, bytes) {
                upsert = true
            }
            
            // Get the public URL
            val publicUrl = bucket.publicUrl(path)
            Log.d(TAG, "Upload successful! Public URL: $publicUrl")
            
            Result.success(publicUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload video: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete a media file by URL
     */
    suspend fun deleteMedia(url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!SupabaseConfig.isConfigured()) {
                return@withContext Result.failure(Exception("Supabase is not configured"))
            }
            
            // Extract path from URL
            // URL format: https://[project].supabase.co/storage/v1/object/public/[bucket]/[path]
            val pathRegex = Regex("/storage/v1/object/public/${SupabaseConfig.BUCKET_NAME}/(.+)")
            val match = pathRegex.find(url)
            val path = match?.groupValues?.get(1) 
                ?: return@withContext Result.failure(Exception("Could not extract path from URL: $url"))
            
            Log.d(TAG, "Deleting file at path: $path")
            
            val bucket = SupabaseConfig.client.storage.from(SupabaseConfig.BUCKET_NAME)
            bucket.delete(path)
            
            Log.d(TAG, "Delete successful!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete media: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete all media for a character
     */
    suspend fun deleteAllCharacterMedia(characterId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!SupabaseConfig.isConfigured()) {
                return@withContext Result.failure(Exception("Supabase is not configured"))
            }
            
            val bucket = SupabaseConfig.client.storage.from(SupabaseConfig.BUCKET_NAME)
            
            // List and delete all photos
            try {
                val photosPath = "characters/$characterId/photos"
                val photos = bucket.list(photosPath)
                if (photos.isNotEmpty()) {
                    val paths = photos.map { "$photosPath/${it.name}" }
                    bucket.delete(paths)
                    Log.d(TAG, "Deleted ${photos.size} photos")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete photos: ${e.message}")
            }
            
            // List and delete all videos
            try {
                val videosPath = "characters/$characterId/videos"
                val videos = bucket.list(videosPath)
                if (videos.isNotEmpty()) {
                    val paths = videos.map { "$videosPath/${it.name}" }
                    bucket.delete(paths)
                    Log.d(TAG, "Deleted ${videos.size} videos")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not delete videos: ${e.message}")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete all media: ${e.message}", e)
            Result.failure(e)
        }
    }
}
