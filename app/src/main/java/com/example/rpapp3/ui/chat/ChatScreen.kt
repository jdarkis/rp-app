package com.example.rpapp3.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.MessageFilterMode
import com.example.rpapp3.data.TTSPlaybackState
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

/**
 * Represents a segment of text in an AI message, either narrator text or character dialogue
 */
data class DisplaySegment(
    val text: String,
    val isCharacterDialogue: Boolean,
    val characterName: String? = null
)

/**
 * Represents a parsed choice from AI message
 */
enum class ChoiceType { ACTION, DIALOGUE }

data class ChoiceItem(
    val type: ChoiceType,
    val label: String,
    val text: String
)

/**
 * Parse choices from AI response text
 * Returns Pair of (list of choices, main text without choices section)
 */
fun parseChoices(text: String): Pair<List<ChoiceItem>, String> {
    val choices = mutableListOf<ChoiceItem>()
    
    // Find the [ACTIONS] marker
    val actionsIndex = text.indexOf("[ACTIONS]")
    if (actionsIndex == -1) {
        return Pair(emptyList(), text)
    }
    
    // Main text is everything before [ACTIONS]
    val mainText = text.substring(0, actionsIndex).trim()
    
    // Get the choices section
    val choicesSection = text.substring(actionsIndex)
    
    // Find [DIALOGUE] marker if it exists
    val dialogueIndex = choicesSection.indexOf("[DIALOGUE]")
    
    val actionsText = if (dialogueIndex != -1) {
        choicesSection.substring("[ACTIONS]".length, dialogueIndex)
    } else {
        choicesSection.substring("[ACTIONS]".length)
    }
    
    val dialogueText = if (dialogueIndex != -1) {
        choicesSection.substring(dialogueIndex + "[DIALOGUE]".length)
    } else {
        ""
    }
    
    // Parse action choices (numbered: 1., 2., 3.)
    val actionPattern = Regex("""(\d)\.\s*(.+)""")
    actionsText.lines().forEach { line ->
        val match = actionPattern.find(line.trim())
        if (match != null) {
            choices.add(ChoiceItem(
                type = ChoiceType.ACTION,
                label = "${match.groupValues[1]}.",
                text = match.groupValues[2].trim()
            ))
        }
    }
    
    // Parse dialogue choices (lettered: a., b., c.)
    val dialoguePattern = Regex("""([a-c])\.\s*"?([^"]+)"?""")
    dialogueText.lines().forEach { line ->
        val match = dialoguePattern.find(line.trim())
        if (match != null) {
            // Remove surrounding quotes if present
            var dialogueContent = match.groupValues[2].trim()
            if (dialogueContent.endsWith("\"")) {
                dialogueContent = dialogueContent.dropLast(1)
            }
            choices.add(ChoiceItem(
                type = ChoiceType.DIALOGUE,
                label = "${match.groupValues[1]}.",
                text = dialogueContent
            ))
        }
    }
    
    return Pair(choices, mainText)
}

/**
 * Parse message text to extract character dialogue segments using [CharacterName]:"dialogue" format
 */
fun parseDialogueSegments(text: String): List<DisplaySegment> {
    val segments = mutableListOf<DisplaySegment>()
    // Regex to match [Character Name]:"dialogue" pattern
    // Allows for any characters in character name, and dialogue in double quotes
    val pattern = Regex("""\[([^\]]+)\]:\s*"([^"]*)"""".trimIndent())
    
    var lastEnd = 0
    val matches = pattern.findAll(text)
    
    for (match in matches) {
        // Add narrator text before this dialogue
        if (match.range.first > lastEnd) {
            val narratorText = text.substring(lastEnd, match.range.first).trim()
            if (narratorText.isNotEmpty()) {
                segments.add(DisplaySegment(narratorText, isCharacterDialogue = false))
            }
        }
        
        // Add character dialogue
        val characterName = match.groupValues[1]
        val dialogue = match.groupValues[2]
        segments.add(DisplaySegment(dialogue, isCharacterDialogue = true, characterName = characterName))
        
        lastEnd = match.range.last + 1
    }
    
    // Add remaining narrator text
    if (lastEnd < text.length) {
        val remainingText = text.substring(lastEnd).trim()
        if (remainingText.isNotEmpty()) {
            segments.add(DisplaySegment(remainingText, isCharacterDialogue = false))
        }
    }
    
    // If no patterns found, return the whole text as narrator
    if (segments.isEmpty()) {
        segments.add(DisplaySegment(text, isCharacterDialogue = false))
    }
    
    return segments
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    worldId: String,
    viewModel: ChatViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCharacter: (String) -> Unit,
    onNavigateToCreateCharacter: (String?) -> Unit // Optional name to pre-fill
) {
    val currentChat by viewModel.currentChat.collectAsState()
    val characters by viewModel.characters.collectAsState()
    val worldCharacters by viewModel.worldCharacters.collectAsState()
    val dialogueCharacters = remember(characters, worldCharacters) {
        (characters + worldCharacters).distinctBy { it.id }
    }
    val messages = viewModel.messages
    val isLoading = viewModel.isLoading
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Track if this is the initial load (to skip scroll animation)
    var isInitialLoad by remember { mutableStateOf(true) }
    
    // Chat settings
    val chatSettingsManager = remember { ChatSettingsManager.getInstance(context) }
    val filterMode by chatSettingsManager.filterMode.collectAsState(initial = MessageFilterMode.OFF)
    val customDelimiter by chatSettingsManager.customDelimiter.collectAsState(initial = ChatSettingsManager.DEFAULT_DELIMITER)
    val customDelimiters by chatSettingsManager.customDelimiters.collectAsState(initial = listOf(ChatSettingsManager.DEFAULT_DELIMITER))
    val paragraphCount by chatSettingsManager.paragraphCount.collectAsState(initial = ChatSettingsManager.DEFAULT_PARAGRAPH_COUNT)
    val separateCharacterDialogue by chatSettingsManager.separateCharacterDialogue.collectAsState(initial = true)
    val provideChoicesEnabled by chatSettingsManager.provideChoicesEnabled.collectAsState(initial = true)
    
    // TTS settings
    val ttsEnabled by chatSettingsManager.ttsEnabled.collectAsState(initial = false)
    val autoTtsEnabled by chatSettingsManager.autoTtsEnabled.collectAsState(initial = false)
    val ttsManager = viewModel.ttsManager
    val playbackState = ttsManager?.playbackState?.collectAsState()
    val currentPlayingId = ttsManager?.currentPlayingId?.collectAsState()
    val ttsGenerationState by viewModel.ttsGenerationState.collectAsState()
    
    // Track last spoken message ID for auto-TTS (saveable to persist across config changes)
    var lastSpokenMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    
    // Track if we were previously loading (to detect when AI response completes)
    var wasLoading by remember { mutableStateOf(false) }
    
    // Track if auto TTS was just enabled (to skip speaking existing messages)
    var previousAutoTtsEnabled by remember { mutableStateOf(autoTtsEnabled) }
    
    // State for pending input text (from choice selection)
    var pendingInputText by remember { mutableStateOf<String?>(null) }
    
    // Initialize ViewModel with context for API key management
    LaunchedEffect(Unit) {
        viewModel.initializeWithContext(context)
    }
    
    LaunchedEffect(chatId, worldId) {
        viewModel.initializeChat(chatId, worldId)
    }
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            if (isInitialLoad) {
                // Instant scroll on initial load (no animation)
                listState.scrollToItem(messages.size - 1)
                isInitialLoad = false
            } else {
                // Animated scroll for new messages during conversation
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    LaunchedEffect(ttsGenerationState.errorMessage) {
        ttsGenerationState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearTtsError()
        }
    }
    
    // Load cached audio URLs for AI messages
    LaunchedEffect(messages) {
        messages.filter { !it.isUser }.forEach { message ->
            viewModel.loadCachedAudioUrlsForMessage(message.id)
        }
    }
    
    // Auto-TTS for NEW AI messages only - triggered when loading finishes
    LaunchedEffect(isLoading, autoTtsEnabled, ttsEnabled) {
        // Detect if auto TTS was just enabled (skip speaking existing messages)
        val autoTtsJustEnabled = autoTtsEnabled && !previousAutoTtsEnabled
        previousAutoTtsEnabled = autoTtsEnabled
        
        // If auto TTS was just enabled, don't speak - wait for new messages
        if (autoTtsJustEnabled) return@LaunchedEffect
        
        // Detect transition from loading to not loading (AI just finished responding)
        val justFinishedLoading = wasLoading && !isLoading
        wasLoading = isLoading
        
        if (!justFinishedLoading) return@LaunchedEffect
        if (!ttsEnabled || !autoTtsEnabled || messages.isEmpty()) return@LaunchedEffect
        
        // Find the last AI message
        val lastAiMessage = messages.lastOrNull { !it.isUser }
        
        // Only speak if it's a new message we haven't spoken yet
        // Skip error messages (don't TTS error responses)
        if (lastAiMessage != null && lastAiMessage.id != lastSpokenMessageId && !lastAiMessage.text.startsWith("Error:")) {
            lastSpokenMessageId = lastAiMessage.id
            
            // Get the display text based on current filter settings (visible text only)
            val displayText = when (filterMode) {
                MessageFilterMode.AFTER_DELIMITER -> {
                    // Try each delimiter in order until one is found
                    var result: String? = null
                    for (delimiter in customDelimiters) {
                        if (delimiter.isNotEmpty() && lastAiMessage.text.contains(delimiter)) {
                            result = lastAiMessage.text.substringAfterLast(delimiter).trim()
                            break
                        }
                    }
                    result?.ifEmpty { lastAiMessage.text } ?: lastAiMessage.text
                }
                MessageFilterMode.LAST_N_PARAGRAPHS -> {
                    val paragraphs = lastAiMessage.text.split("\n\n")
                    if (paragraphs.size > paragraphCount) {
                        paragraphs.takeLast(paragraphCount).joinToString("\n\n").trim()
                    } else {
                        lastAiMessage.text
                    }
                }
                else -> lastAiMessage.text
            }
            
            // Remove action/dialogue choices from the text before speaking
            val (_, textWithoutChoices) = parseChoices(displayText)
            
            // Parse segments if dialogue separation is enabled
            if (separateCharacterDialogue) {
                val segments = parseDialogueSegments(textWithoutChoices)
                // Build list of speakable segments with character IDs
                val speakableSegments = segments.map { segment ->
                    val character = if (segment.isCharacterDialogue && segment.characterName != null) {
                        characters.find { it.name.equals(segment.characterName, ignoreCase = true) }
                            ?: worldCharacters.find {
                                it.name.equals(segment.characterName, ignoreCase = true)
                            }
                    } else null
                    
                    ChatViewModel.SpeakableSegment(
                        text = segment.text,
                        characterId = character?.id
                    )
                }
                // Speak all segments sequentially with appropriate voices, enabling audio caching
                viewModel.speakSegmentsSequentially(speakableSegments, lastAiMessage.id)
            } else {
                // Speak the whole visible message (without choices), enabling audio caching
                viewModel.speakTextWithCaching(
                    text = textWithoutChoices, 
                    characterId = lastAiMessage.characterId,
                    messageId = lastAiMessage.id,
                    segmentIndex = 0
                )
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = currentChat?.title ?: "Chat",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (characters.isNotEmpty()) {
                            Text(
                                text = characters.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Chat Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
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
            // Messages list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Load More button at top (when there are older messages)
                if (viewModel.hasMoreMessages) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (viewModel.isLoadingMore) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                TextButton(
                                    onClick = { viewModel.loadMoreMessages() }
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Load older messages")
                                }
                            }
                        }
                    }
                }
                
                itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                    MessageBubble(
                        message = message,
                        character = characters.find { it.id == message.characterId },
                        allCharacters = dialogueCharacters,
                        filterMode = filterMode,
                        customDelimiters = customDelimiters,
                        paragraphCount = paragraphCount,
                        separateDialogue = separateCharacterDialogue,
                        provideChoicesEnabled = provideChoicesEnabled,
                        isLast = index == messages.size - 1,
                        isChatLoading = isLoading,
                        onCopy = { text ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onDelete = { viewModel.deleteMessage(message.id) },
                        onRegenerate = if (!message.isUser) {
                            { viewModel.regenerateResponse(message.id) }
                        } else null,
                        onCharacterClick = { characterName ->
                            // Find character by name in world characters
                            val matchedChar = worldCharacters.find { 
                                it.name.equals(characterName, ignoreCase = true) 
                            }
                            if (matchedChar != null) {
                                onNavigateToCharacter(matchedChar.id)
                            } else {
                                // Navigate to create character with pre-filled name
                                onNavigateToCreateCharacter(characterName)
                            }
                        },
                        onChoiceSelected = { choiceText ->
                            pendingInputText = choiceText
                        },
                        ttsEnabled = ttsEnabled,
                        isPlayingTTS = playbackState?.value == TTSPlaybackState.PLAYING,
                        isLoadingTTS = playbackState?.value == TTSPlaybackState.LOADING,
                        currentPlayingSegmentId = currentPlayingId?.value,
                        generatingSegmentId = ttsGenerationState.activeSegmentId
                            .takeIf { ttsGenerationState.isGenerating },
                        cachedAudioUrls = viewModel.getCachedAudioUrlsForMessage(message.id),
                        onSpeak = { text, characterId, msgId, segmentIdx -> 
                            viewModel.speakTextWithCaching(text, characterId, msgId, segmentIdx)
                        },
                        onPlayCached = { audioUrl, segmentId ->
                            viewModel.playCachedAudio(audioUrl, segmentId)
                        },
                        onStopSpeaking = { viewModel.stopSpeaking() }
                    )
                }
                
                // Loading indicator
                if (isLoading) {
                    item {
                        LoadingIndicator()
                    }
                }
            }
            
            // Floating TTS Control Bar - shows when TTS is playing, loading, paused, or completed
            val isPlayingOrLoading = playbackState?.value == TTSPlaybackState.PLAYING || 
                                     playbackState?.value == TTSPlaybackState.LOADING ||
                                     playbackState?.value == TTSPlaybackState.PAUSED ||
                                     ttsGenerationState.isGenerating
            val isCompleted = playbackState?.value == TTSPlaybackState.COMPLETED
            val showTTSControls = isPlayingOrLoading || isCompleted
            
            if (showTTSControls && ttsEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    // Replay button (only when completed)
                    if (isCompleted) {
                        FilledTonalButton(
                            onClick = { viewModel.replaySpeaking() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(
                                Icons.Default.Replay,
                                contentDescription = "Replay",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Replay")
                        }
                    }
                    
                    // Pause/Resume button (only when playing or paused)
                    if (playbackState?.value == TTSPlaybackState.PLAYING || 
                        playbackState?.value == TTSPlaybackState.PAUSED) {
                        FilledTonalButton(
                            onClick = { 
                                if (playbackState?.value == TTSPlaybackState.PAUSED) {
                                    viewModel.resumeSpeaking()
                                } else {
                                    viewModel.pauseSpeaking()
                                }
                            }
                        ) {
                            Icon(
                                if (playbackState?.value == TTSPlaybackState.PAUSED) 
                                    Icons.Default.PlayArrow 
                                else 
                                    Icons.Default.Pause,
                                contentDescription = if (playbackState?.value == TTSPlaybackState.PAUSED) "Resume" else "Pause",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (playbackState?.value == TTSPlaybackState.PAUSED) "Resume" else "Pause")
                        }
                    }
                    
                    // Stop/Close button
                    FilledTonalButton(
                        onClick = { viewModel.stopSpeaking() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        if (playbackState?.value == TTSPlaybackState.LOADING ||
                            ttsGenerationState.isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating...")
                        } else {
                            Icon(
                                if (isCompleted) Icons.Default.Close else Icons.Default.Stop,
                                contentDescription = if (isCompleted) "Close" else "Stop",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isCompleted) "Close" else "Stop")
                        }
                    }
                }
            }
            
            // Input field
            ChatInput(
                onSendMessage = { message ->
                    viewModel.sendMessage(message)
                    coroutineScope.launch {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size)
                        }
                    }
                },
                isLoading = isLoading,
                externalText = pendingInputText,
                onExternalTextConsumed = { pendingInputText = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    character: Character?,
    allCharacters: List<Character> = emptyList(),
    filterMode: MessageFilterMode = MessageFilterMode.OFF,
    customDelimiters: List<String> = listOf("***"),
    paragraphCount: Int = 1,
    separateDialogue: Boolean = true,
    provideChoicesEnabled: Boolean = true,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onCharacterClick: ((String) -> Unit)? = null,
    onChoiceSelected: ((String) -> Unit)? = null,
    ttsEnabled: Boolean = false,
    isPlayingTTS: Boolean = false,
    isLoadingTTS: Boolean = false,
    currentPlayingSegmentId: String? = null,
    generatingSegmentId: String? = null,
    cachedAudioUrls: Map<Int, String> = emptyMap(),
    onSpeak: ((text: String, characterId: String?, messageId: String, segmentIndex: Int) -> Unit)? = null,
    onPlayCached: ((audioUrl: String, segmentId: String) -> Unit)? = null,
    onStopSpeaking: (() -> Unit)? = null,
    isLast: Boolean = false,
    isChatLoading: Boolean = false
) {
    val isUser = message.isUser
    var showMenuForSegment by remember { mutableStateOf<Int?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    
    // Delete confirmation dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Message?") },
            text = { Text("This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Get display text based on filter mode (for AI messages only)
    val displayText = if (!isUser) {
        when (filterMode) {
            MessageFilterMode.OFF -> message.text
            MessageFilterMode.LAST_N_PARAGRAPHS -> {
                val paragraphs = message.text.split("\n\n").filter { it.isNotBlank() }
                if (paragraphs.isNotEmpty()) {
                    paragraphs.takeLast(paragraphCount).joinToString("\n\n")
                } else {
                    val lines = message.text.split("\n").filter { it.isNotBlank() }
                    lines.takeLast(paragraphCount).joinToString("\n")
                }
            }
            MessageFilterMode.AFTER_DELIMITER -> {
                // Try each delimiter in order until one is found
                var result: String? = null
                for (delimiter in customDelimiters) {
                    if (delimiter.isNotEmpty() && message.text.contains(delimiter)) {
                        result = message.text.substringAfterLast(delimiter).trim()
                        break
                    }
                }
                result?.ifEmpty { message.text } ?: message.text
            }
        }
    } else {
        message.text
    }
    
    // Parse segments if this is an AI message and dialogue separation is enabled
    // First, parse choices if enabled
    val (choices, textWithoutChoices) = if (!isUser && provideChoicesEnabled) {
        parseChoices(displayText)
    } else {
        Pair(emptyList(), displayText)
    }
    
    val segments = if (!isUser && separateDialogue) {
        parseDialogueSegments(textWithoutChoices)
    } else {
        listOf(DisplaySegment(textWithoutChoices, isCharacterDialogue = false))
    }
    
    // Render each segment
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        segments.forEachIndexed { index, segment ->
            val isLastSegment = index == segments.size - 1
            
            // Find matching character for dialogue segments
            val segmentCharacter = if (segment.isCharacterDialogue && segment.characterName != null) {
                allCharacters.find { it.name.equals(segment.characterName, ignoreCase = true) }
            } else null
            
            val segmentIsNarrator = !segment.isCharacterDialogue
            
            val bubbleColor = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
            
            val textColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
            
            val shape = if (isUser) {
                RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            } else {
                RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom
            ) {
                // Avatar for AI messages
                if (!isUser) {
                    if (segmentIsNarrator) {
                        // Show book icon for narrator (not clickable)
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = "Narrator",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else {
                        // Character avatar - make it clickable
                        val avatarModifier = if (onCharacterClick != null && segment.characterName != null) {
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onCharacterClick(segment.characterName) }
                        } else {
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        }
                        
                        // Check both profilePictureUrl and photoUrls for avatar
                        val avatarUrl = segmentCharacter?.profilePictureUrl 
                            ?: segmentCharacter?.photoUrls?.firstOrNull()
                        
                        if (avatarUrl != null) {
                            // Show character photo
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = segmentCharacter?.name ?: segment.characterName,
                                modifier = avatarModifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Show initial for character without photo
                            Surface(
                                modifier = avatarModifier,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = (segment.characterName ?: "AI").take(1).uppercase(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Column(
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    // Show character/narrator name
                    if (!isUser) {
                        val nameToShow = if (segmentIsNarrator) "Narrator" else segment.characterName
                        if (nameToShow != null) {
                            Text(
                                text = nameToShow,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (segmentIsNarrator) 
                                    MaterialTheme.colorScheme.tertiary 
                                else 
                                    MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                            )
                        }
                    }
                    
                    Box {
                        Surface(
                            shape = shape,
                            color = bubbleColor,
                            shadowElevation = 2.dp,
                            tonalElevation = if (isUser) 0.dp else 1.dp,
                            modifier = Modifier.combinedClickable(
                                onClick = { },
                                onLongClick = { showMenuForSegment = index }
                            )
                        ) {
                            SelectionContainer {
                                Text(
                                    text = segment.text,
                                    color = textColor,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        
                        // Dropdown menu - TTS on all segments, other actions on last segment only
                        DropdownMenu(
                            expanded = showMenuForSegment == index,
                            onDismissRequest = { showMenuForSegment = null }
                        ) {
                            // TTS options for AI messages - available on all segments
                            if (!isUser && ttsEnabled && onStopSpeaking != null) {
                                val segmentId = "${message.id}_$index"
                                val isThisSegmentPlaying = currentPlayingSegmentId == segmentId && isPlayingTTS
                                val isThisSegmentLoading =
                                    generatingSegmentId == segmentId ||
                                        (currentPlayingSegmentId == segmentId && isLoadingTTS)
                                val cachedAudioUrl = cachedAudioUrls[index]
                                
                                // Play button - show when cached audio exists
                                if (cachedAudioUrl != null && onPlayCached != null) {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(if (isThisSegmentPlaying) "Stop" else "Play")
                                        },
                                        onClick = {
                                            showMenuForSegment = null
                                            if (isThisSegmentPlaying) {
                                                onStopSpeaking()
                                            } else {
                                                onPlayCached(cachedAudioUrl, segmentId)
                                            }
                                        },
                                        leadingIcon = {
                                            if (isThisSegmentPlaying) {
                                                Icon(Icons.Default.Stop, contentDescription = null)
                                            } else {
                                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                                
                                // Listen button - always available to generate new TTS
                                if (onSpeak != null) {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(when {
                                                isThisSegmentLoading -> "Generating..."
                                                else -> "Listen"
                                            })
                                        },
                                        onClick = {
                                            showMenuForSegment = null
                                            if (!isThisSegmentLoading) {
                                                onSpeak(segment.text, segmentCharacter?.id, message.id, index)
                                            }
                                        },
                                        leadingIcon = {
                                            if (isThisSegmentLoading) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(Icons.Default.VolumeUp, contentDescription = null)
                                            }
                                        },
                                        enabled = !isThisSegmentLoading
                                    )
                                }
                            }
                            
                            // Copy segment text
                            DropdownMenuItem(
                                text = { Text(if (isLastSegment) "Copy All" else "Copy Segment") },
                                onClick = {
                                    showMenuForSegment = null
                                    onCopy(if (isLastSegment) message.text else segment.text)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                }
                            )
                            
                            // Delete and Regenerate only on last segment
                            if (isLastSegment) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                                showMenuForSegment = null
                                        showDeleteConfirmDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                                
                                if (onRegenerate != null) {
                                    DropdownMenuItem(
                                        text = { Text("Regenerate") },
                                        onClick = {
                                                    showMenuForSegment = null
                                            onRegenerate()
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Render choice buttons if available (for AI messages only)
        // Only show choices if it's the last message and chat is not loading
        if (!isUser && choices.isNotEmpty() && onChoiceSelected != null && isLast && !isChatLoading) {
            ChoiceButtons(
                choices = choices,
                onChoiceSelected = onChoiceSelected
            )
        }
    }
}

/**
 * Composable for displaying action and dialogue choice buttons
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChoiceButtons(
    choices: List<ChoiceItem>,
    onChoiceSelected: (String) -> Unit
) {
    val actionChoices = choices.filter { it.type == ChoiceType.ACTION }
    val dialogueChoices = choices.filter { it.type == ChoiceType.DIALOGUE }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, top = 8.dp), // Align with message content
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Action choices
        if (actionChoices.isNotEmpty()) {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                actionChoices.forEach { choice ->
                    SuggestionChip(
                        onClick = { onChoiceSelected(choice.text) },
                        label = { 
                            Text(
                                text = choice.text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Text(
                                text = choice.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }
        
        // Dialogue choices
        if (dialogueChoices.isNotEmpty()) {
            Text(
                text = "Dialogue",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 4.dp, top = if (actionChoices.isNotEmpty()) 4.dp else 0.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dialogueChoices.forEach { choice ->
                    SuggestionChip(
                        onClick = { onChoiceSelected("\"${choice.text}\"") },
                        label = { 
                            Text(
                                text = "\"${choice.text}\"",
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        icon = {
                            Text(
                                text = choice.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(40.dp)) // Align with messages
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Thinking...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    isLoading: Boolean,
    externalText: String? = null,
    onExternalTextConsumed: (() -> Unit)? = null
) {
    var inputText by remember { mutableStateOf("") }
    
    // Handle external text input (from choice selection) - append to existing text
    LaunchedEffect(externalText) {
        if (externalText != null) {
            inputText = if (inputText.isBlank()) {
                externalText
            } else {
                // Append with space separator
                "${inputText.trimEnd()} $externalText"
            }
            onExternalTextConsumed?.invoke()
        }
    }
    
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = !isLoading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    }
                ),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp)
            )
            
            FilledIconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isLoading) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
