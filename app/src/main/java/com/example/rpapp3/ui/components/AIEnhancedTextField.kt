package com.example.rpapp3.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.AITextFieldService
import com.example.rpapp3.data.CharacterFieldType
import com.example.rpapp3.data.ElaborationResult
import kotlinx.coroutines.launch

/**
 * An OutlinedTextField with an AI button that allows users to generate content
 * from a prompt using Gemini. For multi-line fields, expands when focused.
 */
@Composable
fun AIEnhancedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    fieldType: CharacterFieldType,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    isFormLoading: Boolean = false,
    expandedHeight: Dp = 300.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showAIDialog by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFocused by remember { mutableStateOf(false) }
    
    val aiService = remember { AITextFieldService(context) }
    
    // Default collapsed height for multi-line fields
    val collapsedHeight = 100.dp
    
    // Animated height for expandable text fields (only for multi-line fields)
    // Note: We use a fixed collapsed height instead of Dp.Unspecified because Dp.Unspecified cannot be animated
    val animatedHeight by animateDpAsState(
        targetValue = if (!singleLine && isFocused) expandedHeight else collapsedHeight,
        animationSpec = tween(durationMillis = 300),
        label = "textFieldHeight"
    )
    
    // Determine height modifier - only apply animation to multi-line fields
    val heightModifier = if (!singleLine) {
        Modifier.height(animatedHeight)
    } else {
        Modifier
    }
    
    Column(modifier = modifier.then(heightModifier)) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = label,
                placeholder = placeholder,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { isFocused = it.isFocused },
                singleLine = singleLine,
                enabled = enabled && !isFormLoading && !isGenerating,
                trailingIcon = {
                    IconButton(
                        onClick = { showAIDialog = true },
                        enabled = enabled && !isFormLoading && !isGenerating
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Generate",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    }
    
    // Error Dialog - shows full error message that can be copied
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("AI Generation Error") },
            text = {
                Column {
                    Text(
                        text = "An error occurred while generating content:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        SelectionContainer {
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    // AI Prompt Dialog
    if (showAIDialog) {
        AIPromptDialog(
            fieldType = fieldType,
            onDismiss = { showAIDialog = false },
            onGenerate = { prompt ->
                showAIDialog = false
                isGenerating = true
                errorMessage = null
                
                scope.launch {
                    when (val result = aiService.elaboratePrompt(prompt, fieldType)) {
                        is ElaborationResult.Success -> {
                            onValueChange(result.text)
                        }
                        is ElaborationResult.Error -> {
                            errorMessage = result.message
                        }
                    }
                    isGenerating = false
                }
            }
        )
    }
}

@Composable
private fun AIPromptDialog(
    fieldType: CharacterFieldType,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    
    val fieldName = when (fieldType) {
        CharacterFieldType.NAME -> "Name"
        CharacterFieldType.DESCRIPTION -> "Description"
        CharacterFieldType.APPEARANCE -> "Appearance"
        CharacterFieldType.PERSONALITY -> "Personality"
        CharacterFieldType.SYSTEM_INSTRUCTIONS -> "AI Instructions"
    }
    
    val hintText = when (fieldType) {
        CharacterFieldType.NAME -> "e.g., mysterious elven mage, grumpy dwarf blacksmith"
        CharacterFieldType.DESCRIPTION -> "e.g., orphan raised by wolves, exiled noble seeking revenge"
        CharacterFieldType.APPEARANCE -> "e.g., tall with silver hair, covered in battle scars"
        CharacterFieldType.PERSONALITY -> "e.g., stoic but secretly caring, mischievous trickster"
        CharacterFieldType.SYSTEM_INSTRUCTIONS -> "e.g., speaks in riddles, always formal and polite"
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate $fieldName") },
        text = {
            Column {
                Text(
                    text = "Enter a short prompt and AI will elaborate it:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    placeholder = { Text(hintText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onGenerate(prompt) },
                enabled = prompt.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
