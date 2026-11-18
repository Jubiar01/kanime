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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.api.WatchHistoryStore
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.ui.components.GlassBottomBar
import com.kanime.app.ui.components.GlassNavItem
import com.kanime.app.ui.components.GlassNavigationRail
import com.kanime.app.ui.components.GlassTopBar
import com.kanime.app.ui.screens.DetailScreen
import com.kanime.app.ui.screens.ExploreScreen
import com.kanime.app.ui.screens.HomeScreen
import com.kanime.app.ui.screens.OnboardingScreen
import com.kanime.app.ui.screens.PlayerDialog
import com.kanime.app.ui.screens.PluginsScreen
import com.kanime.app.ui.screens.SettingsScreen
import com.kanime.app.ui.screens.WatchlistScreen
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeImageLoader
import com.kanime.app.ui.theme.KanimeMotion
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.OnboardingPreferences
import com.kanime.app.ui.theme.ThemeMode
import com.kanime.app.ui.theme.ThemePreferences
import com.kanime.app.ui.theme.rememberReduceMotion
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel
import com.kanime.app.ui.viewmodel.NavTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AnimeViewModel by viewModels()

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipModeFlow: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    @Volatile
    var onUserLeaveHintPipHandler: (() -> Unit)? = null

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            lifecycleScope.launch {
                PluginManager.checkPluginStatusAsync(applicationContext, force = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize fast SharedPreferences flags & Coil singleton before first frame (zero flash)
        KanimeImageLoader.init(applicationContext)
        ThemePreferences.init(applicationContext)
        OnboardingPreferences.init(applicationContext)
        CatalogConfig.init(applicationContext)
        WatchHistoryStore.init(applicationContext)

        // Offload PackageManager + filesystem plugin inspection off the main thread
        lifecycleScope.launch {
            PluginManager.checkPluginStatusAsync(applicationContext, force = true)
        }

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
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val isInPipMode by isInPipModeFlow.collectAsStateWithLifecycle()
            val themeMode by ThemePreferences.themeModeFlow.collectAsStateWithLifecycle()
            val dynamicColor by ThemePreferences.dynamicColorFlow.collectAsStateWithLifecycle()
            val onboardingCompleted by OnboardingPreferences.isCompletedFlow.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val resolvedDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            KAnimeTheme(
                darkTheme = resolvedDarkTheme,
                dynamicColor = dynamicColor
            ) {
                if (!onboardingCompleted) {
                    OnboardingScreen(
                        onFinished = { OnboardingPreferences.markCompleted(applicationContext) }
                    )
                } else {
                    KAnimeApp(
                        viewModel = viewModel,
                        uiState = uiState,
                        isDarkTheme = resolvedDarkTheme,
                        dynamicColor = dynamicColor,
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
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            onUserLeaveHintPipHandler?.invoke()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            _isInPipMode.value = isInPictureInPictureMode
        }
        lifecycleScope.launch {
            PluginManager.checkPluginStatusAsync(applicationContext)
        }
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}

@Composable
fun KAnimeApp(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    isInPipMode: Boolean = false
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val autoPlayNext by CatalogConfig.autoPlayNextFlow.collectAsStateWithLifecycle()
    val playing = uiState.playingEpisode

    // Full-screen player is always presented in dark theme regardless of app theme,
    // while honoring the user's Dynamic Color setting.
    if (playing != null) {
        val anime = playing.first
        val currentEp = playing.second
        val sortedEps = anime.episodeList.sortedBy { it.episodeNumber }
        val nextEp = sortedEps.firstOrNull { it.episodeNumber > currentEp.episodeNumber }
        val prevEp = sortedEps.lastOrNull { it.episodeNumber < currentEp.episodeNumber }

        KAnimeTheme(
            darkTheme = true,
            dynamicColor = dynamicColor,
            updateSystemBars = false,
            animateTransition = false
        ) {
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
        }
        return
    }

    BackHandler(enabled = uiState.showPluginsScreen && uiState.selectedAnime == null) {
        viewModel.closePlugins()
    }
    BackHandler(enabled = uiState.selectedAnime != null) {
        viewModel.closeAnime()
    }

    KAnimeShell(
        currentTab = uiState.currentTab,
        watchlistCount = uiState.watchlistIds.size,
        showChrome = uiState.selectedAnime == null && !uiState.showPluginsScreen,
        isDarkTheme = isDarkTheme,
        onSelectTab = { viewModel.selectTab(it) },
        onToggleTheme = { ThemePreferences.cycleThemeMode(context, isDarkTheme) }
    ) { topInsetPadding, bottomInsetPadding ->
        val selected = uiState.selectedAnime
        if (selected != null) {
            DetailScreen(
                anime = selected,
                viewModel = viewModel,
                uiState = uiState,
                onBackClick = { viewModel.closeAnime() }
            )
        } else if (uiState.showPluginsScreen) {
            // Full-screen sub-page: shell top bar + bottom bar are hidden (showChrome = false)
            PluginsScreen(onBackClick = { viewModel.closePlugins() })
        } else {
            AnimatedContent(
                targetState = uiState.currentTab,
                transitionSpec = {
                    fadeIn(KanimeMotion.fadeInTween(reduceMotion)) togetherWith
                        fadeOut(KanimeMotion.fadeOutTween(reduceMotion))
                },
                label = "TabContent",
                modifier = Modifier.fillMaxSize()
            ) { tab ->
                when (tab) {
                    NavTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        topContentPadding = topInsetPadding,
                        bottomContentPadding = bottomInsetPadding,
                        onSeeAllClick = { viewModel.selectTab(NavTab.EXPLORE) }
                    )
                    NavTab.EXPLORE -> ExploreScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        topContentPadding = topInsetPadding,
                        bottomContentPadding = bottomInsetPadding
                    )
                    NavTab.WATCHLIST -> WatchlistScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        topContentPadding = topInsetPadding,
                        bottomContentPadding = bottomInsetPadding
                    )
                    NavTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        topContentPadding = topInsetPadding,
                        bottomContentPadding = bottomInsetPadding
                    )
                }
            }
        }
    }
}

@Composable
internal fun KAnimeShell(
    currentTab: NavTab,
    watchlistCount: Int,
    showChrome: Boolean,
    isDarkTheme: Boolean,
    onSelectTab: (NavTab) -> Unit,
    onToggleTheme: () -> Unit,
    content: @Composable (topContentPadding: androidx.compose.ui.unit.Dp, bottomContentPadding: androidx.compose.ui.unit.Dp) -> Unit
) {
    val windowSize = LocalWindowSizeClass.current
    val homeLabel = stringResource(R.string.nav_home)
    val exploreLabel = stringResource(R.string.nav_explore)
    val libraryLabel = stringResource(R.string.nav_watchlist)
    val settingsLabel = stringResource(R.string.nav_settings)

    val navItems = remember(watchlistCount, homeLabel, exploreLabel, libraryLabel, settingsLabel) {
        listOf(
            GlassNavItem(NavTab.HOME.name, homeLabel, Icons.Filled.Home, Icons.Outlined.Home),
            GlassNavItem(NavTab.EXPLORE.name, exploreLabel, Icons.Filled.Explore, Icons.Outlined.Explore),
            GlassNavItem(NavTab.WATCHLIST.name, libraryLabel, Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, badgeCount = watchlistCount),
            GlassNavItem(NavTab.SETTINGS.name, settingsLabel, Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    }

    val useRail = windowSize.useNavigationRail

    KanimeAmbientBackdrop {
        if (useRail && showChrome) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .displayCutoutPadding()
            ) {
                GlassNavigationRail(
                    items = navItems,
                    selectedId = currentTab.name,
                    onSelect = { id -> onSelectTab(NavTab.valueOf(id)) },
                    header = {
                        Text(
                            text = "K",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    footer = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = stringResource(R.string.action_toggle_theme),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                ) {
                    content(12.dp, 24.dp)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                val topPad = if (showChrome) 76.dp else 0.dp
                val bottomPad = if (showChrome) 104.dp else 0.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (showChrome) it.statusBarsPadding() else it }
                ) {
                    content(topPad, bottomPad)
                }

                AnimatedVisibility(
                    visible = showChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                    GlassTopBar(
                        title = {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(
                                        SpanStyle(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Black
                                        )
                                    ) {
                                        append("K")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    ) {
                                        append("anime")
                                    }
                                },
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        actions = {
                            IconButton(onClick = onToggleTheme) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = stringResource(R.string.action_toggle_theme),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    GlassBottomBar(
                        items = navItems,
                        selectedId = currentTab.name,
                        onSelect = { id -> onSelectTab(NavTab.valueOf(id)) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                    }
                }
            }
        }
    }
}

@Preview(name = "Shell Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Shell Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Shell Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Preview(name = "Shell Dark Foldable", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.FOLDABLE)
@Composable
private fun KAnimeShellPreview() {
    KAnimeTheme(animateTransition = false) {
        KAnimeShell(
            currentTab = NavTab.HOME,
            watchlistCount = 3,
            showChrome = true,
            isDarkTheme = isSystemInDarkTheme(),
            onSelectTab = {},
            onToggleTheme = {}
        ) { topPad, bottomPad ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPad, bottom = bottomPad),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Kanime",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
