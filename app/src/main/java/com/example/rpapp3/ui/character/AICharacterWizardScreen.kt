package com.example.rpapp3.ui.character

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rpapp3.data.GeneratedCharacter
import com.example.rpapp3.viewmodel.AICharacterGeneratorViewModel
import com.example.rpapp3.viewmodel.SelectableChat
import com.example.rpapp3.viewmodel.WizardStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AICharacterWizardScreen(
    worldId: String,
    viewModel: AICharacterGeneratorViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onCharacterCreated: () -> Unit
) {
    val context = LocalContext.current
    val currentStep by viewModel.currentStep.collectAsState()
    val currentWorld by viewModel.currentWorld.collectAsState()
    val currentWorldChats by viewModel.currentWorldChats.collectAsState()
    val otherWorldChats by viewModel.otherWorldChats.collectAsState()
    val generatedCharacters by viewModel.generatedCharacters.collectAsState()
    val selectedCharacterIndex by viewModel.selectedCharacterIndex.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Initialize ViewModel
    LaunchedEffect(worldId) {
        viewModel.initialize(context, worldId)
    }
    
    // Show error messages
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentStep) {
                            WizardStep.CONTEXT_SELECTION -> "Select Context"
                            WizardStep.GENERATION -> "Extracting Characters"
                            WizardStep.REVIEW -> "Review & Approve"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep == WizardStep.CONTEXT_SELECTION) {
                            onNavigateBack()
                        } else {
                            viewModel.previousStep()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentStep) {
                WizardStep.CONTEXT_SELECTION -> {
                    ContextSelectionStep(
                        worldName = currentWorld?.name ?: "Current World",
                        hasWorldDescription = !currentWorld?.description.isNullOrBlank(),
                        hasAIInstructions = !currentWorld?.systemInstructions.isNullOrBlank(),
                        useWorldDescription = viewModel.useWorldDescription,
                        onUseWorldDescriptionChange = { viewModel.useWorldDescription = it },
                        useAIInstructions = viewModel.useAIInstructions,
                        onUseAIInstructionsChange = { viewModel.useAIInstructions = it },
                        additionalPrompt = viewModel.additionalPrompt,
                        onAdditionalPromptChange = { viewModel.additionalPrompt = it },
                        currentWorldChats = currentWorldChats,
                        otherWorldChats = otherWorldChats,
                        onToggleCurrentWorldChat = { viewModel.toggleCurrentWorldChat(it) },
                        onToggleOtherWorldChat = { viewModel.toggleOtherWorldChat(it) },
                        onNext = { viewModel.nextStep() },
                        isLoading = viewModel.isLoading
                    )
                }
                
                WizardStep.GENERATION -> {
                    GenerationStep(
                        isLoading = viewModel.isLoading,
                        characters = generatedCharacters,
                        onRegenerate = { viewModel.generateCharacters() },
                        onUpdateCharacter = { idx, name, desc, app, pers, sys ->
                            viewModel.updateGeneratedCharacter(idx, name, desc, app, pers, sys)
                        },
                        onSaveCharacter = { idx, name, desc, app, pers, sys ->
                            // Update the character first, then save
                            viewModel.updateGeneratedCharacter(idx, name, desc, app, pers, sys)
                            viewModel.saveCharacter(
                                context = context,
                                index = idx,
                                onSuccess = { /* Character removed from list automatically */ },
                                onError = { /* Error shown via snackbar */ }
                            )
                        },
                        onSaveAll = {
                            viewModel.saveAllCharacters(
                                context = context,
                                onSuccess = onCharacterCreated,
                                onError = { /* Error shown via snackbar */ }
                            )
                        },
                        uploadProgress = viewModel.uploadProgress
                    )
                }
                
                WizardStep.REVIEW -> {
                    ReviewStep(
                        characters = generatedCharacters,
                        selectedIndex = selectedCharacterIndex,
                        onSelectCharacter = { viewModel.selectCharacter(it) },
                        onUpdateCharacter = { idx, name, desc, app, pers, sys ->
                            viewModel.updateGeneratedCharacter(idx, name, desc, app, pers, sys)
                        },
                        onSaveAll = {
                            viewModel.saveAllCharacters(
                                context = context,
                                onSuccess = onCharacterCreated,
                                onError = { /* Error shown via snackbar */ }
                            )
                        },
                        isLoading = viewModel.isLoading,
                        uploadProgress = viewModel.uploadProgress
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextSelectionStep(
    worldName: String,
    hasWorldDescription: Boolean,
    hasAIInstructions: Boolean,
    useWorldDescription: Boolean,
    onUseWorldDescriptionChange: (Boolean) -> Unit,
    useAIInstructions: Boolean,
    onUseAIInstructionsChange: (Boolean) -> Unit,
    additionalPrompt: String,
    onAdditionalPromptChange: (String) -> Unit,
    currentWorldChats: List<SelectableChat>,
    otherWorldChats: List<SelectableChat>,
    onToggleCurrentWorldChat: (String) -> Unit,
    onToggleOtherWorldChat: (String) -> Unit,
    onNext: () -> Unit,
    isLoading: Boolean
) {
    var showOtherWorldChats by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // World Context Section
        Text(
            text = "World Context",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("World Description", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (hasWorldDescription) "Include world's description" else "No description set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useWorldDescription,
                        onCheckedChange = onUseWorldDescriptionChange,
                        enabled = hasWorldDescription
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Instructions", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (hasAIInstructions) "Include roleplay style instructions" else "No instructions set",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useAIInstructions,
                        onCheckedChange = onUseAIInstructionsChange,
                        enabled = hasAIInstructions
                    )
                }
            }
        }
        
        // Info Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Column {
                    Text(
                        text = "Character Extraction",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Characters will be extracted from the selected context. Only characters explicitly mentioned in the world description or chat sessions will be found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        // Chat Sessions Section
        if (currentWorldChats.isNotEmpty()) {
            Text(
                text = "Chat Sessions from $worldName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(8.dp)) {
                    currentWorldChats.forEach { selectableChat ->
                        ChatSelectionItem(
                            title = selectableChat.chat.title.ifBlank { "Untitled Chat" },
                            subtitle = null,
                            isSelected = selectableChat.isSelected,
                            onToggle = { onToggleCurrentWorldChat(selectableChat.chat.id) }
                        )
                    }
                }
            }
        }
        
        // Other World Chats Section
        if (otherWorldChats.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOtherWorldChats = !showOtherWorldChats }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chats from Other Worlds (${otherWorldChats.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (showOtherWorldChats) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle"
                )
            }
            
            AnimatedVisibility(visible = showOtherWorldChats) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        otherWorldChats.forEach { selectableChat ->
                            ChatSelectionItem(
                                title = selectableChat.chat.title.ifBlank { "Untitled Chat" },
                                subtitle = selectableChat.worldName,
                                isSelected = selectableChat.isSelected,
                                onToggle = { onToggleOtherWorldChat(selectableChat.chat.id) }
                            )
                        }
                    }
                }
            }
        }
        
        // Additional Prompt
        Text(
            text = "Additional Instructions (Optional)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        
        OutlinedTextField(
            value = additionalPrompt,
            onValueChange = onAdditionalPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            placeholder = { Text("E.g., 'Focus on named NPCs' or 'Include the villains'") },
            enabled = !isLoading
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Next Button
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Extract Characters")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ChatSelectionItem(
    title: String,
    subtitle: String?,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() }
        )
    }
}

@Composable
private fun GenerationStep(
    isLoading: Boolean,
    characters: List<GeneratedCharacter>,
    onRegenerate: () -> Unit,
    onUpdateCharacter: (Int, String?, String?, String?, String?, String?) -> Unit,
    onSaveCharacter: (Int, String, String, String, String, String) -> Unit,
    onSaveAll: () -> Unit,
    uploadProgress: String?
) {
    // Track which character is being edited
    var editingCharacterIndex by remember { mutableStateOf<Int?>(null) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(64.dp))
                    Text(
                        text = uploadProgress ?: "Extracting characters...",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uploadProgress == null) {
                        Text(
                            text = "This may take a moment",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (characters.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "No characters found in context",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = onRegenerate) {
                        Text("Try Again")
                    }
                }
            }
        } else {
            Text(
                text = "Found ${characters.size} character${if (characters.size > 1) "s" else ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Tap a character to view details and edit",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(characters) { index, character ->
                    GeneratedCharacterPreview(
                        index = index + 1,
                        character = character,
                        onClick = { editingCharacterIndex = index }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onRegenerate,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Regenerate")
                }
                
                Button(
                    onClick = onSaveAll,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save All")
                }
            }
        }
    }
    
    // Character edit dialog
    editingCharacterIndex?.let { index ->
        val character = characters.getOrNull(index)
        if (character != null) {
            CharacterEditDialog(
                character = character,
                onDismiss = { editingCharacterIndex = null },
                onUpdateAndClose = { name, desc, app, pers, sys ->
                    onUpdateCharacter(index, name, desc, app, pers, sys)
                    editingCharacterIndex = null
                },
                onSaveToDatabase = { name, desc, app, pers, sys ->
                    // Update first, then save
                    onUpdateCharacter(index, name, desc, app, pers, sys)
                    onSaveCharacter(index, name, desc, app, pers, sys)
                    editingCharacterIndex = null
                },
                isLoading = isLoading
            )
        }
    }
}

@Composable
private fun CharacterEditDialog(
    character: GeneratedCharacter,
    onDismiss: () -> Unit,
    onUpdateAndClose: (String, String, String, String, String) -> Unit,
    onSaveToDatabase: (String, String, String, String, String) -> Unit,
    isLoading: Boolean = false
) {
    var editName by remember { mutableStateOf(character.name) }
    var editDescription by remember { mutableStateOf(character.description) }
    var editAppearance by remember { mutableStateOf(character.appearance) }
    var editPersonality by remember { mutableStateOf(character.personality) }
    var editSystemInstructions by remember { mutableStateOf(character.systemInstructions) }
    
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text(
                text = character.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                
                OutlinedTextField(
                    value = editDescription,
                    onValueChange = { editDescription = it },
                    label = { Text("Description") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    enabled = !isLoading
                )
                
                OutlinedTextField(
                    value = editAppearance,
                    onValueChange = { editAppearance = it },
                    label = { Text("Appearance") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    enabled = !isLoading
                )
                
                OutlinedTextField(
                    value = editPersonality,
                    onValueChange = { editPersonality = it },
                    label = { Text("Personality") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    enabled = !isLoading
                )
                
                OutlinedTextField(
                    value = editSystemInstructions,
                    onValueChange = { editSystemInstructions = it },
                    label = { Text("AI Instructions") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    enabled = !isLoading
                )
                
                // Save This Character button inside dialog content
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        onSaveToDatabase(editName, editDescription, editAppearance, editPersonality, editSystemInstructions)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editName.isNotBlank() && !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving...")
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save This Character")
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = {
                    onUpdateAndClose(editName, editDescription, editAppearance, editPersonality, editSystemInstructions)
                },
                enabled = editName.isNotBlank() && !isLoading
            ) {
                Text("Update & Close")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun GeneratedCharacterPreview(
    index: Int,
    character: GeneratedCharacter,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    text = character.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (character.description.isNotBlank()) {
                Text(
                    text = character.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (character.appearance.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Appearance") },
                        icon = { Icon(Icons.Default.Face, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                if (character.personality.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Personality") },
                        icon = { Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStep(
    characters: List<GeneratedCharacter>,
    selectedIndex: Int,
    onSelectCharacter: (Int) -> Unit,
    onUpdateCharacter: (Int, String?, String?, String?, String?, String?) -> Unit,
    onSaveAll: () -> Unit,
    isLoading: Boolean,
    uploadProgress: String?
) {
    val selectedCharacter = characters.getOrNull(selectedIndex)
    
    // Local edit states
    var editName by remember(selectedIndex) { mutableStateOf(selectedCharacter?.name ?: "") }
    var editDescription by remember(selectedIndex) { mutableStateOf(selectedCharacter?.description ?: "") }
    var editAppearance by remember(selectedIndex) { mutableStateOf(selectedCharacter?.appearance ?: "") }
    var editPersonality by remember(selectedIndex) { mutableStateOf(selectedCharacter?.personality ?: "") }
    var editSystemInstructions by remember(selectedIndex) { mutableStateOf(selectedCharacter?.systemInstructions ?: "") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Character tabs if multiple
        if (characters.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                characters.forEachIndexed { index, character ->
                    Tab(
                        selected = selectedIndex == index,
                        onClick = {
                            // Save current edits before switching
                            onUpdateCharacter(selectedIndex, editName, editDescription, editAppearance, editPersonality, editSystemInstructions)
                            onSelectCharacter(index)
                        },
                        text = { Text(character.name.take(15)) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Edit form
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Character Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLoading
            )
            
            OutlinedTextField(
                value = editDescription,
                onValueChange = { editDescription = it },
                label = { Text("Background/Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                enabled = !isLoading
            )
            
            OutlinedTextField(
                value = editAppearance,
                onValueChange = { editAppearance = it },
                label = { Text("Appearance") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                enabled = !isLoading
            )
            
            OutlinedTextField(
                value = editPersonality,
                onValueChange = { editPersonality = it },
                label = { Text("Personality") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                enabled = !isLoading
            )
            
            OutlinedTextField(
                value = editSystemInstructions,
                onValueChange = { editSystemInstructions = it },
                label = { Text("AI Instructions") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                enabled = !isLoading
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Progress indicator
        uploadProgress?.let { progress ->
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = progress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Save button
        Button(
            onClick = {
                // Save current edits first
                onUpdateCharacter(selectedIndex, editName, editDescription, editAppearance, editPersonality, editSystemInstructions)
                onSaveAll()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && editName.isNotBlank()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (characters.size > 1) "Save All Characters" else "Save Character")
        }
    }
}
