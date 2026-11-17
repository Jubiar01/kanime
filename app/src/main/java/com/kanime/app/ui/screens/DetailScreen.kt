package com.kanime.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import com.kanime.app.data.plugin.AnimeSourcePlugin
import com.kanime.app.data.plugin.PluginBridge
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.components.EpisodeCard
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    anime: Anime,
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    val isInWatchlist = uiState.watchlistIds.contains(anime.id)
    val coroutineScope = rememberCoroutineScope()

    var anikotoEpisodes by remember { mutableStateOf<List<Episode>?>(null) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }

    // Source sheet state (Image 2)
    val availablePlugins by PluginManager.plugins.collectAsState()
    val activeSourceId by PluginManager.activeSourceId.collectAsState()

    var showSourceSheet by remember { mutableStateOf(false) }
    var extensionSearchQuery by remember { mutableStateOf("") }
    var selectedFramework by remember { mutableStateOf("All Sources") }

    // Fetch real episode list from KAnikoto Android library plugin if installed & enabled
    LaunchedEffect(anime.id, availablePlugins, activeSourceId) {
        val activePlugin = availablePlugins.firstOrNull { it.id == activeSourceId } ?: availablePlugins.firstOrNull()
        if (activePlugin == null || !activePlugin.isInstalled || !activePlugin.isEnabled) {
            anikotoEpisodes = null
            return@LaunchedEffect
        }
        coroutineScope.launch {
            isLoadingEpisodes = true
            try {
                val slug = PluginBridge.findSlug(context, anime.title)
                    ?: if (anime.japaneseTitle.isNotBlank()) PluginBridge.findSlug(context, anime.japaneseTitle) else null
                if (slug != null) {
                    val list = PluginBridge.getEpisodes(context, slug)
                    if (list.isNotEmpty()) {
                        anikotoEpisodes = list.map { anikotoEp ->
                            Episode(
                                id = anikotoEp.id,
                                episodeNumber = anikotoEp.num.toInt(),
                                title = anikotoEp.title,
                                duration = anime.duration,
                                thumbnail = anime.bannerUrl.ifBlank { anime.coverUrl },
                                description = "Streamed via KAnikoto Plugin"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingEpisodes = false
            }
        }
    }

    val displayEpisodes = anikotoEpisodes ?: anime.episodeList

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
                        // Refresh button (Image 2)
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isLoadingEpisodes = true
                                    try {
                                        val slug = PluginBridge.findSlug(context, anime.title)
                                            ?: if (anime.japaneseTitle.isNotBlank()) PluginBridge.findSlug(context, anime.japaneseTitle) else null
                                        if (slug != null) {
                                            val list = PluginBridge.getEpisodes(context, slug)
                                            if (list.isNotEmpty()) {
                                                anikotoEpisodes = list.map { anikotoEp ->
                                                    Episode(
                                                        id = anikotoEp.id,
                                                        episodeNumber = anikotoEp.num.toInt(),
                                                        title = anikotoEp.title,
                                                        duration = anime.duration,
                                                        thumbnail = anime.bannerUrl.ifBlank { anime.coverUrl },
                                                        description = "Streamed via KAnikoto Plugin"
                                                    )
                                                }
                                            }
                                        }
                                    } finally {
                                        isLoadingEpisodes = false
                                    }
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }

                        // Bookmark / Favorite button (Image 2)
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

                        // Source extensions button (Image 2)
                        IconButton(
                            onClick = { showSourceSheet = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Extension, contentDescription = "Source")
                        }

                        // More options
                        IconButton(
                            onClick = { },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Header Image with Gradient
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        AsyncImage(
                            model = anime.bannerUrl,
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
                                            Color.Black.copy(alpha = 0.4f),
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                        )
                    }
                }

                // Anime Meta Info
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = anime.japaneseTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Badges row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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

                            Text(
                                text = "${anime.season} ${anime.year} • ${anime.episodes} Ep",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Clickable Source badge opens Source sheet (Image 2)
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Genres Chips
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            anime.genres.forEach { genre ->
                                SuggestionChip(
                                    onClick = { },
                                    label = { Text(genre, style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Primary Play Action Button
                        Button(
                            onClick = {
                                val firstEpisode = displayEpisodes.firstOrNull()
                                if (firstEpisode != null) {
                                    viewModel.playEpisode(anime, firstEpisode)
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
                                text = if (isLoadingEpisodes) "Syncing KAnikoto Episodes..." else "Start Watching Episode 1",
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tab Navigation (Episodes / Info)
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab,
                            containerColor = MaterialTheme.colorScheme.background,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            Tab(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                text = { Text("Episodes (${displayEpisodes.size})") }
                            )
                            Tab(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                text = { Text("Information") }
                            )
                        }
                    }
                }

                // Tab Content
                if (selectedTab == 0) {
                    items(displayEpisodes, key = { it.id }) { episode ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            EpisodeCard(
                                episode = episode,
                                onClick = { viewModel.playEpisode(anime, episode) }
                            )
                        }
                    }
                } else {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InfoRow(label = "Studio", value = anime.studio)
                            InfoRow(label = "Status", value = anime.status)
                            InfoRow(label = "Season", value = "${anime.season} ${anime.year}")
                            InfoRow(label = "Total Episodes", value = "${anime.episodes} Episodes")
                            InfoRow(label = "Episode Duration", value = "${anime.duration} per ep")
                            InfoRow(label = "Rating", value = "★ ${anime.score} / 10")
                        }
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // SOURCE BOTTOM SHEET (Matching Image 2)
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
                    // Drag handle
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

                    // Dynamic Available Plugin Category Chips
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

                    // Search extensions bar
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

                    // Filtered Available Plugins List
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
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
