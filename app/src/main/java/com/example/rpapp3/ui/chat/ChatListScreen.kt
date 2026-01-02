package com.example.rpapp3.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rpapp3.data.model.Chat
import com.example.rpapp3.data.model.Character
import com.example.rpapp3.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    worldId: String,
    viewModel: ChatViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onChatClick: (String) -> Unit,
    onNewChat: () -> Unit
) {
    val chats by viewModel.chats.collectAsState()
    val characters by viewModel.characters.collectAsState()
    
    LaunchedEffect(worldId) {
        viewModel.loadChats(worldId)
        viewModel.loadCharactersForSelection(worldId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Sessions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewChat,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Chat") }
            )
        }
    ) { paddingValues ->
        if (chats.isEmpty()) {
            EmptyChatsState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onNewChat = onNewChat
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chats, key = { it.id }) { chat ->
                    ChatCard(
                        chat = chat,
                        allCharacters = characters,
                        onClick = { onChatClick(chat.id) },
                        onDuplicate = {
                            viewModel.duplicateChat(
                                chatId = chat.id,
                                onSuccess = {},
                                onError = {}
                            )
                        },
                        onUpdateCharacters = { newCharacterIds ->
                            viewModel.updateChatCharacters(
                                chatId = chat.id,
                                characterIds = newCharacterIds,
                                onSuccess = {},
                                onError = {}
                            )
                        },
                        onDelete = {
                            viewModel.deleteChat(
                                chatId = chat.id,
                                onSuccess = {},
                                onError = {}
                            )
                        }
                    )
                }
                
                // Bottom spacing for FAB
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsState(
    modifier: Modifier = Modifier,
    onNewChat: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No chats yet",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start a new conversation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNewChat) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New Chat")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatCard(
    chat: Chat,
    allCharacters: List<Character>,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onUpdateCharacters: (List<String>) -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showManageCharactersDialog by remember { mutableStateOf(false) }
    
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${chat.characterIds.size} character(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = dateFormat.format(Date(chat.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        
        // Dropdown menu on long press
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Duplicate") },
                onClick = {
                    showMenu = false
                    onDuplicate()
                },
                leadingIcon = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Manage Characters") },
                onClick = {
                    showMenu = false
                    showManageCharactersDialog = true
                },
                leadingIcon = {
                    Icon(Icons.Default.People, contentDescription = null)
                }
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showMenu = false
                    showDeleteDialog = true
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Chat?") },
            text = { Text("This will permanently delete this chat and all its messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Manage characters dialog
    if (showManageCharactersDialog) {
        ManageCharactersDialog(
            currentCharacterIds = chat.characterIds,
            allCharacters = allCharacters,
            onDismiss = { showManageCharactersDialog = false },
            onSave = { newCharacterIds ->
                showManageCharactersDialog = false
                onUpdateCharacters(newCharacterIds)
            }
        )
    }
}

@Composable
fun ManageCharactersDialog(
    currentCharacterIds: List<String>,
    allCharacters: List<Character>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var selectedCharacterIds by remember { mutableStateOf(currentCharacterIds.toSet()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Characters") },
        text = {
            if (allCharacters.isEmpty()) {
                Text("No characters available in this world.")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allCharacters, key = { it.id }) { character ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedCharacterIds.contains(character.id),
                                onCheckedChange = { isChecked ->
                                    selectedCharacterIds = if (isChecked) {
                                        selectedCharacterIds + character.id
                                    } else {
                                        selectedCharacterIds - character.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = character.name,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (character.personality.isNotBlank()) {
                                    Text(
                                        text = character.personality,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(selectedCharacterIds.toList()) },
                enabled = allCharacters.isNotEmpty()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
