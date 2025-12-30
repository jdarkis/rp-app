package com.example.rpapp3.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TTSPlaybackState {
    IDLE,
    LOADING,
    PLAYING,
    PAUSED,
    ERROR
}

class TTSManager(private val context: Context) {
    
    private var exoPlayer: ExoPlayer? = null
    
    private val _playbackState = MutableStateFlow(TTSPlaybackState.IDLE)
    val playbackState: StateFlow<TTSPlaybackState> = _playbackState.asStateFlow()
    
    private val _currentPlayingId = MutableStateFlow<String?>(null)
    val currentPlayingId: StateFlow<String?> = _currentPlayingId.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    companion object {
        @Volatile
        private var INSTANCE: TTSManager? = null
        
        fun getInstance(context: Context): TTSManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TTSManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private fun getOrCreatePlayer(): ExoPlayer {
        return exoPlayer ?: ExoPlayer.Builder(context).build().also { player ->
            exoPlayer = player
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_IDLE -> _playbackState.value = TTSPlaybackState.IDLE
                        Player.STATE_BUFFERING -> _playbackState.value = TTSPlaybackState.LOADING
                        Player.STATE_READY -> {
                            if (player.isPlaying) {
                                _playbackState.value = TTSPlaybackState.PLAYING
                            } else {
                                _playbackState.value = TTSPlaybackState.PAUSED
                            }
                        }
                        Player.STATE_ENDED -> {
                            _playbackState.value = TTSPlaybackState.IDLE
                            _currentPlayingId.value = null
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        _playbackState.value = TTSPlaybackState.PLAYING
                    } else if (player.playbackState == Player.STATE_READY) {
                        _playbackState.value = TTSPlaybackState.PAUSED
                    }
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _playbackState.value = TTSPlaybackState.ERROR
                    _error.value = error.localizedMessage ?: "Playback error"
                    _currentPlayingId.value = null
                }
            })
        }
    }
    
    /**
     * Play audio from a URL (e.g., voice preview)
     */
    fun playFromUrl(url: String, playId: String? = null) {
        try {
            val player = getOrCreatePlayer()
            stop()
            
            _currentPlayingId.value = playId
            _playbackState.value = TTSPlaybackState.LOADING
            _error.value = null
            
            val mediaItem = MediaItem.fromUri(url)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            
        } catch (e: Exception) {
            _playbackState.value = TTSPlaybackState.ERROR
            _error.value = e.localizedMessage ?: "Failed to play audio"
            _currentPlayingId.value = null
        }
    }
    
    /**
     * Play audio from byte array (TTS response)
     */
    @OptIn(UnstableApi::class)
    fun playFromBytes(audioData: ByteArray, playId: String? = null) {
        try {
            val player = getOrCreatePlayer()
            stop()
            
            _currentPlayingId.value = playId
            _playbackState.value = TTSPlaybackState.LOADING
            _error.value = null
            
            val dataSourceFactory = DataSource.Factory {
                ByteArrayDataSource(audioData)
            }
            
            val mediaSource: MediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri("audio://tts"))
            
            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()
            
        } catch (e: Exception) {
            _playbackState.value = TTSPlaybackState.ERROR
            _error.value = e.localizedMessage ?: "Failed to play audio"
            _currentPlayingId.value = null
        }
    }
    
    /**
     * Pause current playback
     */
    fun pause() {
        exoPlayer?.pause()
    }
    
    /**
     * Resume playback
     */
    fun resume() {
        exoPlayer?.play()
    }
    
    /**
     * Stop playback
     */
    fun stop() {
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        _currentPlayingId.value = null
        _playbackState.value = TTSPlaybackState.IDLE
    }
    
    /**
     * Toggle play/pause for a given ID
     */
    fun togglePlayPause(playId: String): Boolean {
        val player = exoPlayer ?: return false
        
        return if (_currentPlayingId.value == playId) {
            if (player.isPlaying) {
                pause()
                false
            } else {
                resume()
                true
            }
        } else {
            // Different ID - need to load new audio
            false
        }
    }
    
    /**
     * Check if a specific ID is currently playing
     */
    fun isPlaying(playId: String): Boolean {
        return _currentPlayingId.value == playId && _playbackState.value == TTSPlaybackState.PLAYING
    }
    
    /**
     * Clear any error state
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Release resources - call when done with TTS
     */
    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        _playbackState.value = TTSPlaybackState.IDLE
        _currentPlayingId.value = null
    }
}
