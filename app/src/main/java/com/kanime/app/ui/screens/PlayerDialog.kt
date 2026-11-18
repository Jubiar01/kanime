@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.kanime.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.kanime.app.data.api.AnimeUpscalerMode
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import com.kanime.app.data.plugin.PluginBridge
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.data.plugin.PluginServer
import com.kanime.app.data.plugin.PluginStream
import com.kanime.app.ui.player.Anime4kVideoEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface StreamResolutionState {
    data class Loading(val message: String) : StreamResolutionState
    data class Success(val stream: PluginStream) : StreamResolutionState
    data class Error(val message: String) : StreamResolutionState
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Suppress("DEPRECATION")
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerDialog(
    anime: Anime,
    episode: Episode,
    autoPlayNext: Boolean = true,
    onToggleAutoPlayNext: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onPreviousEpisode: (() -> Unit)? = null,
    onSelectEpisode: ((Episode) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val availablePlugins by PluginManager.plugins.collectAsState()
    val activeSourceId by PluginManager.activeSourceId.collectAsState()

    var resolutionState by remember { mutableStateOf<StreamResolutionState>(StreamResolutionState.Loading("Resolving stream via KAnikoto plugin...")) }
    var availableServers by remember { mutableStateOf<List<PluginServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<PluginServer?>(null) }
    var selectedAudioType by remember { mutableStateOf("sub") } // "sub" or "dub"

    // Bottom sheets state
    var showStreamsSheet by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSubtitlesSheet by remember { mutableStateOf(false) }
    var showEnhanceSheet by remember { mutableStateOf(false) }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var selectedSubtitleTrack by remember { mutableStateOf("English [CC]") }
    var autoSelectStream by remember { mutableStateOf(true) }
    var extensionSearchQuery by remember { mutableStateOf("") }
    var selectedFramework by remember { mutableStateOf("All Sources") }

    // Persisted Anime4K Upscaler & Line Sharpening settings
    val animeUpscalerMode by CatalogConfig.animeUpscalerModeFlow.collectAsState()
    val lineSharpeningEnabled by CatalogConfig.animeLineSharpeningFlow.collectAsState()
    val lineSharpeningStrength by CatalogConfig.animeLineSharpeningStrengthFlow.collectAsState()
    val isVideoEnhanced = animeUpscalerMode != AnimeUpscalerMode.OFF || lineSharpeningEnabled
    val qualityBadgeText = when {
        animeUpscalerMode == AnimeUpscalerMode.ULTRA_4K -> "4K A4K"
        animeUpscalerMode == AnimeUpscalerMode.FAST -> "A4K 1.5x"
        lineSharpeningEnabled -> "SHARP"
        else -> "1080p"
    }

    // Double-tap seek feedback state (-10s left / +10s right, accumulates on rapid taps)
    var doubleTapSeekSide by remember { mutableStateOf<String?>(null) } // "left" or "right"
    var doubleTapSeekSeconds by remember { mutableIntStateOf(0) }
    var doubleTapSeekNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(doubleTapSeekNonce) {
        if (doubleTapSeekNonce > 0) {
            delay(850)
            doubleTapSeekSide = null
            doubleTapSeekSeconds = 0
        }
    }

    // ExoPlayer & Aspect ratio state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var aspectRatioToastMessage by remember { mutableStateOf<String?>(null) }
    var isEpisodeOrderReversed by remember { mutableStateOf(false) }

    // Auto-Play Next Episode state (reset per episode)
    val autoPlayTriggered = remember(anime.id, episode.episodeNumber) { mutableStateOf(false) }
    val latestAutoPlayNext by rememberUpdatedState(autoPlayNext)
    val latestOnNextEpisode by rememberUpdatedState(onNextEpisode)

    // Audio & Brightness Gestures (Image 6)
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var brightness by remember {
        mutableFloatStateOf(
            activity?.window?.attributes?.screenBrightness?.let { if (it < 0f) 0.5f else it } ?: 0.5f
        )
    }
    var volume by remember { mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var gestureType by remember { mutableStateOf<String?>(null) } // "brightness" or "volume"
    var gestureLevel by remember { mutableFloatStateOf(0f) }

    // Auto-dismiss gesture indicator
    LaunchedEffect(gestureType) {
        if (gestureType != null) {
            delay(1500)
            gestureType = null
        }
    }

    // Cycle aspect ratio: Fit -> Cover (Zoom) -> Stretch (Fill) -> Fit
    fun cycleAspectRatio() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> {
                aspectRatioToastMessage = "Cover (Fill Screen)"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> {
                aspectRatioToastMessage = "Stretch (Full)"
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            else -> {
                aspectRatioToastMessage = "Fit (Original 16:9)"
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    // Auto-dismiss aspect ratio toast after 2 seconds
    LaunchedEffect(aspectRatioToastMessage) {
        if (aspectRatioToastMessage != null) {
            delay(2000)
            aspectRatioToastMessage = null
        }
    }

    // Restore screen orientation when player dialog is dismissed
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto-hide controls timer (5 seconds)
    LaunchedEffect(showControls, isPlaying, isControlsLocked, showStreamsSheet, showSourceSheet, showSpeedSheet, showSubtitlesSheet, showEnhanceSheet) {
        if (showControls && isPlaying && !isControlsLocked && !showStreamsSheet && !showSourceSheet && !showSpeedSheet && !showSubtitlesSheet && !showEnhanceSheet) {
            delay(5000)
            showControls = false
        }
    }

    // Resolve stream via KAnikoto plugin through PluginBridge
    fun resolveStream(serverToUse: PluginServer? = null) {
        coroutineScope.launch {
            PluginManager.checkPluginStatus(context)
            val activePlugin = PluginManager.plugins.value.firstOrNull { it.id == activeSourceId }
                ?: PluginManager.plugins.value.firstOrNull()

            if (activePlugin == null || !activePlugin.isInstalled) {
                resolutionState = StreamResolutionState.Error("KAnikoto plugin is not installed. Go to Settings > Video Source Plugins to download and install it.")
                return@launch
            }
            if (!activePlugin.isEnabled) {
                resolutionState = StreamResolutionState.Error("${activePlugin.name} is turned off. Turn on the switch in Settings > Video Source Plugins to stream.")
                return@launch
            }

            resolutionState = StreamResolutionState.Loading("Resolving anime via ${activePlugin.name}...")
            try {
                val titleCandidates = listOfNotNull(
                    anime.englishTitle,
                    anime.title,
                    anime.romajiTitle,
                    anime.japaneseTitle
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                var slug: String? = null
                for (candidate in titleCandidates) {
                    slug = PluginBridge.findSlug(context, candidate)
                    if (slug != null) break
                }

                if (slug == null) {
                    resolutionState = StreamResolutionState.Error("Anime '${anime.title}' was not found in KAnikoto catalog.")
                    return@launch
                }

                resolutionState = StreamResolutionState.Loading("Fetching episode nodes...")
                val episodes = PluginBridge.getEpisodes(context, slug)
                val targetEp = episodes.firstOrNull { it.num == episode.episodeNumber.toDouble() }
                    ?: episodes.firstOrNull { Math.abs(it.num - episode.episodeNumber) < 0.1 }
                    ?: episodes.firstOrNull()

                if (targetEp == null) {
                    resolutionState = StreamResolutionState.Error("Episode ${episode.episodeNumber} not found on source.")
                    return@launch
                }

                resolutionState = StreamResolutionState.Loading("Loading server nodes...")
                val servers = PluginBridge.getServers(context, targetEp.id)
                availableServers = servers

                val sortedServers = servers.sortedWith(
                    compareByDescending<PluginServer> { it.type.equals(selectedAudioType, ignoreCase = true) }
                        .thenByDescending { !it.sourceId.startsWith("kiwi") }
                )

                val serversToTry = if (serverToUse != null) listOf(serverToUse) else sortedServers
                if (serversToTry.isEmpty()) {
                    resolutionState = StreamResolutionState.Error("No streaming servers available for this episode.")
                    return@launch
                }

                var foundStream: PluginStream? = null
                var foundServer: PluginServer? = null

                for (srv in serversToTry) {
                    resolutionState = StreamResolutionState.Loading("Connecting to ${srv.name} (${srv.type.uppercase()})...")
                    val stream = PluginBridge.getStream(context, srv.sourceId)
                    if (stream != null && !stream.m3u8.isNullOrBlank()) {
                        foundStream = stream
                        foundServer = srv
                        break
                    }
                }

                if (foundStream == null) {
                    for (srv in serversToTry) {
                        val stream = PluginBridge.getStream(context, srv.sourceId)
                        if (stream != null) {
                            foundStream = stream
                            foundServer = srv
                            break
                        }
                    }
                }

                if (foundStream != null && foundServer != null) {
                    selectedServer = foundServer
                    resolutionState = StreamResolutionState.Success(foundStream)
                } else {
                    resolutionState = StreamResolutionState.Error("Unable to connect to streaming servers. Please verify plugin is installed and active.")
                }
            } catch (e: Exception) {
                resolutionState = StreamResolutionState.Error("KAnikoto stream extraction error: ${e.message}")
            }
        }
    }

    LaunchedEffect(anime.id, episode.episodeNumber, selectedAudioType) {
        resolveStream()
    }

    // Native back press handling: in landscape, rotate back to portrait first; in portrait, close player
    BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            onDismiss()
        }
    }

    // ══════════════════════════════════════════════════════════════
    // ULTRA-RELIABLE IMMERSIVE MODE & DISPLAY CUTOUT HANDLING
    // (Fixes Image 5 status bar & letterbox notch issue on HyperOS / MIUI)
    // ══════════════════════════════════════════════════════════════
    DisposableEffect(activity, isLandscape) {
        val win = activity?.window
        if (win != null) {
            if (isLandscape) {
                win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = win.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    win.attributes = lp
                }
                win.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                win.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
                win.statusBarColor = android.graphics.Color.TRANSPARENT
                win.navigationBarColor = android.graphics.Color.TRANSPARENT

                WindowCompat.setDecorFitsSystemWindows(win, false)
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())

                val flags = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
                win.decorView.systemUiVisibility = flags
                win.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                    if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        win.decorView.systemUiVisibility = flags
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            } else {
                // Portrait mode: normal layout
                win.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                win.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                win.decorView.setOnSystemUiVisibilityChangeListener(null)
                WindowCompat.setDecorFitsSystemWindows(win, false)
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                val insetsController = WindowCompat.getInsetsController(w, w.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = w.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    w.attributes = lp
                }
                w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                w.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                w.decorView.setOnSystemUiVisibilityChangeListener(null)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val state = resolutionState) {
            is StreamResolutionState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFFA5B4FC),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = state.message,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFF333C57),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "KAnikoto Android Plugin",
                            color = Color(0xFFA5B4FC),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            is StreamResolutionState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Streaming Unavailable",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = { resolveStream() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry KAnikoto")
                        }
                        if (availableServers.isNotEmpty()) {
                            Button(onClick = { showStreamsSheet = true }) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Switch Stream")
                            }
                        }
                    }
                }
            }

            is StreamResolutionState.Success -> {
                val stream = state.stream
                val streamUrl = stream.m3u8 ?: stream.embedUrl

                val exoPlayer = remember(streamUrl) {
                    val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
                        .setDefaultRequestProperties(
                            mapOf(
                                "Referer" to (stream.referer ?: "https://anikototv.bz/"),
                                "Origin" to (stream.referer?.trimEnd('/') ?: "https://anikototv.bz")
                            )
                        )

                    val validSubtitles = stream.subtitles.filter { sub ->
                        !sub.url.contains("thumbnails", ignoreCase = true) &&
                        !sub.lang.contains("thumbnail", ignoreCase = true) &&
                        !sub.lang.contains("unknown", ignoreCase = true)
                    }

                    val subtitleConfigs = validSubtitles.map { sub ->
                        val langCode = when {
                            sub.lang.startsWith("Eng", ignoreCase = true) -> "en"
                            sub.lang.startsWith("Spa", ignoreCase = true) -> "es"
                            sub.lang.startsWith("Por", ignoreCase = true) -> "pt"
                            sub.lang.startsWith("Fre", ignoreCase = true) -> "fr"
                            else -> sub.lang.lowercase().take(3)
                        }
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                            .setMimeType(if (sub.url.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.TEXT_VTT)
                            .setLanguage(langCode)
                            .setLabel(sub.lang)
                            .setSelectionFlags(if (sub.isDefault || langCode == "en") C.SELECTION_FLAG_DEFAULT else 0)
                            .build()
                    }

                    val mediaSourceFactory = DefaultMediaSourceFactory(context)
                        .setDataSourceFactory(httpDataSourceFactory)

                    val trackSelector = DefaultTrackSelector(context).apply {
                        setParameters(
                            buildUponParameters()
                                .setPreferredTextLanguage("en")
                                .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE or C.ROLE_FLAG_CAPTION)
                                .setSelectUndeterminedTextLanguage(true)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
                        )
                    }

                    ExoPlayer.Builder(context)
                        .setTrackSelector(trackSelector)
                        .setMediaSourceFactory(mediaSourceFactory)
                        .build().apply {
                            val mediaItem = MediaItem.Builder()
                                .setUri(Uri.parse(streamUrl))
                                .setMimeType(if (streamUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else null)
                                .setSubtitleConfigurations(subtitleConfigs)
                                .build()

                            try {
                                val effects = if (animeUpscalerMode != AnimeUpscalerMode.OFF || lineSharpeningEnabled) {
                                    listOf(
                                        Anime4kVideoEffect(
                                            mode = animeUpscalerMode,
                                            lineSharpeningEnabled = lineSharpeningEnabled,
                                            lineSharpeningStrength = lineSharpeningStrength
                                        )
                                    )
                                } else {
                                    emptyList()
                                }
                                setVideoEffects(effects)
                            } catch (_: Throwable) {
                                // Gracefully fall back to standard rendering if GPU effect pipeline is unavailable
                            }

                            setMediaItem(mediaItem)
                            prepare()
                            playWhenReady = true

                            addListener(object : Player.Listener {
                                override fun onIsPlayingChanged(playing: Boolean) {
                                    isPlaying = playing
                                }

                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    if (playbackState == Player.STATE_ENDED) {
                                        if (latestAutoPlayNext && latestOnNextEpisode != null && !autoPlayTriggered.value) {
                                            autoPlayTriggered.value = true
                                            latestOnNextEpisode?.invoke()
                                        } else {
                                            showControls = true
                                        }
                                    }
                                }
                            })
                        }
                }

                // Live-apply Anime4K Upscaler / Line Sharpening changes mid-playback
                var hasAppliedInitialEffects by remember(exoPlayer) { mutableStateOf(false) }
                LaunchedEffect(exoPlayer, animeUpscalerMode, lineSharpeningEnabled, lineSharpeningStrength) {
                    if (!hasAppliedInitialEffects) {
                        hasAppliedInitialEffects = true
                        return@LaunchedEffect
                    }
                    try {
                        val savedPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val wasPlaying = exoPlayer.playWhenReady
                        val effects = if (animeUpscalerMode != AnimeUpscalerMode.OFF || lineSharpeningEnabled) {
                            listOf(
                                Anime4kVideoEffect(
                                    mode = animeUpscalerMode,
                                    lineSharpeningEnabled = lineSharpeningEnabled,
                                    lineSharpeningStrength = lineSharpeningStrength
                                )
                            )
                        } else {
                            emptyList()
                        }
                        exoPlayer.stop()
                        exoPlayer.setVideoEffects(effects)
                        exoPlayer.prepare()
                        exoPlayer.seekTo(savedPos)
                        exoPlayer.playWhenReady = wasPlaying
                    } catch (_: Throwable) {
                        // Ignore if device GPU cannot reconfigure video effects dynamically
                    }
                }

                fun performDoubleTapSeek(deltaSeconds: Int) {
                    val side = if (deltaSeconds < 0) "left" else "right"
                    val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                    val targetMs = if (maxDur > 0L) {
                        (exoPlayer.currentPosition + deltaSeconds * 1000L).coerceIn(0L, maxDur)
                    } else {
                        (exoPlayer.currentPosition + deltaSeconds * 1000L).coerceAtLeast(0L)
                    }
                    exoPlayer.seekTo(targetMs)
                    currentPositionMs = targetMs
                    doubleTapSeekSeconds = if (doubleTapSeekSide == side) {
                        doubleTapSeekSeconds + kotlin.math.abs(deltaSeconds)
                    } else {
                        kotlin.math.abs(deltaSeconds)
                    }
                    doubleTapSeekSide = side
                    doubleTapSeekNonce++
                }

                // Keep playback speed in sync
                LaunchedEffect(playbackSpeed) {
                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                }

                // Subtitle track selection
                LaunchedEffect(subtitlesEnabled, selectedSubtitleTrack) {
                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                    if (!subtitlesEnabled || selectedSubtitleTrack == "Off") {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    } else {
                        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        val targetLang = when {
                            selectedSubtitleTrack.startsWith("Eng", ignoreCase = true) -> "en"
                            selectedSubtitleTrack.startsWith("Spa", ignoreCase = true) -> "es"
                            selectedSubtitleTrack.startsWith("Por", ignoreCase = true) -> "pt"
                            selectedSubtitleTrack.startsWith("Fre", ignoreCase = true) -> "fr"
                            else -> selectedSubtitleTrack.lowercase().take(3)
                        }
                        builder.setPreferredTextLanguage(targetLang)
                        builder.setSelectUndeterminedTextLanguage(true)

                        for (group in exoPlayer.currentTracks.groups) {
                            if (group.type == C.TRACK_TYPE_TEXT) {
                                val trackGroup = group.mediaTrackGroup
                                for (i in 0 until trackGroup.length) {
                                    val format = trackGroup.getFormat(i)
                                    val label = format.label ?: format.language ?: ""
                                    if (label.contains(selectedSubtitleTrack, ignoreCase = true) ||
                                        (selectedSubtitleTrack.startsWith("Eng", ignoreCase = true) && (format.language == "en" || format.language == "eng"))
                                    ) {
                                        builder.setOverrideForType(TrackSelectionOverride(trackGroup, i))
                                        break
                                    }
                                }
                            }
                        }
                    }
                    exoPlayer.trackSelectionParameters = builder.build()
                }

                // Poll playback position
                LaunchedEffect(exoPlayer) {
                    while (true) {
                        currentPositionMs = exoPlayer.currentPosition
                        durationMs = exoPlayer.duration.coerceAtLeast(0L)
                        delay(350)
                    }
                }

                val playerView = remember(context) {
                    PlayerView(context).apply {
                        this.player = exoPlayer
                        useController = false
                        this.resizeMode = resizeMode
                        subtitleView?.apply {
                            setFractionalTextSize(0.053f)
                            setUserDefaultStyle()
                            setUserDefaultTextSize()
                            setApplyEmbeddedStyles(true)
                        }
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }

                DisposableEffect(exoPlayer) {
                    onDispose {
                        playerView.player = null
                        exoPlayer.release()
                    }
                }

                // Common Video Surface Composable
                @Composable
                fun VideoSurface(modifier: Modifier = Modifier) {
                    AndroidView(
                        factory = {
                            (playerView.parent as? ViewGroup)?.removeView(playerView)
                            playerView
                        },
                        update = { pv ->
                            if (pv.player != exoPlayer) pv.player = exoPlayer
                            pv.resizeMode = resizeMode
                        },
                        modifier = modifier
                    )
                }

                // ══════════════════════════════════════════════════════════════
                // CONDITIONAL LAYOUT: PORTRAIT (IMAGE 1) VS LANDSCAPE (IMAGE 4)
                // ══════════════════════════════════════════════════════════════
                if (!isLandscape) {
                    // ──────────────────────────────────────────────────────────
                    // PORTRAIT MODE (Image 1: Embedded 16:9 Video + Details & Episodes)
                    // ──────────────────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F0F12))
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        // Embedded 16:9 Video Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        ) {
                            VideoSurface(modifier = Modifier.fillMaxSize())

                            // Scrim overlay to intercept single taps (controls) and double-taps (-10s / +10s)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(exoPlayer) {
                                        detectTapGestures(
                                            onTap = {
                                                showControls = !showControls
                                            },
                                            onDoubleTap = { offset ->
                                                when {
                                                    offset.x < size.width * 0.45f -> performDoubleTapSeek(-10)
                                                    offset.x > size.width * 0.55f -> performDoubleTapSeek(+10)
                                                    else -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                }
                                            }
                                        )
                                    }
                            )

                            // Portrait Double-Tap Seek Feedback (-10s / +10s)
                            DoubleTapSeekOverlay(
                                side = doubleTapSeekSide,
                                seconds = doubleTapSeekSeconds,
                                compact = true
                            )

                            // Portrait Player Controls Overlay
                            androidx.compose.animation.AnimatedVisibility(
                                visible = showControls,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f))
                                ) {
                                    // Top Bar in Portrait: Back arrow, Server icon, 1080p (Image 1)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        IconButton(onClick = onDismiss) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            IconButton(onClick = { showStreamsSheet = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.FolderOpen,
                                                    contentDescription = "Streams",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Text(
                                                text = qualityBadgeText,
                                                color = if (isVideoEnhanced) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { showEnhanceSheet = true }
                                                    .padding(end = 8.dp)
                                            )
                                        }
                                    }

                                    // Center Play / Pause
                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                exoPlayer.pause()
                                            } else {
                                                if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                                    autoPlayTriggered.value = false
                                                    exoPlayer.seekTo(0L)
                                                }
                                                exoPlayer.play()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .align(Alignment.Center)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause" else "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(46.dp)
                                        )
                                    }

                                    // Bottom row inside video: Time on left, PiP and Fullscreen on right (Image 1)
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            activity?.enterPictureInPictureMode()
                                                        }
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PictureInPicture,
                                                        contentDescription = "PiP",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Fullscreen,
                                                        contentDescription = "Fullscreen",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Slider(
                                            value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                                            onValueChange = { ratio ->
                                                val newPos = (ratio * durationMs).toLong()
                                                exoPlayer.seekTo(newPos)
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(20.dp),
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFFA5B4FC),
                                                activeTrackColor = Color(0xFFA5B4FC),
                                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Scrollable Content Under Video (Matching Image 1)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Title & Subtitle (Image 1)
                            Text(
                                text = "Episode ${episode.episodeNumber}: ${episode.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = anime.title.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.60f)
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action Pills Row 1 (Image 1)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionPill(
                                    icon = Icons.Default.AutoAwesome,
                                    label = if (isVideoEnhanced) "Anime4K: ON" else "Anime4K",
                                    isActive = isVideoEnhanced
                                ) {
                                    showEnhanceSheet = true
                                }
                                ActionPill(
                                    icon = Icons.Default.SkipNext,
                                    label = if (autoPlayNext) "Auto-Play: ON" else "Auto-Play: OFF",
                                    isActive = autoPlayNext
                                ) {
                                    val nextState = !autoPlayNext
                                    onToggleAutoPlayNext?.invoke(nextState)
                                    aspectRatioToastMessage = if (nextState) "Auto-Play Next Episode enabled" else "Auto-Play Next Episode disabled"
                                }
                                ActionPill(icon = Icons.Default.Speed, label = "Playback speed") {
                                    showSpeedSheet = true
                                }
                                ActionPill(
                                    icon = Icons.Default.Subtitles,
                                    label = "Subtitles",
                                    isActive = subtitlesEnabled
                                ) {
                                    showSubtitlesSheet = true
                                }
                                ActionPill(
                                    icon = Icons.Default.Headphones,
                                    label = "Audio",
                                    isActive = selectedAudioType == "sub"
                                ) {
                                    selectedAudioType = if (selectedAudioType == "sub") "dub" else "sub"
                                }
                                ActionPill(
                                    icon = Icons.Default.FolderOpen,
                                    label = "Streams",
                                    isActive = showStreamsSheet
                                ) {
                                    showStreamsSheet = true
                                }
                                ActionPill(icon = Icons.Default.Fullscreen, label = "Fullscreen") {
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                                ActionPill(icon = Icons.Default.FormatListBulleted, label = "Chapters") {
                                    aspectRatioToastMessage = "No chapters for this episode"
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Pills Row 2 (Image 1)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionPill(
                                    icon = Icons.Default.Extension,
                                    label = "Source: KAnikoto",
                                    isActive = showSourceSheet
                                ) {
                                    showSourceSheet = true
                                }
                                ActionPill(icon = Icons.Default.AspectRatio, label = "Picture") {
                                    cycleAspectRatio()
                                }
                                ActionPill(icon = Icons.Default.CloudUpload, label = "Submit skip times") {
                                    aspectRatioToastMessage = "Skip times submitted"
                                }
                                ActionPill(icon = Icons.Default.Group, label = "Together") {
                                    aspectRatioToastMessage = "Watch Together session started"
                                }
                                ActionPill(icon = Icons.Default.Cast, label = "Cast to device") {
                                    aspectRatioToastMessage = "Searching for Google Cast devices..."
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.8.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // "Show comments >" Strip (Image 1)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Show comments",
                                        color = Color.White.copy(alpha = 0.85f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.12f), thickness = 0.8.dp)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Episodes Section Header (Image 1: "25 Episodes" + action icons)
                            val episodeList = if (isEpisodeOrderReversed) anime.episodeList.reversed() else anime.episodeList
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${anime.episodeList.size} Episodes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(onClick = {
                                        aspectRatioToastMessage = "Auto-skip intro enabled"
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Smart Skip",
                                            tint = Color(0xFFA5B4FC),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(onClick = { isEpisodeOrderReversed = !isEpisodeOrderReversed }) {
                                        Icon(
                                            imageVector = Icons.Default.Sort,
                                            contentDescription = "Sort",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Episode Cards
                            episodeList.forEach { ep ->
                                val isCurrent = ep.episodeNumber == episode.episodeNumber
                                Surface(
                                    onClick = {
                                        if (!isCurrent) {
                                            onSelectEpisode?.invoke(ep)
                                        }
                                    },
                                    color = if (isCurrent) Color(0xFF28283E) else Color(0xFF18181E),
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (isCurrent) BorderStroke(1.5.dp, Color(0xFFA5B4FC)) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 80.dp, height = 48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF282830))
                                        ) {
                                            AsyncImage(
                                                model = ep.thumbnail.ifBlank { anime.coverUrl },
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            if (isCurrent) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(Color.Black.copy(alpha = 0.45f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color(0xFFA5B4FC),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Episode ${ep.episodeNumber}",
                                                color = if (isCurrent) Color(0xFFA5B4FC) else Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = ep.title,
                                                color = Color.White.copy(alpha = 0.65f),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ──────────────────────────────────────────────────────────
                    // LANDSCAPE MODE (Image 4 & Image 6: 100% Immersive Fullscreen HUD)
                    // ──────────────────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        VideoSurface(modifier = Modifier.fillMaxSize())

                        // Scrim to catch vertical drag gestures, single-taps, and double-taps (-10s / +10s)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(exoPlayer, isControlsLocked) {
                                    detectTapGestures(
                                        onTap = {
                                            if (!isControlsLocked) {
                                                showControls = !showControls
                                            } else {
                                                showControls = true
                                            }
                                        },
                                        onDoubleTap = { offset ->
                                            if (!isControlsLocked) {
                                                when {
                                                    offset.x < size.width * 0.45f -> performDoubleTapSeek(-10)
                                                    offset.x > size.width * 0.55f -> performDoubleTapSeek(+10)
                                                    else -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                                }
                                            }
                                        }
                                    )
                                }
                                .pointerInput(isControlsLocked) {
                                    detectVerticalDragGestures(
                                        onDragStart = { offset ->
                                            if (isControlsLocked) return@detectVerticalDragGestures
                                            if (offset.x < size.width * 0.45f) {
                                                gestureType = "brightness"
                                                gestureLevel = brightness
                                            } else if (offset.x > size.width * 0.55f) {
                                                gestureType = "volume"
                                                gestureLevel = volume.toFloat() / maxVolume.toFloat()
                                            }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (isControlsLocked) return@detectVerticalDragGestures
                                            change.consume()
                                            if (gestureType == "brightness") {
                                                brightness = (brightness - (dragAmount / 350f)).coerceIn(0.01f, 1.0f)
                                                gestureLevel = brightness
                                                activity?.window?.attributes = activity.window.attributes.apply {
                                                    screenBrightness = brightness
                                                }
                                            } else if (gestureType == "volume") {
                                                val step = - (dragAmount / 25f).toInt()
                                                val newVol = (volume + step).coerceIn(0, maxVolume)
                                                if (newVol != volume) {
                                                    volume = newVol
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                                    gestureLevel = volume.toFloat() / maxVolume.toFloat()
                                                }
                                            }
                                        }
                                    )
                                }
                        )

                        // Landscape Double-Tap Seek Feedback (-10s / +10s)
                        DoubleTapSeekOverlay(
                            side = doubleTapSeekSide,
                            seconds = doubleTapSeekSeconds,
                            compact = false
                        )

                        // ══════════════════════════════════════════════════════════════
                        // VERTICAL GESTURE INDICATOR OVERLAYS (Matching Image 6)
                        // ══════════════════════════════════════════════════════════════
                        AnimatedVisibility(
                            visible = gestureType != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier
                                .align(if (gestureType == "brightness") Alignment.CenterStart else Alignment.CenterEnd)
                                .padding(horizontal = 40.dp)
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (gestureType == "brightness") Icons.Default.BrightnessMedium else Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color(0xFFA5B4FC),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(110.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(Color.White.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(gestureLevel.coerceIn(0f, 1f))
                                                .background(Color(0xFFA5B4FC))
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "${(gestureLevel * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // ══════════════════════════════════════════════════════════════
                        // NATIVE JETPACK COMPOSE LANDSCAPE HUD (Exact Image 4)
                        // ══════════════════════════════════════════════════════════════
                        AnimatedVisibility(
                            visible = showControls,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = if (isControlsLocked) 0.05f else 0.40f))
                            ) {
                                if (isControlsLocked) {
                                    IconButton(
                                        onClick = { isControlsLocked = false },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 20.dp, end = 36.dp)
                                            .size(48.dp)
                                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = "Unlock", tint = Color.White)
                                    }
                                } else {
                                    // ── Top Bar (Image 4) ──────────────────────────────────
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.TopCenter)
                                            .padding(horizontal = 36.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Down-arrow / collapse button (Image 4 top-left)
                                        IconButton(
                                            onClick = {
                                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Collapse",
                                                tint = Color.White,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Episode & Anime Title (Image 4)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Episode ${episode.episodeNumber}: ${episode.title}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = anime.title.uppercase(),
                                                color = Color.White.copy(alpha = 0.65f),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Top Right Actions (Image 4): PiP, Speed, Lock, and 1080p
                                        Column(horizontalAlignment = Alignment.End) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { showEnhanceSheet = true },
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.AutoAwesome,
                                                        contentDescription = "Anime4K Upscaler",
                                                        tint = if (isVideoEnhanced) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.65f),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val nextState = !autoPlayNext
                                                        onToggleAutoPlayNext?.invoke(nextState)
                                                        aspectRatioToastMessage = if (nextState) "Auto-Play: ON" else "Auto-Play: OFF"
                                                    },
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.PlaylistPlay,
                                                        contentDescription = "Auto-Play Next",
                                                        tint = if (autoPlayNext) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.45f),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            activity?.enterPictureInPictureMode()
                                                        }
                                                    },
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PictureInPictureAlt,
                                                        contentDescription = "PiP",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { showSpeedSheet = true },
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Speed,
                                                        contentDescription = "Speed",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = { isControlsLocked = true },
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.LockOpen,
                                                        contentDescription = "Lock",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = qualityBadgeText,
                                                color = if (isVideoEnhanced) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clickable { showEnhanceSheet = true }
                                                    .padding(end = 8.dp)
                                            )
                                        }
                                    }

                                    // ── Center Playback Controls (Image 4) ────────────────
                                    Row(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(48.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onPreviousEpisode?.invoke() },
                                            enabled = onPreviousEpisode != null,
                                            modifier = Modifier.size(54.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SkipPrevious,
                                                contentDescription = "Previous Episode",
                                                tint = if (onPreviousEpisode != null) Color.White else Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }

                                        // Clean White Play/Pause (|| / ▶) - matching Image 4
                                        IconButton(
                                            onClick = {
                                                if (isPlaying) {
                                                    exoPlayer.pause()
                                                } else {
                                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                                        autoPlayTriggered.value = false
                                                        exoPlayer.seekTo(0L)
                                                    }
                                                    exoPlayer.play()
                                                }
                                            },
                                            modifier = Modifier.size(72.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(56.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = { onNextEpisode?.invoke() },
                                            enabled = onNextEpisode != null,
                                            modifier = Modifier.size(54.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.SkipNext,
                                                contentDescription = "Next Episode",
                                                tint = if (onNextEpisode != null) Color.White else Color.White.copy(alpha = 0.3f),
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    }

                                    // ── Bottom Section (Image 4 & Image 6) ────────────────
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .padding(start = 36.dp, end = 36.dp, top = 6.dp, bottom = 24.dp)
                                    ) {
                                        // Row 1: Time on left (06:04 / 23:50), +10s pill on right (Image 4)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            // Outlined +10s pill button (Image 4)
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(20.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                                    .background(Color.Black.copy(alpha = 0.25f))
                                                    .clickable {
                                                        val nextPos = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                                        exoPlayer.seekTo(nextPos)
                                                    }
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.FastForward,
                                                        contentDescription = "+10s",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "+10s",
                                                        color = Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        // Row 2: Full width progress slider (Image 4)
                                        Slider(
                                            value = if (durationMs > 0) currentPositionMs.toFloat() / durationMs.toFloat() else 0f,
                                            onValueChange = { ratio ->
                                                val newPos = (ratio * durationMs).toLong()
                                                exoPlayer.seekTo(newPos)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(26.dp),
                                            colors = SliderDefaults.colors(
                                                thumbColor = Color(0xFFA5B4FC),
                                                activeTrackColor = Color(0xFFA5B4FC),
                                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                            )
                                        )

                                        // Row 3: Bottom Action Row (Image 4 & Image 6)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Left group of 4 icons (Image 4)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                // Icon 1: Video Settings / Speed [▶⚙]
                                                IconButton(
                                                    onClick = { showSpeedSheet = true },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Tune,
                                                        contentDescription = "Player Settings",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                // Icon 2: Folder / Streams list [📁] (Image 1 trigger)
                                                IconButton(
                                                    onClick = { showStreamsSheet = true },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.FolderOpen,
                                                        contentDescription = "Streams",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                // Icon 3: Subtitles / CC [💳] (With active container pill in Image 6)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (subtitlesEnabled) Color(0xFFA5B4FC).copy(alpha = 0.25f) else Color.Transparent)
                                                ) {
                                                    IconButton(
                                                        onClick = { showSubtitlesSheet = true },
                                                        modifier = Modifier.size(44.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Subtitles,
                                                            contentDescription = "Subtitles",
                                                            tint = if (subtitlesEnabled) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.6f),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }

                                                // Icon 4: Audio track [🎵] (With active container pill in Image 6)
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (selectedAudioType == "sub") Color(0xFFA5B4FC).copy(alpha = 0.25f) else Color.Transparent)
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            selectedAudioType = if (selectedAudioType == "sub") "dub" else "sub"
                                                        },
                                                        modifier = Modifier.size(44.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.MusicNote,
                                                            contentDescription = "Audio Sub/Dub",
                                                            tint = if (selectedAudioType == "sub") Color(0xFFA5B4FC) else Color.White,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Right group of 3 icons (Image 4)
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                // Icon 1: PiP [⧉]
                                                IconButton(
                                                    onClick = {
                                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                                            activity?.enterPictureInPictureMode()
                                                        }
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PictureInPicture,
                                                        contentDescription = "Picture-in-Picture",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                // Icon 2: Aspect ratio toggle [ ⛶ ]
                                                IconButton(
                                                    onClick = { cycleAspectRatio() },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.AspectRatio,
                                                        contentDescription = "Aspect Ratio",
                                                        tint = if (resizeMode != AspectRatioFrameLayout.RESIZE_MODE_FIT) Color(0xFFA5B4FC) else Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                // Icon 3: Fullscreen exit [ ⛶ ] -> switches back to portrait
                                                IconButton(
                                                    onClick = {
                                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FullscreenExit,
                                                        contentDescription = "Exit Fullscreen",
                                                        tint = Color.White,
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
        }

        // ══════════════════════════════════════════════════════════════
        // FLOATING ASPECT RATIO TOAST OVERLAY
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = aspectRatioToastMessage != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 70.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = Color(0xFFA5B4FC),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = aspectRatioToastMessage ?: "",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // BOTTOM SHEETS BACKDROP
        // ══════════════════════════════════════════════════════════════
        if (showStreamsSheet || showSourceSheet || showSpeedSheet || showSubtitlesSheet || showEnhanceSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(15f)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable {
                        showStreamsSheet = false
                        showSourceSheet = false
                        showSpeedSheet = false
                        showSubtitlesSheet = false
                        showEnhanceSheet = false
                    }
            )
        }

        // ══════════════════════════════════════════════════════════════
        // STREAMS BOTTOM SHEET (Exact Match to Image 1)
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showStreamsSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(if (isLandscape) 0.88f else 0.65f),
                color = Color(0xFF1E1E24),
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
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )

                    Text(
                        text = "Streams",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Auto select stream switch (Image 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto select stream",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Pick the stream most like your last choice for this title",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = autoSelectStream,
                            onCheckedChange = { autoSelectStream = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFA5B4FC)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val grouped = if (availableServers.isNotEmpty()) {
                        availableServers.groupBy { it.name }
                    } else {
                        mapOf(
                            "HD-1" to listOf(
                                PluginServer("HD-1", "hd1-sub", "sub"),
                                PluginServer("HD-1", "hd1-dub", "dub")
                            ),
                            "Vidstream-2" to listOf(
                                PluginServer("Vidstream-2", "vid2-sub", "sub"),
                                PluginServer("Vidstream-2", "vid2-dub", "dub")
                            )
                        )
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                    ) {
                        grouped.forEach { (serverName, srvList) ->
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$serverName  ${srvList.size} streams",
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            items(srvList) { srv ->
                                val isSelected = selectedServer?.sourceId == srv.sourceId ||
                                        (selectedServer == null && srv.name == "HD-1" && srv.type == selectedAudioType)

                                Surface(
                                    onClick = {
                                        selectedServer = srv
                                        showStreamsSheet = false
                                        resolveStream(srv)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFFA5B4FC) else Color.White,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = "${srv.name} - ${srv.type.replaceFirstChar { it.uppercase() }} - 1080p",
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = Color(0xFFA5B4FC),
                                                modifier = Modifier.size(22.dp)
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

        // ══════════════════════════════════════════════════════════════
        // SUBTITLES BOTTOM SHEET
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showSubtitlesSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(if (isLandscape) 0.82f else 0.55f),
                color = Color(0xFF1E1E24),
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
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )

                    Text(
                        text = "Subtitles & Captions",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Subtitles",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (subtitlesEnabled) "Captions are currently visible" else "Captions are turned off",
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = subtitlesEnabled,
                            onCheckedChange = { subtitlesEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFA5B4FC)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val subOptions = mutableListOf("English [CC]", "Off")
                    if (resolutionState is StreamResolutionState.Success) {
                        (resolutionState as StreamResolutionState.Success).stream.subtitles.forEach { sub ->
                            if (!subOptions.contains(sub.lang)) {
                                subOptions.add(0, sub.lang)
                            }
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                    ) {
                        items(subOptions.distinct()) { opt ->
                            val isSelected = (opt == "Off" && !subtitlesEnabled) || (opt != "Off" && subtitlesEnabled && selectedSubtitleTrack.equals(opt, ignoreCase = true))

                            Surface(
                                onClick = {
                                    if (opt == "Off") {
                                        subtitlesEnabled = false
                                    } else {
                                        subtitlesEnabled = true
                                        selectedSubtitleTrack = opt
                                    }
                                    showSubtitlesSheet = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Subtitles,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFA5B4FC) else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = opt,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = Color(0xFFA5B4FC),
                                            modifier = Modifier.size(22.dp)
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
        // SOURCE BOTTOM SHEET (Exact Match to Image 2)
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showSourceSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(if (isLandscape) 0.88f else 0.70f),
                color = Color(0xFF1E1E24),
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
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )

                    Text(
                        text = "Source",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Extension Provider Tabs (Image 2: Mangayomi, Sora, Aniyomi, CloudStream)
                    val providerTabs = listOf("Mangayomi", "Sora", "Aniyomi", "CloudStream")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (tab in providerTabs) {
                            val isChosen = selectedFramework == tab
                            FilterChip(
                                selected = isChosen,
                                onClick = { selectedFramework = tab },
                                label = { Text(tab) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF333C57),
                                    selectedLabelColor = Color(0xFFA5B4FC),
                                    containerColor = Color(0xFF282830),
                                    labelColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search extensions bar (Image 2)
                    OutlinedTextField(
                        value = extensionSearchQuery,
                        onValueChange = { extensionSearchQuery = it },
                        placeholder = { Text("Search extensions...", color = Color.White.copy(alpha = 0.5f)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF282830),
                            unfocusedContainerColor = Color(0xFF282830),
                            focusedBorderColor = Color(0xFFA5B4FC),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Extension Sources List (Image 2)
                    val extensionsList = remember {
                        listOf(
                            ExtensionItem("kanikoto", "Anikoto", "EN", true),
                            ExtensionItem("anilist", "AniList", "EN", false),
                            ExtensionItem("animegg", "AnimeGG", "EN", false),
                            ExtensionItem("123anime", "123Anime", "EN", false),
                            ExtensionItem("animekai", "AnimeKai (Unoriginal)", "EN", false),
                            ExtensionItem("reanime", "Re:ANIME", "EN", false),
                            ExtensionItem("donghua", "DonghuaStream", "EN", false),
                            ExtensionItem("anidb", "AniDB", "EN", false),
                            ExtensionItem("anipm", "AniPM", "EN", false),
                            ExtensionItem("animepahe", "AnimePahe", "EN", false)
                        )
                    }

                    val filteredExtensions = extensionsList.filter {
                        extensionSearchQuery.isBlank() || it.name.contains(extensionSearchQuery, ignoreCase = true)
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)
                    ) {
                        items(filteredExtensions) { ext ->
                            val isSelected = ext.id == activeSourceId || (activeSourceId == "kanikoto" && ext.id == "kanikoto")
                            Surface(
                                onClick = {
                                    PluginManager.setActiveSource(ext.id)
                                    showSourceSheet = false
                                    resolveStream()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (isSelected) Color(0xFF2196F3) else Color(0xFF37474F),
                                            shape = CircleShape,
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column {
                                            Text(
                                                text = ext.name,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = ext.lang,
                                                color = Color.White.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = Color(0xFFA5B4FC),
                                            modifier = Modifier.size(22.dp)
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
        // SPEED BOTTOM SHEET
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showSpeedSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                color = Color(0xFF1E1E24),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp)
                            .size(width = 44.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )

                    Text(
                        text = "Playback Speed",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    speeds.forEach { spd ->
                        val isSelected = playbackSpeed == spd
                        Surface(
                            onClick = {
                                playbackSpeed = spd
                                showSpeedSheet = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (spd == 1.0f) "1.0x (Normal)" else "${spd}x",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFA5B4FC),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // ANIME4K UPSCALER & LINE SHARPENING BOTTOM SHEET
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showEnhanceSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                color = Color(0xFF1E1E24),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 4.dp)
                            .size(width = 44.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Anime4K & Line Sharpening",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "GPU Upscaler Mode",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    AnimeUpscalerMode.entries.forEach { mode ->
                        val isSelected = animeUpscalerMode == mode
                        Surface(
                            onClick = {
                                CatalogConfig.setAnimeUpscalerMode(mode)
                                aspectRatioToastMessage = "Upscaler: ${mode.label}"
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                            border = if (isSelected) BorderStroke(1.dp, Color(0xFFA5B4FC)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.label,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                    )
                                    Text(
                                        text = mode.description,
                                        color = Color.White.copy(alpha = 0.65f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = Color(0xFFA5B4FC),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Anime Line Sharpening",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sobel dark-line thinning & crisp contours",
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = lineSharpeningEnabled,
                            onCheckedChange = {
                                CatalogConfig.setAnimeLineSharpening(it)
                                aspectRatioToastMessage = if (it) "Anime Line Sharpening: ON" else "Anime Line Sharpening: OFF"
                            }
                        )
                    }

                    if (lineSharpeningEnabled) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Sharpening Strength",
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "${(lineSharpeningStrength * 100).toInt()}%",
                                    color = Color(0xFFA5B4FC),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = lineSharpeningStrength,
                                onValueChange = { CatalogConfig.setAnimeLineSharpeningStrength(it) },
                                valueRange = 0.15f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFA5B4FC),
                                    activeTrackColor = Color(0xFFA5B4FC),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun BoxScope.DoubleTapSeekOverlay(
    side: String?,
    seconds: Int,
    compact: Boolean
) {
    AnimatedVisibility(
        visible = side != null && seconds > 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(if (side == "left") Alignment.CenterStart else Alignment.CenterEnd)
            .padding(horizontal = if (compact) 24.dp else 64.dp)
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, Color(0xFFA5B4FC).copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 14.dp else 20.dp,
                    vertical = if (compact) 10.dp else 14.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (side == "left") Icons.Default.FastRewind else Icons.Default.FastForward,
                    contentDescription = null,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(if (compact) 22.dp else 28.dp)
                )
                Text(
                    text = if (side == "left") "-${seconds}s" else "+${seconds}s",
                    color = Color.White,
                    fontSize = if (compact) 14.sp else 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Data class for extensions in Source Bottom Sheet
private data class ExtensionItem(
    val id: String,
    val name: String,
    val lang: String,
    val isInstalled: Boolean
)

// Reusable Action Pill Button (Image 1)
@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) Color(0xFF333C57) else Color(0xFF22222A),
        border = if (isActive) BorderStroke(1.dp, Color(0xFFA5B4FC)) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (isActive) Color(0xFFA5B4FC) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
