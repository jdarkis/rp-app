package com.example.rpapp3.ui.components

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.GeminiTtsService
import com.example.rpapp3.data.InworldService
import com.example.rpapp3.data.TTSManager
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * A comprehensive voice selector for character settings that allows:
 * - Selecting the voice model (ElevenLabs, Inworld, or Gemini)
 * - Picking a voice based on the selected model
 * - Showing voice info with preview similar to InworldVoicesScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterVoiceSelector(
    voices: List<Voice>,
    selectedVoiceId: String?,
    selectedVoiceSource: VoiceSource,
    onVoiceSelected: (Voice?, VoiceSource) -> Unit,
    ttsManager: TTSManager,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    filterGender: String? = null,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedVoice = remember(selectedVoiceId, voices) {
        voices.find { it.voiceId == selectedVoiceId }
    }
    val playbackState by ttsManager.playbackState.collectAsState()
    val currentPlayingId by ttsManager.currentPlayingId.collectAsState()
    
    Column(modifier = modifier) {
        // Voice Model Selector
        Text(
            text = "Voice Model",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceSource.entries.forEach { source ->
                FilterChip(
                    selected = selectedVoiceSource == source,
                    onClick = { 
                        if (enabled && !isLoading) {
                            // Clear voice selection when changing source
                            onVoiceSelected(null, source)
                        }
                    },
                    label = { 
                        Text(
                            when (source) {
                                VoiceSource.ELEVEN_LABS -> "ElevenLabs"
                                VoiceSource.INWORLD -> "Inworld"
                                VoiceSource.GEMINI -> "Gemini"
                            }
                        )
                    },
                    leadingIcon = if (selectedVoiceSource == source) {
                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                    } else null,
                    enabled = enabled && !isLoading
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Voice Selector
        Text(
            text = "Voice",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        OutlinedCard(
            onClick = { if (enabled && !isLoading) showDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && !isLoading
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedVoice?.name ?: "Select a voice",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (selectedVoice != null) {
                        val details = buildList {
                            selectedVoice.gender?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
                            selectedVoice.accent?.let { add(it) }
                        }.joinToString(" • ")
                        if (details.isNotEmpty()) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Show description for Inworld voices
                        selectedVoice.description?.let { desc ->
                            if (desc.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Preview button for selected voice
                    selectedVoice?.let { voice ->
                        voice.previewUrl?.let { previewUrl ->
                            val isPlayingThis = currentPlayingId == voice.voiceId
                            IconButton(
                                onClick = {
                                    if (isPlayingThis && playbackState == TTSPlaybackState.PLAYING) {
                                        ttsManager.stop()
                                    } else {
                                        ttsManager.playFromUrl(previewUrl, voice.voiceId)
                                    }
                                },
                                enabled = enabled
                            ) {
                                Icon(
                                    imageVector = if (isPlayingThis && playbackState == TTSPlaybackState.PLAYING) {
                                        Icons.Default.Stop
                                    } else {
                                        Icons.Default.PlayArrow
                                    },
                                    contentDescription = "Preview voice"
                                )
                            }
                        }
                    }
                    
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
    
    if (showDialog) {
        CharacterVoiceSelectorDialog(
            voices = voices,
            selectedVoiceId = selectedVoiceId,
            voiceSource = selectedVoiceSource,
            onVoiceSelected = { voice ->
                onVoiceSelected(voice, selectedVoiceSource)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            ttsManager = ttsManager,
            filterGender = filterGender
        )
    }
}

@Composable
private fun CharacterVoiceSelectorDialog(
    voices: List<Voice>,
    selectedVoiceId: String?,
    voiceSource: VoiceSource,
    onVoiceSelected: (Voice?) -> Unit,
    onDismiss: () -> Unit,
    ttsManager: TTSManager,
    filterGender: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inworldService = remember { InworldService.getInstance(context) }
    val geminiTtsService = remember { GeminiTtsService.getInstance(context) }
    
    val playbackState by ttsManager.playbackState.collectAsState()
    val currentPlayingId by ttsManager.currentPlayingId.collectAsState()
    
    // For providers that synthesize previews instead of using preview URLs.
    var generatedPreviewLoading by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }
    var generatedPlayingVoiceId by remember { mutableStateOf<String?>(null) }
    
    // Cleanup media player on dispose
    DisposableEffect(Unit) {
        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Filter voices by source and gender
    val filteredVoices = remember(voices, voiceSource, filterGender) {
        voices.filter { voice ->
            voice.source == voiceSource &&
            (filterGender == null || voice.gender?.lowercase() == filterGender.lowercase())
        }
    }
    
    fun playGeneratedPreview(
        voice: Voice,
        extension: String,
        synthesize: suspend (String, String) -> Result<ByteArray>
    ) {
        scope.launch {
            // Stop current playback
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.reset()
            } catch (e: Exception) {
                mediaPlayer.release()
            }
            
            if (generatedPlayingVoiceId == voice.voiceId && generatedPreviewLoading == null) {
                generatedPlayingVoiceId = null
                return@launch
            }
            
            generatedPlayingVoiceId = voice.voiceId
            generatedPreviewLoading = voice.voiceId
            
            val text = "Hello, I am ${voice.name}. This is a preview of my voice."
            synthesize(text, voice.voiceId)
                .onSuccess { audioData ->
                    try {
                        val safePrefix = "preview_${voice.source.name.lowercase()}_${voice.name.filter { it.isLetterOrDigit() }.take(16).ifBlank { "voice" }}"
                        val tempFile = File.createTempFile(safePrefix, extension, context.cacheDir)
                        FileOutputStream(tempFile).use { it.write(audioData) }
                        
                        mediaPlayer.setDataSource(tempFile.absolutePath)
                        mediaPlayer.setOnCompletionListener {
                            generatedPlayingVoiceId = null
                            generatedPreviewLoading = null
                        }
                        mediaPlayer.prepare()
                        mediaPlayer.start()
                        generatedPreviewLoading = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                        generatedPlayingVoiceId = null
                        generatedPreviewLoading = null
                    }
                }
                .onFailure { e ->
                    e.printStackTrace()
                    generatedPlayingVoiceId = null
                    generatedPreviewLoading = null
                }
        }
    }
    
    AlertDialog(
        onDismissRequest = {
            ttsManager.stop()
            try {
                if (mediaPlayer.isPlaying) mediaPlayer.stop()
            } catch (e: Exception) { /* ignore */ }
            onDismiss()
        },
        title = { 
            Text(
                when (voiceSource) {
                    VoiceSource.ELEVEN_LABS -> "Select ElevenLabs Voice"
                    VoiceSource.INWORLD -> "Select Inworld Voice"
                    VoiceSource.GEMINI -> "Select Gemini Voice"
                }
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 500.dp)
            ) {
                // Option to clear voice selection
                item {
                    VoiceListItemEnhanced(
                        voice = null,
                        isSelected = selectedVoiceId == null,
                        isPlaying = false,
                        isLoading = false,
                        onSelect = { onVoiceSelected(null) },
                        onPreview = null,
                        showAsNone = true
                    )
                }
                
                items(filteredVoices) { voice ->
                    val isPlayingThis = when (voiceSource) {
                        VoiceSource.ELEVEN_LABS -> currentPlayingId == voice.voiceId
                        VoiceSource.INWORLD,
                        VoiceSource.GEMINI -> generatedPlayingVoiceId == voice.voiceId
                    }
                    val isLoadingThis = when (voiceSource) {
                        VoiceSource.ELEVEN_LABS -> isPlayingThis && playbackState == TTSPlaybackState.LOADING
                        VoiceSource.INWORLD,
                        VoiceSource.GEMINI -> generatedPreviewLoading == voice.voiceId
                    }
                    val isActuallyPlaying = when (voiceSource) {
                        VoiceSource.ELEVEN_LABS -> isPlayingThis && playbackState == TTSPlaybackState.PLAYING
                        VoiceSource.INWORLD,
                        VoiceSource.GEMINI -> generatedPlayingVoiceId == voice.voiceId && generatedPreviewLoading == null
                    }
                    
                    VoiceListItemEnhanced(
                        voice = voice,
                        isSelected = voice.voiceId == selectedVoiceId,
                        isPlaying = isActuallyPlaying,
                        isLoading = isLoadingThis,
                        onSelect = { onVoiceSelected(voice) },
                        onPreview = {
                            when (voiceSource) {
                                VoiceSource.ELEVEN_LABS -> {
                                    voice.previewUrl?.let { url ->
                                        if (isPlayingThis && playbackState == TTSPlaybackState.PLAYING) {
                                            ttsManager.stop()
                                        } else {
                                            ttsManager.playFromUrl(url, voice.voiceId)
                                        }
                                    }
                                }
                                VoiceSource.INWORLD -> {
                                    playGeneratedPreview(voice, ".mp3") { previewText, voiceId ->
                                        inworldService.textToSpeech(previewText, voiceId)
                                    }
                                }
                                VoiceSource.GEMINI -> {
                                    playGeneratedPreview(voice, ".wav") { previewText, voiceId ->
                                        geminiTtsService.textToSpeech(previewText, voiceId)
                                    }
                                }
                            }
                        },
                        showAsNone = false
                    )
                }
                
                if (filteredVoices.isEmpty()) {
                    item {
                        Text(
                            text = "No voices available for this model${if (filterGender != null) " and gender" else ""}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ttsManager.stop()
                try {
                    if (mediaPlayer.isPlaying) mediaPlayer.stop()
                } catch (e: Exception) { /* ignore */ }
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun VoiceListItemEnhanced(
    voice: Voice?,
    isSelected: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onPreview: (() -> Unit)?,
    showAsNone: Boolean
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    
    // Animation for loading state
    val rotation by animateFloatAsState(
        targetValue = if (isLoading) 360f else 0f,
        label = "loading"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (showAsNone) {
                    Text(
                        text = "None (Use narrator voice)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                } else if (voice != null) {
                    Text(
                        text = voice.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    
                    // Description
                    voice.description?.let { desc ->
                        if (desc.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Metadata line (gender, accent, age, etc.)
                    val detailText = buildString {
                        voice.labels["workspace"]?.let { ws ->
                            if (voice.source == VoiceSource.INWORLD) append("Workspace: $ws")
                        }
                        voice.labels["style"]?.let { style ->
                            if (style.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(style)
                            }
                        }
                        voice.labels["language"]?.let { lang ->
                            if (lang.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(lang)
                            }
                        }
                        voice.gender?.let { gender ->
                            if (gender != "unknown") {
                                if (isNotEmpty()) append(" • ")
                                append(gender.replaceFirstChar { it.uppercase() })
                            }
                        }
                        voice.age?.let { age ->
                            if (age != "unknown") {
                                if (isNotEmpty()) append(" • ")
                                append(age)
                            }
                        }
                        voice.accent?.let { accent ->
                            if (isNotEmpty()) append(" • ")
                            append(accent)
                        }
                    }
                    
                    if (detailText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Tags for Inworld voices
                    voice.labels["tags"]?.let { tags ->
                        if (tags.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tags: $tags",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }
            
            if (onPreview != null && !showAsNone) {
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier.size(40.dp)
                ) {
                    when {
                        isLoading -> CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer { rotationZ = rotation },
                            strokeWidth = 2.dp
                        )
                        isPlaying -> Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        else -> Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
