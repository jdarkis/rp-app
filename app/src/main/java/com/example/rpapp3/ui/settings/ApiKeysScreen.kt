package com.example.rpapp3.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.ApiKeyManager
import com.example.rpapp3.data.GeminiApiKeyValidator
import com.example.rpapp3.data.GeminiKeyStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeysScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val apiKeyManager = remember { ApiKeyManager.getInstance(context) }
    val apiKeyValidator = remember { GeminiApiKeyValidator() }
    val scope = rememberCoroutineScope()
    
    val apiKeys by apiKeyManager.apiKeys.collectAsState(initial = emptyList())
    val currentIndex by apiKeyManager.currentKeyIndex.collectAsState(initial = 0)
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newApiKey by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<String?>(null) }
    var keyStatuses by remember {
        mutableStateOf<Map<String, GeminiKeyStatus>>(emptyMap())
    }
    var validationJob by remember { mutableStateOf<Job?>(null) }

    fun validateKeys(keys: List<String>, forceRefresh: Boolean) {
        validationJob?.cancel()

        val keysToValidate = if (forceRefresh) {
            keys
        } else {
            keys.filter {
                keyStatuses[it] == null ||
                    keyStatuses[it] == GeminiKeyStatus.Checking
            }
        }

        keyStatuses = keyStatuses
            .filterKeys { it in keys }
            .toMutableMap()
            .apply {
                keysToValidate.forEach { put(it, GeminiKeyStatus.Checking) }
            }

        if (keysToValidate.isEmpty()) return

        validationJob = scope.launch {
            val semaphore = Semaphore(3)
            coroutineScope {
                keysToValidate.map { key ->
                    async {
                        val status = semaphore.withPermit {
                            apiKeyValidator.validate(key)
                        }
                        if (key in apiKeys) {
                            keyStatuses = keyStatuses + (key to status)
                        }
                    }
                }.awaitAll()
            }
        }
    }
    
    // Initialize defaults on first launch
    LaunchedEffect(Unit) {
        apiKeyManager.initializeDefaults()
    }

    LaunchedEffect(apiKeys) {
        validateKeys(apiKeys, forceRefresh = false)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("API Keys") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    val isChecking = keyStatuses.values.any {
                        it == GeminiKeyStatus.Checking
                    }
                    IconButton(
                        onClick = { validateKeys(apiKeys, forceRefresh = true) },
                        enabled = apiKeys.isNotEmpty() && !isChecking
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh key status",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, "Add API Key")
            }
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
                    .padding(16.dp)
            ) {
                Text(
                    text = "Manage your Gemini API keys. When one key runs out of quota, the app will automatically switch to the next available key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                if (apiKeys.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No API keys configured.\nTap + to add one.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        apiKeys.forEachIndexed { index, key ->
                            ApiKeyItem(
                                apiKey = key,
                                isActive = index == currentIndex,
                                status = keyStatuses[key],
                                onSelect = {
                                    scope.launch {
                                        apiKeyManager.setActiveKeyIndex(index)
                                    }
                                },
                                onDelete = {
                                    keyToDelete = key
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                val activeCount = apiKeys.count {
                    keyStatuses[it] == GeminiKeyStatus.Active
                }
                val invalidCount = apiKeys.count {
                    keyStatuses[it] == GeminiKeyStatus.InvalidOrBlocked
                }
                val uncheckedCount = apiKeys.size - activeCount - invalidCount
                
                // Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Total keys: ${apiKeys.size} | Selected: ${if (apiKeys.isNotEmpty()) currentIndex + 1 else 0}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Valid: $activeCount | Invalid or blocked: $invalidCount | Unchecked: $uncheckedCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Add API Key Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                newApiKey = ""
            },
            title = { Text("Add API Key") },
            text = {
                OutlinedTextField(
                    value = newApiKey,
                    onValueChange = { newApiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            apiKeyManager.addApiKey(newApiKey)
                            newApiKey = ""
                            showAddDialog = false
                        }
                    },
                    enabled = newApiKey.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    newApiKey = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog && keyToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteConfirmDialog = false
                keyToDelete = null
            },
            title = { Text("Delete API Key?") },
            text = { 
                Text("Are you sure you want to remove this API key from the pool?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            keyToDelete?.let { apiKeyManager.removeApiKey(it) }
                            showDeleteConfirmDialog = false
                            keyToDelete = null
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false
                    keyToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ApiKeyItem(
    apiKey: String,
    isActive: Boolean,
    status: GeminiKeyStatus?,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Active indicator
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Active",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            
            // API Key (masked)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = maskApiKey(apiKey),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isActive) "Currently selected" else "Tap to select this key",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                GeminiKeyStatusRow(status)
            }
            
            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun GeminiKeyStatusRow(status: GeminiKeyStatus?) {
    val statusText: String
    val statusColor: androidx.compose.ui.graphics.Color
    val statusIcon: androidx.compose.ui.graphics.vector.ImageVector

    when (status) {
        GeminiKeyStatus.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Checking credential...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }
        GeminiKeyStatus.Active -> {
            statusText = "Credential active"
            statusColor = MaterialTheme.colorScheme.primary
            statusIcon = Icons.Default.CheckCircle
        }
        GeminiKeyStatus.InvalidOrBlocked -> {
            statusText = "Invalid or blocked"
            statusColor = MaterialTheme.colorScheme.error
            statusIcon = Icons.Default.Error
        }
        GeminiKeyStatus.UnableToVerify -> {
            statusText = "Unable to verify"
            statusColor = MaterialTheme.colorScheme.tertiary
            statusIcon = Icons.AutoMirrored.Filled.Help
        }
        null -> {
            statusText = "Not checked"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusIcon = Icons.AutoMirrored.Filled.Help
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun maskApiKey(key: String): String {
    return if (key.length > 12) {
        "${key.take(6)}...${key.takeLast(6)}"
    } else {
        key
    }
}
