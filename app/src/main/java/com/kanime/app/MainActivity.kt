package com.kanime.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.* 
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.api.WatchHistoryStore
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.screens.*
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel
import com.kanime.app.ui.viewmodel.NavTab

class MainActivity : ComponentActivity() {

    private val viewModel: AnimeViewModel by viewModels()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipModeFlow: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    @Volatile
    var onUserLeaveHintPipHandler: (() -> Unit)? = null

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            PluginManager.checkPluginStatus(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize catalog config, persisted watchlist/history & plugin status sync
        CatalogConfig.init(applicationContext)
        WatchHistoryStore.init(applicationContext)
        PluginManager.checkPluginStatus(applicationContext)

        // Listen for real-time APK install / uninstall / update events
        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            packageFilter,
            ContextCompat.RECEIVER_EXPORTED
        )

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val isInPipMode by isInPipModeFlow.collectAsState()

            KAnimeTheme(darkTheme = uiState.isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KAnimeApp(
                        viewModel = viewModel,
                        uiState = uiState,
                        isInPipMode = isInPipMode
                    )
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Auto-enter PiP on Android 8.0–11 when pressing Home during active video playback
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            onUserLeaveHintPipHandler?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            _isInPipMode.value = isInPictureInPictureMode
        }
        // Immediately refresh plugin status when returning from system installer/uninstaller
        PluginManager.checkPluginStatus(applicationContext)
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KAnimeApp(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    isInPipMode: Boolean = false
) {
    // If an episode is playing, present the full-screen player directly on the Activity window
    val autoPlayNext by CatalogConfig.autoPlayNextFlow.collectAsState()
    val playing = uiState.playingEpisode
    if (playing != null) {
        val anime = playing.first
        val currentEp = playing.second
        val sortedEps = anime.episodeList.sortedBy { it.episodeNumber }
        val nextEp = sortedEps.firstOrNull { it.episodeNumber > currentEp.episodeNumber }
        val prevEp = sortedEps.lastOrNull { it.episodeNumber < currentEp.episodeNumber }

        PlayerDialog(
            anime = anime,
            episode = currentEp,
            initialPositionMs = uiState.playingInitialPositionMs,
            autoPlayNext = autoPlayNext,
            isInPipMode = isInPipMode,
            onToggleAutoPlayNext = { viewModel.setAutoPlayNext(it) },
            onSaveProgress = { posMs, durMs -> viewModel.saveWatchProgress(anime, currentEp, posMs, durMs) },
            onDismiss = { viewModel.closePlayer() },
            onNextEpisode = if (nextEp != null) {
                { viewModel.playEpisode(anime, nextEp) }
            } else null,
            onPreviousEpisode = if (prevEp != null) {
                { viewModel.playEpisode(anime, prevEp) }
            } else null,
            onSelectEpisode = { ep ->
                viewModel.playEpisode(anime, ep)
            }
        )
        return
    }

    // Intercept back button if Detail Screen is open
    BackHandler(enabled = uiState.selectedAnime != null) {
        viewModel.closeAnime()
    }

    Scaffold(
        topBar = {
            if (uiState.selectedAnime == null) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "K",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Anime",
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleTheme() }) {
                            Icon(
                                imageVector = if (uiState.isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle Theme",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.selectedAnime == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == NavTab.HOME,
                        onClick = { viewModel.selectTab(NavTab.HOME) },
                        icon = {
                            Icon(
                                if (uiState.currentTab == NavTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == NavTab.EXPLORE,
                        onClick = { viewModel.selectTab(NavTab.EXPLORE) },
                        icon = {
                            Icon(
                                if (uiState.currentTab == NavTab.EXPLORE) Icons.Filled.Explore else Icons.Outlined.Explore,
                                contentDescription = "Explore"
                            )
                        },
                        label = { Text("Explore") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == NavTab.WATCHLIST,
                        onClick = { viewModel.selectTab(NavTab.WATCHLIST) },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (uiState.watchlistIds.isNotEmpty()) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text("${uiState.watchlistIds.size}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    if (uiState.currentTab == NavTab.WATCHLIST) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Watchlist"
                                )
                            }
                        },
                        label = { Text("Watchlist") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )

                    NavigationBarItem(
                        selected = uiState.currentTab == NavTab.SETTINGS,
                        onClick = { viewModel.selectTab(NavTab.SETTINGS) },
                        icon = {
                            Icon(
                                if (uiState.currentTab == NavTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val selected = uiState.selectedAnime
            if (selected != null) {
                DetailScreen(
                    anime = selected,
                    viewModel = viewModel,
                    uiState = uiState,
                    onBackClick = { viewModel.closeAnime() }
                )
            } else {
                AnimatedContent(
                    targetState = uiState.currentTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "TabContent"
                ) { tab ->
                    when (tab) {
                        NavTab.HOME -> HomeScreen(
                            viewModel = viewModel,
                            uiState = uiState,
                            onSeeAllClick = {
                                viewModel.selectTab(NavTab.EXPLORE)
                            }
                        )
                        NavTab.EXPLORE -> ExploreScreen(
                            viewModel = viewModel,
                            uiState = uiState
                        )
                        NavTab.WATCHLIST -> WatchlistScreen(
                            viewModel = viewModel,
                            uiState = uiState
                        )
                        NavTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            uiState = uiState
                        )
                    }
                }
            }
        }
    }
}
