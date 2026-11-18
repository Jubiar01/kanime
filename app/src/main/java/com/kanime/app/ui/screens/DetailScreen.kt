package com.kanime.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ExtensionOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.rememberLazyListState
import coil3.compose.AsyncImage
import com.kanime.app.R
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.AnimeExtras
import com.kanime.app.data.model.Episode
import com.kanime.app.data.model.MalCharacter
import com.kanime.app.data.model.MalCharacterDetails
import com.kanime.app.data.model.MalLink
import com.kanime.app.data.model.MalPicture
import com.kanime.app.data.model.MalRecommendation
import com.kanime.app.data.model.MalTheme
import com.kanime.app.data.model.MalVideo
import com.kanime.app.data.plugin.AnimeSourcePlugin
import com.kanime.app.data.plugin.PluginBridge
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.data.repository.AnimeRepository
import com.kanime.app.ui.components.EmptyStateView
import com.kanime.app.ui.components.EpisodeCard
import com.kanime.app.ui.components.GlassButton
import com.kanime.app.ui.components.GlassButtonStyle
import com.kanime.app.ui.components.GlassChip
import com.kanime.app.ui.components.GlassIconButton
import com.kanime.app.ui.components.GlassSheet
import com.kanime.app.ui.components.GlassSurface
import com.kanime.app.ui.components.ShimmerBox
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel
import kotlinx.coroutines.launch

internal sealed interface SourceAvailabilityState {
    data class Checking(val pluginName: String) : SourceAvailabilityState
    data class Available(val pluginName: String, val episodeCount: Int) : SourceAvailabilityState
    data class Unavailable(
        val pluginName: String,
        val reason: String,
        val verifiedAlternatives: List<AnimeSourcePlugin> = emptyList(),
        val otherInstalledPlugins: List<AnimeSourcePlugin> = emptyList()
    ) : SourceAvailabilityState
    data class NoActivePlugin(val message: String) : SourceAvailabilityState
}

private data class CachedPluginLookup(
    val episodes: List<Episode>?,
    val availability: SourceAvailabilityState,
    val checkedTitles: Set<String>
)

private fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
fun DetailScreen(
    anime: Anime,
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(anime.id) { mutableIntStateOf(0) }
    val isInWatchlist = uiState.watchlistIds.contains(anime.id)
    val coroutineScope = rememberCoroutineScope()

    var pluginEpisodes by remember(anime.id) { mutableStateOf<List<Episode>?>(null) }
    val allPlugins by PluginManager.plugins.collectAsStateWithLifecycle()
    val availablePlugins = remember(allPlugins) { allPlugins.filter { it.isVideoPlugin } }
    val activeSourceId by PluginManager.activeSourceId.collectAsStateWithLifecycle()

    val activePlugin = remember(availablePlugins, activeSourceId) {
        availablePlugins.firstOrNull { it.id == activeSourceId }
            ?: availablePlugins.firstOrNull { it.isInstalled && it.isEnabled }
            ?: availablePlugins.firstOrNull()
    }
    val activeSourceShortName = remember(activePlugin) {
        activePlugin?.name
            ?.removeSuffix(" Extractor")
            ?.trim()
            ?.ifBlank { activePlugin.name }
            ?: "None"
    }

    val pluginLookupCache = remember(anime.id) { mutableMapOf<String, CachedPluginLookup>() }

    var sourceAvailability by remember(anime.id) {
        mutableStateOf<SourceAvailabilityState>(
            SourceAvailabilityState.Checking(activePlugin?.name ?: "Source")
        )
    }

    var showSourceSheet by remember { mutableStateOf(false) }

    suspend fun fetchPluginEpisodes(forceRefresh: Boolean = false) {
        val currentPlugin = availablePlugins.firstOrNull { it.id == activeSourceId }
            ?: availablePlugins.firstOrNull { it.isInstalled && it.isEnabled }
            ?: availablePlugins.firstOrNull()

        if (currentPlugin == null || !currentPlugin.isInstalled) {
            pluginEpisodes = null
            sourceAvailability = SourceAvailabilityState.NoActivePlugin(
                "${currentPlugin?.name ?: "Source plugin"} is not installed."
            )
            return
        }
        if (!currentPlugin.isEnabled) {
            pluginEpisodes = null
            sourceAvailability = SourceAvailabilityState.NoActivePlugin(
                "${currentPlugin.name} is turned off."
            )
            return
        }

        val titleCandidates = listOfNotNull(
            anime.englishTitle,
            anime.title,
            anime.romajiTitle,
            anime.japaneseTitle
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val titleSet = titleCandidates.toSet()

        if (forceRefresh) {
            pluginLookupCache.remove(currentPlugin.id)
        } else {
            val cached = pluginLookupCache[currentPlugin.id]
            if (cached != null) {
                val alreadyFound = cached.availability is SourceAvailabilityState.Available
                val noNewTitles = cached.checkedTitles.containsAll(titleSet)
                if (alreadyFound || noNewTitles) {
                    pluginEpisodes = cached.episodes
                    sourceAvailability = cached.availability
                    return
                }
            }
        }

        pluginEpisodes = null
        sourceAvailability = SourceAvailabilityState.Checking(currentPlugin.name)
        try {
            val authority = currentPlugin.providerAuthority
            var slug: String? = null
            for (candidate in titleCandidates) {
                slug = PluginBridge.findSlug(context, candidate, authority = authority)
                if (slug != null) break
            }

            val fetchedEpisodes = if (slug != null) {
                PluginBridge.getEpisodes(context, slug, authority = authority)
            } else {
                emptyList()
            }

            if (fetchedEpisodes.isNotEmpty()) {
                val mapped = fetchedEpisodes.map { ep ->
                    Episode(
                        id = ep.id,
                        episodeNumber = ep.num.toInt(),
                        title = ep.title,
                        duration = anime.duration,
                        thumbnail = anime.bannerUrl.ifBlank { anime.coverUrl },
                        description = "Streamed via ${currentPlugin.name}"
                    )
                }
                val availState = SourceAvailabilityState.Available(
                    pluginName = currentPlugin.name,
                    episodeCount = mapped.size
                )
                pluginEpisodes = mapped
                sourceAvailability = availState
                pluginLookupCache[currentPlugin.id] = CachedPluginLookup(
                    episodes = mapped,
                    availability = availState,
                    checkedTitles = titleSet
                )
            } else {
                val otherInstalled = availablePlugins.filter {
                    it.isInstalled && it.isEnabled && it.id != currentPlugin.id
                }
                val verifiedAlts = mutableListOf<AnimeSourcePlugin>()
                for (alt in otherInstalled) {
                    val altCached = pluginLookupCache[alt.id]
                    if (altCached != null) {
                        if (altCached.availability is SourceAvailabilityState.Available) {
                            verifiedAlts.add(alt)
                        }
                        continue
                    }
                    var altFound = false
                    for (candidate in titleCandidates) {
                        val altSlug = PluginBridge.findSlug(context, candidate, authority = alt.providerAuthority)
                        if (altSlug != null) {
                            altFound = true
                            break
                        }
                    }
                    if (altFound) {
                        verifiedAlts.add(alt)
                    }
                }

                val unavailState = SourceAvailabilityState.Unavailable(
                    pluginName = currentPlugin.name,
                    reason = "'${anime.title}' is unavailable on ${currentPlugin.name}.",
                    verifiedAlternatives = verifiedAlts,
                    otherInstalledPlugins = otherInstalled
                )
                pluginEpisodes = null
                sourceAvailability = unavailState
                pluginLookupCache[currentPlugin.id] = CachedPluginLookup(
                    episodes = null,
                    availability = unavailState,
                    checkedTitles = titleSet
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val otherInstalled = availablePlugins.filter {
                it.isInstalled && it.isEnabled && it.id != currentPlugin.id
            }
            pluginEpisodes = null
            sourceAvailability = SourceAvailabilityState.Unavailable(
                pluginName = currentPlugin.name,
                reason = "Could not load from ${currentPlugin.name}.",
                verifiedAlternatives = emptyList(),
                otherInstalledPlugins = otherInstalled
            )
        }
    }

    LaunchedEffect(
        anime.id,
        activePlugin?.id,
        activePlugin?.isInstalled,
        activePlugin?.isEnabled,
        anime.englishTitle,
        anime.romajiTitle
    ) {
        fetchPluginEpisodes(forceRefresh = false)
    }

    LaunchedEffect(selectedTab, anime.id, anime.malId) {
        when (selectedTab) {
            1 -> viewModel.loadCharactersForSelected()
            2 -> viewModel.loadMediaForSelected()
            3 -> viewModel.loadMoreForSelected()
        }
    }

    val catalogByNum = remember(anime.episodeList) {
        anime.episodeList.associateBy { it.episodeNumber }
    }
    val displayEpisodes = remember(pluginEpisodes, catalogByNum) {
        val pluginEps = pluginEpisodes ?: return@remember emptyList()
        pluginEps.map { pEp ->
            val meta = catalogByNum[pEp.episodeNumber]
            if (meta != null) {
                pEp.copy(
                    title = meta.title.ifBlank { pEp.title },
                    thumbnail = meta.thumbnail.ifBlank { pEp.thumbnail },
                    titleJapanese = meta.titleJapanese,
                    aired = meta.aired,
                    isFiller = meta.isFiller,
                    isRecap = meta.isRecap,
                    infoSource = meta.infoSource,
                    thumbnailSource = meta.thumbnailSource
                )
            } else pEp
        }
    }
    val animeForPlayback = remember(anime, displayEpisodes) {
        anime.copy(episodeList = displayEpisodes)
    }

    DetailScreenContent(
        anime = anime,
        displayEpisodes = displayEpisodes,
        isInWatchlist = isInWatchlist,
        isLoadingDetails = uiState.isLoadingDetails,
        isLoadingExtras = uiState.isLoadingExtras,
        extras = uiState.selectedAnimeExtras,
        selectedTab = selectedTab,
        onSelectTab = { selectedTab = it },
        sourceAvailability = sourceAvailability,
        activeSourceShortName = activeSourceShortName,
        availablePlugins = availablePlugins,
        activeSourceId = activeSourceId,
        showSourceSheet = showSourceSheet,
        onShowSourceSheet = { showSourceSheet = it },
        showCharacterSheet = uiState.showCharacterSheet,
        isLoadingCharacter = uiState.isLoadingCharacter,
        selectedCharacter = uiState.selectedCharacter,
        selectedCharacterPictures = uiState.selectedCharacterPictures,
        onBackClick = onBackClick,
        onRefresh = {
            viewModel.refreshSelectedAnime()
            coroutineScope.launch { fetchPluginEpisodes(forceRefresh = true) }
        },
        onToggleWatchlist = { viewModel.toggleWatchlist(anime.id) },
        onPlayEpisode = { ep -> viewModel.playEpisode(animeForPlayback, ep) },
        onSwitchPlugin = { plugin ->
            if (!plugin.isInstalled) {
                Toast.makeText(context, context.getString(R.string.plugins_msg_not_installed, plugin.name), Toast.LENGTH_SHORT).show()
            } else if (!plugin.isEnabled) {
                Toast.makeText(context, context.getString(R.string.plugins_msg_disabled, plugin.name), Toast.LENGTH_SHORT).show()
            } else {
                PluginManager.setActiveSource(plugin.id)
                showSourceSheet = false
                Toast.makeText(context, context.getString(R.string.plugins_msg_source_switched, plugin.name), Toast.LENGTH_SHORT).show()
            }
        },
        onOpenCharacter = { viewModel.openCharacter(it) },
        onCloseCharacter = { viewModel.closeCharacter() },
        onOpenAnimeByMalId = { malId, title, image ->
            viewModel.closeCharacter()
            viewModel.openAnimeByMalId(malId, title, image)
        },
        onOpenExternalUrl = { url -> openExternalUrl(context, url) }
    )
}

@Composable
internal fun DetailScreenContent(
    anime: Anime,
    displayEpisodes: List<Episode>,
    isInWatchlist: Boolean,
    isLoadingDetails: Boolean,
    isLoadingExtras: Boolean,
    extras: AnimeExtras,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    sourceAvailability: SourceAvailabilityState,
    activeSourceShortName: String,
    availablePlugins: List<AnimeSourcePlugin>,
    activeSourceId: String,
    showSourceSheet: Boolean,
    onShowSourceSheet: (Boolean) -> Unit,
    showCharacterSheet: Boolean,
    isLoadingCharacter: Boolean,
    selectedCharacter: MalCharacterDetails?,
    selectedCharacterPictures: List<MalPicture>,
    onBackClick: () -> Unit,
    onRefresh: () -> Unit,
    onToggleWatchlist: () -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onSwitchPlugin: (AnimeSourcePlugin) -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onCloseCharacter: () -> Unit,
    onOpenAnimeByMalId: (Int, String, String?) -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val windowSize = LocalWindowSizeClass.current
    var isSynopsisExpanded by remember(anime.id) { mutableStateOf(false) }
    val compactListState = rememberLazyListState()
    val topBarScrimAlpha = remember(compactListState) {
        derivedStateOf {
            if (compactListState.firstVisibleItemIndex > 0) 0.92f
            else (compactListState.firstVisibleItemScrollOffset / 360f).coerceIn(0f, 0.92f)
        }
    }
    val topBarBgColor = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxSize()) {
        if (windowSize.useTwoPaneDetail) {
            // Two-pane Detail layout for foldables & tablets: Left (poster + info) | Right (tabs + episodes)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
                    .padding(top = 72.dp, start = KanimeSpacing.xl, end = KanimeSpacing.xl),
                horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.xl)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = KanimeSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                ) {
                    DetailBackdropCard(anime = anime, height = 220.dp)
                    DetailHeroMetaAndActions(
                        anime = anime,
                        displayEpisodes = displayEpisodes,
                        isInWatchlist = isInWatchlist,
                        isLoadingDetails = isLoadingDetails,
                        isSynopsisExpanded = isSynopsisExpanded,
                        onToggleSynopsis = { isSynopsisExpanded = !isSynopsisExpanded },
                        sourceAvailability = sourceAvailability,
                        activeSourceShortName = activeSourceShortName,
                        onToggleWatchlist = onToggleWatchlist,
                        onOpenSourceSheet = { onShowSourceSheet(true) },
                        onPlayFirst = { displayEpisodes.firstOrNull()?.let(onPlayEpisode) },
                        onSwitchPlugin = onSwitchPlugin
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                ) {
                    DetailTabBar(
                        selectedTab = selectedTab,
                        episodeCount = displayEpisodes.size,
                        onSelectTab = onSelectTab
                    )
                    Spacer(modifier = Modifier.height(KanimeSpacing.md))
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                    ) {
                        detailTabItems(
                            selectedTab = selectedTab,
                            anime = anime,
                            displayEpisodes = displayEpisodes,
                            sourceAvailability = sourceAvailability,
                            isLoadingExtras = isLoadingExtras,
                            extras = extras,
                            onPlayEpisode = onPlayEpisode,
                            onSwitchPlugin = onSwitchPlugin,
                            onOpenSourceSheet = { onShowSourceSheet(true) },
                            onOpenCharacter = onOpenCharacter,
                            onOpenAnimeByMalId = onOpenAnimeByMalId,
                            onOpenExternalUrl = onOpenExternalUrl,
                            horizontalPadding = 0.dp
                        )
                    }
                }
            }
        } else {
            // Single-column Compact phone layout
            LazyColumn(
                state = compactListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 56.dp)
            ) {
                item(key = "header", contentType = "backdrop") {
                    DetailFullBleedBackdrop(anime = anime)
                }

                item(key = "meta", contentType = "meta") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = KanimeSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                    ) {
                        DetailHeroMetaAndActions(
                            anime = anime,
                            displayEpisodes = displayEpisodes,
                            isInWatchlist = isInWatchlist,
                            isLoadingDetails = isLoadingDetails,
                            isSynopsisExpanded = isSynopsisExpanded,
                            onToggleSynopsis = { isSynopsisExpanded = !isSynopsisExpanded },
                            sourceAvailability = sourceAvailability,
                            activeSourceShortName = activeSourceShortName,
                            onToggleWatchlist = onToggleWatchlist,
                            onOpenSourceSheet = { onShowSourceSheet(true) },
                            onPlayFirst = { displayEpisodes.firstOrNull()?.let(onPlayEpisode) },
                            onSwitchPlugin = onSwitchPlugin
                        )

                        DetailTabBar(
                            selectedTab = selectedTab,
                            episodeCount = displayEpisodes.size,
                            onSelectTab = onSelectTab
                        )
                        Spacer(modifier = Modifier.height(KanimeSpacing.xs))
                    }
                }

                detailTabItems(
                    selectedTab = selectedTab,
                    anime = anime,
                    displayEpisodes = displayEpisodes,
                    sourceAvailability = sourceAvailability,
                    isLoadingExtras = isLoadingExtras,
                    extras = extras,
                    onPlayEpisode = onPlayEpisode,
                    onSwitchPlugin = onSwitchPlugin,
                    onOpenSourceSheet = { onShowSourceSheet(true) },
                    onOpenCharacter = onOpenCharacter,
                    onOpenAnimeByMalId = onOpenAnimeByMalId,
                    onOpenExternalUrl = onOpenExternalUrl,
                    horizontalPadding = KanimeSpacing.lg
                )
            }
        }

        // Sticky Floating Glass Action Top Bar (scroll alpha read in drawBehind — zero scroll recomposition)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val a = topBarScrimAlpha.value
                    if (a > 0.01f) {
                        drawRect(color = topBarBgColor.copy(alpha = a))
                    }
                }
                .statusBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = KanimeSpacing.lg, vertical = KanimeSpacing.sm)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                overMedia = true,
                onClick = onBackClick
            )

            Row(horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
                GlassIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    overMedia = true,
                    onClick = onRefresh
                )
                GlassIconButton(
                    icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(R.string.cd_watchlist),
                    overMedia = true,
                    selected = isInWatchlist,
                    onClick = onToggleWatchlist
                )
                GlassIconButton(
                    icon = Icons.Default.Extension,
                    contentDescription = stringResource(R.string.cd_source),
                    overMedia = true,
                    onClick = { onShowSourceSheet(true) }
                )
            }
        }

        // Character Details Glass Sheet
        GlassSheet(
            visible = showCharacterSheet,
            onDismiss = onCloseCharacter,
            maxHeightFraction = 0.82f
        ) {
            CharacterDetailsSheetBody(
                isLoading = isLoadingCharacter,
                character = selectedCharacter,
                pictures = selectedCharacterPictures,
                onDismiss = onCloseCharacter,
                onOpenAnime = onOpenAnimeByMalId
            )
        }

        // Source Plugin Picker Glass Sheet
        GlassSheet(
            visible = showSourceSheet,
            onDismiss = { onShowSourceSheet(false) },
            maxHeightFraction = 0.72f
        ) {
            SourcePickerSheetBody(
                availablePlugins = availablePlugins,
                activeSourceId = activeSourceId,
                onSelectPlugin = onSwitchPlugin
            )
        }
    }
}

@Composable
private fun DetailFullBleedBackdrop(anime: Anime) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = anime.bannerUrl.ifBlank { anime.coverUrl },
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to bg.copy(alpha = 0.25f),
                        0.45f to Color.Transparent,
                        0.82f to bg.copy(alpha = 0.85f),
                        1.0f to bg
                    )
                )
        )
    }
}

@Composable
private fun DetailBackdropCard(
    anime: Anime,
    height: androidx.compose.ui.unit.Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = anime.bannerUrl.ifBlank { anime.coverUrl },
            contentDescription = anime.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun DetailHeroMetaAndActions(
    anime: Anime,
    displayEpisodes: List<Episode>,
    isInWatchlist: Boolean,
    isLoadingDetails: Boolean,
    isSynopsisExpanded: Boolean,
    onToggleSynopsis: () -> Unit,
    sourceAvailability: SourceAvailabilityState,
    activeSourceShortName: String,
    onToggleWatchlist: () -> Unit,
    onOpenSourceSheet: () -> Unit,
    onPlayFirst: () -> Unit,
    onSwitchPlugin: (AnimeSourcePlugin) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)) {
        Text(
            text = anime.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Concise 1-row metadata pills: Score (tertiary), Status, Year · Episodes, Source chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (anime.score > 0.0) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = PillShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = String.format("%.1f", anime.score),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = PillShape
            ) {
                Text(
                    text = anime.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            val epCount = if (anime.episodes > 0) anime.episodes else displayEpisodes.size
            val meta = buildList {
                if (anime.year > 0) add(anime.year.toString())
                if (epCount > 0) add("$epCount Ep")
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Compact Source Availability Chip (pure M3 color roles)
            val isUnavailable = sourceAvailability is SourceAvailabilityState.Unavailable ||
                sourceAvailability is SourceAvailabilityState.NoActivePlugin
            val sourceBg = if (isUnavailable) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val sourceFg = if (isUnavailable) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
            Surface(
                onClick = onOpenSourceSheet,
                color = sourceBg,
                shape = PillShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (sourceAvailability is SourceAvailabilityState.Checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.6.dp,
                            color = sourceFg
                        )
                    } else {
                        Icon(
                            imageVector = if (isUnavailable) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = sourceFg,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = activeSourceShortName,
                        style = MaterialTheme.typography.labelMedium,
                        color = sourceFg
                    )
                }
            }
        }

        if (isLoadingDetails) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(PillShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }

        // Primary Action Row: Play / Switch Source + Save
        val recommendedPlugin = (sourceAvailability as? SourceAvailabilityState.Unavailable)
            ?.verifiedAlternatives?.firstOrNull()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val primaryLabel = when (sourceAvailability) {
                is SourceAvailabilityState.Checking -> stringResource(R.string.detail_checking_source, activeSourceShortName)
                is SourceAvailabilityState.Available -> stringResource(R.string.detail_start_watching)
                is SourceAvailabilityState.Unavailable -> if (recommendedPlugin != null) {
                    stringResource(R.string.detail_switch_to, recommendedPlugin.name.removeSuffix(" Extractor").trim())
                } else {
                    stringResource(R.string.action_switch_source)
                }
                is SourceAvailabilityState.NoActivePlugin -> stringResource(R.string.detail_select_source)
            }
            val primaryIcon = when (sourceAvailability) {
                is SourceAvailabilityState.Available -> Icons.Default.PlayArrow
                is SourceAvailabilityState.Unavailable -> Icons.Default.SwapHoriz
                is SourceAvailabilityState.NoActivePlugin -> Icons.Default.Extension
                is SourceAvailabilityState.Checking -> null
            }

            GlassButton(
                text = primaryLabel,
                icon = primaryIcon,
                style = GlassButtonStyle.Primary,
                enabled = sourceAvailability !is SourceAvailabilityState.Checking,
                onClick = {
                    when (sourceAvailability) {
                        is SourceAvailabilityState.Available -> onPlayFirst()
                        is SourceAvailabilityState.Unavailable -> {
                            if (recommendedPlugin != null) onSwitchPlugin(recommendedPlugin)
                            else onOpenSourceSheet()
                        }
                        is SourceAvailabilityState.NoActivePlugin -> onOpenSourceSheet()
                        is SourceAvailabilityState.Checking -> Unit
                    }
                },
                modifier = Modifier.weight(1f)
            )

            GlassButton(
                text = stringResource(if (isInWatchlist) R.string.hero_saved else R.string.hero_save),
                icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                style = if (isInWatchlist) GlassButtonStyle.Tonal else GlassButtonStyle.Glass,
                onClick = onToggleWatchlist
            )
        }

        // Collapsible Synopsis
        if (anime.synopsis.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = anime.synopsis.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = onToggleSynopsis,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Text(
                        text = stringResource(if (isSynopsisExpanded) R.string.detail_less else R.string.detail_more),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Clean genre pills
        if (anime.genres.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
            ) {
                anime.genres.forEach { genre ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        shape = PillShape
                    ) {
                        Text(
                            text = genre,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTabBar(
    selectedTab: Int,
    episodeCount: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        if (episodeCount > 0) "${stringResource(R.string.detail_tab_episodes)} ($episodeCount)"
        else stringResource(R.string.detail_tab_episodes),
        stringResource(R.string.detail_tab_cast),
        stringResource(R.string.detail_tab_media),
        stringResource(R.string.detail_tab_info)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
    ) {
        tabs.forEachIndexed { idx, label ->
            GlassChip(
                label = label,
                selected = selectedTab == idx,
                onClick = { onSelectTab(idx) }
            )
        }
    }
}

private fun LazyListScope.detailTabItems(
    selectedTab: Int,
    anime: Anime,
    displayEpisodes: List<Episode>,
    sourceAvailability: SourceAvailabilityState,
    isLoadingExtras: Boolean,
    extras: AnimeExtras,
    onPlayEpisode: (Episode) -> Unit,
    onSwitchPlugin: (AnimeSourcePlugin) -> Unit,
    onOpenSourceSheet: () -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenAnimeByMalId: (Int, String, String?) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    // Tab 0: Episodes
    if (selectedTab == 0) {
        when (sourceAvailability) {
            is SourceAvailabilityState.Checking -> {
                items(4) {
                    Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp)) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(86.dp),
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }
            }

            is SourceAvailabilityState.Available -> {
                items(displayEpisodes, key = { "${it.id}_${it.episodeNumber}" }, contentType = { "episode_card" }) { episode ->
                    Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 5.dp)) {
                        EpisodeCard(
                            episode = episode,
                            onClick = { onPlayEpisode(episode) }
                        )
                    }
                }
            }

            is SourceAvailabilityState.Unavailable -> {
                item(key = "source_unavailable") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(KanimeSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                        ) {
                            Text(
                                text = stringResource(R.string.detail_source_unavailable, sourceAvailability.pluginName),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.detail_source_switch_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val alts = sourceAvailability.verifiedAlternatives.ifEmpty {
                                sourceAvailability.otherInstalledPlugins
                            }
                            alts.forEach { alt ->
                                GlassButton(
                                    text = stringResource(R.string.detail_switch_to, alt.name),
                                    icon = Icons.Default.SwapHoriz,
                                    style = GlassButtonStyle.Primary,
                                    onClick = { onSwitchPlugin(alt) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            GlassButton(
                                text = stringResource(R.string.detail_choose_source),
                                icon = Icons.Default.Extension,
                                style = GlassButtonStyle.Glass,
                                onClick = onOpenSourceSheet,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            is SourceAvailabilityState.NoActivePlugin -> {
                item(key = "no_plugin") {
                    EmptyStateView(
                        icon = Icons.Default.ExtensionOff,
                        title = stringResource(R.string.detail_no_plugin_title),
                        subtitle = sourceAvailability.message,
                        actionLabel = stringResource(R.string.detail_choose_source),
                        onAction = onOpenSourceSheet
                    )
                }
            }
        }
    }

    // Tab 1: Cast
    if (selectedTab == 1) {
        if (isLoadingExtras && !extras.charactersLoaded) {
            items(4) {
                Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 6.dp)) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp),
                        shape = MaterialTheme.shapes.medium
                    )
                }
            }
        } else if (extras.characters.isEmpty()) {
            item {
                EmptyStateView(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.detail_cast_empty)
                )
            }
        } else {
            items(extras.characters, key = { it.characterId ?: it.name.hashCode() }, contentType = { "character_row" }) { character ->
                Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 5.dp)) {
                    CharacterRowCard(
                        character = character,
                        onClick = { character.characterId?.let(onOpenCharacter) }
                    )
                }
            }
        }
    }

    // Tab 2: Media
    if (selectedTab == 2) {
        if (isLoadingExtras && !extras.mediaLoaded) {
            item {
                Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp)) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        shape = MaterialTheme.shapes.large
                    )
                }
            }
        } else {
            val allVideos = extras.videos.trailers + extras.videos.musicVideos
            if (allVideos.isNotEmpty()) {
                item(key = "media_videos") {
                    Column(modifier = Modifier.padding(vertical = KanimeSpacing.sm)) {
                        Text(
                            text = stringResource(R.string.detail_trailers),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = horizontalPadding)
                        )
                        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                        ) {
                            items(allVideos, key = { it.embedUrl }) { video ->
                                VideoCard(
                                    video = video,
                                    onClick = {
                                        val url = video.youtubeId?.let { "https://www.youtube.com/watch?v=$it" }
                                            ?: video.embedUrl
                                        onOpenExternalUrl(url)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (extras.themes.opening.isNotEmpty() || extras.themes.ending.isNotEmpty()) {
                item(key = "media_themes") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = KanimeSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                    ) {
                        if (extras.themes.opening.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.detail_openings),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            extras.themes.opening.forEach { theme ->
                                ThemeSongCard("OP ${theme.number}", theme, onOpenExternalUrl)
                            }
                        }
                        if (extras.themes.ending.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                            Text(
                                text = stringResource(R.string.detail_endings),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            extras.themes.ending.forEach { theme ->
                                ThemeSongCard("ED ${theme.number}", theme, onOpenExternalUrl)
                            }
                        }
                    }
                }
            }

            if (extras.pictures.isNotEmpty()) {
                item(key = "media_gallery") {
                    Column(modifier = Modifier.padding(vertical = KanimeSpacing.sm)) {
                        Text(
                            text = stringResource(R.string.detail_gallery),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = horizontalPadding)
                        )
                        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                        ) {
                            items(extras.pictures, key = { it.image }) { pic ->
                                AsyncImage(
                                    model = pic.image,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .width(126.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable { onOpenExternalUrl(pic.image) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            if (allVideos.isEmpty() && extras.themes.opening.isEmpty() && extras.themes.ending.isEmpty() && extras.pictures.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.MusicNote,
                        title = stringResource(R.string.detail_media_empty)
                    )
                }
            }
        }
    }

    // Tab 3: Info + Progressive Disclosure Details + Recommendations
    if (selectedTab == 3) {
        item(key = "info_card") {
            DetailInfoSection(
                anime = anime,
                modifier = Modifier.padding(horizontal = horizontalPadding, vertical = KanimeSpacing.sm)
            )
        }

        val platforms = extras.streamingPlatforms.ifEmpty { anime.streamingPlatforms }
        if (platforms.isNotEmpty()) {
            item(key = "info_platforms") {
                LinkChipSection(
                    title = stringResource(R.string.detail_platforms),
                    links = platforms,
                    onLinkClick = { onOpenExternalUrl(it.url) },
                    horizontalPadding = horizontalPadding
                )
            }
        }

        if (extras.externalLinks.isNotEmpty()) {
            item(key = "info_links") {
                LinkChipSection(
                    title = stringResource(R.string.detail_links),
                    links = extras.externalLinks,
                    onLinkClick = { onOpenExternalUrl(it.url) },
                    horizontalPadding = horizontalPadding
                )
            }
        }

        if (extras.recommendations.isNotEmpty()) {
            item(key = "info_recs") {
                Column(modifier = Modifier.padding(vertical = KanimeSpacing.md)) {
                    Text(
                        text = stringResource(R.string.detail_recommendations),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = horizontalPadding)
                    )
                    Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                    ) {
                        items(extras.recommendations, key = { it.animeId }) { rec ->
                            RecommendationCard(
                                rec = rec,
                                onClick = { onOpenAnimeByMalId(rec.animeId, rec.title, rec.image) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoSection(
    anime: Anime,
    modifier: Modifier = Modifier
) {
    var showTechnicalDetails by remember(anime.id) { mutableStateOf(false) }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(KanimeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            InfoRow("Studio", anime.studio)
            anime.type?.let { InfoRow("Format", it) }
            InfoRow("Season", if (anime.year > 0) "${anime.season} ${anime.year}" else anime.season)
            InfoRow("Episodes", if (anime.episodes > 0) "${anime.episodes}" else "—")
            InfoRow("Duration", anime.duration)
            anime.rank?.let { InfoRow("Rank", "#$it") }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clickable { showTechnicalDetails = !showTechnicalDetails },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.detail_more_details),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = showTechnicalDetails) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    anime.source?.let { InfoRow("Source", it) }
                    anime.aired?.let { InfoRow("Aired", it) }
                    anime.rating?.let { InfoRow("Rating", it) }
                    anime.popularity?.let { InfoRow("Popularity", "#$it") }
                    anime.members?.let { InfoRow("Members", "%,d".format(it)) }
                    anime.malId?.let { InfoRow("MAL", it.toString()) }
                    anime.anilistId?.let { InfoRow("AniList", it.toString()) }
                }
            }
        }
    }
}

@Composable
private fun SourcePickerSheetBody(
    availablePlugins: List<AnimeSourcePlugin>,
    activeSourceId: String,
    onSelectPlugin: (AnimeSourcePlugin) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filterInstalled by remember { mutableStateOf(false) }

    val displayed = remember(availablePlugins, query, filterInstalled) {
        availablePlugins.filter { p ->
            val matchesQuery = query.isBlank() ||
                p.name.contains(query, ignoreCase = true) ||
                p.description.contains(query, ignoreCase = true)
            val matchesFilter = !filterInstalled || p.isInstalled
            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = KanimeSpacing.lg)
    ) {
        Text(
            text = stringResource(R.string.detail_source_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
            GlassChip(
                label = stringResource(R.string.filter_all),
                selected = !filterInstalled,
                onClick = { filterInstalled = false }
            )
            GlassChip(
                label = stringResource(R.string.plugins_active),
                selected = filterInstalled,
                onClick = { filterInstalled = true }
            )
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = PillShape,
            showShadow = false
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.detail_source_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(displayed, key = { it.id }) { plugin ->
                val isActive = activeSourceId == plugin.id
                Surface(
                    onClick = { onSelectPlugin(plugin) },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = plugin.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (plugin.isInstalled) "v${plugin.version}" else stringResource(R.string.plugins_not_installed),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                } else if (!plugin.isInstalled) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        if (isActive) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterRowCard(
    character: MalCharacter,
    onClick: () -> Unit
) {
    val jpVa = character.voiceActors.firstOrNull { it.language.equals("Japanese", ignoreCase = true) }
        ?: character.voiceActors.firstOrNull()

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                AsyncImage(
                    model = character.image,
                    contentDescription = character.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    character.role?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (jpVa != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = jpVa.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                        jpVa.language?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!jpVa.image.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AsyncImage(
                            model = jpVa.image,
                            contentDescription = jpVa.name,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterDetailsSheetBody(
    isLoading: Boolean,
    character: MalCharacterDetails?,
    pictures: List<MalPicture>,
    onDismiss: () -> Unit,
    onOpenAnime: (Int, String, String?) -> Unit
) {
    var showSpoilers by remember(character?.characterId) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = KanimeSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = character?.name ?: stringResource(R.string.detail_character_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading || character == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(KanimeSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                    ) {
                        if (!character.image.isNullOrBlank()) {
                            AsyncImage(
                                model = character.image,
                                contentDescription = character.name,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            character.nameKanji?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            character.favorites?.let {
                                Text(
                                    text = "%,d favorites".format(it),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (!character.about.isNullOrBlank()) {
                    item {
                        Text(
                            text = stringResource(R.string.detail_biography),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = character.about,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (character.spoilers.isNotEmpty()) {
                    item {
                        GlassButton(
                            text = if (showSpoilers) {
                                stringResource(R.string.detail_spoilers_hide)
                            } else {
                                stringResource(R.string.detail_spoilers_show, character.spoilers.size)
                            },
                            icon = if (showSpoilers) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            style = GlassButtonStyle.Tonal,
                            onClick = { showSpoilers = !showSpoilers }
                        )
                        if (showSpoilers) {
                            Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                            character.spoilers.forEach { spoiler ->
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Text(
                                        text = spoiler,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (pictures.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.detail_gallery),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)) {
                            items(pictures, key = { it.image }) { pic ->
                                AsyncImage(
                                    model = pic.image,
                                    contentDescription = character.name,
                                    modifier = Modifier
                                        .width(92.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }

                if (character.voiceActors.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.detail_voice_actors),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            character.voiceActors.take(10).forEach { va ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!va.image.isNullOrBlank()) {
                                            AsyncImage(
                                                model = va.image,
                                                contentDescription = va.name,
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        Column {
                                            Text(va.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                            va.language?.let {
                                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (character.animeography.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.detail_animeography),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            character.animeography.take(12).forEach { entry ->
                                Surface(
                                    onClick = {
                                        entry.animeId?.let { onOpenAnime(it, entry.title, entry.image) }
                                    },
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (!entry.image.isNullOrBlank()) {
                                            AsyncImage(
                                                model = entry.image,
                                                contentDescription = entry.title,
                                                modifier = Modifier
                                                    .width(38.dp)
                                                    .aspectRatio(2f / 3f)
                                                    .clip(MaterialTheme.shapes.extraSmall),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = entry.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            entry.role?.let {
                                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSongCard(
    prefix: String,
    theme: MalTheme,
    onSpotifyClick: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$prefix · ${theme.title}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (theme.artist.isNotBlank()) {
                    Text(
                        text = theme.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!theme.spotifyUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                GlassIconButton(
                    icon = Icons.Default.MusicNote,
                    contentDescription = "Listen",
                    size = 40.dp,
                    onClick = { onSpotifyClick(theme.spotifyUrl) }
                )
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: MalVideo,
    onClick: () -> Unit
) {
    val thumbUrl = video.youtubeId?.let { "https://img.youtube.com/vi/$it/hqdefault.jpg" }
    Column(
        modifier = Modifier
            .width(204.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (thumbUrl != null) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = video.label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            GlassSurface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp),
                shape = CircleShape,
                overMedia = true,
                showShadow = false
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.action_play),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = video.songTitle ?: video.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LinkChipSection(
    title: String,
    links: List<MalLink>,
    onLinkClick: (MalLink) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = KanimeSpacing.sm)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )
        Spacer(modifier = Modifier.height(KanimeSpacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = horizontalPadding)
        ) {
            links.forEach { link ->
                GlassChip(
                    label = link.name,
                    leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                    selected = false,
                    onClick = { onLinkClick(link) }
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: MalRecommendation,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = rec.image,
                contentDescription = rec.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = rec.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(name = "Detail Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Detail Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Detail Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Preview(name = "Detail Dark Foldable", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.FOLDABLE)
@Composable
private fun DetailScreenPreview() {
    val sample = AnimeRepository.sampleAnimes.first()
    val eps = listOf(
        Episode("1", 1, "The Journey's End", "24m", sample.bannerUrl),
        Episode("2", 2, "It Didn't Have to Be Magic", "24m", sample.bannerUrl)
    )
    KAnimeTheme(animateTransition = false) {
        KanimeAmbientBackdrop {
            DetailScreenContent(
                anime = sample,
                displayEpisodes = eps,
                isInWatchlist = true,
                isLoadingDetails = false,
                isLoadingExtras = false,
                extras = AnimeExtras(),
                selectedTab = 0,
                onSelectTab = {},
                sourceAvailability = SourceAvailabilityState.Available("KAnikoto", eps.size),
                activeSourceShortName = "KAnikoto",
                availablePlugins = emptyList(),
                activeSourceId = "kanikoto",
                showSourceSheet = false,
                onShowSourceSheet = {},
                showCharacterSheet = false,
                isLoadingCharacter = false,
                selectedCharacter = null,
                selectedCharacterPictures = emptyList(),
                onBackClick = {},
                onRefresh = {},
                onToggleWatchlist = {},
                onPlayEpisode = {},
                onSwitchPlugin = {},
                onOpenCharacter = {},
                onCloseCharacter = {},
                onOpenAnimeByMalId = { _, _, _ -> },
                onOpenExternalUrl = {}
            )
        }
    }
}
