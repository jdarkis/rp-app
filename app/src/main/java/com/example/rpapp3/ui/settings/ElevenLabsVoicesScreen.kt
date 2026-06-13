package com.example.rpapp3.ui.settings

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.ElevenLabsCatalogSource
import com.example.rpapp3.data.ElevenLabsCatalogVoice
import com.example.rpapp3.data.ElevenLabsService
import com.example.rpapp3.data.isFreeApiCompatibleElevenLabsVoice
import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import com.example.rpapp3.data.repository.VoiceRepository
import com.example.rpapp3.data.selectableElevenLabsVoices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VoiceCatalogTab {
    AVAILABLE,
    ACTIVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevenLabsVoicesScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceRepository = remember { VoiceRepository() }
    val elevenLabsService = remember { ElevenLabsService.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val availableListState = rememberLazyListState()
    val activeListState = rememberLazyListState()

    val savedVoicesFlow = remember(voiceRepository) { voiceRepository.getCustomVoices() }
    val savedVoices by savedVoicesFlow.collectAsState(initial = emptyList())
    val savedElevenLabsVoices = remember(savedVoices) {
        savedVoices.filter { it.source == VoiceSource.ELEVEN_LABS }
    }
    val assignableVoices = remember(savedVoices) { selectableElevenLabsVoices(savedVoices) }
    val activeVoiceIds = remember(savedElevenLabsVoices) {
        savedElevenLabsVoices.mapTo(hashSetOf()) { it.voiceId }
    }

    var selectedTab by remember { mutableStateOf(VoiceCatalogTab.AVAILABLE) }
    var searchInput by remember { mutableStateOf("") }
    var catalogSearch by remember { mutableStateOf("") }
    var catalogVoices by remember { mutableStateOf<List<ElevenLabsCatalogVoice>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(-1) }
    var hasMore by remember { mutableStateOf(true) }
    var isInitialLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var refreshGeneration by remember { mutableIntStateOf(0) }
    var updatingVoiceIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentlyPlayingId by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    fun stopPreview() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentlyPlayingId = null
    }

    fun togglePreview(voiceId: String, previewUrl: String?) {
        if (currentlyPlayingId == voiceId) {
            stopPreview()
            return
        }
        if (previewUrl.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("No preview is available for this voice") }
            return
        }

        try {
            stopPreview()
            currentlyPlayingId = voiceId
            val player = MediaPlayer()
            mediaPlayer = player
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                it.release()
                if (mediaPlayer === it) {
                    mediaPlayer = null
                    currentlyPlayingId = null
                }
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                failedPlayer.release()
                if (mediaPlayer === failedPlayer) {
                    mediaPlayer = null
                    currentlyPlayingId = null
                }
                scope.launch { snackbarHostState.showSnackbar("Failed to play voice preview") }
                true
            }
            player.setDataSource(previewUrl)
            player.prepareAsync()
        } catch (_: Exception) {
            stopPreview()
            scope.launch { snackbarHostState.showSnackbar("Failed to play voice preview") }
        }
    }

    suspend fun loadCatalog(reset: Boolean) {
        if (!reset && (!hasMore || isInitialLoading || isLoadingMore)) return

        val requestedPage = if (reset) 0 else currentPage + 1
        if (reset) {
            isInitialLoading = true
        } else {
            isLoadingMore = true
        }

        elevenLabsService.listFreeTierVoices(
            page = requestedPage,
            search = catalogSearch
        ).onSuccess { page ->
            catalogVoices = if (reset) {
                page.voices
            } else {
                (catalogVoices + page.voices).distinctBy { it.voiceId }
            }
            currentPage = page.page
            hasMore = page.hasMore
            catalogError = null
            if (reset) availableListState.scrollToItem(0)
        }.onFailure { error ->
            catalogError = error.message ?: "Unable to load the ElevenLabs voice catalog"
        }

        isInitialLoading = false
        isLoadingMore = false
    }

    fun setCatalogVoiceActive(voice: ElevenLabsCatalogVoice, active: Boolean) {
        scope.launch {
            updatingVoiceIds = updatingVoiceIds + voice.voiceId
            voiceRepository.setVoiceActive(voice.toVoice(), active)
                .onSuccess {
                    snackbarHostState.showSnackbar(
                        if (active) "${voice.name} activated" else "${voice.name} deactivated"
                    )
                }
                .onFailure {
                    snackbarHostState.showSnackbar("Could not update ${voice.name}")
                }
            updatingVoiceIds = updatingVoiceIds - voice.voiceId
        }
    }

    fun setSavedVoiceActive(voice: Voice, active: Boolean) {
        scope.launch {
            updatingVoiceIds = updatingVoiceIds + voice.voiceId
            voiceRepository.setVoiceActive(voice, active)
                .onSuccess {
                    snackbarHostState.showSnackbar(
                        if (active) "${voice.name} activated" else "${voice.name} deactivated"
                    )
                }
                .onFailure {
                    snackbarHostState.showSnackbar("Could not update ${voice.name}")
                }
            updatingVoiceIds = updatingVoiceIds - voice.voiceId
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(searchInput) {
        delay(350)
        catalogSearch = searchInput.trim()
    }

    LaunchedEffect(catalogSearch, refreshGeneration) {
        loadCatalog(reset = true)
    }

    LaunchedEffect(
        availableListState,
        selectedTab,
        catalogSearch,
        catalogVoices.size,
        hasMore
    ) {
        snapshotFlow {
            availableListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisibleIndex ->
            if (
                selectedTab == VoiceCatalogTab.AVAILABLE &&
                catalogVoices.isNotEmpty() &&
                lastVisibleIndex >= catalogVoices.lastIndex - 4
            ) {
                loadCatalog(reset = false)
            }
        }
    }

    val filteredActiveVoices = remember(savedElevenLabsVoices, searchInput) {
        val query = searchInput.trim()
        if (query.isEmpty()) {
            savedElevenLabsVoices
        } else {
            savedElevenLabsVoices.filter { voice ->
                voice.name.contains(query, ignoreCase = true) ||
                    voice.voiceId.contains(query, ignoreCase = true) ||
                    voice.labels.values.any { it.contains(query, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ElevenLabs Voices") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshGeneration++ },
                        enabled = !isInitialLoading && !isLoadingMore
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh voices")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            CatalogInfoCard(
                assignableCount = assignableVoices.size,
                savedCount = savedElevenLabsVoices.size
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search voices") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchInput.isNotEmpty()) {
                        IconButton(onClick = { searchInput = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            TabRow(selectedTabIndex = selectedTab.ordinal) {
                Tab(
                    selected = selectedTab == VoiceCatalogTab.AVAILABLE,
                    onClick = { selectedTab = VoiceCatalogTab.AVAILABLE },
                    text = { Text("Available") }
                )
                Tab(
                    selected = selectedTab == VoiceCatalogTab.ACTIVE,
                    onClick = { selectedTab = VoiceCatalogTab.ACTIVE },
                    text = { Text("Active (${savedElevenLabsVoices.size})") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                VoiceCatalogTab.AVAILABLE -> {
                    if (isInitialLoading && catalogVoices.isEmpty()) {
                        LoadingContent()
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = availableListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            catalogError?.let { message ->
                                item(key = "catalog-error") {
                                    CatalogErrorCard(
                                        message = message,
                                        onRetry = { refreshGeneration++ }
                                    )
                                }
                            }
                            if (catalogVoices.isEmpty() && catalogError == null) {
                                item(key = "empty-catalog") {
                                    EmptyContent("No free-tier voices match this search.")
                                }
                            }
                            items(catalogVoices, key = { it.voiceId }) { voice ->
                                CatalogVoiceCard(
                                    voice = voice,
                                    isActive = voice.voiceId in activeVoiceIds,
                                    isUpdating = voice.voiceId in updatingVoiceIds,
                                    isPlaying = currentlyPlayingId == voice.voiceId,
                                    onPreview = {
                                        togglePreview(voice.voiceId, voice.previewUrl)
                                    },
                                    onActiveChange = { active ->
                                        setCatalogVoiceActive(voice, active)
                                    }
                                )
                            }
                            if (isLoadingMore) {
                                item(key = "loading-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                }
                            }
                            item(key = "available-bottom-space") {
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }
                }

                VoiceCatalogTab.ACTIVE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = activeListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (filteredActiveVoices.isEmpty()) {
                            item(key = "empty-active") {
                                EmptyContent(
                                    if (savedElevenLabsVoices.isEmpty()) {
                                        "No voices are active. Enable voices from the Available tab."
                                    } else {
                                        "No active voices match this search."
                                    }
                                )
                            }
                        }
                        items(filteredActiveVoices, key = { it.voiceId }) { voice ->
                            ActiveVoiceCard(
                                voice = voice,
                                isUpdating = voice.voiceId in updatingVoiceIds,
                                isPlaying = currentlyPlayingId == voice.voiceId,
                                onPreview = {
                                    togglePreview(voice.voiceId, voice.previewUrl)
                                },
                                onDeactivate = {
                                    setSavedVoiceActive(voice, active = false)
                                }
                            )
                        }
                        item(key = "active-bottom-space") {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogInfoCard(
    assignableCount: Int,
    savedCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "$assignableCount assignable free API voice${if (assignableCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "The catalog contains only ElevenLabs Default voices available to the current API key. " +
                        "$savedCount saved voice${if (savedCount == 1) "" else "s"} remain visible; " +
                        "Voice Library entries require a paid API plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CatalogVoiceCard(
    voice: ElevenLabsCatalogVoice,
    isActive: Boolean,
    isUpdating: Boolean,
    isPlaying: Boolean,
    onPreview: () -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
    VoiceCardLayout(
        name = voice.name,
        voiceId = voice.voiceId,
        description = voice.description,
        metadata = listOfNotNull(
            voice.language,
            voice.gender,
            voice.accent,
            voice.category
        ).distinct().joinToString(" | "),
        sourceLabel = "Default",
        eligibilityLabel = "Free API",
        hasPreview = !voice.previewUrl.isNullOrBlank(),
        isPlaying = isPlaying,
        isUpdating = isUpdating,
        isActive = isActive,
        onPreview = onPreview,
        onActiveChange = onActiveChange
    )
}

@Composable
private fun ActiveVoiceCard(
    voice: Voice,
    isUpdating: Boolean,
    isPlaying: Boolean,
    onPreview: () -> Unit,
    onDeactivate: () -> Unit
) {
    val sourceLabel = when (voice.labels["catalog_source"]) {
        ElevenLabsCatalogSource.DEFAULT.name -> "Default"
        ElevenLabsCatalogSource.SHARED.name -> "Voice Library"
        else -> "Saved voice"
    }
    val isFreeApiCompatible = isFreeApiCompatibleElevenLabsVoice(voice)
    VoiceCardLayout(
        name = voice.name,
        voiceId = voice.voiceId,
        description = voice.labels["description"],
        metadata = listOfNotNull(
            voice.labels["language"],
            voice.gender,
            voice.accent,
            voice.labels["category"]
        ).distinct().joinToString(" | "),
        sourceLabel = sourceLabel,
        eligibilityLabel = if (isFreeApiCompatible) "Free API" else "Paid/legacy",
        hasPreview = !voice.previewUrl.isNullOrBlank(),
        isPlaying = isPlaying,
        isUpdating = isUpdating,
        isActive = true,
        onPreview = onPreview,
        onActiveChange = { active ->
            if (!active) onDeactivate()
        }
    )
}

@Composable
private fun VoiceCardLayout(
    name: String,
    voiceId: String,
    description: String?,
    metadata: String,
    sourceLabel: String,
    eligibilityLabel: String,
    hasPreview: Boolean,
    isPlaying: Boolean,
    isUpdating: Boolean,
    isActive: Boolean,
    onPreview: () -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = voiceId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onPreview,
                    enabled = hasPreview
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop preview" else "Preview voice"
                    )
                }
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Switch(
                        checked = isActive,
                        onCheckedChange = onActiveChange
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VoiceBadge(eligibilityLabel)
                VoiceBadge(sourceLabel)
            }
            if (metadata.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            description?.takeIf { it.isNotBlank() }?.let {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!hasPreview) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = "Preview unavailable",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun CatalogErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Catalog could not be refreshed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onRetry,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
