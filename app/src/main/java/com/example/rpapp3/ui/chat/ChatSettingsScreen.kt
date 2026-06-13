package com.example.rpapp3.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rpapp3.data.AiProvider
import com.example.rpapp3.data.BedrockSamplingMode
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.TTSManager
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.MessageFilterMode
import com.example.rpapp3.data.ResponseLength
import com.example.rpapp3.data.SafetyThreshold
import com.example.rpapp3.data.model.AllTTSModels
import com.example.rpapp3.data.model.ElevenLabsTTSModels
import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.viewmodel.ChatViewModel
import com.example.rpapp3.ui.components.SUPPORTED_LANGUAGES
import com.example.rpapp3.data.SummaryDetailLevel
import com.example.rpapp3.data.repository.SummarizerPromptsRepository
import com.example.rpapp3.data.repository.SummarizerPrompts
import com.example.rpapp3.data.repository.VoiceRepository
import com.example.rpapp3.data.InworldService
import com.example.rpapp3.data.selectableElevenLabsVoices
import com.example.rpapp3.data.model.InworldTTSModels
import com.example.rpapp3.data.model.VoiceSource
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
    val customDelimiters by chatSettingsManager.customDelimiters.collectAsState(initial = listOf(ChatSettingsManager.DEFAULT_DELIMITER))
    val paragraphCount by chatSettingsManager.paragraphCount.collectAsState(initial = ChatSettingsManager.DEFAULT_PARAGRAPH_COUNT)
    val streamingEnabled by chatSettingsManager.streamingEnabled.collectAsState(initial = false)
    val temperature by chatSettingsManager.temperature.collectAsState(initial = ChatSettingsManager.DEFAULT_TEMPERATURE)
    val topP by chatSettingsManager.topP.collectAsState(initial = ChatSettingsManager.DEFAULT_TOP_P)
    val topK by chatSettingsManager.topK.collectAsState(initial = ChatSettingsManager.DEFAULT_TOP_K)
    val maxOutputTokens by chatSettingsManager.maxOutputTokens.collectAsState(initial = ChatSettingsManager.DEFAULT_MAX_OUTPUT_TOKENS)
    val presencePenalty by chatSettingsManager.presencePenalty.collectAsState(initial = ChatSettingsManager.DEFAULT_PRESENCE_PENALTY)
    val frequencyPenalty by chatSettingsManager.frequencyPenalty.collectAsState(initial = ChatSettingsManager.DEFAULT_FREQUENCY_PENALTY)
    val thinkingEnabled by chatSettingsManager.thinkingEnabled.collectAsState(initial = false)
    val bedrockSamplingMode by chatSettingsManager.bedrockSamplingMode.collectAsState(
        initial = ChatSettingsManager.DEFAULT_BEDROCK_SAMPLING_MODE
    )
    val bedrockTemperature by chatSettingsManager.bedrockTemperature.collectAsState(
        initial = ChatSettingsManager.DEFAULT_BEDROCK_TEMPERATURE
    )
    val bedrockTopP by chatSettingsManager.bedrockTopP.collectAsState(
        initial = ChatSettingsManager.DEFAULT_BEDROCK_TOP_P
    )
    val bedrockTopK by chatSettingsManager.bedrockTopK.collectAsState(
        initial = ChatSettingsManager.DEFAULT_BEDROCK_TOP_K
    )
    val bedrockTopKEnabled by chatSettingsManager.bedrockTopKEnabled.collectAsState(
        initial = ChatSettingsManager.DEFAULT_BEDROCK_TOP_K_ENABLED
    )
    val bedrockMaxOutputTokens by chatSettingsManager.bedrockMaxOutputTokens.collectAsState(
        initial = ChatSettingsManager.DEFAULT_BEDROCK_MAX_OUTPUT_TOKENS
    )
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

    // System Prompt Setting
    val systemPromptEnabled by chatSettingsManager.systemPromptEnabled.collectAsState(initial = true)

    // Narrator Language Setting
    val narratorLanguage by chatSettingsManager.narratorLanguage.collectAsState(initial = "en")
    
    // AI Model Setting
    val aiModelId by chatSettingsManager.aiModelId.collectAsState(initial = ChatSettingsManager.DEFAULT_AI_MODEL_ID)
    val isBedrockModel = ChatSettingsManager.aiProviderFor(aiModelId) == AiProvider.BEDROCK
    
    // Saved ElevenLabs voices and the live Inworld catalog
    val voiceRepository = remember { VoiceRepository() }
    val savedElevenLabsVoicesFlow = remember(voiceRepository) {
        voiceRepository.getCustomVoices()
    }
    val savedElevenLabsVoices by savedElevenLabsVoicesFlow
        .collectAsState(initial = emptyList())
    val inworldService = remember { InworldService.getInstance(context) }
    val ttsManager = remember { TTSManager.getInstance(context) }
    var inworldVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    val voices = remember(savedElevenLabsVoices, inworldVoices) {
        selectableElevenLabsVoices(savedElevenLabsVoices) +
            inworldVoices.filter { it.source == VoiceSource.INWORLD }
    }
    var voicesLoading by remember { mutableStateOf(false) }
    val playbackState by ttsManager.playbackState.collectAsState()
    val currentPlayingId by ttsManager.currentPlayingId.collectAsState()
    
    // Load voices when TTS section is opened
    LaunchedEffect(Unit) {
        inworldService.initialize()
    }
    
    // Local state for inputs
    var delimiterInputs by remember(customDelimiters) { mutableStateOf(customDelimiters) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    
    // Summarizer state
    val summaryProposal by chatViewModel.summaryProposal.collectAsState()
    val isSummarizing by chatViewModel.isSummarizing.collectAsState()
    val summaryError by chatViewModel.summaryError.collectAsState()
    var showSummaryErrorSnackbar by remember { mutableStateOf(false) }
    
    // Expandable sections
    var displaySectionExpanded by remember { mutableStateOf(true) }
    var generationSectionExpanded by remember { mutableStateOf(true) }
    var styleSectionExpanded by remember { mutableStateOf(false) }
    var safetySectionExpanded by remember { mutableStateOf(false) }
    var advancedSectionExpanded by remember { mutableStateOf(false) }
    var ttsSectionExpanded by remember { mutableStateOf(false) }
    var summarizerPromptsSectionExpanded by remember { mutableStateOf(false) }
    
    // Summarizer Prompts Repository and State
    val summarizerPromptsRepository = remember { SummarizerPromptsRepository() }
    var summarizerPrompts by remember { mutableStateOf(SummarizerPrompts()) }
    var promptsLoading by remember { mutableStateOf(true) }
    
    // Load summarizer prompts
    LaunchedEffect(Unit) {
        summarizerPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
        promptsLoading = false
    }
    
    // Prompt editing state - which prompt is currently being edited
    var editingPromptType by remember { mutableStateOf<String?>(null) }
    var editingPromptText by remember { mutableStateOf("") }
    var showDefaultPromptDialog by remember { mutableStateOf<String?>(null) }
    
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
    
    // Summary Review Dialog
    summaryProposal?.let { proposal ->
        SummaryReviewDialog(
            proposal = proposal,
            onApply = { selectedUpdates ->
                chatViewModel.applySummaryUpdates(
                    selectedUpdates = selectedUpdates,
                    onSuccess = {
                        chatViewModel.dismissSummaryProposal()
                    },
                    onError = { /* Error is handled internally */ }
                )
            },
            onDismiss = {
                chatViewModel.dismissSummaryProposal()
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
                        
                        Text(
                            text = "Delimiters are tried in order - first match is used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Display each delimiter input
                        delimiterInputs.forEachIndexed { index, delimiter ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Reorder buttons column
                                Column {
                                    // Move up button (hide for first item)
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                delimiterInputs = delimiterInputs.toMutableList().also {
                                                    val temp = it[index]
                                                    it[index] = it[index - 1]
                                                    it[index - 1] = temp
                                                }
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move up",
                                            tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    // Move down button (hide for last item)
                                    IconButton(
                                        onClick = {
                                            if (index < delimiterInputs.size - 1) {
                                                delimiterInputs = delimiterInputs.toMutableList().also {
                                                    val temp = it[index]
                                                    it[index] = it[index + 1]
                                                    it[index + 1] = temp
                                                }
                                            }
                                        },
                                        enabled = index < delimiterInputs.size - 1,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move down",
                                            tint = if (index < delimiterInputs.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                
                                OutlinedTextField(
                                    value = delimiter,
                                    onValueChange = { newValue ->
                                        delimiterInputs = delimiterInputs.toMutableList().also { it[index] = newValue }
                                    },
                                    label = { Text(if (index == 0) "Primary" else "Fallback ${index}") },
                                    placeholder = { Text(if (index == 0) "e.g. ***" else "e.g. ---") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Delete button (only if more than 1 delimiter)
                                if (delimiterInputs.size > 1) {
                                    IconButton(
                                        onClick = {
                                            delimiterInputs = delimiterInputs.toMutableList().also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove delimiter",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        
                        // Add delimiter button (max 5)
                        if (delimiterInputs.size < 5) {
                            OutlinedButton(
                                onClick = {
                                    delimiterInputs = delimiterInputs + ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Fallback Delimiter")
                            }
                        }
                        
                        // Save button
                        val hasChanges = delimiterInputs.filter { it.isNotEmpty() } != customDelimiters
                        if (hasChanges && delimiterInputs.any { it.isNotEmpty() }) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    scope.launch { 
                                        chatSettingsManager.setCustomDelimiters(delimiterInputs.filter { it.isNotEmpty() })
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Delimiters")
                            }
                        }
                    }
                }
                
                // Response Generation Section
                SettingsSection(
                    title = "Response Generation",
                    expanded = generationSectionExpanded,
                    onToggle = { generationSectionExpanded = !generationSectionExpanded }
                ) {
                    if (!isBedrockModel) {
                        SettingsToggle(
                            title = "Streaming",
                            description = "See responses appear in real-time as they're generated",
                            checked = streamingEnabled,
                            onCheckedChange = { scope.launch { chatSettingsManager.setStreamingEnabled(it) } }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
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
                    
                    if (isBedrockModel) {
                        BedrockSamplingModeSelector(
                            value = bedrockSamplingMode,
                            onValueChange = {
                                scope.launch { chatSettingsManager.setBedrockSamplingMode(it) }
                            }
                        )

                        when (bedrockSamplingMode) {
                            BedrockSamplingMode.TEMPERATURE -> {
                                SettingsSlider(
                                    title = "Temperature",
                                    description = "Controls response randomness",
                                    value = bedrockTemperature,
                                    valueRange = 0f..1f,
                                    steps = 19,
                                    valueFormatter = { String.format("%.2f", it) },
                                    onValueChange = {
                                        scope.launch { chatSettingsManager.setBedrockTemperature(it) }
                                    }
                                )
                            }
                            BedrockSamplingMode.TOP_P -> {
                                SettingsSlider(
                                    title = "Top P",
                                    description = "Controls nucleus sampling probability",
                                    value = bedrockTopP,
                                    valueRange = 0f..1f,
                                    steps = 999,
                                    valueFormatter = { String.format("%.3f", it) },
                                    onValueChange = {
                                        scope.launch { chatSettingsManager.setBedrockTopP(it) }
                                    }
                                )
                            }
                        }

                        SettingsToggle(
                            title = "Top K",
                            description = "Limit sampling to the highest-probability tokens",
                            checked = bedrockTopKEnabled,
                            onCheckedChange = {
                                scope.launch { chatSettingsManager.setBedrockTopKEnabled(it) }
                            }
                        )

                        if (bedrockTopKEnabled) {
                            SettingsSlider(
                                title = "Top K Value",
                                description = "Maximum candidate tokens considered during sampling",
                                value = bedrockTopK.toFloat(),
                                valueRange = 0f..500f,
                                steps = 499,
                                valueFormatter = { it.roundToInt().toString() },
                                onValueChange = {
                                    scope.launch { chatSettingsManager.setBedrockTopK(it.roundToInt()) }
                                }
                            )
                        }

                        SettingsSlider(
                            title = "Max Output Tokens",
                            description = "Maximum response length",
                            value = bedrockMaxOutputTokens.toFloat(),
                            valueRange = 256f..128_000f,
                            steps = 127,
                            valueFormatter = { it.roundToInt().toString() },
                            onValueChange = {
                                scope.launch {
                                    chatSettingsManager.setBedrockMaxOutputTokens(it.roundToInt())
                                }
                            }
                        )
                    } else {
                        SettingsSlider(
                            title = "Temperature",
                            description = "Controls creativity/randomness (0 = focused, 2 = creative)",
                            value = temperature,
                            valueRange = 0f..2f,
                            steps = 19,
                            valueFormatter = { String.format("%.1f", it) },
                            onValueChange = { scope.launch { chatSettingsManager.setTemperature(it) } }
                        )

                        SettingsSlider(
                            title = "Top P (Nucleus Sampling)",
                            description = "Probability threshold for response diversity",
                            value = topP,
                            valueRange = 0f..1f,
                            steps = 19,
                            valueFormatter = { String.format("%.2f", it) },
                            onValueChange = { scope.launch { chatSettingsManager.setTopP(it) } }
                        )

                        SettingsSlider(
                            title = "Top K",
                            description = "Maximum tokens to consider when sampling",
                            value = topK.toFloat(),
                            valueRange = 1f..100f,
                            steps = 98,
                            valueFormatter = { it.roundToInt().toString() },
                            onValueChange = { scope.launch { chatSettingsManager.setTopK(it.roundToInt()) } }
                        )

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
                }
                
                // Text-to-Speech Section
                SettingsSection(
                    title = "Text-to-Speech",
                    expanded = ttsSectionExpanded,
                    onToggle = { 
                        ttsSectionExpanded = !ttsSectionExpanded
                        // Load voices when opening
                        if (ttsSectionExpanded && inworldVoices.isEmpty()) {
                            voicesLoading = true
                            scope.launch {
                                inworldService.getVoices()
                                    .onSuccess { inworldVoices = it }
                                voicesLoading = false
                            }
                        }
                    }
                ) {
                    Text(
                        text = "Enable voice synthesis for AI messages using ElevenLabs or Inworld",
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
                            text = "Select the TTS model for voice synthesis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        var modelExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = !modelExpanded },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            val selectedModel = AllTTSModels.DEFAULT_MODELS.find { it.modelId == ttsModelId }
                            OutlinedTextField(
                                value = selectedModel?.name ?: "Select Model",
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
                                AllTTSModels.DEFAULT_MODELS.forEach { model ->
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
                            
                            val filteredVoices = remember(voices, ttsModelId) {
                                if (ttsModelId == InworldTTSModels.INWORLD_TTS_1_5_MAX.modelId) {
                                    voices.filter { it.source == VoiceSource.INWORLD }
                                } else {
                                    voices.filter { it.source == VoiceSource.ELEVEN_LABS }
                                }
                            }
                            
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
                                    filteredVoices.take(50).forEach { voice ->
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
                
                if (!isBedrockModel) {
                    SettingsSection(
                        title = "Response Style",
                        expanded = styleSectionExpanded,
                        onToggle = { styleSectionExpanded = !styleSectionExpanded }
                    ) {
                        SettingsSlider(
                            title = "Presence Penalty",
                            description = "Discourages repeating tokens already used",
                            value = presencePenalty,
                            valueRange = -2f..2f,
                            steps = 39,
                            valueFormatter = { String.format("%.1f", it) },
                            onValueChange = { scope.launch { chatSettingsManager.setPresencePenalty(it) } }
                        )

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
                }
                
                // Advanced Section
                SettingsSection(
                    title = "Advanced",
                    expanded = advancedSectionExpanded,
                    onToggle = { advancedSectionExpanded = !advancedSectionExpanded }
                ) {
                    if (!isBedrockModel) {
                        SettingsToggle(
                            title = "Thinking Mode",
                            description = "Enable extended reasoning for better narrative planning (Gemini 3+)",
                            checked = thinkingEnabled,
                            onCheckedChange = { scope.launch { chatSettingsManager.setThinkingEnabled(it) } }
                        )
                    }

                    // System Prompt Toggle
                    SettingsToggle(
                        title = "Enable System Prompt",
                        description = "Include the global system prompt from app settings",
                        checked = systemPromptEnabled,
                        onCheckedChange = { scope.launch { chatSettingsManager.setSystemPromptEnabled(it) } }
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
                
                // Story Tools Section
                SettingsSection(
                    title = "Story Tools",
                    expanded = true,
                    onToggle = { }
                ) {
                    Text(
                        text = "Analyze and manage your story progression",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Detail Level Selector
                    var selectedDetailLevel by remember { mutableStateOf(SummaryDetailLevel.MEDIUM) }
                    var detailLevelExpanded by remember { mutableStateOf(false) }
                    
                    Text(
                        text = "Summary Detail Level",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = detailLevelExpanded,
                        onExpandedChange = { detailLevelExpanded = !detailLevelExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = when (selectedDetailLevel) {
                                SummaryDetailLevel.LOW -> "Low - Major events only"
                                SummaryDetailLevel.MEDIUM -> "Medium - Balanced"
                                SummaryDetailLevel.HIGH -> "High - Full detail"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = detailLevelExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = detailLevelExpanded,
                            onDismissRequest = { detailLevelExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text("Low")
                                        Text(
                                            "Major events only",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedDetailLevel = SummaryDetailLevel.LOW
                                    detailLevelExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text("Medium")
                                        Text(
                                            "Balanced summary",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedDetailLevel = SummaryDetailLevel.MEDIUM
                                    detailLevelExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text("High")
                                        Text(
                                            "Full detail, all events",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    selectedDetailLevel = SummaryDetailLevel.HIGH
                                    detailLevelExpanded = false
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Summarize Story Button
                    Button(
                        onClick = { chatViewModel.generateStorySummary(selectedDetailLevel) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSummarizing
                    ) {
                        if (isSummarizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Summarizing...")
                        } else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Summarize Story")
                        }
                    }
                    
                    Text(
                        text = "Analyze the story, track character development, and suggest updates to character/world descriptions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    // Show error if any
                    summaryError?.let { error ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
                
                // Summarizer Prompts Section
                SettingsSection(
                    title = "Summarizer Prompts",
                    expanded = summarizerPromptsSectionExpanded,
                    onToggle = { summarizerPromptsSectionExpanded = !summarizerPromptsSectionExpanded }
                ) {
                    Text(
                        text = "Customize the prompts used for story summarization and field-specific updates. Leave empty to use defaults.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    if (promptsLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        // Prompt editor cards
                        val promptTypes = listOf(
                            Triple("high_detail", "High Detail Summary", summarizerPrompts.highDetailSummaryPrompt),
                            Triple("background", "Character Background", summarizerPrompts.characterBackgroundPrompt),
                            Triple("appearance", "Character Appearance", summarizerPrompts.characterAppearancePrompt),
                            Triple("personality", "Character Personality", summarizerPrompts.characterPersonalityPrompt),
                            Triple("world", "World Description", summarizerPrompts.worldDescriptionPrompt)
                        )
                        
                        promptTypes.forEach { (typeId, title, currentPrompt) ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = if (currentPrompt.isBlank()) "Using default" else "Custom prompt set",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (currentPrompt.isBlank()) 
                                                    MaterialTheme.colorScheme.onSurfaceVariant 
                                                else 
                                                    MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // View Default Button
                                        OutlinedButton(
                                            onClick = { showDefaultPromptDialog = typeId },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("View Default", style = MaterialTheme.typography.labelSmall)
                                        }
                                        
                                        // Edit Button
                                        Button(
                                            onClick = {
                                                editingPromptType = typeId
                                                editingPromptText = currentPrompt
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Edit", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    
                                    // Reset to Default (only if custom prompt is set)
                                    if (currentPrompt.isNotBlank()) {
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    when (typeId) {
                                                        "high_detail" -> summarizerPromptsRepository.resetHighDetailSummaryPrompt()
                                                        "background" -> summarizerPromptsRepository.resetCharacterBackgroundPrompt()
                                                        "appearance" -> summarizerPromptsRepository.resetCharacterAppearancePrompt()
                                                        "personality" -> summarizerPromptsRepository.resetCharacterPersonalityPrompt()
                                                        "world" -> summarizerPromptsRepository.resetWorldDescriptionPrompt()
                                                    }
                                                    // Reload prompts
                                                    summarizerPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(
                                                Icons.Default.Restore,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Reset to Default", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Reset All Prompts Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    summarizerPromptsRepository.resetAllPrompts()
                                    summarizerPrompts = SummarizerPrompts()
                                }
                            },
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
                            Text("Reset All Prompts to Default")
                        }
                    }
                }
                
                // Edit Prompt Dialog
                editingPromptType?.let { typeId ->
                    val title = when (typeId) {
                        "high_detail" -> "High Detail Summary Prompt"
                        "background" -> "Character Background Prompt"
                        "appearance" -> "Character Appearance Prompt"
                        "personality" -> "Character Personality Prompt"
                        "world" -> "World Description Prompt"
                        else -> "Edit Prompt"
                    }
                    
                    AlertDialog(
                        onDismissRequest = { editingPromptType = null },
                        title = { Text(title) },
                        text = {
                            Column {
                                Text(
                                    text = "Enter your custom prompt. Leave empty to use the default.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                OutlinedTextField(
                                    value = editingPromptText,
                                    onValueChange = { editingPromptText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 200.dp, max = 400.dp),
                                    label = { Text("Custom Prompt") },
                                    placeholder = { Text("Enter custom prompt or leave empty for default...") }
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        when (typeId) {
                                            "high_detail" -> summarizerPromptsRepository.setHighDetailSummaryPrompt(editingPromptText)
                                            "background" -> summarizerPromptsRepository.setCharacterBackgroundPrompt(editingPromptText)
                                            "appearance" -> summarizerPromptsRepository.setCharacterAppearancePrompt(editingPromptText)
                                            "personality" -> summarizerPromptsRepository.setCharacterPersonalityPrompt(editingPromptText)
                                            "world" -> summarizerPromptsRepository.setWorldDescriptionPrompt(editingPromptText)
                                        }
                                        // Reload prompts
                                        summarizerPrompts = summarizerPromptsRepository.getSummarizerPromptsOnce()
                                        editingPromptType = null
                                    }
                                }
                            ) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { editingPromptType = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                
                // View Default Prompt Dialog
                showDefaultPromptDialog?.let { typeId ->
                    val title = when (typeId) {
                        "high_detail" -> "High Detail Summary"
                        "background" -> "Character Background"
                        "appearance" -> "Character Appearance"
                        "personality" -> "Character Personality"
                        "world" -> "World Description"
                        else -> "Default Prompt"
                    }
                    
                    val defaultPrompt = when (typeId) {
                        "high_detail" -> SummarizerPromptsRepository.DEFAULT_HIGH_DETAIL_SUMMARY_PROMPT
                        "background" -> SummarizerPromptsRepository.DEFAULT_CHARACTER_BACKGROUND_PROMPT
                        "appearance" -> SummarizerPromptsRepository.DEFAULT_CHARACTER_APPEARANCE_PROMPT
                        "personality" -> SummarizerPromptsRepository.DEFAULT_CHARACTER_PERSONALITY_PROMPT
                        "world" -> SummarizerPromptsRepository.DEFAULT_WORLD_DESCRIPTION_PROMPT
                        else -> ""
                    }
                    
                    AlertDialog(
                        onDismissRequest = { showDefaultPromptDialog = null },
                        title = { Text("Default: $title") },
                        text = {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = defaultPrompt,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .verticalScroll(rememberScrollState())
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showDefaultPromptDialog = null }) {
                                Text("Close")
                            }
                        }
                    )
                }
                
                // AI Model Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = "AI Model",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "Select the AI model for roleplay responses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        
                        var aiModelExpanded by remember { mutableStateOf(false) }
                        val selectedModel = ChatSettingsManager.aiModelOptionFor(aiModelId)
                        val selectedModelName = selectedModel?.let { "${it.displayName} (${it.providerLabel})" } ?: aiModelId
                        
                        ExposedDropdownMenuBox(
                            expanded = aiModelExpanded,
                            onExpandedChange = { aiModelExpanded = !aiModelExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedModelName,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = aiModelExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = aiModelExpanded,
                                onDismissRequest = { aiModelExpanded = false }
                            ) {
                                ChatSettingsManager.AVAILABLE_AI_MODELS
                                    .groupBy { it.providerLabel }
                                    .forEach { (providerLabel, models) ->
                                        Text(
                                            text = providerLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                        models.forEach { model ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(model.displayName)
                                                        Text(
                                                            model.modelId,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    scope.launch { chatSettingsManager.setAiModelId(model.modelId) }
                                                    aiModelExpanded = false
                                                },
                                                leadingIcon = if (model.modelId == aiModelId) {
                                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                                } else null
                                            )
                                        }
                                }
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedrockSamplingModeSelector(
    value: BedrockSamplingMode,
    onValueChange: (BedrockSamplingMode) -> Unit
) {
    val options = listOf(
        BedrockSamplingMode.TEMPERATURE to "Temperature",
        BedrockSamplingMode.TOP_P to "Top P"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Sampling Mode",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = value == mode,
                    onClick = { onValueChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size
                    )
                ) {
                    Text(label)
                }
            }
        }
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
