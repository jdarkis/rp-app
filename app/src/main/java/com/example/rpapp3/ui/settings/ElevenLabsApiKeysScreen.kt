package com.example.rpapp3.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.ElevenLabsApiKeyManager
import com.example.rpapp3.data.ElevenLabsService
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevenLabsApiKeysScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val apiKeyManager = remember { ElevenLabsApiKeyManager.getInstance(context) }
    val elevenLabsService = remember { ElevenLabsService.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    val apiKeys by apiKeyManager.apiKeys.collectAsState(initial = emptyList())
    val currentIndex by apiKeyManager.currentKeyIndex.collectAsState(initial = 0)
    
    var showAddDialog by remember { mutableStateOf(false) }
    var newApiKey by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<String?>(null) }
    
    // Store subscription info for each key
    var subscriptionInfoMap by remember { mutableStateOf<Map<String, ElevenLabsService.SubscriptionInfo?>>(emptyMap()) }
    var loadingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Function to load subscription info for all keys
    fun loadSubscriptionInfo(keys: List<String>) {
        scope.launch {
            isRefreshing = true
            keys.forEach { key ->
                loadingKeys = loadingKeys + key
                val result = elevenLabsService.getSubscriptionInfoForKey(key)
                subscriptionInfoMap = subscriptionInfoMap + (key to result.getOrNull())
                loadingKeys = loadingKeys - key
            }
            isRefreshing = false
        }
    }
    
    // Initialize defaults and load subscription info on first launch
    LaunchedEffect(Unit) {
        apiKeyManager.initializeDefaults()
    }
    
    // Load subscription info when keys change
    LaunchedEffect(apiKeys) {
        if (apiKeys.isNotEmpty()) {
            loadSubscriptionInfo(apiKeys)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ElevenLabs API Keys") },
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
                    // Refresh button
                    IconButton(
                        onClick = { loadSubscriptionInfo(apiKeys) },
                        enabled = !isRefreshing && apiKeys.isNotEmpty()
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                "Refresh credits",
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
                    text = "Manage your ElevenLabs API keys for text-to-speech voice synthesis. When one key runs out of quota, the app will automatically switch to the next available key.",
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
                            ElevenLabsApiKeyItem(
                                apiKey = key,
                                isActive = index == currentIndex,
                                subscriptionInfo = subscriptionInfoMap[key],
                                isLoading = key in loadingKeys,
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
                
                // Info card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Total keys: ${apiKeys.size} | Active: ${if (apiKeys.isNotEmpty()) currentIndex + 1 else 0}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
            title = { Text("Add ElevenLabs API Key") },
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
                Text("Are you sure you want to remove this ElevenLabs API key from the pool?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            keyToDelete?.let { key ->
                                apiKeyManager.removeApiKey(key)
                                // Also remove from subscription info map
                                subscriptionInfoMap = subscriptionInfoMap - key
                            }
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
private fun ElevenLabsApiKeyItem(
    apiKey: String,
    isActive: Boolean,
    subscriptionInfo: ElevenLabsService.SubscriptionInfo?,
    isLoading: Boolean,
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
            
            // API Key (masked) and credits info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = maskElevenLabsApiKey(apiKey),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Credits info
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Loading credits...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (subscriptionInfo != null) {
                    val numberFormat = remember { NumberFormat.getNumberInstance() }
                    val remaining = subscriptionInfo.remainingCharacters
                    val limit = subscriptionInfo.characterLimit
                    val percentRemaining = if (limit > 0) (remaining.toFloat() / limit * 100).toInt() else 0
                    
                    val creditsColor = when {
                        percentRemaining > 50 -> MaterialTheme.colorScheme.primary
                        percentRemaining > 20 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                    
                    Text(
                        text = "${numberFormat.format(remaining)} / ${numberFormat.format(limit)} chars ($percentRemaining%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = creditsColor,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Progress bar for visual representation
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { remaining.toFloat() / limit.coerceAtLeast(1) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = creditsColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    
                    Text(
                        text = if (isActive) "Currently active • ${subscriptionInfo.tier}" else "Tap to use • ${subscriptionInfo.tier}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = if (isActive) "Currently active • Unable to load credits" else "Tap to use this key",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

private fun maskElevenLabsApiKey(key: String): String {
    return if (key.length > 12) {
        "${key.take(6)}...${key.takeLast(6)}"
    } else {
        key
    }
}
