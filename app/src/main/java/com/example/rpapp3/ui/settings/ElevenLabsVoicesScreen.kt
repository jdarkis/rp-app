package com.example.rpapp3.ui.settings

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.ElevenLabsService
import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.repository.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevenLabsVoicesScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceRepository = remember { VoiceRepository() }
    val elevenLabsService = remember { ElevenLabsService.getInstance(context) }
    
    val customVoices by voiceRepository.getCustomVoices().collectAsState(initial = emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentlyPlayingId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Cleanup media player on dispose
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ElevenLabs Voices") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Voice")
            }
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
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Description Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = "Custom Voices",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Add ElevenLabs voices by their Voice ID. You can find voice IDs in your ElevenLabs Voice Library.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (customVoices.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No custom voices added yet.\nTap + to add a voice.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(customVoices, key = { it.voiceId }) { voice ->
                                VoiceItem(
                                    voice = voice,
                                    isPlaying = currentlyPlayingId == voice.voiceId,
                                    onPreview = {
                                        scope.launch {
                                            if (currentlyPlayingId == voice.voiceId) {
                                                // Stop playing
                                                mediaPlayer?.stop()
                                                mediaPlayer?.release()
                                                mediaPlayer = null
                                                currentlyPlayingId = null
                                            } else {
                                                // Start playing
                                                val previewUrl = voice.previewUrl
                                                if (previewUrl != null) {
                                                    try {
                                                        mediaPlayer?.release()
                                                        currentlyPlayingId = voice.voiceId
                                                        mediaPlayer = MediaPlayer().apply {
                                                            setDataSource(previewUrl)
                                                            setOnCompletionListener {
                                                                currentlyPlayingId = null
                                                            }
                                                            setOnErrorListener { _, _, _ ->
                                                                currentlyPlayingId = null
                                                                true
                                                            }
                                                            prepareAsync()
                                                            setOnPreparedListener { start() }
                                                        }
                                                    } catch (e: Exception) {
                                                        currentlyPlayingId = null
                                                        snackbarHostState.showSnackbar("Failed to play preview")
                                                    }
                                                } else {
                                                    snackbarHostState.showSnackbar("No preview available")
                                                }
                                            }
                                        }
                                    },
                                    onDelete = {
                                        scope.launch {
                                            voiceRepository.removeVoice(voice.voiceId)
                                                .onSuccess {
                                                    snackbarHostState.showSnackbar("Voice removed")
                                                }
                                                .onFailure {
                                                    snackbarHostState.showSnackbar("Failed to remove voice")
                                                }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Add Voice Dialog
    if (showAddDialog) {
        AddVoiceDialog(
            onDismiss = { showAddDialog = false },
            onAddVoice = { voiceId ->
                scope.launch {
                    isLoading = true
                    showAddDialog = false
                    
                    // Check if voice already exists
                    if (voiceRepository.hasVoice(voiceId)) {
                        snackbarHostState.showSnackbar("Voice already added")
                        isLoading = false
                        return@launch
                    }
                    
                    // Search for the voice in ElevenLabs
                    val sharedVoice = withContext(Dispatchers.IO) {
                        elevenLabsService.searchSharedVoice(voiceId)
                    }
                    
                    if (sharedVoice != null) {
                        val voice = Voice(
                            voiceId = sharedVoice.voiceId,
                            name = sharedVoice.name,
                            previewUrl = sharedVoice.previewUrl,
                            labels = emptyMap()
                        )
                        voiceRepository.addVoice(voice)
                            .onSuccess {
                                snackbarHostState.showSnackbar("Voice '${voice.name}' added")
                            }
                            .onFailure {
                                snackbarHostState.showSnackbar("Failed to save voice")
                            }
                    } else {
                        snackbarHostState.showSnackbar("Voice not found. Check the ID and try again.")
                    }
                    
                    isLoading = false
                }
            }
        )
    }
}

@Composable
private fun VoiceItem(
    voice: Voice,
    isPlaying: Boolean,
    onPreview: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = voice.voiceId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Preview button
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Preview",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            // Delete button
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Voice") },
            text = { Text("Are you sure you want to remove '${voice.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AddVoiceDialog(
    onDismiss: () -> Unit,
    onAddVoice: (String) -> Unit
) {
    var voiceId by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Voice") },
        text = {
            Column {
                Text(
                    text = "Enter the ElevenLabs Voice ID:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = voiceId,
                    onValueChange = { voiceId = it.trim() },
                    label = { Text("Voice ID") },
                    placeholder = { Text("e.g., cgSgspJ2msm6clMCkdW9") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAddVoice(voiceId) },
                enabled = voiceId.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
