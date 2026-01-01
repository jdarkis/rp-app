package com.example.rpapp3.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.ElevenLabsService
import com.example.rpapp3.data.TTSManager
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.MessageFilterMode
import com.example.rpapp3.data.ResponseLength
import com.example.rpapp3.data.SafetyThreshold
import com.example.rpapp3.data.model.ElevenLabsTTSModels
import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.viewmodel.ChatViewModel
import com.example.rpapp3.ui.components.SUPPORTED_LANGUAGES
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSettingsScreen(
    chatId: String,
    worldId: String,
    onNavigateBack: () -> Unit,
    chatViewModel: ChatViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatSettingsManager = remember { ChatSettingsManager.getInstance(context) }
    
    // Initialize ViewModel to load the system prompt
    LaunchedEffect(chatId, worldId) {
        chatViewModel.initializeWithContext(context)
        chatViewModel.initializeChat(chatId, worldId)
    }
    
    // Collect the system prompt from the ViewModel
    val systemPrompt by chatViewModel.systemPrompt.collectAsState()
    
    // Collect all settings
    val filterMode by chatSettingsManager.filterMode.collectAsState(initial = MessageFilterMode.OFF)
    val customDelimiter by chatSettingsManager.customDelimiter.collectAsState(initial = ChatSettingsManager.DEFAULT_DELIMITER)
    val paragraphCount by chatSettingsManager.paragraphCount.collectAsState(initial = ChatSettingsManager.DEFAULT_PARAGRAPH_COUNT)
    val streamingEnabled by chatSettingsManager.streamingEnabled.collectAsState(initial = false)
    val temperature by chatSettingsManager.temperature.collectAsState(initial = ChatSettingsManager.DEFAULT_TEMPERATURE)
    val topP by chatSettingsManager.topP.collectAsState(initial = ChatSettingsManager.DEFAULT_TOP_P)
    val topK by chatSettingsManager.topK.collectAsState(initial = ChatSettingsManager.DEFAULT_TOP_K)
    val maxOutputTokens by chatSettingsManager.maxOutputTokens.collectAsState(initial = ChatSettingsManager.DEFAULT_MAX_OUTPUT_TOKENS)
    val presencePenalty by chatSettingsManager.presencePenalty.collectAsState(initial = ChatSettingsManager.DEFAULT_PRESENCE_PENALTY)
    val frequencyPenalty by chatSettingsManager.frequencyPenalty.collectAsState(initial = ChatSettingsManager.DEFAULT_FREQUENCY_PENALTY)
    val thinkingEnabled by chatSettingsManager.thinkingEnabled.collectAsState(initial = false)
    val safetyHarassment by chatSettingsManager.safetyHarassment.collectAsState(initial = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE)
    val safetyHateSpeech by chatSettingsManager.safetyHateSpeech.collectAsState(initial = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE)
    val safetySexuallyExplicit by chatSettingsManager.safetySexuallyExplicit.collectAsState(initial = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE)
    val safetyDangerousContent by chatSettingsManager.safetyDangerousContent.collectAsState(initial = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE)
    val separateCharacterDialogue by chatSettingsManager.separateCharacterDialogue.collectAsState(initial = true)
    val provideChoicesEnabled by chatSettingsManager.provideChoicesEnabled.collectAsState(initial = true)
    val responseLength by chatSettingsManager.responseLength.collectAsState(initial = ResponseLength.MEDIUM)
    
    // TTS Settings
    val ttsEnabled by chatSettingsManager.ttsEnabled.collectAsState(initial = false)
    val autoTtsEnabled by chatSettingsManager.autoTtsEnabled.collectAsState(initial = false)
    val ttsAudioTagsEnabled by chatSettingsManager.ttsAudioTagsEnabled.collectAsState(initial = false)
    val narratorVoiceId by chatSettingsManager.narratorVoiceId.collectAsState(initial = "")
    val ttsModelId by chatSettingsManager.ttsModelId.collectAsState(initial = ChatSettingsManager.DEFAULT_TTS_MODEL_ID)
    
    // Unlock Prompt Setting
    val unlockPromptEnabled by chatSettingsManager.unlockPromptEnabled.collectAsState(initial = false)
    
    // Narrator Language Setting
    val narratorLanguage by chatSettingsManager.narratorLanguage.collectAsState(initial = "en")
    
    // ElevenLabs Service and Voices
    val elevenLabsService = remember { ElevenLabsService.getInstance(context) }
    val ttsManager = remember { TTSManager.getInstance(context) }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var voicesLoading by remember { mutableStateOf(false) }
    val playbackState by ttsManager.playbackState.collectAsState()
    val currentPlayingId by ttsManager.currentPlayingId.collectAsState()
    
    // Load voices when TTS section is opened
    LaunchedEffect(Unit) {
        elevenLabsService.initialize()
    }
    
    // Local state for inputs
    var delimiterInput by remember(customDelimiter) { mutableStateOf(customDelimiter) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    
    // Expandable sections
    var displaySectionExpanded by remember { mutableStateOf(true) }
    var generationSectionExpanded by remember { mutableStateOf(true) }
    var styleSectionExpanded by remember { mutableStateOf(false) }
    var safetySectionExpanded by remember { mutableStateOf(false) }
    var advancedSectionExpanded by remember { mutableStateOf(false) }
    var ttsSectionExpanded by remember { mutableStateOf(false) }
    
    // System Prompt Dialog
    if (showSystemPromptDialog) {
        AlertDialog(
            onDismissRequest = { showSystemPromptDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("System Prompt")
                }
            },
            text = {
                Column {
                    Text(
                        text = "This is the prompt that will be sent to the AI for the next message:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = systemPrompt ?: "No system prompt available. Start a chat to see the prompt.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSystemPromptDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Settings") },
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
        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Message Display Section
                SettingsSection(
                    title = "Message Display",
                    expanded = displaySectionExpanded,
                    onToggle = { displaySectionExpanded = !displaySectionExpanded }
                ) {
                    Text(
                        text = "Choose how to display AI-generated messages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Separate Character Dialogue Toggle
                    SettingsToggle(
                        title = "Separate Character Dialogue",
                        description = "Display character speech as individual messages with avatars",
                        checked = separateCharacterDialogue,
                        onCheckedChange = { scope.launch { chatSettingsManager.setSeparateCharacterDialogue(it) } }
                    )
                    
                    // Provide Action & Dialogue Choices Toggle
                    SettingsToggle(
                        title = "Provide Action & Dialogue Choices",
                        description = "AI will provide clickable action and dialogue options at the end of messages",
                        checked = provideChoicesEnabled,
                        onCheckedChange = { scope.launch { chatSettingsManager.setProvideChoicesEnabled(it) } }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "Message filtering",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    FilterModeOption(
                        title = "Show full message",
                        description = "Display complete AI responses",
                        selected = filterMode == MessageFilterMode.OFF,
                        onClick = {
                            scope.launch { chatSettingsManager.setFilterMode(MessageFilterMode.OFF) }
                        }
                    )
                    
                    FilterModeOption(
                        title = "Last N paragraphs",
                        description = "Show only the last $paragraphCount paragraph${if (paragraphCount > 1) "s" else ""}",
                        selected = filterMode == MessageFilterMode.LAST_N_PARAGRAPHS,
                        onClick = {
                            scope.launch { chatSettingsManager.setFilterMode(MessageFilterMode.LAST_N_PARAGRAPHS) }
                        }
                    )
                    
                    if (filterMode == MessageFilterMode.LAST_N_PARAGRAPHS) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Number of paragraphs",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = paragraphCount.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Slider(
                                value = paragraphCount.toFloat(),
                                onValueChange = { newValue ->
                                    scope.launch { chatSettingsManager.setParagraphCount(newValue.toInt()) }
                                },
                                valueRange = 1f..20f,
                                steps = 18,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    FilterModeOption(
                        title = "After custom delimiter",
                        description = "Show text appearing after a specific symbol",
                        selected = filterMode == MessageFilterMode.AFTER_DELIMITER,
                        onClick = {
                            scope.launch { chatSettingsManager.setFilterMode(MessageFilterMode.AFTER_DELIMITER) }
                        }
                    )
                    
                    if (filterMode == MessageFilterMode.AFTER_DELIMITER) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = delimiterInput,
                            onValueChange = { delimiterInput = it },
                            label = { Text("Delimiter") },
                            placeholder = { Text("e.g. *** or ---") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = { Text("Text after this symbol will be shown") },
                            trailingIcon = {
                                if (delimiterInput != customDelimiter && delimiterInput.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            scope.launch { chatSettingsManager.setCustomDelimiter(delimiterInput) }
                                        }
                                    ) {
                                        Text("Save")
                                    }
                                }
                            }
                        )
                    }
                }
                
                // Response Generation Section
                SettingsSection(
                    title = "Response Generation",
                    expanded = generationSectionExpanded,
                    onToggle = { generationSectionExpanded = !generationSectionExpanded }
                ) {
                    // Streaming Toggle
                    SettingsToggle(
                        title = "Streaming",
                        description = "See responses appear in real-time as they're generated",
                        checked = streamingEnabled,
                        onCheckedChange = { scope.launch { chatSettingsManager.setStreamingEnabled(it) } }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Response Length Dropdown
                    ResponseLengthDropdown(
                        value = responseLength,
                        onValueChange = { scope.launch { chatSettingsManager.setResponseLength(it) } }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Narrator Language Selector
                    Text(
                        text = "Narrator Language",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Language for narration (descriptions, actions). Dialogue uses each character's language.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    var narratorLangExpanded by remember { mutableStateOf(false) }
                    val selectedLangLabel = SUPPORTED_LANGUAGES.find { it.first == narratorLanguage }?.second ?: "English"
                    
                    ExposedDropdownMenuBox(
                        expanded = narratorLangExpanded,
                        onExpandedChange = { narratorLangExpanded = !narratorLangExpanded },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedLangLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = narratorLangExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = narratorLangExpanded,
                            onDismissRequest = { narratorLangExpanded = false }
                        ) {
                            SUPPORTED_LANGUAGES.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        scope.launch { chatSettingsManager.setNarratorLanguage(code) }
                                        narratorLangExpanded = false
                                    },
                                    leadingIcon = if (code == narratorLanguage) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Temperature Slider
                    SettingsSlider(
                        title = "Temperature",
                        description = "Controls creativity/randomness (0 = focused, 2 = creative)",
                        value = temperature,
                        valueRange = 0f..2f,
                        steps = 19,
                        valueFormatter = { String.format("%.1f", it) },
                        onValueChange = { scope.launch { chatSettingsManager.setTemperature(it) } }
                    )
                    
                    // TopP Slider
                    SettingsSlider(
                        title = "Top P (Nucleus Sampling)",
                        description = "Probability threshold for response diversity",
                        value = topP,
                        valueRange = 0f..1f,
                        steps = 19,
                        valueFormatter = { String.format("%.2f", it) },
                        onValueChange = { scope.launch { chatSettingsManager.setTopP(it) } }
                    )
                    
                    // TopK Slider
                    SettingsSlider(
                        title = "Top K",
                        description = "Maximum tokens to consider when sampling",
                        value = topK.toFloat(),
                        valueRange = 1f..100f,
                        steps = 98,
                        valueFormatter = { it.roundToInt().toString() },
                        onValueChange = { scope.launch { chatSettingsManager.setTopK(it.roundToInt()) } }
                    )
                    
                    // Max Output Tokens Slider
                    SettingsSlider(
                        title = "Max Output Tokens",
                        description = "Maximum response length (higher = longer responses)",
                        value = maxOutputTokens.toFloat(),
                        valueRange = 256f..32768f,
                        steps = 127,
                        valueFormatter = { it.roundToInt().toString() },
                        onValueChange = { scope.launch { chatSettingsManager.setMaxOutputTokens(it.roundToInt()) } }
                    )
                }
                
                // Text-to-Speech Section
                SettingsSection(
                    title = "Text-to-Speech",
                    expanded = ttsSectionExpanded,
                    onToggle = { 
                        ttsSectionExpanded = !ttsSectionExpanded
                        // Load voices when opening
                        if (!ttsSectionExpanded.not() && voices.isEmpty()) {
                            voicesLoading = true
                            scope.launch {
                                elevenLabsService.getVoices().onSuccess {
                                    voices = it
                                }
                                voicesLoading = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Enable voice synthesis for AI messages using ElevenLabs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // TTS Enable Toggle
                    SettingsToggle(
                        title = "Enable Text-to-Speech",
                        description = "Add play buttons to AI messages for voice playback",
                        checked = ttsEnabled,
                        onCheckedChange = { scope.launch { chatSettingsManager.setTtsEnabled(it) } }
                    )
                    
                    if (ttsEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // Auto-play TTS Toggle
                        SettingsToggle(
                            title = "Auto-play TTS",
                            description = "Automatically speak AI responses as they appear",
                            checked = autoTtsEnabled,
                            onCheckedChange = { scope.launch { chatSettingsManager.setAutoTtsEnabled(it) } }
                        )
                        
                        // Audio Tags Toggle
                        SettingsToggle(
                            title = "Enable Audio Tags",
                            description = "AI will include performance directions like [whispers], [laughs] for realistic speech",
                            checked = ttsAudioTagsEnabled,
                            onCheckedChange = { scope.launch { chatSettingsManager.setTtsAudioTagsEnabled(it) } }
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // TTS Model Selector
                        Text(
                            text = "TTS Model",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Select the ElevenLabs model for voice synthesis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        var modelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = !modelExpanded },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            val selectedModel = ElevenLabsTTSModels.DEFAULT_MODELS.find { it.modelId == ttsModelId }
                            OutlinedTextField(
                                value = selectedModel?.name ?: "Eleven V3",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelExpanded) },
                                supportingText = { Text(selectedModel?.description ?: "") },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                ElevenLabsTTSModels.DEFAULT_MODELS.forEach { model ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(model.name)
                                                Text(
                                                    model.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            scope.launch { chatSettingsManager.setTtsModelId(model.modelId) }
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Narrator Voice Selector
                        Text(
                            text = "Narrator Voice",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Voice used for narration and characters without assigned voices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (voicesLoading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        } else {
                            var voiceExpanded by remember { mutableStateOf(false) }
                            val selectedVoice = voices.find { it.voiceId == narratorVoiceId }
                            
                            ExposedDropdownMenuBox(
                                expanded = voiceExpanded,
                                onExpandedChange = { voiceExpanded = !voiceExpanded },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedVoice?.name ?: "Select a voice",
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { 
                                        Row {
                                            // Preview button
                                            selectedVoice?.previewUrl?.let { url ->
                                                val isPlaying = currentPlayingId == selectedVoice.voiceId && playbackState == TTSPlaybackState.PLAYING
                                                IconButton(
                                                    onClick = {
                                                        if (isPlaying) {
                                                            ttsManager.stop()
                                                        } else {
                                                            ttsManager.playFromUrl(url, selectedVoice.voiceId)
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                        contentDescription = "Preview"
                                                    )
                                                }
                                            }
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceExpanded)
                                        }
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = voiceExpanded,
                                    onDismissRequest = { voiceExpanded = false }
                                ) {
                                    voices.take(50).forEach { voice ->
                                        DropdownMenuItem(
                                            text = { 
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(voice.name)
                                                        val details = listOfNotNull(
                                                            voice.gender?.replaceFirstChar { it.uppercase() },
                                                            voice.accent
                                                        ).joinToString(" • ")
                                                        if (details.isNotEmpty()) {
                                                            Text(
                                                                details,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    voice.previewUrl?.let { url ->
                                                        val isPlaying = currentPlayingId == voice.voiceId && playbackState == TTSPlaybackState.PLAYING
                                                        IconButton(
                                                            onClick = {
                                                                if (isPlaying) {
                                                                    ttsManager.stop()
                                                                } else {
                                                                    ttsManager.playFromUrl(url, voice.voiceId)
                                                                }
                                                            }
                                                        ) {
                                                            Icon(
                                                                if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                                                contentDescription = "Preview"
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                scope.launch { chatSettingsManager.setNarratorVoiceId(voice.voiceId) }
                                                voiceExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Response Style Section
                SettingsSection(
                    title = "Response Style",
                    expanded = styleSectionExpanded,
                    onToggle = { styleSectionExpanded = !styleSectionExpanded }
                ) {
                    // Presence Penalty
                    SettingsSlider(
                        title = "Presence Penalty",
                        description = "Discourages repeating tokens already used",
                        value = presencePenalty,
                        valueRange = -2f..2f,
                        steps = 39,
                        valueFormatter = { String.format("%.1f", it) },
                        onValueChange = { scope.launch { chatSettingsManager.setPresencePenalty(it) } }
                    )
                    
                    // Frequency Penalty
                    SettingsSlider(
                        title = "Frequency Penalty",
                        description = "Reduces repetition proportional to token usage",
                        value = frequencyPenalty,
                        valueRange = -2f..2f,
                        steps = 39,
                        valueFormatter = { String.format("%.1f", it) },
                        onValueChange = { scope.launch { chatSettingsManager.setFrequencyPenalty(it) } }
                    )
                }
                
                // Safety Settings Section
                SettingsSection(
                    title = "Safety Settings",
                    expanded = safetySectionExpanded,
                    onToggle = { safetySectionExpanded = !safetySectionExpanded }
                ) {
                    Text(
                        text = "Adjust content filtering thresholds for different categories",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    SafetySettingDropdown(
                        title = "Harassment",
                        value = safetyHarassment,
                        onValueChange = { scope.launch { chatSettingsManager.setSafetyHarassment(it) } }
                    )
                    
                    SafetySettingDropdown(
                        title = "Hate Speech",
                        value = safetyHateSpeech,
                        onValueChange = { scope.launch { chatSettingsManager.setSafetyHateSpeech(it) } }
                    )
                    
                    SafetySettingDropdown(
                        title = "Sexually Explicit",
                        value = safetySexuallyExplicit,
                        onValueChange = { scope.launch { chatSettingsManager.setSafetySexuallyExplicit(it) } }
                    )
                    
                    SafetySettingDropdown(
                        title = "Dangerous Content",
                        value = safetyDangerousContent,
                        onValueChange = { scope.launch { chatSettingsManager.setSafetyDangerousContent(it) } }
                    )
                }
                
                // Advanced Section
                SettingsSection(
                    title = "Advanced",
                    expanded = advancedSectionExpanded,
                    onToggle = { advancedSectionExpanded = !advancedSectionExpanded }
                ) {
                    // Thinking Mode Toggle
                    SettingsToggle(
                        title = "Thinking Mode",
                        description = "Enable extended reasoning for better narrative planning (Gemini 3+)",
                        checked = thinkingEnabled,
                        onCheckedChange = { scope.launch { chatSettingsManager.setThinkingEnabled(it) } }
                    )
                    
                    // Unlock Prompt Toggle
                    SettingsToggle(
                        title = "Enable Unlock Prompt",
                        description = "Include the unlock prompt from app settings at the start of system instructions",
                        checked = unlockPromptEnabled,
                        onCheckedChange = { scope.launch { chatSettingsManager.setUnlockPromptEnabled(it) } }
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // View System Prompt Button
                    OutlinedButton(
                        onClick = { showSystemPromptDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("View System Prompt")
                    }
                    
                    Text(
                        text = "See the exact instructions being sent to the AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                // Model Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "Current Model",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "gemini-3-flash-preview",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                
                // Restore to Defaults Button
                var showRestoreConfirmDialog by remember { mutableStateOf(false) }
                
                if (showRestoreConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showRestoreConfirmDialog = false },
                        title = { Text("Restore Defaults?") },
                        text = { Text("This will reset all chat settings to their default values. This action cannot be undone.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showRestoreConfirmDialog = false
                                    scope.launch { chatSettingsManager.restoreDefaults() }
                                }
                            ) {
                                Text("Restore", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestoreConfirmDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                
                OutlinedButton(
                    onClick = { showRestoreConfirmDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Restore to Defaults")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun FilterModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    description: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = valueFormatter(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SafetySettingDropdown(
    title: String,
    value: SafetyThreshold,
    onValueChange: (SafetyThreshold) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val options = listOf(
        SafetyThreshold.BLOCK_NONE to "Block None",
        SafetyThreshold.BLOCK_ONLY_HIGH to "Block Only High",
        SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE to "Block Medium+",
        SafetyThreshold.BLOCK_LOW_AND_ABOVE to "Block Low+ (Strictest)"
    )
    
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = options.find { it.first == value }?.second ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (threshold, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onValueChange(threshold)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponseLengthDropdown(
    value: ResponseLength,
    onValueChange: (ResponseLength) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    val options = listOf(
        ResponseLength.SHORT to "Short" to "1-2 paragraphs, concise",
        ResponseLength.MEDIUM to "Medium" to "2-3 paragraphs, balanced",
        ResponseLength.LONG to "Long" to "4-5 paragraphs, detailed",
        ResponseLength.VERY_LONG to "Very Long" to "No limit, elaborate"
    )
    
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = "Response Length",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Controls how long AI responses should be",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.padding(top = 8.dp)
        ) {
            val selectedOption = options.find { it.first.first == value }
            OutlinedTextField(
                value = selectedOption?.first?.second ?: "Medium",
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                supportingText = { Text(selectedOption?.second ?: "") },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (lengthAndLabel, description) ->
                    val (length, label) = lengthAndLabel
                    DropdownMenuItem(
                        text = { 
                            Column {
                                Text(label)
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onValueChange(length)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
