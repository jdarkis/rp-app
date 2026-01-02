package com.example.rpapp3.ui.character

import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.rpapp3.viewmodel.CharacterViewModel
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterDetailScreen(
    characterId: String,
    viewModel: CharacterViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onEditCharacter: () -> Unit,
    onPrivateChat: () -> Unit = {}  // Navigate to private chat with this character
) {
    val character by viewModel.currentCharacter.collectAsState()
    
    // State for expanded media
    var expandedGalleryState by remember { mutableStateOf<GalleryState?>(null) }
    var expandedVideoUrl by remember { mutableStateOf<String?>(null) }
    
    // Clipboard and snackbar for copy functionality
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(characterId) {
        viewModel.loadCharacter(characterId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(character?.name ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditCharacter) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onPrivateChat,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = "Private Chat"
                )
            }
        }
    ) { paddingValues ->
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            character?.let { char ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Character header with avatar (using profile picture or first photo)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val avatarUrl = char.profilePictureUrl ?: char.photoUrls.firstOrNull()
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = char.name,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable { 
                                        // If clicking avatar, show just the avatar or setup a custom gallery logic
                                        // For simplicity here, we treat it as a single image gallery unless it matches a photo
                                        val index = char.photoUrls.indexOf(avatarUrl)
                                        if (index >= 0) {
                                            expandedGalleryState = GalleryState(char.photoUrls, index)
                                        } else {
                                            expandedGalleryState = GalleryState(listOf(avatarUrl), 0)
                                        }
                                    },
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = char.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        
                        Column {
                            Text(
                                text = char.name,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }
                    
                    HorizontalDivider()
                    
                    // Description
                    if (char.description.isNotBlank()) {
                        InfoSection(
                            title = "Background",
                            content = char.description,
                            icon = Icons.Default.Book,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(char.description))
                                scope.launch { snackbarHostState.showSnackbar("Background copied") }
                            }
                        )
                    }
                    
                    // Appearance
                    if (char.appearance.isNotBlank()) {
                        InfoSection(
                            title = "Appearance",
                            content = char.appearance,
                            icon = Icons.Default.Face,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(char.appearance))
                                scope.launch { snackbarHostState.showSnackbar("Appearance copied") }
                            }
                        )
                    }
                    
                    // Personality
                    if (char.personality.isNotBlank()) {
                        InfoSection(
                            title = "Personality",
                            content = char.personality,
                            icon = Icons.Default.Psychology,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(char.personality))
                                scope.launch { snackbarHostState.showSnackbar("Personality copied") }
                            }
                        )
                    }
                    
                    // AI Instructions
                    if (char.systemInstructions.isNotBlank()) {
                        InfoSection(
                            title = "AI Instructions",
                            content = char.systemInstructions,
                            icon = Icons.Default.SmartToy,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(char.systemInstructions))
                                scope.launch { snackbarHostState.showSnackbar("AI Instructions copied") }
                            }
                        )
                    }
                    
                    // Photos gallery
                    if (char.photoUrls.isNotEmpty()) {
                        Text(
                            text = "Photos (${char.photoUrls.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(char.photoUrls.size) { index ->
                                    val url = char.photoUrls[index]
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { expandedGalleryState = GalleryState(char.photoUrls, index) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                    
                    // NSFW Photos gallery
                    if (char.nsfwPhotoUrls.isNotEmpty()) {
                        Text(
                            text = "NSFW Photos (${char.nsfwPhotoUrls.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(char.nsfwPhotoUrls.size) { index ->
                                    val url = char.nsfwPhotoUrls[index]
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { expandedGalleryState = GalleryState(char.nsfwPhotoUrls, index) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                    
                    // Spicy NSFW Photos gallery
                    if (char.spicyNsfwPhotoUrls.isNotEmpty()) {
                        Text(
                            text = "Spicy NSFW Photos (${char.spicyNsfwPhotoUrls.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(char.spicyNsfwPhotoUrls.size) { index ->
                                    val url = char.spicyNsfwPhotoUrls[index]
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { expandedGalleryState = GalleryState(char.spicyNsfwPhotoUrls, index) },
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                    }
                    
                    // Videos gallery
                    if (char.videoUrls.isNotEmpty()) {
                        Text(
                            text = "Videos (${char.videoUrls.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(char.videoUrls) { url ->
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { expandedVideoUrl = url },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Video Thumbnail
                                    AsyncImage(
                                        model = url,
                                        contentDescription = "Video Thumbnail",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    // Play Icon Overlay
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
    
    // Expanded image dialog with pager
    expandedGalleryState?.let { state ->
        ExpandedImageDialog(
            images = state.images,
            initialIndex = state.initialIndex,
            onDismiss = { expandedGalleryState = null }
        )
    }
    
    // Expanded video dialog
    expandedVideoUrl?.let { url ->
        ExpandedVideoDialog(
            videoUrl = url,
            onDismiss = { expandedVideoUrl = null }
        )
    }
}

data class GalleryState(
    val images: List<String>,
    val initialIndex: Int
)

@Composable
private fun ExpandedImageDialog(
    images: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                initialPage = initialIndex,
                pageCount = { images.size }
            )
            
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 16.dp
            ) { page ->
                val zoomableState = rememberZoomableState()
                ZoomableAsyncImage(
                    model = images[page],
                    contentDescription = "Expanded image",
                    modifier = Modifier.fillMaxSize(),
                    state = rememberZoomableImageState(zoomableState),
                    onClick = { onDismiss() }
                )
            }
            
            // Close button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            // Page indicator (optional but helpful)
            if (images.size > 1) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .statusBarsPadding()
                )
            }
        }
    }
}

@Composable
private fun ExpandedVideoDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // Create ExoPlayer
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            prepare()
            playWhenReady = true
        }
    }
    
    // Dispose player when dialog is closed
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Video with Pinch to Zoom
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale *= zoom
                            scale = scale.coerceIn(1f, 4f) // Max 4x zoom
                            
                            // Adjust offset only when zoomed in
                            if (scale > 1f) {
                                val maxOffsetX = (size.width * (scale - 1)) / 2
                                val maxOffsetY = (size.height * (scale - 1)) / 2
                                
                                val newX = offset.x + pan.x
                                val newY = offset.y + pan.y
                                
                                offset = Offset(
                                    x = newX.coerceIn(-maxOffsetX, maxOffsetX),
                                    y = newY.coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            } else {
                                offset = Offset.Zero
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                            // Hide controller when using zoom gestures? Not necessary, but maybe good UX.
                            // For now keep standard controls.
                            useController = true 
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Close button (ensure it's on top of everything)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    content: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCopy: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
