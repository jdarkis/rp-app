package com.example.rpapp3.ui.world

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rpapp3.ui.components.VersionHistoryDialog
import com.example.rpapp3.viewmodel.WorldViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorldScreen(
    worldId: String,
    viewModel: WorldViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onWorldUpdated: () -> Unit,
    onWorldDeleted: () -> Unit
) {
    val world by viewModel.currentWorld.collectAsState()
    
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var writingStyle by remember { mutableStateOf("") }
    var systemInstructions by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showVersionHistoryDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Focus states for expandable text fields
    var isDescriptionFocused by remember { mutableStateOf(false) }
    var isWritingStyleFocused by remember { mutableStateOf(false) }
    var isSystemInstructionsFocused by remember { mutableStateOf(false) }
    
    // Animated heights for expandable text fields
    val descriptionHeight by animateDpAsState(
        targetValue = if (isDescriptionFocused) 300.dp else 120.dp,
        animationSpec = tween(durationMillis = 300),
        label = "descriptionHeight"
    )
    val writingStyleHeight by animateDpAsState(
        targetValue = if (isWritingStyleFocused) 300.dp else 120.dp,
        animationSpec = tween(durationMillis = 300),
        label = "writingStyleHeight"
    )
    val systemInstructionsHeight by animateDpAsState(
        targetValue = if (isSystemInstructionsFocused) 300.dp else 120.dp,
        animationSpec = tween(durationMillis = 300),
        label = "systemInstructionsHeight"
    )
    
    val snackbarHostState = remember { SnackbarHostState() }
    val worldVersions by viewModel.worldVersions.collectAsState()
    
    // Load world data
    LaunchedEffect(worldId) {
        viewModel.loadWorld(worldId)
    }
    
    // Update form when world is loaded
    LaunchedEffect(world) {
        world?.let {
            name = it.name
            description = it.description
            writingStyle = it.writingStyle
            systemInstructions = it.systemInstructions
        }
    }
    
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit World") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Version History button
                    if (worldVersions.isNotEmpty()) {
                        IconButton(onClick = { showVersionHistoryDialog = true }) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = "Version History",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("World Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(descriptionHeight)
                    .onFocusChanged { isDescriptionFocused = it.isFocused },
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = writingStyle,
                onValueChange = { writingStyle = it },
                label = { Text("Writing Style") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(writingStyleHeight)
                    .onFocusChanged { isWritingStyleFocused = it.isFocused },
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = systemInstructions,
                onValueChange = { systemInstructions = it },
                label = { Text("AI Instructions") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(systemInstructionsHeight)
                    .onFocusChanged { isSystemInstructionsFocused = it.isFocused },
                enabled = !viewModel.isLoading
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    world?.let {
                        viewModel.updateWorld(
                            world = it.copy(
                                name = name,
                                description = description,
                                writingStyle = writingStyle,
                                systemInstructions = systemInstructions
                            ),
                            onSuccess = onWorldUpdated,
                            onError = { errorMessage = it }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete World?") },
            text = { 
                Text("This will permanently delete this world, all its characters, and all chat history. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteWorld(
                            worldId = worldId,
                            onSuccess = onWorldDeleted,
                            onError = { errorMessage = it }
                        )
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
    
    // Version History Dialog
    if (showVersionHistoryDialog) {
        VersionHistoryDialog(
            versions = worldVersions,
            entityType = "world",
            onRestore = { version ->
                viewModel.restoreWorldFromVersion(
                    version = version,
                    onSuccess = {
                        showVersionHistoryDialog = false
                        viewModel.loadWorld(worldId)
                    },
                    onError = { errorMessage = it }
                )
            },
            onDismiss = { showVersionHistoryDialog = false }
        )
    }
}
