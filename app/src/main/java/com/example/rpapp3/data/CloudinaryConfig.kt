package com.example.rpapp3.data

/**
 * Cloudinary configuration singleton for media storage.
 * 
 * Setup instructions:
 * 1. Go to https://cloudinary.com and create a free account
 * 2. Go to Dashboard and copy your Cloud Name
 * 3. Go to Settings > Upload > Upload presets
 * 4. Create an unsigned upload preset
 * 5. Replace the values below
 */
object CloudinaryConfig {
    // Cloudinary cloud name
    const val CLOUD_NAME = "dlxdiqyxp"
    
    // Unsigned upload preset name
    const val UPLOAD_PRESET = "rpappp"
    
    // Base URL for Cloudinary assets
    val BASE_URL: String
        get() = "https://res.cloudinary.com/$CLOUD_NAME"
    
    /**
     * Check if Cloudinary is configured
     */
    fun isConfigured(): Boolean {
        return CLOUD_NAME.isNotBlank() && 
               CLOUD_NAME != "YOUR_CLOUD_NAME" &&
               UPLOAD_PRESET.isNotBlank() &&
               UPLOAD_PRESET != "YOUR_UPLOAD_PRESET"
    }
}
