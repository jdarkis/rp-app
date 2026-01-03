package com.example.rpapp3.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.model.CharacterUpdateProposal
import com.example.rpapp3.data.model.SummaryProposal
import com.example.rpapp3.data.model.WorldUpdateProposal

/**
 * Tracks which updates the user has selected to apply
 */
data class SelectedUpdates(
    val characterBackgrounds: MutableMap<String, Boolean> = mutableMapOf(),
    val characterAppearances: MutableMap<String, Boolean> = mutableMapOf(),
    val characterPersonalities: MutableMap<String, Boolean> = mutableMapOf(),
    val worldDescription: Boolean = false
)

/**
 * Dialog for reviewing story summary and proposed character/world updates.
 * Shows side-by-side comparison of old vs new values with checkboxes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryReviewDialog(
    proposal: SummaryProposal,
    onApply: (SelectedUpdates) -> Unit,
    onDismiss: () -> Unit
) {
    // Track selected updates
    var selectedUpdates by remember { 
        mutableStateOf(SelectedUpdates().apply {
            // Pre-select all changes by default
            proposal.characterUpdates.forEach { char ->
                if (char.backgroundNew != null) characterBackgrounds[char.characterId] = true
                if (char.appearanceNew != null) characterAppearances[char.characterId] = true
                if (char.personalityNew != null) characterPersonalities[char.characterId] = true
            }
            if (proposal.worldUpdateProposal?.descriptionNew != null) {
                // Use reflection-like approach for mutable copy
            }
        })
    }
    
    var worldDescriptionSelected by remember { 
        mutableStateOf(proposal.worldUpdateProposal?.descriptionNew != null) 
    }
    
    // Track expanded sections
    var summaryExpanded by remember { mutableStateOf(true) }
    val expandedCharacters = remember { mutableStateMapOf<String, Boolean>() }
    var worldExpanded by remember { mutableStateOf(true) }
    
    val hasAnyChanges = proposal.characterUpdates.any { it.hasChanges() } || 
            (proposal.worldUpdateProposal?.hasChanges() == true)
    
    BasicAlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                TopAppBar(
                    title = { Text("Story Summary") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                )
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Story Summary Section
                    SummarySection(
                        title = "📖 Story Summary",
                        expanded = summaryExpanded,
                        onToggle = { summaryExpanded = !summaryExpanded }
                    ) {
                        Text(
                            text = proposal.storySummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    HorizontalDivider()
                    
                    // Proposed Updates Header
                    if (hasAnyChanges) {
                        Text(
                            text = "Proposed Updates",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "Select the changes you want to apply:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Character Updates
                    proposal.characterUpdates.filter { it.hasChanges() }.forEach { charUpdate ->
                        val isExpanded = expandedCharacters[charUpdate.characterId] ?: true
                        
                        CharacterUpdateCard(
                            update = charUpdate,
                            expanded = isExpanded,
                            onToggle = { expandedCharacters[charUpdate.characterId] = !isExpanded },
                            backgroundSelected = selectedUpdates.characterBackgrounds[charUpdate.characterId] ?: false,
                            onBackgroundChange = { 
                                selectedUpdates = selectedUpdates.copy(
                                    characterBackgrounds = selectedUpdates.characterBackgrounds.toMutableMap().apply {
                                        put(charUpdate.characterId, it)
                                    }
                                )
                            },
                            appearanceSelected = selectedUpdates.characterAppearances[charUpdate.characterId] ?: false,
                            onAppearanceChange = {
                                selectedUpdates = selectedUpdates.copy(
                                    characterAppearances = selectedUpdates.characterAppearances.toMutableMap().apply {
                                        put(charUpdate.characterId, it)
                                    }
                                )
                            },
                            personalitySelected = selectedUpdates.characterPersonalities[charUpdate.characterId] ?: false,
                            onPersonalityChange = {
                                selectedUpdates = selectedUpdates.copy(
                                    characterPersonalities = selectedUpdates.characterPersonalities.toMutableMap().apply {
                                        put(charUpdate.characterId, it)
                                    }
                                )
                            }
                        )
                    }
                    
                    // World Update
                    proposal.worldUpdateProposal?.takeIf { it.hasChanges() }?.let { worldUpdate ->
                        WorldUpdateCard(
                            update = worldUpdate,
                            expanded = worldExpanded,
                            onToggle = { worldExpanded = !worldExpanded },
                            selected = worldDescriptionSelected,
                            onSelectedChange = { worldDescriptionSelected = it }
                        )
                    }
                    
                    // No changes message
                    if (!hasAnyChanges) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = "No significant changes detected in the story that would require updating character or world descriptions.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Bottom Actions
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dismiss")
                        }
                        
                        if (hasAnyChanges) {
                            Button(
                                onClick = {
                                    onApply(selectedUpdates.copy(
                                        worldDescription = worldDescriptionSelected
                                    ))
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Apply Selected")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun CharacterUpdateCard(
    update: CharacterUpdateProposal,
    expanded: Boolean,
    onToggle: () -> Unit,
    backgroundSelected: Boolean,
    onBackgroundChange: (Boolean) -> Unit,
    appearanceSelected: Boolean,
    onAppearanceChange: (Boolean) -> Unit,
    personalitySelected: Boolean,
    onPersonalityChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = update.characterName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Background comparison
                    update.backgroundNew?.let { newBg ->
                        ComparisonField(
                            label = "Background",
                            oldValue = update.backgroundOld,
                            newValue = newBg,
                            selected = backgroundSelected,
                            onSelectedChange = onBackgroundChange
                        )
                    }
                    
                    // Appearance comparison
                    update.appearanceNew?.let { newApp ->
                        ComparisonField(
                            label = "Appearance",
                            oldValue = update.appearanceOld,
                            newValue = newApp,
                            selected = appearanceSelected,
                            onSelectedChange = onAppearanceChange
                        )
                    }
                    
                    // Personality comparison
                    update.personalityNew?.let { newPers ->
                        ComparisonField(
                            label = "Personality",
                            oldValue = update.personalityOld,
                            newValue = newPers,
                            selected = personalitySelected,
                            onSelectedChange = onPersonalityChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldUpdateCard(
    update: WorldUpdateProposal,
    expanded: Boolean,
    onToggle: () -> Unit,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = update.worldName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            
            if (expanded) {
                update.descriptionNew?.let { newDesc ->
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        ComparisonField(
                            label = "Description",
                            oldValue = update.descriptionOld,
                            newValue = newDesc,
                            selected = selected,
                            onSelectedChange = onSelectedChange
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonField(
    label: String,
    oldValue: String,
    newValue: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    // Track expanded state for each panel
    var oldExpanded by remember { mutableStateOf(false) }
    var newExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Apply",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectedChange
                )
            }
        }
        
        // Side by side comparison
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Old value - clickable to expand
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { oldExpanded = !oldExpanded },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Current",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            if (oldExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (oldExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = oldValue.ifBlank { "[empty]" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = if (oldExpanded) Int.MAX_VALUE else 6,
                        overflow = if (oldExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
                    )
                }
            }
            
            // New value - clickable to expand
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { newExpanded = !newExpanded },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Proposed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            if (newExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (newExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = newValue,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        maxLines = if (newExpanded) Int.MAX_VALUE else 6,
                        overflow = if (newExpanded) TextOverflow.Visible else TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

