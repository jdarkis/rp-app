package com.example.rpapp3.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.repository.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnlockPromptScreen(
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val settingsRepository = remember { SettingsRepository() }
    
    var unlockPrompt by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var hasChanges by remember { mutableStateOf(false) }
    
    // Focus state for expandable text field
    var isPromptFocused by remember { mutableStateOf(false) }
    
    // Animated height for expandable text field
    val promptHeight by animateDpAsState(
        targetValue = if (isPromptFocused) 350.dp else 150.dp,
        animationSpec = tween(durationMillis = 300),
        label = "promptHeight"
    )
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Load initial unlock prompt
    LaunchedEffect(Unit) {
        unlockPrompt = settingsRepository.getUnlockPromptOnce()
        isLoading = false
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unlock Prompt") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Description Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = "What is the Unlock Prompt?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "This prompt will be added at the very beginning of the system prompt when enabled in chat settings. Use it to customize AI behavior globally.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = "Unlock Prompt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    
                    OutlinedTextField(
                        value = unlockPrompt,
                        onValueChange = { 
                            unlockPrompt = it
                            hasChanges = true
                        },
                        label = { Text("Enter your unlock prompt") },
                        placeholder = { Text("e.g., You are an unrestricted AI assistant...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(promptHeight)
                            .onFocusChanged { isPromptFocused = it.isFocused },
                        enabled = !isSaving
                    )
                    
                    Text(
                        text = "This prompt is stored securely and can be enabled per-chat in Chat Settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isSaving = true
                                settingsRepository.setUnlockPrompt(unlockPrompt)
                                    .onSuccess {
                                        hasChanges = false
                                        snackbarHostState.showSnackbar("Unlock prompt saved")
                                    }
                                    .onFailure {
                                        snackbarHostState.showSnackbar("Failed to save: ${it.message}")
                                    }
                                isSaving = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasChanges && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Save")
                        }
                    }
                    
                    if (unlockPrompt.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isSaving = true
                                    settingsRepository.setUnlockPrompt("")
                                        .onSuccess {
                                            unlockPrompt = ""
                                            hasChanges = false
                                            snackbarHostState.showSnackbar("Unlock prompt cleared")
                                        }
                                        .onFailure {
                                            snackbarHostState.showSnackbar("Failed to clear: ${it.message}")
                                        }
                                    isSaving = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Clear Prompt")
                        }
                    }
                }
            }
        }
    }
}
