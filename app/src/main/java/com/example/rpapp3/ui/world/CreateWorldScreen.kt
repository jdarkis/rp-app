package com.example.rpapp3.ui.world

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.rpapp3.viewmodel.WorldViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateWorldScreen(
    viewModel: WorldViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onWorldCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var writingStyle by remember { mutableStateOf("") }
    var systemInstructions by remember { mutableStateOf("") }
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
    
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            errorMessage = null
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create World") },
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
                placeholder = { Text("e.g., Medieval Fantasy Kingdom") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                placeholder = { Text("Describe the world, its lore, setting, and atmosphere...") },
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
                placeholder = { Text("e.g., Formal, Fantasy prose, Casual, Dramatic...") },
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
                placeholder = { Text("Special instructions for the AI when roleplaying in this world...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(systemInstructionsHeight)
                    .onFocusChanged { isSystemInstructionsFocused = it.isFocused },
                enabled = !viewModel.isLoading
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    viewModel.createWorld(
                        name = name,
                        description = description,
                        writingStyle = writingStyle,
                        systemInstructions = systemInstructions,
                        onSuccess = onWorldCreated,
                        onError = { errorMessage = it }
                    )
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
                    Text("Create World")
                }
            }
        }
    }
}
