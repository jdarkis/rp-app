package com.example.rpapp3.ui.settings

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.InworldService
import com.example.rpapp3.data.model.Voice
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InworldVoicesScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inworldService = remember { InworldService.getInstance(context) }
    
    var availableVoices by remember { mutableStateOf<List<Voice>?>(null) }
    var isLoadingAvailable by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Audio Preview State
    var currentPlayingVoiceId by remember { mutableStateOf<String?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }
    val mediaPlayer = remember { MediaPlayer() }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
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
    
    fun playPreview(voice: Voice) {
        scope.launch {
            // Stop current playback
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.reset()
            } catch (e: Exception) {
                // If reset fails, re-create might be safer, but reset usually works
                mediaPlayer.release()
            }
            
            if (currentPlayingVoiceId == voice.voiceId && !isPreviewLoading) {
                // Toggle stop
                currentPlayingVoiceId = null
                return@launch
            }
            
            currentPlayingVoiceId = voice.voiceId
            isPreviewLoading = true
            
            // Generate speech
            val text = "Hello, I am ${voice.name}. This is a preview of my voice."
            inworldService.textToSpeech(text, voice.voiceId)
                .onSuccess { audioData ->
                    try {
                        // Write to temp file
                        val tempFile = File.createTempFile("preview_${voice.voiceId}", ".mp3", context.cacheDir)
                        FileOutputStream(tempFile).use { it.write(audioData) }
                        
                        mediaPlayer.setDataSource(tempFile.absolutePath)
                        mediaPlayer.setOnCompletionListener {
                            currentPlayingVoiceId = null
                            isPreviewLoading = false
                        }
                        mediaPlayer.prepare()
                        mediaPlayer.start()
                        isPreviewLoading = false
                    } catch (e: Exception) {
                        e.printStackTrace()
                        snackbarHostState.showSnackbar("Failed to play preview: ${e.message}")
                        currentPlayingVoiceId = null
                        isPreviewLoading = false
                    }
                }
                .onFailure { e ->
                    snackbarHostState.showSnackbar("Failed to generate preview: ${e.message}")
                    currentPlayingVoiceId = null
                    isPreviewLoading = false
                }
        }
    }
    
    fun loadAvailableVoices() {
        scope.launch {
            isLoadingAvailable = true
            error = null
            inworldService.getVoices()
                .onSuccess { voices ->
                    availableVoices = voices
                }
                .onFailure { e ->
                    error = e.message ?: "Failed to load voices"
                    snackbarHostState.showSnackbar(error ?: "Unknown error")
                }
            isLoadingAvailable = false
        }
    }
    
    LaunchedEffect(Unit) {
        loadAvailableVoices()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inworld Voices") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadAvailableVoices() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(16.dp)) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = "Inworld Voices",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Browse available Inworld characters. Changes to character voices can be made in the chat settings.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                if (isLoadingAvailable) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (availableVoices == null) {
                    item {
                        Text(
                            text = "Tap refresh to load voices from Inworld.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (availableVoices!!.isEmpty()) {
                    item {
                        Text(
                            text = "No characters found. Check your API key and workspaces.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    items(availableVoices!!) { voice ->
                        AvailableVoiceItem(
                            voice = voice,
                            isPlaying = currentPlayingVoiceId == voice.voiceId,
                            isLoading = currentPlayingVoiceId == voice.voiceId && isPreviewLoading,
                            onPlayPreview = { playPreview(voice) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AvailableVoiceItem(
    voice: Voice,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPreview: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Description
                val description = voice.labels["description"]
                if (!description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Metadata line
                val ws = voice.labels["workspace"]
                val language = voice.labels["language"]
                val gender = voice.labels["gender"]
                val age = voice.labels["age"]
                
                val detailText = buildString {
                    if (ws != null) append("Workspace: $ws") 
                    
                    if (!language.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(language)
                    }
                    if (gender != null && gender != "unknown") {
                        if (isNotEmpty()) append(" • ")
                        append(gender)
                    }
                    if (age != null && age != "unknown") {
                        if (isNotEmpty()) append(" • ")
                        append(age)
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
                
                // Tags
                val tags = voice.labels["tags"]
                if (!tags.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tags: $tags",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
            
            IconButton(onClick = onPlayPreview) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (isPlaying) "Stop Preview" else "Play Preview",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

