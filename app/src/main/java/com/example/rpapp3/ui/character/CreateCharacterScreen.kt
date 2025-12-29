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
fun CreateCharacterScreen(
    worldId: String,
    viewModel: CharacterViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onCharacterCreated: () -> Unit,
    onAIGenerateClick: () -> Unit = {}
) {
    val context = LocalContext.current
    
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var appearance by remember { mutableStateOf("") }
    var personality by remember { mutableStateOf("") }
    var systemInstructions by remember { mutableStateOf("") }
    var profilePictureUri by remember { mutableStateOf<Uri?>(null) }
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var videoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
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
            profilePictureUri = it
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
        photoUris = photoUris + uris
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
        videoUris = videoUris + uris
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
                title = { Text("Create Character") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onAIGenerateClick,
                        enabled = !viewModel.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Generate",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "AI",
                            color = MaterialTheme.colorScheme.onPrimaryContainer
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
                    if (profilePictureUri != null) {
                        AsyncImage(
                            model = profilePictureUri,
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
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
                
                if (profilePictureUri != null) {
                    TextButton(
                        onClick = { profilePictureUri = null },
                        enabled = !viewModel.isLoading
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove")
                    }
                }
            }
            
            HorizontalDivider()
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Character Name *") },
                placeholder = { Text("e.g., Luna the Enchantress") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Background/Description") },
                placeholder = { Text("Character's backstory, history, and role in the world...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = appearance,
                onValueChange = { appearance = it },
                label = { Text("Appearance") },
                placeholder = { Text("Physical description, clothing, distinctive features...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text("Personality") },
                placeholder = { Text("Traits, mannerisms, speech patterns, quirks...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                enabled = !viewModel.isLoading
            )
            
            OutlinedTextField(
                value = systemInstructions,
                onValueChange = { systemInstructions = it },
                label = { Text("Character-Specific AI Instructions") },
                placeholder = { Text("Special instructions for how the AI should portray this character...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                enabled = !viewModel.isLoading
            )
            
            // Photos section
            Text(
                text = "Photos",
                style = MaterialTheme.typography.titleMedium
            )
            
            MediaPreviewRow(
                uris = photoUris,
                onAddClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onRemoveClick = { uri ->
                    photoUris = photoUris.filter { it != uri }
                },
                isVideo = false,
                enabled = !viewModel.isLoading
            )
            
            // Videos section
            Text(
                text = "Videos",
                style = MaterialTheme.typography.titleMedium
            )
            
            MediaPreviewRow(
                uris = videoUris,
                onAddClick = {
                    videoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                },
                onRemoveClick = { uri ->
                    videoUris = videoUris.filter { it != uri }
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
                    viewModel.createCharacter(
                        context = context,
                        worldId = worldId,
                        name = name,
                        description = description,
                        appearance = appearance,
                        personality = personality,
                        systemInstructions = systemInstructions,
                        profilePictureUri = profilePictureUri,
                        photoUris = photoUris,
                        videoUris = videoUris,
                        onSuccess = onCharacterCreated,
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating...")
                } else {
                    Text("Create Character")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun MediaPreviewRow(
    uris: List<Uri>,
    onAddClick: () -> Unit,
    onRemoveClick: (Uri) -> Unit,
    isVideo: Boolean,
    enabled: Boolean
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        // Add button
        item {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = enabled, onClick = onAddClick),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (isVideo) Icons.Default.VideoCall else Icons.Default.AddPhotoAlternate,
                        contentDescription = "Add",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isVideo) "Add Video" else "Add Photo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        
        // Preview items
        items(uris) { uri ->
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
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Remove button
                IconButton(
                    onClick = { onRemoveClick(uri) },
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
