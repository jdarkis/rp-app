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


    /**
     * Generates an optimized URL for a Cloudinary image.
     * Applies resizing, quality optimization, and format automation.
     */
    fun getOptimizedUrl(url: String, width: Int, height: Int): String {
        if (!url.contains("res.cloudinary.com")) return url
        
        // Check if already has transformations (simple check)
        if (url.contains("/c_fill,")) return url
        
        val transformation = "w_$width,h_$height,c_fill,q_auto,f_auto"
        return url.replace("/upload/", "/upload/$transformation/")
    }

    /**
     * Generates a thumbnail image URL for a Cloudinary video.
     * Changes extension to .jpg and applies optimizations.
     */
    fun getVideoThumbnailUrl(url: String, width: Int, height: Int): String {
        if (!url.contains("res.cloudinary.com")) return url
        
        var optimUrl = url
        
        // Replace extension with .jpg for video thumbnail
        val extensionIndex = optimUrl.lastIndexOf('.')
        if (extensionIndex > 0) {
            optimUrl = optimUrl.substring(0, extensionIndex) + ".jpg"
        } else {
            optimUrl += ".jpg"
        }
        
        // Add transformations
        // so_0 grabs the frame at 0 seconds
        val transformation = "w_$width,h_$height,c_fill,q_auto,f_auto,so_0"
        return optimUrl.replace("/upload/", "/upload/$transformation/")
    }
}
