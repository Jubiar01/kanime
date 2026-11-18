package com.kanime.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import com.kanime.app.data.model.MalCharacter
import com.kanime.app.data.model.MalLink
import com.kanime.app.data.model.MalRecommendation
import com.kanime.app.data.model.MalTheme
import com.kanime.app.data.model.MalVideo
import com.kanime.app.data.plugin.PluginBridge
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.components.EpisodeCard
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel
import kotlinx.coroutines.launch

private fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    anime: Anime,
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember(anime.id) { mutableIntStateOf(0) }
    var isSynopsisExpanded by remember(anime.id) { mutableStateOf(false) }
    val isInWatchlist = uiState.watchlistIds.contains(anime.id)
    val coroutineScope = rememberCoroutineScope()

    var anikotoEpisodes by remember(anime.id) { mutableStateOf<List<Episode>?>(null) }
    var isLoadingPluginEpisodes by remember(anime.id) { mutableStateOf(false) }

    val availablePlugins by PluginManager.plugins.collectAsState()
    val activeSourceId by PluginManager.activeSourceId.collectAsState()

    var showSourceSheet by remember { mutableStateOf(false) }
    var extensionSearchQuery by remember { mutableStateOf("") }
    var selectedFramework by remember { mutableStateOf("All Sources") }

    suspend fun fetchPluginEpisodes() {
        val activePlugin = availablePlugins.firstOrNull { it.id == activeSourceId } ?: availablePlugins.firstOrNull()
        if (activePlugin == null || !activePlugin.isInstalled || !activePlugin.isEnabled) {
            anikotoEpisodes = null
            return
        }
        isLoadingPluginEpisodes = true
        try {
            val titleCandidates = listOfNotNull(
                anime.englishTitle,
                anime.title,
                anime.romajiTitle,
                anime.japaneseTitle
            ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

            val authority = activePlugin.providerAuthority
            var slug: String? = null
            for (candidate in titleCandidates) {
                slug = PluginBridge.findSlug(context, candidate, authority = authority)
                if (slug != null) break
            }
            if (slug != null) {
                val list = PluginBridge.getEpisodes(context, slug, authority = authority)
                if (list.isNotEmpty()) {
                    anikotoEpisodes = list.map { anikotoEp ->
                        Episode(
                            id = anikotoEp.id,
                            episodeNumber = anikotoEp.num.toInt(),
                            title = anikotoEp.title,
                            duration = anime.duration,
                            thumbnail = anime.bannerUrl.ifBlank { anime.coverUrl },
                            description = "Streamed via ${activePlugin.name}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoadingPluginEpisodes = false
        }
    }

    // Fetch real playable episode list from KAnikoto Android library plugin if installed & enabled
    LaunchedEffect(anime.id, anime.englishTitle, anime.romajiTitle, availablePlugins, activeSourceId) {
        fetchPluginEpisodes()
    }

    // Lazily load MAL tab data when the user switches to Cast / Media / Info
    LaunchedEffect(selectedTab, anime.id, anime.malId) {
        when (selectedTab) {
            1 -> viewModel.loadCharactersForSelected()
            2 -> viewModel.loadMediaForSelected()
            3 -> viewModel.loadMoreForSelected()
        }
    }

    // Merge KAnikoto playable episodes with MAL/TMDB/Kitsu/AniList episode metadata & stills
    val catalogByNum = remember(anime.episodeList) {
        anime.episodeList.associateBy { it.episodeNumber }
    }
    val displayEpisodes = remember(anikotoEpisodes, anime.episodeList, catalogByNum) {
        val pluginEps = anikotoEpisodes
        if (pluginEps != null) {
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
        } else {
            anime.episodeList
        }
    }
    val animeForPlayback = remember(anime, displayEpisodes) {
        anime.copy(episodeList = displayEpisodes)
    }

    val extras = uiState.selectedAnimeExtras

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                viewModel.refreshSelectedAnime()
                                coroutineScope.launch { fetchPluginEpisodes() }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }

                        IconButton(
                            onClick = { viewModel.toggleWatchlist(anime.id) },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = if (isInWatchlist) MaterialTheme.colorScheme.primary else Color.White
                            )
                        ) {
                            Icon(
                                imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Watchlist"
                            )
                        }

                        IconButton(
                            onClick = { showSourceSheet = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Extension, contentDescription = "Source")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { _ ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Header Banner with Gradient + Optional TMDB Logo
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
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
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.45f),
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                        if (!anime.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = anime.logoUrl,
                                contentDescription = anime.title,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .height(64.dp)
                                    .widthIn(max = 260.dp),
                                contentScale = ContentScale.Fit,
                                alignment = Alignment.CenterStart
                            )
                        }
                    }
                }

                // Anime Meta Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        if (anime.japaneseTitle.isNotBlank()) {
                            Text(
                                text = anime.japaneseTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Badges row (horizontally scrollable to avoid clipping)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            if (anime.score > 0.0) {
                                Surface(
                                    color = Color(0xFFFFB400).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB400),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${anime.score}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFB400)
                                        )
                                    }
                                }
                            }

                            if (anime.rank != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "#${anime.rank}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = anime.status,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            val epLabel = if (anime.episodes > 0) "${anime.episodes} Ep" else "${displayEpisodes.size} Ep"
                            val yearLabel = if (anime.year > 0) "${anime.season} ${anime.year}" else anime.season
                            Text(
                                text = "$yearLabel • $epLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { showSourceSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Extension,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Source: KAnikoto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (uiState.isLoadingDetails) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Genres Chips (horizontally scrollable)
                        if (anime.genres.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                anime.genres.forEach { genre ->
                                    SuggestionChip(
                                        onClick = { },
                                        label = { Text(genre, style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Primary Play Action Button
                        Button(
                            onClick = {
                                val firstEpisode = displayEpisodes.firstOrNull()
                                if (firstEpisode != null) {
                                    viewModel.playEpisode(animeForPlayback, firstEpisode)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isLoadingPluginEpisodes -> "Syncing KAnikoto Episodes..."
                                    uiState.isLoadingCatalogEpisodes && displayEpisodes.isEmpty() -> "Loading Episodes..."
                                    else -> "Start Watching Episode 1"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Synopsis
                        Text(
                            text = "Synopsis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = anime.synopsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(
                            onClick = { isSynopsisExpanded = !isSynopsisExpanded },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (isSynopsisExpanded) "Show Less" else "Read More")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 4-Tab Navigation: Episodes | Cast | Media | Info
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Episodes (${displayEpisodes.size})", maxLines = 1) }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Cast", maxLines = 1) }
                            )
                            Tab(
                                selected = selectedTab == 2,
                                onClick = { selectedTab = 2 },
                                text = { Text("Media", maxLines = 1) }
                            )
                            Tab(
                                selected = selectedTab == 3,
                                onClick = { selectedTab = 3 },
                                text = { Text("Info", maxLines = 1) }
                            )
                        }
                    }
                }

                // ── Tab 0: Episodes ─────────────────────────────────────────
                if (selectedTab == 0) {
                    if (uiState.isLoadingCatalogEpisodes && displayEpisodes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        items(displayEpisodes, key = { "${it.id}_${it.episodeNumber}" }) { episode ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                EpisodeCard(
                                    episode = episode,
                                    onClick = { viewModel.playEpisode(animeForPlayback, episode) }
                                )
                            }
                        }
                    }
                }

                // ── Tab 1: Cast (MAL Characters + Voice Actors) ─────────────
                if (selectedTab == 1) {
                    if (uiState.isLoadingExtras && !extras.charactersLoaded) {
                        item {
                            LoadingTabPlaceholder("Loading MyAnimeList Cast...")
                        }
                    } else if (extras.characters.isEmpty()) {
                        item {
                            EmptyTabPlaceholder("No character data available on MyAnimeList.")
                        }
                    } else {
                        items(extras.characters, key = { it.characterId ?: it.name.hashCode() }) { character ->
                            CharacterRowCard(
                                character = character,
                                onClick = {
                                    character.characterId?.let { viewModel.openCharacter(it) }
                                }
                            )
                        }
                    }
                }

                // ── Tab 2: Media (Themes, Trailers/MVs, Pictures) ───────────
                if (selectedTab == 2) {
                    if (uiState.isLoadingExtras && !extras.mediaLoaded) {
                        item {
                            LoadingTabPlaceholder("Loading Themes, Trailers & Gallery...")
                        }
                    } else {
                        // Trailers & Music Videos
                        val allVideos = extras.videos.trailers + extras.videos.musicVideos
                        if (allVideos.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    Text(
                                        text = "Trailers & Music Videos (${allVideos.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(allVideos, key = { it.embedUrl }) { video ->
                                            VideoCard(
                                                video = video,
                                                onClick = {
                                                    val url = video.youtubeId?.let { "https://www.youtube.com/watch?v=$it" }
                                                        ?: video.embedUrl
                                                    openExternalUrl(context, url)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Opening & Ending Theme Songs
                        if (extras.themes.opening.isNotEmpty() || extras.themes.ending.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (extras.themes.opening.isNotEmpty()) {
                                        Text(
                                            text = "Opening Themes",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        extras.themes.opening.forEach { theme ->
                                            ThemeSongCard(
                                                prefix = "OP ${theme.number}",
                                                theme = theme,
                                                onSpotifyClick = { url -> openExternalUrl(context, url) }
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    if (extras.themes.ending.isNotEmpty()) {
                                        Text(
                                            text = "Ending Themes",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        extras.themes.ending.forEach { theme ->
                                            ThemeSongCard(
                                                prefix = "ED ${theme.number}",
                                                theme = theme,
                                                onSpotifyClick = { url -> openExternalUrl(context, url) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Pictures Gallery
                        if (extras.pictures.isNotEmpty()) {
                            item {
                                Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                                    Text(
                                        text = "Pictures (${extras.pictures.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(extras.pictures, key = { it.image }) { pic ->
                                            Card(
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .width(130.dp)
                                                    .height(185.dp)
                                                    .clickable { openExternalUrl(context, pic.image) }
                                            ) {
                                                AsyncImage(
                                                    model = pic.image,
                                                    contentDescription = "Anime Picture",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (allVideos.isEmpty() && extras.themes.opening.isEmpty() && extras.themes.ending.isEmpty() && extras.pictures.isEmpty()) {
                            item {
                                EmptyTabPlaceholder("No themes, trailers, or pictures found on MyAnimeList.")
                            }
                        }
                    }
                }

                // ── Tab 3: Info, Legal Streaming, External Links, Recommendations
                if (selectedTab == 3) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            anime.type?.let { InfoRow(label = "Type", value = it) }
                            InfoRow(label = "Studio", value = anime.studio)
                            anime.source?.let { InfoRow(label = "Source Material", value = it) }
                            InfoRow(label = "Status", value = anime.status)
                            anime.aired?.let { InfoRow(label = "Aired", value = it) }
                            InfoRow(label = "Season", value = if (anime.year > 0) "${anime.season} ${anime.year}" else anime.season)
                            InfoRow(label = "Total Episodes", value = if (anime.episodes > 0) "${anime.episodes} Episodes" else "Ongoing / Unknown")
                            InfoRow(label = "Episode Duration", value = anime.duration)
                            anime.rating?.let { InfoRow(label = "Age Rating", value = it) }
                            InfoRow(label = "MAL Score", value = if (anime.score > 0.0) "★ ${anime.score} / 10" else "N/A")
                            anime.rank?.let { InfoRow(label = "Ranked", value = "#$it") }
                            anime.popularity?.let { InfoRow(label = "Popularity", value = "#$it") }
                            anime.members?.let { InfoRow(label = "Members", value = "%,d".format(it)) }
                            anime.malId?.let { InfoRow(label = "MyAnimeList ID", value = it.toString()) }
                            anime.anilistId?.let { InfoRow(label = "AniList ID", value = it.toString()) }
                            if (!anime.posterSource.isNullOrBlank() || !anime.bannerSource.isNullOrBlank()) {
                                InfoRow(
                                    label = "Art Sources",
                                    value = "Poster: ${(anime.posterSource ?: "mal").uppercase()} • Banner: ${(anime.bannerSource ?: "mal").uppercase()}"
                                )
                            }
                        }
                    }

                    // Legal Streaming Platforms (from MAL)
                    val platforms = extras.streamingPlatforms.ifEmpty { anime.streamingPlatforms }
                    if (platforms.isNotEmpty()) {
                        item {
                            LinkChipSection(
                                title = "Official Streaming Platforms",
                                links = platforms,
                                onLinkClick = { openExternalUrl(context, it.url) }
                            )
                        }
                    }

                    // External Links (from MAL)
                    if (extras.externalLinks.isNotEmpty()) {
                        item {
                            LinkChipSection(
                                title = "External Links",
                                links = extras.externalLinks,
                                onLinkClick = { openExternalUrl(context, it.url) }
                            )
                        }
                    }

                    // Recommendations Carousel ("You Might Also Like")
                    if (uiState.isLoadingExtras && !extras.moreLoaded) {
                        item {
                            LoadingTabPlaceholder("Loading Recommendations & Links...")
                        }
                    } else if (extras.recommendations.isNotEmpty()) {
                        item {
                            Column(modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                                Text(
                                    text = "You Might Also Like",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(extras.recommendations, key = { it.animeId }) { rec ->
                                        RecommendationCard(
                                            rec = rec,
                                            onClick = {
                                                viewModel.openAnimeByMalId(rec.animeId, rec.title, rec.image)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // CHARACTER DETAILS BOTTOM SHEET (Single MAL Character Scrape)
        // ══════════════════════════════════════════════════════════════
        if (uiState.showCharacterSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { viewModel.closeCharacter() }
            )
        }

        AnimatedVisibility(
            visible = uiState.showCharacterSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            CharacterDetailsSheet(
                uiState = uiState,
                onDismiss = { viewModel.closeCharacter() },
                onOpenAnime = { malId, title, image ->
                    viewModel.closeCharacter()
                    viewModel.openAnimeByMalId(malId, title, image)
                }
            )
        }

        // ══════════════════════════════════════════════════════════════
        // SOURCE BOTTOM SHEET (KAnikoto Streaming Plugin Picker)
        // ══════════════════════════════════════════════════════════════
        if (showSourceSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showSourceSheet = false }
            )
        }

        AnimatedVisibility(
            visible = showSourceSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp)
                            .size(width = 44.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
                    )

                    Text(
                        text = "Source",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val categoryChips = remember(availablePlugins) {
                        listOf("All Sources", "Installed") + availablePlugins.map { it.name.substringBefore(" ") }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (categoryName in categoryChips.distinct()) {
                            val isChosen = selectedFramework == categoryName
                            FilterChip(
                                selected = isChosen,
                                onClick = { selectedFramework = categoryName },
                                label = { Text(categoryName) },
                                leadingIcon = {
                                    if (categoryName == "Installed") {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    } else if (categoryName == "All Sources") {
                                        Icon(Icons.Default.AllInclusive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    } else {
                                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = extensionSearchQuery,
                        onValueChange = { extensionSearchQuery = it },
                        placeholder = { Text("Search available plugins...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val displayedPlugins = availablePlugins.filter { p ->
                        val matchesQuery = extensionSearchQuery.isBlank() ||
                            p.name.contains(extensionSearchQuery, ignoreCase = true) ||
                            p.description.contains(extensionSearchQuery, ignoreCase = true)
                        val matchesCategory = when (selectedFramework) {
                            "Installed" -> p.isInstalled
                            "All Sources" -> true
                            else -> p.name.contains(selectedFramework, ignoreCase = true)
                        }
                        matchesQuery && matchesCategory
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp)
                    ) {
                        items(displayedPlugins) { plugin ->
                            val isActive = activeSourceId == plugin.id
                            Surface(
                                onClick = {
                                    PluginManager.setActiveSource(plugin.id)
                                    showSourceSheet = false
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = plugin.name,
                                                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = if (plugin.isInstalled) Color(0xFF4CAF50).copy(alpha = 0.2f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (plugin.isInstalled) "INSTALLED" else "NOT INSTALLED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = if (plugin.isInstalled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${plugin.packageName} • v${plugin.version}",
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    if (isActive) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
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
private fun LoadingTabPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(36.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyTabPlaceholder(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CharacterRowCard(
    character: MalCharacter,
    onClick: () -> Unit
) {
    val jpVa = character.voiceActors.firstOrNull { it.language.equals("Japanese", ignoreCase = true) }
        ?: character.voiceActors.firstOrNull()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                        .size(52.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!character.role.isNullOrBlank()) {
                        Text(
                            text = character.role,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (jpVa != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = jpVa.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!jpVa.language.isNullOrBlank()) {
                            Text(
                                text = jpVa.language,
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
                                .size(42.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterDetailsSheet(
    uiState: AnimeUiState,
    onDismiss: () -> Unit,
    onOpenAnime: (Int, String, String?) -> Unit
) {
    var showSpoilers by remember(uiState.selectedCharacter?.characterId) { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.82f),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Character Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (uiState.isLoadingCharacter) {
                LoadingTabPlaceholder("Loading character from MyAnimeList...")
            } else {
                val char = uiState.selectedCharacter
                if (char == null) {
                    EmptyTabPlaceholder("Character details not found on MyAnimeList.")
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = char.image,
                                    contentDescription = char.name,
                                    modifier = Modifier
                                        .width(90.dp)
                                        .height(126.dp)
                                        .clip(RoundedCornerShape(14.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = char.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (!char.nameKanji.isNullOrBlank()) {
                                        Text(
                                            text = char.nameKanji,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (char.favorites != null) {
                                        Text(
                                            text = "♥ %,d Favorites".format(char.favorites),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (!char.about.isNullOrBlank()) {
                            item {
                                Text(
                                    text = "Biography",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = char.about,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (char.spoilers.isNotEmpty()) {
                            item {
                                OutlinedButton(
                                    onClick = { showSpoilers = !showSpoilers },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showSpoilers) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (showSpoilers) "Hide Spoilers (${char.spoilers.size})" else "Show Spoilers (${char.spoilers.size})")
                                }
                                if (showSpoilers) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    char.spoilers.forEach { spoiler ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = spoiler,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.selectedCharacterPictures.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Gallery (${uiState.selectedCharacterPictures.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(uiState.selectedCharacterPictures, key = { it.image }) { pic ->
                                        AsyncImage(
                                            model = pic.image,
                                            contentDescription = char.name,
                                            modifier = Modifier
                                                .width(96.dp)
                                                .height(136.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }

                        if (char.voiceActors.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Voice Actors",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    char.voiceActors.take(10).forEach { va ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(10.dp),
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
                                                    Text(va.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
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

                        if (char.animeography.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Animeography",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    char.animeography.take(12).forEach { entry ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    entry.animeId?.let { onOpenAnime(it, entry.title, entry.image) }
                                                }
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
                                                            .width(40.dp)
                                                            .height(54.dp)
                                                            .clip(RoundedCornerShape(6.dp)),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = entry.title,
                                                        fontWeight = FontWeight.SemiBold,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    entry.role?.let {
                                                        Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
    }
}

@Composable
private fun ThemeSongCard(
    prefix: String,
    theme: MalTheme,
    onSpotifyClick: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
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
                    text = "$prefix • ${theme.title}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = buildString {
                    if (theme.artist.isNotBlank()) append("by ${theme.artist}")
                    if (!theme.episodes.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append("eps ${theme.episodes}")
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!theme.spotifyUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = { onSpotifyClick(theme.spotifyUrl) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Spotify", style = MaterialTheme.typography.labelSmall)
                }
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
    Card(
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .background(Color.Black)
            ) {
                if (thumbUrl != null) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = video.label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "Play Trailer",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.songTitle ?: video.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = video.songArtist ?: video.label
                if (sub.isNotBlank() && sub != (video.songTitle ?: video.label)) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkChipSection(
    title: String,
    links: List<MalLink>,
    onLinkClick: (MalLink) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            links.forEach { link ->
                AssistChip(
                    onClick = { onLinkClick(link) },
                    label = { Text(link.name, maxLines = 1) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
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
    Card(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                AsyncImage(
                    model = rec.image,
                    contentDescription = rec.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(topStart = 8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text(
                        text = "${rec.votes} recs",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = rec.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
