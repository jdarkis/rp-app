package com.example.rpapp3.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToApiKeys: () -> Unit,
    onNavigateToBedrockApiKey: () -> Unit,
    onNavigateToElevenLabsApiKeys: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToSystemPrompt: () -> Unit,
    onNavigateToUnlockPrompt: () -> Unit,
    onNavigateToTtsVoices: () -> Unit,
    onNavigateToInworldApiKeys: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SettingsCategoryItem(
                    icon = Icons.Default.Palette,
                    title = "Appearance",
                    subtitle = "Theme and visual customization",
                    onClick = onNavigateToAppearance
                )
                
                SettingsCategoryItem(
                    icon = Icons.Default.Code,
                    title = "System Prompt",
                    subtitle = "Edit the global roleplay instruction",
                    onClick = onNavigateToSystemPrompt
                )

                SettingsCategoryItem(
                    icon = Icons.Default.LockOpen,
                    title = "Unlock Prompt",
                    subtitle = "Custom prompt added to chat system instructions",
                    onClick = onNavigateToUnlockPrompt
                )
                
                SettingsCategoryItem(
                    icon = Icons.Default.Mic,
                    title = "TTS Voices",
                    subtitle = "Manage ElevenLabs, Inworld, and Gemini voices",
                    onClick = onNavigateToTtsVoices
                )
                
                SettingsCategoryItem(
                    icon = Icons.Default.Key,
                    title = "Gemini API Keys",
                    subtitle = "Manage your Gemini API keys",
                    onClick = onNavigateToApiKeys
                )

                SettingsCategoryItem(
                    icon = Icons.Default.Key,
                    title = "Amazon Bedrock API Key",
                    subtitle = "Manage the key for Claude Opus 4.6",
                    onClick = onNavigateToBedrockApiKey
                )
                
                SettingsCategoryItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "ElevenLabs API Keys",
                    subtitle = "Manage your ElevenLabs API keys for TTS",
                    onClick = onNavigateToElevenLabsApiKeys
                )
                
                SettingsCategoryItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Inworld API Keys",
                    subtitle = "Manage Inworld Studio API keys",
                    onClick = onNavigateToInworldApiKeys
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
