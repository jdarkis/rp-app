package com.example.rpapp3.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.TTSManager
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.model.Voice

/**
 * Common language options for character TTS
 */
val SUPPORTED_LANGUAGES = listOf(
    "en" to "English",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "it" to "Italian",
    "pt" to "Portuguese",
    "pl" to "Polish",
    "ru" to "Russian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "zh" to "Chinese",
    "ar" to "Arabic",
    "hi" to "Hindi",
    "tr" to "Turkish",
    "nl" to "Dutch",
    "sv" to "Swedish"
)

val GENDER_OPTIONS = listOf(
    null to "Any",
    "male" to "Male",
    "female" to "Female"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = SUPPORTED_LANGUAGES.find { it.first == selectedLanguage }?.second ?: "English"
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Language") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
                .fillMaxWidth(),
            enabled = enabled
        )
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SUPPORTED_LANGUAGES.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onLanguageSelected(code)
                        expanded = false
                    },
                    leadingIcon = if (code == selectedLanguage) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun GenderSelector(
    selectedGender: String?,
    onGenderSelected: (String?) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GENDER_OPTIONS.forEach { (genderValue, label) ->
            FilterChip(
                selected = selectedGender == genderValue,
                onClick = { if (enabled) onGenderSelected(genderValue) },
                label = { Text(label) },
                leadingIcon = if (selectedGender == genderValue) {
                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                } else null,
                enabled = enabled
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelector(
    voices: List<Voice>,
    selectedVoiceId: String?,
    onVoiceSelected: (Voice?) -> Unit,
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
        VoiceSelectorDialog(
            voices = voices,
            selectedVoiceId = selectedVoiceId,
            onVoiceSelected = { voice ->
                onVoiceSelected(voice)
                showDialog = false
            },
            onDismiss = { showDialog = false },
            ttsManager = ttsManager,
            filterGender = filterGender
        )
    }
}

@Composable
private fun VoiceSelectorDialog(
    voices: List<Voice>,
    selectedVoiceId: String?,
    onVoiceSelected: (Voice?) -> Unit,
    onDismiss: () -> Unit,
    ttsManager: TTSManager,
    filterGender: String? = null
) {
    val playbackState by ttsManager.playbackState.collectAsState()
    val currentPlayingId by ttsManager.currentPlayingId.collectAsState()
    
    // Filter voices by gender if specified
    val filteredVoices = remember(voices, filterGender) {
        if (filterGender != null) {
            voices.filter { it.gender?.lowercase() == filterGender.lowercase() }
        } else {
            voices
        }
    }
    
    AlertDialog(
        onDismissRequest = {
            ttsManager.stop()
            onDismiss()
        },
        title = { Text("Select Voice") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                // Option to clear voice selection
                item {
                    VoiceListItem(
                        name = "None (Use narrator voice)",
                        details = null,
                        isSelected = selectedVoiceId == null,
                        isPlaying = false,
                        isLoading = false,
                        onSelect = { onVoiceSelected(null) },
                        onPreview = null
                    )
                }
                
                items(filteredVoices) { voice ->
                    val isPlayingThis = currentPlayingId == voice.voiceId
                    val isLoadingThis = isPlayingThis && playbackState == TTSPlaybackState.LOADING
                    
                    VoiceListItem(
                        name = voice.name,
                        details = buildList {
                            voice.gender?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
                            voice.accent?.let { add(it) }
                            voice.age?.let { add(it) }
                        }.joinToString(" • "),
                        isSelected = voice.voiceId == selectedVoiceId,
                        isPlaying = isPlayingThis && playbackState == TTSPlaybackState.PLAYING,
                        isLoading = isLoadingThis,
                        onSelect = { onVoiceSelected(voice) },
                        onPreview = voice.previewUrl?.let { url ->
                            {
                                if (isPlayingThis && playbackState == TTSPlaybackState.PLAYING) {
                                    ttsManager.stop()
                                } else {
                                    ttsManager.playFromUrl(url, voice.voiceId)
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ttsManager.stop()
                onDismiss()
            }) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun VoiceListItem(
    name: String,
    details: String?,
    isSelected: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onPreview: (() -> Unit)?
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }
    
    // Animation for play button
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                if (!details.isNullOrBlank()) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (onPreview != null) {
                IconButton(
                    onClick = onPreview,
                    modifier = Modifier.size(36.dp)
                ) {
                    when {
                        isLoading -> CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
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
