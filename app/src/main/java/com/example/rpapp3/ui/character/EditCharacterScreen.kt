package com.example.rpapp3.ui.character

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.rpapp3.viewmodel.CharacterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCharacterScreen(
    characterId: String,
    viewModel: CharacterViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onCharacterUpdated: () -> Unit,
    onCharacterDeleted: () -> Unit
) {
    val context = LocalContext.current
    val character by viewModel.currentCharacter.collectAsState()
    
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var appearance by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var systemInstructions by remember { mutableStateOf("") }
    var newProfilePictureUri by remember { mutableStateOf<Uri?>(null) }
    var currentProfilePictureUrl by remember { mutableStateOf<String?>(null) }
    var newPhotoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var newVideoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSelectFromPhotosDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Profile picture picker
    val profilePicturePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Some URIs don't support persistable permissions
            }
            newProfilePictureUri = it
            currentProfilePictureUrl = null // Clear existing URL when new one is selected
        }
    }
    
    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Some URIs don't support persistable permissions, which is fine
            }
        }
        newPhotoUris = newPhotoUris + uris
    }
    
    // Video picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                // Some URIs don't support persistable permissions, which is fine
            }
        }
        newVideoUris = newVideoUris + uris
    }
    
    // Load character data
    LaunchedEffect(characterId) {
        viewModel.loadCharacter(characterId)
    }
    
    // Update form when character is loaded
    LaunchedEffect(character) {
        character?.let {
            name = it.name
            description = it.description
            appearance = it.appearance
            personality = it.personality
            systemInstructions = it.systemInstructions
            currentProfilePictureUrl = it.profilePictureUrl
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
                title = { Text("Edit Character") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
            // Profile Picture section
            Text(
                text = "Profile Picture",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(enabled = !viewModel.isLoading) {
                            profilePicturePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        newProfilePictureUri != null -> {
                            AsyncImage(
                                model = newProfilePictureUri,
                                contentDescription = "Profile picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        currentProfilePictureUrl != null -> {
                            AsyncImage(
                                model = currentProfilePictureUrl,
                                contentDescription = "Profile picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Add profile picture",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Add",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                
                Column {
                    if (newProfilePictureUri != null || currentProfilePictureUrl != null) {
                        TextButton(
                            onClick = { 
                                newProfilePictureUri = null
                                currentProfilePictureUrl = null
                            },
                            enabled = !viewModel.isLoading
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                    
                    character?.let { char ->
                        if (char.photoUrls.isNotEmpty()) {
                            TextButton(
                                onClick = { showSelectFromPhotosDialog = true },
                                enabled = !viewModel.isLoading
                            ) {
                                Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("From Photos")
                            }
                        }
                    }
                }
            }
            
            HorizontalDivider()
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Character Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Background/Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = appearance,
                onValueChange = { appearance = it },
                label = { Text("Appearance") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text("Personality") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = systemInstructions,
                onValueChange = { systemInstructions = it },
                label = { Text("AI Instructions") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                enabled = !viewModel.isLoading
            )
            
            // Existing photos
            character?.let { char ->
                if (char.photoUrls.isNotEmpty()) {
                    Text(
                        text = "Current Photos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    ExistingMediaRow(
                        urls = char.photoUrls,
                        isVideo = false,
                        onRemoveClick = { url ->
                            viewModel.removePhoto(char, url) {}
                        },
                        enabled = !viewModel.isLoading
                    )
                }
            }
            
            // Add new photos
            Text(
                text = "Add New Photos",
                style = MaterialTheme.typography.titleMedium
            )
            MediaPreviewRow(
                uris = newPhotoUris,
                onAddClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveClick = { uri ->
                    newPhotoUris = newPhotoUris.filter { it != uri }
                },
                isVideo = false,
                enabled = !viewModel.isLoading
            )
            
            // Existing videos
            character?.let { char ->
                if (char.videoUrls.isNotEmpty()) {
                    Text(
                        text = "Current Videos",
                        style = MaterialTheme.typography.titleMedium
                    )
                    ExistingMediaRow(
                        urls = char.videoUrls,
                        isVideo = true,
                        onRemoveClick = { url ->
                            viewModel.removeVideo(char, url) {}
                        },
                        enabled = !viewModel.isLoading
                    )
                }
            }
            
            // Add new videos
            Text(
                text = "Add New Videos",
                style = MaterialTheme.typography.titleMedium
            )
            MediaPreviewRow(
                uris = newVideoUris,
                onAddClick = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onRemoveClick = { uri ->
                    newVideoUris = newVideoUris.filter { it != uri }
                },
                isVideo = true,
                enabled = !viewModel.isLoading
            )
            
            // Upload progress
            viewModel.uploadProgress?.let { progress ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = progress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    character?.let { char ->
                        viewModel.updateCharacter(
                            context = context,
                            character = char.copy(
                                name = name,
                                description = description,
                                appearance = appearance,
                                personality = personality,
                                systemInstructions = systemInstructions,
                                profilePictureUrl = currentProfilePictureUrl
                            ),
                            newProfilePictureUri = newProfilePictureUri,
                            newPhotoUris = newPhotoUris,
                            newVideoUris = newVideoUris,
                            onSuccess = onCharacterUpdated,
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save Changes")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Character?") },
            text = { 
                Text("This will permanently delete this character and all associated photos and videos. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteCharacter(
                            characterId = characterId,
                            onSuccess = onCharacterDeleted,
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
    
    // Select from existing photos dialog
    if (showSelectFromPhotosDialog) {
        character?.let { char ->
            AlertDialog(
                onDismissRequest = { showSelectFromPhotosDialog = false },
                title = { Text("Select Profile Picture") },
                text = {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(char.photoUrls) { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        currentProfilePictureUrl = url
                                        newProfilePictureUri = null
                                        showSelectFromPhotosDialog = false
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSelectFromPhotosDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ExistingMediaRow(
    urls: List<String>,
    isVideo: Boolean,
    onRemoveClick: (String) -> Unit,
    enabled: Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(urls) { url ->
            Box(
                modifier = Modifier.size(100.dp)
            ) {
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Remove button
                IconButton(
                    onClick = { onRemoveClick(url) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp),
                    enabled = enabled
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.error
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove",
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp),
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }
        }
    }
}
