package com.example.rpapp3.ui.privatechat

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rpapp3.data.model.ChatMessage
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.viewmodel.DisplayFilterSettings
import com.example.rpapp3.viewmodel.PrivateChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatScreen(
    characterId: String,
    worldId: String,
    viewModel: PrivateChatViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val character by viewModel.character.collectAsState()
    val messages = viewModel.messages
    val isLoading = viewModel.isLoading
    val error = viewModel.error
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Display filter settings
    val displayFilterSettings by viewModel.displayFilterSettings.collectAsState()
    
    // Initialize
    LaunchedEffect(characterId, worldId) {
        viewModel.initializeWithContext(context)
        viewModel.initializePrivateChat(characterId, worldId)
    }
    
    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Show error Toast
    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            PrivateChatTopBar(
                character = character,
                onNavigateBack = onNavigateBack,
                onNavigateToSettings = onNavigateToSettings
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        character = character,
                        displayFilterSettings = displayFilterSettings
                    )
                }
                
                // Loading indicator
                if (isLoading) {
                    item {
                        TypingIndicator(character = character)
                    }
                }
            }
            
            // Message input
            ChatInput(
                onSendMessage = { viewModel.sendMessage(it) },
                isLoading = isLoading
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateChatTopBar(
    character: Character?,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Character avatar
                val avatarUrl = character?.profilePictureUrl ?: character?.photoUrls?.firstOrNull()
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = character?.name,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = character?.name?.take(1)?.uppercase() ?: "?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                
                // Character name
                Column {
                    Text(
                        text = character?.name ?: "Loading...",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Private Chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onNavigateToSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    character: Character?,
    displayFilterSettings: DisplayFilterSettings = DisplayFilterSettings()
) {
    val isUser = message.isUser
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(message.timestamp) { 
        timeFormat.format(Date(message.timestamp)) 
    }
    
    // Apply display filter to AI messages (not user messages)
    val displayText = remember(message.text, isUser, displayFilterSettings) {
        if (!isUser && displayFilterSettings.enabled) {
            extractBracketedText(
                text = message.text,
                openBracket = displayFilterSettings.openBracket,
                closeBracket = displayFilterSettings.closeBracket
            )
        } else {
            message.text
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // Character avatar for non-user messages
        if (!isUser) {
            val avatarUrl = character?.profilePictureUrl ?: character?.photoUrls?.firstOrNull()
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .align(Alignment.Bottom),
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Bottom),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = character?.name?.take(1)?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        // Message bubble
        Surface(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = displayText,
                        color = if (isUser) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    },
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
        
        // Spacer for user messages (to balance with avatar)
        if (isUser) {
            Spacer(modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun TypingIndicator(character: Character?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Character avatar
        val avatarUrl = character?.profilePictureUrl ?: character?.photoUrls?.firstOrNull()
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = character?.name?.take(1)?.uppercase() ?: "?",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Typing bubble
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Animated dots
                repeat(3) { index ->
                    val alpha by rememberTypingDotAnimation(index)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha),
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTypingDotAnimation(index: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "typing")
    return transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 150)
        ),
        label = "dot$index"
    )
}

@Composable
private fun ChatInput(
    onSendMessage: (String) -> Unit,
    isLoading: Boolean
) {
    var text by remember { mutableStateOf("") }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Text input
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                ),
                maxLines = 4
            )
            
            // Send button
            FilledIconButton(
                onClick = {
                    if (text.isNotBlank() && !isLoading) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                enabled = text.isNotBlank() && !isLoading,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }
        }
    }
}

/**
 * Extracts all text segments within the specified brackets and joins them.
 * For example, with openBracket="[\"" and closeBracket="\"]":
 * Input: "Hello! [\"World\"] And [\"More\"]"
 * Output: "World More"
 * 
 * If no bracketed text is found, returns the original text.
 */
private fun extractBracketedText(
    text: String,
    openBracket: String,
    closeBracket: String
): String {
    if (openBracket.isEmpty() || closeBracket.isEmpty()) {
        return text
    }
    
    val extractedSegments = mutableListOf<String>()
    var currentIndex = 0
    
    while (currentIndex < text.length) {
        val startIndex = text.indexOf(openBracket, currentIndex)
        if (startIndex == -1) {
            break
        }
        
        val contentStart = startIndex + openBracket.length
        val endIndex = text.indexOf(closeBracket, contentStart)
        if (endIndex == -1) {
            break
        }
        
        val extractedContent = text.substring(contentStart, endIndex)
        if (extractedContent.isNotBlank()) {
            extractedSegments.add(extractedContent.trim())
        }
        
        currentIndex = endIndex + closeBracket.length
    }
    
    return if (extractedSegments.isNotEmpty()) {
        extractedSegments.joinToString(" ")
    } else {
        text // Return original text if no brackets found
    }
}
