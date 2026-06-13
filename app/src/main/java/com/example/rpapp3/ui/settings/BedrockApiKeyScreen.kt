package com.example.rpapp3.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.BedrockApiKeyManager
import com.example.rpapp3.data.BedrockKeyStatus
import com.example.rpapp3.data.BedrockService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedrockApiKeyScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val apiKeyManager = remember { BedrockApiKeyManager.getInstance(context) }
    val bedrockService = remember { BedrockService() }
    val scope = rememberCoroutineScope()

    val apiKeyState by apiKeyManager.apiKeyState.collectAsState(initial = com.example.rpapp3.data.BedrockApiKeyState())

    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var newApiKey by remember { mutableStateOf("") }
    var keyStatus by remember { mutableStateOf<BedrockKeyStatus?>(null) }
    var keyStatusDetail by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    fun testSavedKey() {
        if (isTesting) return
        scope.launch {
            isTesting = true
            keyStatus = BedrockKeyStatus.Checking
            val key = apiKeyManager.getApiKey()
            if (key.isNullOrBlank()) {
                keyStatus = BedrockKeyStatus.InvalidOrBlocked
                keyStatusDetail = "No Bedrock API key is saved."
            } else {
                val result = bedrockService.testApiKey(key)
                keyStatus = result.status
                keyStatusDetail = result.detail
            }
            isTesting = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Amazon Bedrock API Key") },
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
                    IconButton(
                        onClick = { testSavedKey() },
                        enabled = apiKeyState.hasKey && !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Test Bedrock key",
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Save a Bedrock API key for Claude Opus 4.6. AWS recommends these keys for exploration and development; generate a new key when yours expires.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (apiKeyState.hasKey) apiKeyState.maskedKey else "No Bedrock key saved",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (apiKeyState.hasKey) "Used for Claude Opus 4.6 roleplay chat" else "Add a key to enable Bedrock models",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (apiKeyState.hasKey) {
                                IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Bedrock key",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        BedrockKeyStatusRow(
                            status = keyStatus,
                            detail = keyStatusDetail
                        )
                    }
                }

                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(if (apiKeyState.hasKey) "Replace API Key" else "Add API Key")
                }

                OutlinedButton(
                    onClick = { testSavedKey() },
                    enabled = apiKeyState.hasKey && !isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text("Test Key")
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = {
                showSaveDialog = false
                newApiKey = ""
            },
            title = { Text(if (apiKeyState.hasKey) "Replace Bedrock API Key" else "Add Bedrock API Key") },
            text = {
                OutlinedTextField(
                    value = newApiKey,
                    onValueChange = { newApiKey = it },
                    label = { Text("Bedrock API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            apiKeyManager.saveApiKey(newApiKey)
                            keyStatus = null
                            keyStatusDetail = null
                            newApiKey = ""
                            showSaveDialog = false
                        }
                    },
                    enabled = newApiKey.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    newApiKey = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete Bedrock API Key?") },
            text = { Text("Claude Opus 4.6 will stop working until you add a Bedrock key again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            apiKeyManager.clearApiKey()
                            keyStatus = null
                            keyStatusDetail = null
                            showDeleteConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BedrockKeyStatusRow(
    status: BedrockKeyStatus?,
    detail: String?
) {
    val statusText: String
    val statusColor: androidx.compose.ui.graphics.Color
    val statusIcon: androidx.compose.ui.graphics.vector.ImageVector

    when (status) {
        BedrockKeyStatus.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Testing key with Bedrock...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }
        BedrockKeyStatus.Active -> {
            statusText = "Credential active"
            statusColor = MaterialTheme.colorScheme.primary
            statusIcon = Icons.Default.CheckCircle
        }
        BedrockKeyStatus.InvalidOrBlocked -> {
            statusText = "Invalid, expired, or blocked"
            statusColor = MaterialTheme.colorScheme.error
            statusIcon = Icons.Default.Error
        }
        BedrockKeyStatus.UnableToVerify -> {
            statusText = "Unable to verify"
            statusColor = MaterialTheme.colorScheme.tertiary
            statusIcon = Icons.Default.Help
        }
        null -> {
            statusText = "Not checked"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant
            statusIcon = Icons.Default.Help
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
