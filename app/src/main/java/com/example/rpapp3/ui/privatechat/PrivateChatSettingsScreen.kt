package com.example.rpapp3.ui.privatechat

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rpapp3.data.ChatSettingsManager
import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.viewmodel.PrivateChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateChatSettingsScreen(
    characterId: String,
    worldId: String,
    viewModel: PrivateChatViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatSettingsManager = remember { ChatSettingsManager.getInstance(context) }
    
    val character by viewModel.character.collectAsState()
    val currentChat by viewModel.currentChat.collectAsState()
    val availableContextChats by viewModel.availableContextChats.collectAsState()
    
    // Track selected context chat IDs
    var selectedContextChatIds by remember { mutableStateOf(emptySet<String>()) }
    
    // TTS Settings state (separate from normal chat)
    var ttsEnabled by remember { mutableStateOf(false) }
    var autoTtsEnabled by remember { mutableStateOf(false) }
    var ttsAudioTagsEnabled by remember { mutableStateOf(false) }
    
    // Advanced settings state (separate from normal chat)
    var thinkingEnabled by remember { mutableStateOf(false) }
    var unlockPromptEnabled by remember { mutableStateOf(false) }
    
    // Writing style state (stored in Firebase per-chat)
    var writingStyle by remember { mutableStateOf("") }
    
    // Dialog states
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    val systemPrompt by viewModel.systemPrompt.collectAsState()
    
    // Load current settings
    LaunchedEffect(Unit) {
        val settings = chatSettingsManager.getPrivateChatSettings()
        ttsEnabled = settings.ttsEnabled
        autoTtsEnabled = settings.autoTtsEnabled
        ttsAudioTagsEnabled = settings.ttsAudioTagsEnabled
        thinkingEnabled = settings.thinkingEnabled
        unlockPromptEnabled = settings.unlockPromptEnabled
    }
    
    // Initialize with current context and writing style
    LaunchedEffect(currentChat) {
        currentChat?.contextChatIds?.let { ids ->
            selectedContextChatIds = ids.toSet()
        }
        currentChat?.writingStyle?.let { style ->
            writingStyle = style
        }
    }
    
    // Initialize viewModel
    LaunchedEffect(characterId, worldId) {
        viewModel.initializeWithContext(context)
        viewModel.initializePrivateChat(characterId, worldId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private Chat Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                // Save context chat IDs
                                viewModel.updateContextChatIds(selectedContextChatIds.toList())
                                // Save writing style
                                viewModel.updateWritingStyle(writingStyle)
                                // Save TTS settings
                                chatSettingsManager.setPrivateTtsEnabled(ttsEnabled)
                                chatSettingsManager.setPrivateAutoTtsEnabled(autoTtsEnabled)
                                chatSettingsManager.setPrivateTtsAudioTagsEnabled(ttsAudioTagsEnabled)
                                // Save advanced settings
                                chatSettingsManager.setPrivateThinkingEnabled(thinkingEnabled)
                                chatSettingsManager.setPrivateUnlockPromptEnabled(unlockPromptEnabled)
                                
                                Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Character info header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Chatting with ${character?.name ?: "Unknown"}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Private conversation mode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // TTS Settings Section
            item {
                Text(
                    text = "Text-to-Speech",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "These settings are separate from normal chat TTS settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        // TTS Enabled Toggle
                        ListItem(
                            headlineContent = { Text("Enable TTS") },
                            supportingContent = { Text("Enable text-to-speech for messages") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = ttsEnabled,
                                    onCheckedChange = { ttsEnabled = it }
                                )
                            }
                        )
                        
                        HorizontalDivider()
                        
                        // Auto TTS Toggle
                        ListItem(
                            headlineContent = { Text("Auto TTS") },
                            supportingContent = { Text("Automatically read new messages") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = autoTtsEnabled,
                                    onCheckedChange = { autoTtsEnabled = it },
                                    enabled = ttsEnabled
                                )
                            }
                        )
                        
                        HorizontalDivider()
                        
                        // Audio Tags Toggle
                        ListItem(
                            headlineContent = { Text("Audio Expression Tags") },
                            supportingContent = { Text("Allow [sighs], [laughs] etc. in speech") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Tag,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = ttsAudioTagsEnabled,
                                    onCheckedChange = { ttsAudioTagsEnabled = it },
                                    enabled = ttsEnabled
                                )
                            }
                        )
                    }
                }
            }
            
            // Writing Style Section
            item {
                Text(
                    text = "Writing Style",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Custom instructions for how the character should write and respond.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            item {
                OutlinedTextField(
                    value = writingStyle,
                    onValueChange = { writingStyle = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    placeholder = { Text("e.g., Use casual language, add emojis, be flirty, speak like a poet...") },
                    label = { Text("Writing Style Instructions") },
                    minLines = 3,
                    maxLines = 8
                )
            }
            
            // Advanced Settings Section
            item {
                Text(
                    text = "Advanced Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        // Thinking Mode Toggle
                        ListItem(
                            headlineContent = { Text("Thinking Mode") },
                            supportingContent = { Text("Allow AI to reason before responding") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = thinkingEnabled,
                                    onCheckedChange = { thinkingEnabled = it }
                                )
                            }
                        )
                        
                        HorizontalDivider()
                        
                        // Unlock Prompt Toggle
                        ListItem(
                            headlineContent = { Text("Enable Unlock Prompt") },
                            supportingContent = { Text("Include custom unlock prompt in system instructions") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Switch(
                                    checked = unlockPromptEnabled,
                                    onCheckedChange = { unlockPromptEnabled = it }
                                )
                            }
                        )
                        
                        HorizontalDivider()
                        
                        // View System Prompt
                        ListItem(
                            modifier = Modifier.clickable { showSystemPromptDialog = true },
                            headlineContent = { Text("View System Prompt") },
                            supportingContent = { Text("See the full AI instructions") },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
            
            // Context Knowledge Section
            item {
                Text(
                    text = "Context Knowledge",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Select world chats to give the character additional memories and context.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            if (availableContextChats.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No world chats available.\nCreate some chats in the world first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(availableContextChats, key = { it.id }) { chat ->
                    ContextChatItem(
                        chat = chat,
                        isSelected = selectedContextChatIds.contains(chat.id),
                        onToggle = { selected ->
                            selectedContextChatIds = if (selected) {
                                selectedContextChatIds + chat.id
                            } else {
                                selectedContextChatIds - chat.id
                            }
                        }
                    )
                }
            }
            
            // Info note
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Selected chats will be included as the character's \"memories\" in conversations. " +
                                   "This allows the character to reference past interactions naturally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
            
            // Danger Zone Section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            
            item {
                var showRestartDialog by remember { mutableStateOf(false) }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    ListItem(
                        headlineContent = { Text("Restart Chat") },
                        supportingContent = { Text("Delete all messages and start fresh") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { showRestartDialog = true },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Restart")
                            }
                        }
                    )
                }
                
                // Restart confirmation dialog
                if (showRestartDialog) {
                    AlertDialog(
                        onDismissRequest = { showRestartDialog = false },
                        icon = {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = { Text("Restart Chat?") },
                        text = { 
                            Text("This will delete all messages in this private chat. This action cannot be undone.") 
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showRestartDialog = false
                                    viewModel.restartChat(
                                        onSuccess = {
                                            Toast.makeText(context, "Chat restarted", Toast.LENGTH_SHORT).show()
                                            onNavigateBack()
                                        },
                                        onError = { error ->
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Restart")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestartDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
    
    // System Prompt Dialog
    if (showSystemPromptDialog) {
        AlertDialog(
            onDismissRequest = { showSystemPromptDialog = false },
            title = { Text("System Prompt") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    if (systemPrompt != null) {
                        androidx.compose.foundation.lazy.LazyColumn {
                            item {
                                Text(
                                    text = systemPrompt!!,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "System prompt not available yet. Start the chat first.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun ContextChatItem(
    chat: Chat,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isSelected) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${chat.characterIds.size} character(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

