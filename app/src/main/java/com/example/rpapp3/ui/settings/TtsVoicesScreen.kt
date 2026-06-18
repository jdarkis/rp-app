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
import androidx.compose.material3.FilterChip
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
import com.example.rpapp3.data.GeminiTtsService
import com.example.rpapp3.data.GeminiTtsVoices
import com.example.rpapp3.data.InworldService
import com.example.rpapp3.data.isFreeApiCompatibleElevenLabsVoice
import com.example.rpapp3.data.model.Voice
import com.example.rpapp3.data.model.VoiceSource
import com.example.rpapp3.data.repository.VoiceRepository
import com.example.rpapp3.data.selectableElevenLabsVoices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

internal enum class TtsVoiceProvider(
    val label: String,
    val source: VoiceSource
) {
    ELEVEN_LABS("ElevenLabs", VoiceSource.ELEVEN_LABS),
    INWORLD("Inworld", VoiceSource.INWORLD),
    GEMINI("Gemini", VoiceSource.GEMINI)
}

private enum class TtsVoiceCatalogTab {
    AVAILABLE,
    ACTIVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TtsVoicesScreen(
    onNavigateBack: () -> Unit,
    initialProvider: TtsVoiceProvider = TtsVoiceProvider.ELEVEN_LABS
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voiceRepository = remember { VoiceRepository() }
    val elevenLabsService = remember { ElevenLabsService.getInstance(context) }
    val inworldService = remember { InworldService.getInstance(context) }
    val geminiTtsService = remember { GeminiTtsService.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val availableListState = rememberLazyListState()
    val activeListState = rememberLazyListState()

    val savedVoicesFlow = remember(voiceRepository) { voiceRepository.getCustomVoices() }
    val savedVoices by savedVoicesFlow.collectAsState(initial = emptyList())

    var selectedProvider by remember { mutableStateOf(initialProvider) }
    var selectedTab by remember { mutableStateOf(TtsVoiceCatalogTab.AVAILABLE) }
    var searchInput by remember { mutableStateOf("") }
    var elevenLabsSearch by remember { mutableStateOf("") }
    var updatingVoiceKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    var elevenLabsVoices by remember { mutableStateOf<List<ElevenLabsCatalogVoice>>(emptyList()) }
    var elevenLabsPage by remember { mutableIntStateOf(-1) }
    var elevenLabsHasMore by remember { mutableStateOf(true) }
    var elevenLabsInitialLoading by remember { mutableStateOf(false) }
    var elevenLabsLoadingMore by remember { mutableStateOf(false) }
    var elevenLabsError by remember { mutableStateOf<String?>(null) }
    var elevenLabsRefresh by remember { mutableIntStateOf(0) }

    var inworldVoices by remember { mutableStateOf<List<Voice>?>(null) }
    var inworldLoading by remember { mutableStateOf(false) }
    var inworldError by remember { mutableStateOf<String?>(null) }
    val geminiVoices = remember { GeminiTtsVoices.DEFAULT_VOICES }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewVoiceKey by remember { mutableStateOf<String?>(null) }
    var previewLoadingKey by remember { mutableStateOf<String?>(null) }
    var previewTempFile by remember { mutableStateOf<File?>(null) }
    var previewGeneration by remember { mutableIntStateOf(0) }

    fun voiceKey(voice: Voice): String = "${voice.source.name}:${voice.voiceId}"

    fun stopPreview() {
        previewGeneration++
        mediaPlayer?.release()
        mediaPlayer = null
        previewTempFile?.delete()
        previewTempFile = null
        previewVoiceKey = null
        previewLoadingKey = null
    }

    fun playUrlPreview(voiceId: String, previewUrl: String?) {
        val key = "${VoiceSource.ELEVEN_LABS.name}:$voiceId"
        if (previewVoiceKey == key) {
            stopPreview()
            return
        }
        if (previewUrl.isNullOrBlank()) {
            scope.launch { snackbarHostState.showSnackbar("No preview is available for this voice") }
            return
        }

        try {
            stopPreview()
            previewVoiceKey = key
            previewLoadingKey = key
            val player = MediaPlayer()
            mediaPlayer = player
            player.setOnPreparedListener { preparedPlayer ->
                if (mediaPlayer === preparedPlayer && previewVoiceKey == key) {
                    previewLoadingKey = null
                    preparedPlayer.start()
                } else {
                    preparedPlayer.release()
                }
            }
            player.setOnCompletionListener { completedPlayer ->
                if (mediaPlayer === completedPlayer) {
                    stopPreview()
                } else {
                    completedPlayer.release()
                }
            }
            player.setOnErrorListener { failedPlayer, _, _ ->
                if (mediaPlayer === failedPlayer) {
                    stopPreview()
                    scope.launch {
                        snackbarHostState.showSnackbar("Failed to play voice preview")
                    }
                } else {
                    failedPlayer.release()
                }
                true
            }
            player.setDataSource(previewUrl)
            player.prepareAsync()
        } catch (_: Exception) {
            stopPreview()
            scope.launch { snackbarHostState.showSnackbar("Failed to play voice preview") }
        }
    }

    fun playInworldPreview(voice: Voice) {
        val key = voiceKey(voice)
        if (previewVoiceKey == key) {
            stopPreview()
            return
        }

        stopPreview()
        previewVoiceKey = key
        previewLoadingKey = key
        val requestGeneration = previewGeneration
        scope.launch {
            val text = "Hello, I am ${voice.name}. This is a preview of my voice."
            inworldService.textToSpeech(text, voice.voiceId)
                .onSuccess { audioData ->
                    if (requestGeneration != previewGeneration || previewVoiceKey != key) {
                        return@onSuccess
                    }
                    try {
                        val tempFile = File.createTempFile("inworld_preview_", ".mp3", context.cacheDir)
                        FileOutputStream(tempFile).use { it.write(audioData) }
                        previewTempFile = tempFile

                        val player = MediaPlayer()
                        mediaPlayer = player
                        player.setOnPreparedListener { preparedPlayer ->
                            if (mediaPlayer === preparedPlayer && previewVoiceKey == key) {
                                previewLoadingKey = null
                                preparedPlayer.start()
                            } else {
                                preparedPlayer.release()
                            }
                        }
                        player.setOnCompletionListener { completedPlayer ->
                            if (mediaPlayer === completedPlayer) {
                                stopPreview()
                            } else {
                                completedPlayer.release()
                            }
                        }
                        player.setOnErrorListener { failedPlayer, _, _ ->
                            if (mediaPlayer === failedPlayer) {
                                stopPreview()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to play Inworld preview")
                                }
                            } else {
                                failedPlayer.release()
                            }
                            true
                        }
                        player.setDataSource(tempFile.absolutePath)
                        player.prepareAsync()
                    } catch (error: Exception) {
                        stopPreview()
                        snackbarHostState.showSnackbar(
                            "Failed to play Inworld preview: ${error.message ?: "Unknown error"}"
                        )
                    }
                }
                .onFailure { error ->
                    if (requestGeneration == previewGeneration) {
                        stopPreview()
                        snackbarHostState.showSnackbar(
                            "Failed to generate Inworld preview: ${error.message ?: "Unknown error"}"
                        )
                    }
                }
        }
    }

    fun playGeminiPreview(voice: Voice) {
        val key = voiceKey(voice)
        if (previewVoiceKey == key) {
            stopPreview()
            return
        }

        stopPreview()
        previewVoiceKey = key
        previewLoadingKey = key
        val requestGeneration = previewGeneration
        scope.launch {
            val text = "Hello, I am ${voice.name}. This is a preview of my voice."
            geminiTtsService.textToSpeech(text, voice.voiceId)
                .onSuccess { audioData ->
                    if (requestGeneration != previewGeneration || previewVoiceKey != key) {
                        return@onSuccess
                    }
                    try {
                        val tempFile = File.createTempFile("gemini_preview_", ".wav", context.cacheDir)
                        FileOutputStream(tempFile).use { it.write(audioData) }
                        previewTempFile = tempFile

                        val player = MediaPlayer()
                        mediaPlayer = player
                        player.setOnPreparedListener { preparedPlayer ->
                            if (mediaPlayer === preparedPlayer && previewVoiceKey == key) {
                                previewLoadingKey = null
                                preparedPlayer.start()
                            } else {
                                preparedPlayer.release()
                            }
                        }
                        player.setOnCompletionListener { completedPlayer ->
                            if (mediaPlayer === completedPlayer) {
                                stopPreview()
                            } else {
                                completedPlayer.release()
                            }
                        }
                        player.setOnErrorListener { failedPlayer, _, _ ->
                            if (mediaPlayer === failedPlayer) {
                                stopPreview()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Failed to play Gemini preview")
                                }
                            } else {
                                failedPlayer.release()
                            }
                            true
                        }
                        player.setDataSource(tempFile.absolutePath)
                        player.prepareAsync()
                    } catch (error: Exception) {
                        stopPreview()
                        snackbarHostState.showSnackbar(
                            "Failed to play Gemini preview: ${error.message ?: "Unknown error"}"
                        )
                    }
                }
                .onFailure { error ->
                    if (requestGeneration == previewGeneration) {
                        stopPreview()
                        snackbarHostState.showSnackbar(
                            "Failed to generate Gemini preview: ${error.message ?: "Unknown error"}"
                        )
                    }
                }
        }
    }

    suspend fun loadElevenLabsCatalog(reset: Boolean) {
        if (!reset && (!elevenLabsHasMore || elevenLabsInitialLoading || elevenLabsLoadingMore)) {
            return
        }

        val requestedPage = if (reset) 0 else elevenLabsPage + 1
        if (reset) {
            elevenLabsInitialLoading = true
        } else {
            elevenLabsLoadingMore = true
        }

        elevenLabsService.listFreeTierVoices(
            page = requestedPage,
            search = elevenLabsSearch
        ).onSuccess { page ->
            elevenLabsVoices = if (reset) {
                page.voices
            } else {
                (elevenLabsVoices + page.voices).distinctBy { it.voiceId }
            }
            elevenLabsPage = page.page
            elevenLabsHasMore = page.hasMore
            elevenLabsError = null
            if (reset) availableListState.scrollToItem(0)
        }.onFailure { error ->
            elevenLabsError = error.message ?: "Unable to load the ElevenLabs voice catalog"
        }

        elevenLabsInitialLoading = false
        elevenLabsLoadingMore = false
    }

    suspend fun loadInworldCatalog() {
        inworldLoading = true
        inworldError = null
        runCatching {
            inworldService.initialize()
            inworldService.getVoices().getOrThrow()
        }.onSuccess {
            inworldVoices = it
            availableListState.scrollToItem(0)
        }.onFailure { error ->
            inworldError = error.message ?: "Unable to load the Inworld voice catalog"
        }
        inworldLoading = false
    }

    fun setVoiceActive(voice: Voice, active: Boolean) {
        val key = voiceKey(voice)
        scope.launch {
            updatingVoiceKeys = updatingVoiceKeys + key
            voiceRepository.setVoiceActive(voice, active)
                .onSuccess {
                    snackbarHostState.showSnackbar(
                        if (active) "${voice.name} activated" else "${voice.name} deactivated"
                    )
                }
                .onFailure {
                    snackbarHostState.showSnackbar("Could not update ${voice.name}")
                }
            updatingVoiceKeys = updatingVoiceKeys - key
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopPreview() }
    }

    LaunchedEffect(selectedProvider) {
        stopPreview()
        searchInput = ""
        if (selectedProvider == TtsVoiceProvider.INWORLD && inworldVoices == null) {
            loadInworldCatalog()
        }
    }

    LaunchedEffect(searchInput, selectedProvider) {
        if (selectedProvider == TtsVoiceProvider.ELEVEN_LABS) {
            delay(350)
            elevenLabsSearch = searchInput.trim()
        }
    }

    LaunchedEffect(selectedProvider, elevenLabsSearch, elevenLabsRefresh) {
        if (selectedProvider == TtsVoiceProvider.ELEVEN_LABS) {
            loadElevenLabsCatalog(reset = true)
        }
    }

    LaunchedEffect(
        availableListState,
        selectedProvider,
        selectedTab,
        elevenLabsSearch,
        elevenLabsVoices.size,
        elevenLabsHasMore
    ) {
        snapshotFlow {
            availableListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisibleIndex ->
            if (
                selectedProvider == TtsVoiceProvider.ELEVEN_LABS &&
                selectedTab == TtsVoiceCatalogTab.AVAILABLE &&
                elevenLabsVoices.isNotEmpty() &&
                lastVisibleIndex >= elevenLabsVoices.lastIndex - 4
            ) {
                loadElevenLabsCatalog(reset = false)
            }
        }
    }

    val activeProviderVoices = remember(savedVoices, selectedProvider) {
        savedVoices.filter { it.source == selectedProvider.source }
    }
    val activeVoiceIds = remember(activeProviderVoices) {
        activeProviderVoices.mapTo(hashSetOf()) { it.voiceId }
    }
    val filteredActiveVoices = remember(activeProviderVoices, searchInput) {
        activeProviderVoices.filterByVoiceSearch(searchInput)
    }
    val filteredInworldVoices = remember(inworldVoices, searchInput) {
        inworldVoices.orEmpty().filterByVoiceSearch(searchInput)
    }
    val filteredGeminiVoices = remember(geminiVoices, searchInput) {
        geminiVoices.filterByVoiceSearch(searchInput)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TTS Voices") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val refreshing = if (selectedProvider == TtsVoiceProvider.ELEVEN_LABS) {
                        elevenLabsInitialLoading || elevenLabsLoadingMore
                    } else if (selectedProvider == TtsVoiceProvider.INWORLD) {
                        inworldLoading
                    } else {
                        false
                    }
                    IconButton(
                        onClick = {
                            if (selectedProvider == TtsVoiceProvider.ELEVEN_LABS) {
                                elevenLabsRefresh++
                            } else if (selectedProvider == TtsVoiceProvider.INWORLD) {
                                scope.launch { loadInworldCatalog() }
                            }
                        },
                        enabled = !refreshing
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
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TtsVoiceProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = { selectedProvider = provider },
                        label = { Text(provider.label) }
                    )
                }
            }
            TtsCatalogInfoCard(
                provider = selectedProvider,
                activeCount = activeProviderVoices.size,
                assignableElevenLabsCount = selectableElevenLabsVoices(savedVoices).size
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search ${selectedProvider.label} voices") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                    selected = selectedTab == TtsVoiceCatalogTab.AVAILABLE,
                    onClick = { selectedTab = TtsVoiceCatalogTab.AVAILABLE },
                    text = { Text("Available") }
                )
                Tab(
                    selected = selectedTab == TtsVoiceCatalogTab.ACTIVE,
                    onClick = { selectedTab = TtsVoiceCatalogTab.ACTIVE },
                    text = { Text("Active (${activeProviderVoices.size})") }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTab) {
                TtsVoiceCatalogTab.AVAILABLE -> {
                    when (selectedProvider) {
                        TtsVoiceProvider.ELEVEN_LABS -> {
                            ElevenLabsAvailableContent(
                                voices = elevenLabsVoices,
                                activeVoiceIds = activeVoiceIds,
                                updatingVoiceKeys = updatingVoiceKeys,
                                previewVoiceKey = previewVoiceKey,
                                previewLoadingKey = previewLoadingKey,
                                isInitialLoading = elevenLabsInitialLoading,
                                isLoadingMore = elevenLabsLoadingMore,
                                error = elevenLabsError,
                                listState = availableListState,
                                onRetry = { elevenLabsRefresh++ },
                                onPreview = { voice ->
                                    playUrlPreview(voice.voiceId, voice.previewUrl)
                                },
                                onActiveChange = { voice, active ->
                                    setVoiceActive(voice.toVoice(), active)
                                }
                            )
                        }

                        TtsVoiceProvider.INWORLD -> {
                            InworldAvailableContent(
                                voices = filteredInworldVoices,
                                hasLoaded = inworldVoices != null,
                                activeVoiceIds = activeVoiceIds,
                                updatingVoiceKeys = updatingVoiceKeys,
                                previewVoiceKey = previewVoiceKey,
                                previewLoadingKey = previewLoadingKey,
                                isLoading = inworldLoading,
                                error = inworldError,
                                listState = availableListState,
                                onRetry = { scope.launch { loadInworldCatalog() } },
                                onPreview = ::playInworldPreview,
                                onActiveChange = ::setVoiceActive
                            )
                        }

                        TtsVoiceProvider.GEMINI -> {
                            GeminiAvailableContent(
                                voices = filteredGeminiVoices,
                                activeVoiceIds = activeVoiceIds,
                                updatingVoiceKeys = updatingVoiceKeys,
                                previewVoiceKey = previewVoiceKey,
                                previewLoadingKey = previewLoadingKey,
                                listState = availableListState,
                                onPreview = ::playGeminiPreview,
                                onActiveChange = ::setVoiceActive
                            )
                        }
                    }
                }

                TtsVoiceCatalogTab.ACTIVE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = activeListState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (filteredActiveVoices.isEmpty()) {
                            item(key = "empty-active") {
                                TtsEmptyContent(
                                    if (activeProviderVoices.isEmpty()) {
                                        "No ${selectedProvider.label} voices are active. Enable voices from the Available tab."
                                    } else {
                                        "No active voices match this search."
                                    }
                                )
                            }
                        }
                        items(
                            items = filteredActiveVoices,
                            key = { voiceKey(it) }
                        ) { voice ->
                            val key = voiceKey(voice)
                            TtsSavedVoiceCard(
                                voice = voice,
                                isUpdating = key in updatingVoiceKeys,
                                isPlaying = previewVoiceKey == key && previewLoadingKey == null,
                                isPreviewLoading = previewLoadingKey == key,
                                onPreview = {
                                    when (voice.source) {
                                        VoiceSource.ELEVEN_LABS -> playUrlPreview(voice.voiceId, voice.previewUrl)
                                        VoiceSource.INWORLD -> playInworldPreview(voice)
                                        VoiceSource.GEMINI -> playGeminiPreview(voice)
                                    }
                                },
                                onDeactivate = { setVoiceActive(voice, false) }
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

private fun List<Voice>.filterByVoiceSearch(search: String): List<Voice> {
    val query = search.trim()
    if (query.isEmpty()) return this
    return filter { voice ->
        voice.name.contains(query, ignoreCase = true) ||
            voice.voiceId.contains(query, ignoreCase = true) ||
            voice.labels.values.any { it.contains(query, ignoreCase = true) }
    }
}

@Composable
private fun ElevenLabsAvailableContent(
    voices: List<ElevenLabsCatalogVoice>,
    activeVoiceIds: Set<String>,
    updatingVoiceKeys: Set<String>,
    previewVoiceKey: String?,
    previewLoadingKey: String?,
    isInitialLoading: Boolean,
    isLoadingMore: Boolean,
    error: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRetry: () -> Unit,
    onPreview: (ElevenLabsCatalogVoice) -> Unit,
    onActiveChange: (ElevenLabsCatalogVoice, Boolean) -> Unit
) {
    if (isInitialLoading && voices.isEmpty()) {
        TtsLoadingContent()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        error?.let { message ->
            item(key = "elevenlabs-error") {
                TtsCatalogErrorCard(message = message, onRetry = onRetry)
            }
        }
        if (voices.isEmpty() && error == null) {
            item(key = "empty-elevenlabs") {
                TtsEmptyContent("No free-tier voices match this search.")
            }
        }
        items(voices, key = { it.voiceId }) { voice ->
            val key = "${VoiceSource.ELEVEN_LABS.name}:${voice.voiceId}"
            TtsVoiceCard(
                name = voice.name,
                voiceId = voice.voiceId,
                description = voice.description,
                metadata = listOfNotNull(
                    voice.language,
                    voice.gender,
                    voice.accent,
                    voice.category
                ).distinct().joinToString(" | "),
                badges = listOf("Free API", "Default"),
                isActive = voice.voiceId in activeVoiceIds,
                isUpdating = key in updatingVoiceKeys,
                isPlaying = previewVoiceKey == key && previewLoadingKey == null,
                isPreviewLoading = previewLoadingKey == key,
                canPreview = !voice.previewUrl.isNullOrBlank(),
                onPreview = { onPreview(voice) },
                onActiveChange = { onActiveChange(voice, it) }
            )
        }
        if (isLoadingMore) {
            item(key = "elevenlabs-loading-more") {
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
        item(key = "elevenlabs-bottom-space") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun InworldAvailableContent(
    voices: List<Voice>,
    hasLoaded: Boolean,
    activeVoiceIds: Set<String>,
    updatingVoiceKeys: Set<String>,
    previewVoiceKey: String?,
    previewLoadingKey: String?,
    isLoading: Boolean,
    error: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRetry: () -> Unit,
    onPreview: (Voice) -> Unit,
    onActiveChange: (Voice, Boolean) -> Unit
) {
    if (isLoading && !hasLoaded) {
        TtsLoadingContent()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        error?.let { message ->
            item(key = "inworld-error") {
                TtsCatalogErrorCard(message = message, onRetry = onRetry)
            }
        }
        if (hasLoaded && voices.isEmpty() && error == null) {
            item(key = "empty-inworld") {
                TtsEmptyContent("No Inworld voices match this search.")
            }
        }
        items(voices, key = { it.voiceId }) { voice ->
            val key = "${voice.source.name}:${voice.voiceId}"
            TtsVoiceCard(
                name = voice.name,
                voiceId = voice.voiceId,
                description = voice.description,
                metadata = listOfNotNull(
                    voice.labels["language"],
                    voice.gender?.takeUnless { it == "unknown" },
                    voice.age?.takeUnless { it == "unknown" },
                    voice.labels["tags"]
                ).distinct().joinToString(" | "),
                badges = listOf("Inworld"),
                isActive = voice.voiceId in activeVoiceIds,
                isUpdating = key in updatingVoiceKeys,
                isPlaying = previewVoiceKey == key && previewLoadingKey == null,
                isPreviewLoading = previewLoadingKey == key,
                canPreview = true,
                onPreview = { onPreview(voice) },
                onActiveChange = { onActiveChange(voice, it) }
            )
        }
        item(key = "inworld-bottom-space") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GeminiAvailableContent(
    voices: List<Voice>,
    activeVoiceIds: Set<String>,
    updatingVoiceKeys: Set<String>,
    previewVoiceKey: String?,
    previewLoadingKey: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPreview: (Voice) -> Unit,
    onActiveChange: (Voice, Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (voices.isEmpty()) {
            item(key = "empty-gemini") {
                TtsEmptyContent("No Gemini voices match this search.")
            }
        }
        items(voices, key = { it.voiceId }) { voice ->
            val key = "${voice.source.name}:${voice.voiceId}"
            TtsVoiceCard(
                name = voice.name,
                voiceId = voice.voiceId,
                description = voice.description,
                metadata = listOfNotNull(
                    voice.labels["style"],
                    voice.labels["language"]
                ).distinct().joinToString(" | "),
                badges = listOf("Gemini", "Free tier", "Preview"),
                isActive = voice.voiceId in activeVoiceIds,
                isUpdating = key in updatingVoiceKeys,
                isPlaying = previewVoiceKey == key && previewLoadingKey == null,
                isPreviewLoading = previewLoadingKey == key,
                canPreview = true,
                onPreview = { onPreview(voice) },
                onActiveChange = { onActiveChange(voice, it) }
            )
        }
        item(key = "gemini-bottom-space") {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TtsSavedVoiceCard(
    voice: Voice,
    isUpdating: Boolean,
    isPlaying: Boolean,
    isPreviewLoading: Boolean,
    onPreview: () -> Unit,
    onDeactivate: () -> Unit
) {
    val badges = when (voice.source) {
        VoiceSource.ELEVEN_LABS -> {
            val sourceLabel = when (voice.labels["catalog_source"]) {
                ElevenLabsCatalogSource.DEFAULT.name -> "Default"
                ElevenLabsCatalogSource.SHARED.name -> "Voice Library"
                else -> "Saved voice"
            }
            listOf(
                if (isFreeApiCompatibleElevenLabsVoice(voice)) "Free API" else "Paid/legacy",
                sourceLabel
            )
        }

        VoiceSource.INWORLD -> listOf("Inworld")

        VoiceSource.GEMINI -> listOf("Gemini", "Free tier", "Preview")
    }
    val metadata = listOfNotNull(
        voice.labels["language"],
        voice.gender?.takeUnless { it == "unknown" },
        voice.accent,
        voice.age?.takeUnless { it == "unknown" },
        voice.labels["tags"]
    ).distinct().joinToString(" | ")

    TtsVoiceCard(
        name = voice.name,
        voiceId = voice.voiceId,
        description = voice.description,
        metadata = metadata,
        badges = badges,
        isActive = true,
        isUpdating = isUpdating,
        isPlaying = isPlaying,
        isPreviewLoading = isPreviewLoading,
        canPreview = voice.source == VoiceSource.INWORLD ||
            voice.source == VoiceSource.GEMINI ||
            !voice.previewUrl.isNullOrBlank(),
        onPreview = onPreview,
        onActiveChange = { active -> if (!active) onDeactivate() }
    )
}

@Composable
private fun TtsCatalogInfoCard(
    provider: TtsVoiceProvider,
    activeCount: Int,
    assignableElevenLabsCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
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
                    text = when (provider) {
                        TtsVoiceProvider.ELEVEN_LABS ->
                            "$assignableElevenLabsCount assignable ElevenLabs voice${if (assignableElevenLabsCount == 1) "" else "s"}"
                        TtsVoiceProvider.INWORLD ->
                            "$activeCount active Inworld voice${if (activeCount == 1) "" else "s"}"
                        TtsVoiceProvider.GEMINI ->
                            "$activeCount active Gemini voice${if (activeCount == 1) "" else "s"}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when (provider) {
                        TtsVoiceProvider.ELEVEN_LABS ->
                            "Only free-API-compatible Default voices can be assigned."
                        TtsVoiceProvider.INWORLD ->
                            "Activate the Inworld voices that should appear in character and chat settings."
                        TtsVoiceProvider.GEMINI ->
                            "Activate the Gemini voices that should appear in character and chat settings."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TtsVoiceCard(
    name: String,
    voiceId: String,
    description: String?,
    metadata: String,
    badges: List<String>,
    isActive: Boolean,
    isUpdating: Boolean,
    isPlaying: Boolean,
    isPreviewLoading: Boolean,
    canPreview: Boolean,
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
                    enabled = canPreview && !isUpdating
                ) {
                    if (isPreviewLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Stop preview" else "Preview voice"
                        )
                    }
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
                badges.forEach { TtsVoiceBadge(it) }
            }
            if (metadata.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
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
            if (!canPreview) {
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
private fun TtsVoiceBadge(text: String) {
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
private fun TtsCatalogErrorCard(
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
private fun TtsLoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun TtsEmptyContent(message: String) {
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
