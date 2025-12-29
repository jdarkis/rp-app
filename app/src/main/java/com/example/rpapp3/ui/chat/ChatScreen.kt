package com.example.rpapp3.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val messages = viewModel.messages
    val isLoading = viewModel.isLoading
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Chat settings
    val chatSettingsManager = remember { ChatSettingsManager.getInstance(context) }
    val filterMode by chatSettingsManager.filterMode.collectAsState(initial = MessageFilterMode.OFF)
    val customDelimiter by chatSettingsManager.customDelimiter.collectAsState(initial = ChatSettingsManager.DEFAULT_DELIMITER)
    val paragraphCount by chatSettingsManager.paragraphCount.collectAsState(initial = ChatSettingsManager.DEFAULT_PARAGRAPH_COUNT)
    val separateCharacterDialogue by chatSettingsManager.separateCharacterDialogue.collectAsState(initial = true)
    
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
            listState.animateScrollToItem(messages.size - 1)
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
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        character = characters.find { it.id == message.characterId },
                        allCharacters = worldCharacters,
                        filterMode = filterMode,
                        customDelimiter = customDelimiter,
                        paragraphCount = paragraphCount,
                        separateDialogue = separateCharacterDialogue,
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
                        }
                    )
                }
                
                // Loading indicator
                if (isLoading) {
                    item {
                        LoadingIndicator()
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
                isLoading = isLoading
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
    customDelimiter: String = "***",
    paragraphCount: Int = 1,
    separateDialogue: Boolean = true,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
    onRegenerate: (() -> Unit)?,
    onCharacterClick: ((String) -> Unit)? = null
) {
    val isUser = message.isUser
    var showMenu by remember { mutableStateOf(false) }
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
                if (customDelimiter.isNotEmpty() && message.text.contains(customDelimiter)) {
                    message.text.substringAfterLast(customDelimiter).trim()
                        .ifEmpty { message.text }
                } else {
                    message.text
                }
            }
        }
    } else {
        message.text
    }
    
    // Parse segments if this is an AI message and dialogue separation is enabled
    val segments = if (!isUser && separateDialogue) {
        parseDialogueSegments(displayText)
    } else {
        listOf(DisplaySegment(displayText, isCharacterDialogue = false))
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
                                    imageVector = Icons.Filled.MenuBook,
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
                                onLongClick = { if (isLastSegment) showMenu = true }
                            )
                        ) {
                            Text(
                                text = segment.text,
                                color = textColor,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        // Dropdown menu only on last segment
                        if (isLastSegment) {
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy") },
                                    onClick = {
                                        showMenu = false
                                        onCopy(message.text)
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    }
                                )
                                
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
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
                                            showMenu = false
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
    isLoading: Boolean
) {
    var inputText by remember { mutableStateOf("") }
    
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
