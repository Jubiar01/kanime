@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.kanime.app.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon as AndroidIcon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.kanime.app.MainActivity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.kanime.app.data.api.AniSkipApi
import com.kanime.app.data.api.CatalogConfig
import com.kanime.app.data.api.EpisodeSkipTimes
import com.kanime.app.data.api.SkipSegment
import com.kanime.app.data.model.Anime
import com.kanime.app.data.model.Episode
import com.kanime.app.data.plugin.PluginBridge
import com.kanime.app.data.plugin.PluginManager
import com.kanime.app.data.plugin.PluginServer
import com.kanime.app.data.plugin.PluginStream
import com.kanime.app.ui.player.CaptionBackground
import com.kanime.app.ui.player.CaptionColor
import com.kanime.app.ui.player.CaptionEdgeStyle
import com.kanime.app.ui.player.CaptionFont
import com.kanime.app.ui.player.CaptionPosition
import com.kanime.app.ui.player.CaptionTextSize
import com.kanime.app.ui.player.applyCaptionStyle
import com.kanime.app.ui.player.anime.AnimeEnhancementStatus
import com.kanime.app.ui.player.anime.AnimeShaderStatusTracker
import com.kanime.app.ui.player.anime.AnimeShaders
import com.kanime.app.ui.player.anime.AnimeSharpenStrength
import com.kanime.app.ui.player.anime.AnimeUpscaleQuality
import com.kanime.app.ui.theme.GeistFontFamily
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

private const val ACTION_PIP_CONTROL = "com.kanime.app.PIP_CONTROL"
private const val EXTRA_PIP_COMMAND = "pip_command"
private const val PIP_CMD_PLAY_PAUSE = 1
private const val PIP_CMD_REWIND = 2
private const val PIP_CMD_FORWARD = 3

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
private fun buildPipParams(
    context: Context,
    isPlaying: Boolean,
    videoWidth: Int,
    videoHeight: Int,
    autoEnter: Boolean
): PictureInPictureParams {
    val ratio = if (videoWidth > 0 && videoHeight > 0) {
        val aspect = videoWidth.toFloat() / videoHeight.toFloat()
        // Android PiP requires aspect ratio between 0.418410 and 2.390000
        if (aspect in 0.42f..2.38f) Rational(videoWidth, videoHeight) else Rational(16, 9)
    } else {
        Rational(16, 9)
    }

    fun makeAction(cmd: Int, iconRes: Int, title: String): RemoteAction {
        val intent = Intent(ACTION_PIP_CONTROL)
            .setPackage(context.packageName)
            .putExtra(EXTRA_PIP_COMMAND, cmd)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            cmd,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(
            AndroidIcon.createWithResource(context, iconRes),
            title,
            title,
            pendingIntent
        )
    }

    val actions = listOf(
        makeAction(PIP_CMD_REWIND, android.R.drawable.ic_media_rew, "Rewind 10s"),
        makeAction(
            PIP_CMD_PLAY_PAUSE,
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play"
        ),
        makeAction(PIP_CMD_FORWARD, android.R.drawable.ic_media_ff, "Forward 10s")
    )

    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(ratio)
        .setActions(actions)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        builder.setSeamlessResizeEnabled(true)
    }

    return builder.build()
}

@Suppress("DEPRECATION")
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerDialog(
    anime: Anime,
    episode: Episode,
    initialPositionMs: Long = 0L,
    autoPlayNext: Boolean = true,
    isInPipMode: Boolean = false,
    onToggleAutoPlayNext: ((Boolean) -> Unit)? = null,
    onSaveProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null,
    onDismiss: () -> Unit,
    onNextEpisode: (() -> Unit)? = null,
    onPreviousEpisode: (() -> Unit)? = null,
    onSelectEpisode: ((Episode) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current
    val effectiveInPip = isInPipMode || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true)
    val isLandscape = !effectiveInPip && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val availablePlugins by PluginManager.plugins.collectAsState()
    val activeSourceId by PluginManager.activeSourceId.collectAsState()

    var resolutionState by remember { mutableStateOf<StreamResolutionState>(StreamResolutionState.Loading("Resolving stream via KAnikoto plugin...")) }
    var availableServers by remember { mutableStateOf<List<PluginServer>>(emptyList()) }
    var selectedServer by remember { mutableStateOf<PluginServer?>(null) }
    var selectedAudioType by remember { mutableStateOf("sub") } // e.g. "sub", "dub", "raw", "id"
    var embeddedAudioTracks by remember { mutableStateOf<List<EmbeddedAudioTrack>>(emptyList()) }
    var selectedEmbeddedAudioKey by remember { mutableStateOf<String?>(null) }
    var pendingAudioTrackSelection by remember { mutableStateOf<EmbeddedAudioTrack?>(null) }
    var pendingAudioResumePosMs by remember(anime.id, episode.episodeNumber) { mutableLongStateOf(0L) }

    // Bottom sheets state
    var showStreamsSheet by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showSubtitlesSheet by remember { mutableStateOf(false) }
    var showAudioSheet by remember { mutableStateOf(false) }
    var showEnhanceSheet by remember { mutableStateOf(false) }
    var showPictureSheet by remember { mutableStateOf(false) }

    val activeAudioShortLabel = remember(embeddedAudioTracks, selectedEmbeddedAudioKey, selectedAudioType) {
        val activeEmbedded = embeddedAudioTracks.firstOrNull { it.key == selectedEmbeddedAudioKey }
            ?: embeddedAudioTracks.firstOrNull { it.isSelected }
        activeEmbedded?.shortLanguage ?: audioTypeShortLanguage(selectedAudioType)
    }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var selectedSubtitleTrack by remember { mutableStateOf("English [CC]") }
    var subtitleSheetTab by remember { mutableIntStateOf(0) } // 0 = Tracks, 1 = Style
    val captionStyle by CatalogConfig.captionStyleFlow.collectAsState()
    var autoSelectStream by remember { mutableStateOf(true) }
    var extensionSearchQuery by remember { mutableStateOf("") }
    var sourceFilterTab by remember { mutableStateOf("Installed") } // "Installed" or "All Plugins"

    val currentActivePlugin = remember(availablePlugins, activeSourceId) {
        availablePlugins.firstOrNull { it.id == activeSourceId } ?: availablePlugins.firstOrNull()
    }
    val activePluginLabel = currentActivePlugin?.name ?: "No Plugin"

    // Persisted Anime4K 2x Upscaler & Line Sharpening settings
    val animeUpscaleEnabled by CatalogConfig.animeUpscaleEnabledFlow.collectAsState()
    val lineSharpeningEnabled by CatalogConfig.animeLineSharpeningFlow.collectAsState()
    val animeUpscaleQuality by CatalogConfig.animeUpscaleQualityFlow.collectAsState()
    val animeSharpenStrength by CatalogConfig.animeSharpenStrengthFlow.collectAsState()
    var animeEnhancementStatus by remember { mutableStateOf<AnimeEnhancementStatus?>(null) }
    val isVideoEnhanced = animeUpscaleEnabled || lineSharpeningEnabled
    val qualityBadgeText = animeEnhancementStatus?.badgeLabel() ?: when {
        animeUpscaleEnabled && lineSharpeningEnabled -> "A4K 2x ${animeUpscaleQuality.shortLabel} • SHARP"
        animeUpscaleEnabled -> "A4K 2x ${animeUpscaleQuality.shortLabel}"
        lineSharpeningEnabled -> "A4K SHARP"
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

    // ExoPlayer & Picture / Aspect ratio state
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var pictureMode by remember { mutableStateOf(VideoPictureMode.FIT) }
    var videoZoom by remember { mutableFloatStateOf(1.0f) }
    val resizeMode = pictureMode.exoResizeMode
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var aspectRatioToastMessage by remember { mutableStateOf<String?>(null) }
    var isEpisodeOrderReversed by remember { mutableStateOf(false) }

    val formattedEpisodeHeading = remember(episode.episodeNumber, episode.title) {
        formatEpisodeHeading(episode.episodeNumber, episode.title)
    }
    val picturePillLabel = remember(pictureMode, videoZoom) {
        val zoomSuffix = if (videoZoom > 1.01f) " ${(videoZoom * 100).toInt()}%" else ""
        "Picture: ${pictureMode.shortLabel}$zoomSuffix"
    }

    // Auto-Play Next Episode & Watch Progress state (reset per episode)
    val autoPlayTriggered = remember(anime.id, episode.episodeNumber) { mutableStateOf(false) }
    val latestAutoPlayNext by rememberUpdatedState(autoPlayNext)
    val latestOnNextEpisode by rememberUpdatedState(onNextEpisode)
    val latestOnSaveProgress by rememberUpdatedState(onSaveProgress)

    // AniSkip Intro / Recap / Outro timestamps matched to the exact stream duration
    var skipTimes by remember(anime.id, episode.episodeNumber) { mutableStateOf(EpisodeSkipTimes()) }
    val activeSkipSegment = remember(skipTimes, currentPositionMs) {
        skipTimes.activeSegmentAt(currentPositionMs)
    }

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

    // Cycle aspect ratio: Fit -> Cover (Fill Screen) -> Stretch (Full) -> 4:3 Classic -> Fit
    fun cycleAspectRatio() {
        val next = when (pictureMode) {
            VideoPictureMode.FIT -> VideoPictureMode.COVER
            VideoPictureMode.COVER -> VideoPictureMode.STRETCH
            VideoPictureMode.STRETCH -> VideoPictureMode.CLASSIC_4_3
            VideoPictureMode.CLASSIC_4_3 -> VideoPictureMode.FIT
        }
        pictureMode = next
        if (next == VideoPictureMode.FIT) {
            videoZoom = 1.0f
        }
        aspectRatioToastMessage = next.toastLabel
    }

    // Auto-dismiss aspect ratio toast after 2 seconds
    LaunchedEffect(aspectRatioToastMessage) {
        if (aspectRatioToastMessage != null) {
            delay(2000)
            aspectRatioToastMessage = null
        }
    }

    // Restore screen orientation & disable auto-PiP when player dialog is dismissed
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            (activity as? MainActivity)?.onUserLeaveHintPipHandler = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    activity?.setPictureInPictureParams(
                        PictureInPictureParams.Builder()
                            .setAutoEnterEnabled(false)
                            .build()
                    )
                } catch (_: Exception) {
                }
            }
        }
    }

    // Auto-hide controls timer (5 seconds)
    LaunchedEffect(showControls, isPlaying, isControlsLocked, showStreamsSheet, showSourceSheet, showSpeedSheet, showSubtitlesSheet, showAudioSheet, showEnhanceSheet, showPictureSheet) {
        if (showControls && isPlaying && !isControlsLocked && !showStreamsSheet && !showSourceSheet && !showSpeedSheet && !showSubtitlesSheet && !showAudioSheet && !showEnhanceSheet && !showPictureSheet) {
            delay(5000)
            showControls = false
        }
    }

    // Resolve stream via the active installed plugin through PluginBridge
    fun resolveStream(serverToUse: PluginServer? = null) {
        coroutineScope.launch {
            PluginManager.checkPluginStatus(context)
            val activePlugin = PluginManager.plugins.value.firstOrNull { it.id == activeSourceId }
                ?: PluginManager.plugins.value.firstOrNull { it.isInstalled && it.isEnabled }
                ?: PluginManager.plugins.value.firstOrNull()

            if (activePlugin == null || !activePlugin.isInstalled) {
                resolutionState = StreamResolutionState.Error(
                    "${activePlugin?.name ?: "Source plugin"} is not installed. Go to Settings > Video Source Plugins to download and install it."
                )
                return@launch
            }
            if (!activePlugin.isEnabled) {
                resolutionState = StreamResolutionState.Error("${activePlugin.name} is turned off. Turn on the switch in Settings > Video Source Plugins to stream.")
                return@launch
            }

            val authority = activePlugin.providerAuthority
            resolutionState = StreamResolutionState.Loading("Loading servers via ${activePlugin.name}...")
            try {
                // Fast path: if DetailScreen already resolved this episode ID for this exact plugin,
                // skip redundant findSlug + getEpisodes and fetch servers directly!
                val alreadyResolvedForActivePlugin = episode.id.isNotBlank() &&
                    episode.description == "Streamed via ${activePlugin.name}"

                var servers = if (alreadyResolvedForActivePlugin) {
                    PluginBridge.getServers(context, episode.id, authority = authority)
                } else {
                    emptyList()
                }

                if (servers.isEmpty()) {
                    resolutionState = StreamResolutionState.Loading("Resolving anime via ${activePlugin.name}...")
                    val titleCandidates = listOfNotNull(
                        anime.englishTitle,
                        anime.title,
                        anime.romajiTitle,
                        anime.japaneseTitle
                    ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                    var slug: String? = null
                    for (candidate in titleCandidates) {
                        slug = PluginBridge.findSlug(context, candidate, authority = authority)
                        if (slug != null) break
                    }

                    if (slug == null) {
                        resolutionState = StreamResolutionState.Error("Anime '${anime.title}' was not found on ${activePlugin.name}.")
                        return@launch
                    }

                    resolutionState = StreamResolutionState.Loading("Fetching episode nodes...")
                    val episodes = PluginBridge.getEpisodes(context, slug, authority = authority)
                    val targetEp = episodes.firstOrNull { it.num == episode.episodeNumber.toDouble() }
                        ?: episodes.firstOrNull { Math.abs(it.num - episode.episodeNumber) < 0.1 }
                        ?: episodes.firstOrNull()

                    if (targetEp == null) {
                        resolutionState = StreamResolutionState.Error("Episode ${episode.episodeNumber} not found on ${activePlugin.name}.")
                        return@launch
                    }

                    resolutionState = StreamResolutionState.Loading("Loading server nodes...")
                    servers = PluginBridge.getServers(context, targetEp.id, authority = authority)
                }
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
                    val stream = PluginBridge.getStream(context, srv.sourceId, authority = authority)
                    if (stream != null && !stream.m3u8.isNullOrBlank()) {
                        foundStream = stream
                        foundServer = srv
                        break
                    }
                }

                if (foundStream == null) {
                    for (srv in serversToTry) {
                        val stream = PluginBridge.getStream(context, srv.sourceId, authority = authority)
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
                    resolutionState = StreamResolutionState.Error("Unable to connect to streaming servers on ${activePlugin.name}. Please verify plugin is installed and active.")
                }
            } catch (e: Exception) {
                resolutionState = StreamResolutionState.Error("${activePlugin.name} stream extraction error: ${e.message}")
            }
        }
    }

    LaunchedEffect(anime.id, episode.episodeNumber, selectedAudioType, activeSourceId) {
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
                        .padding(horizontal = 28.dp, vertical = 24.dp),
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
                        text = anime.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formattedEpisodeHeading,
                        color = Color(0xFFA5B4FC),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.message,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 340.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFF333C57),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = activePluginLabel,
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
                            Text("Retry")
                        }
                        Button(onClick = { showSourceSheet = true }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Switch Source")
                        }
                        if (availableServers.isNotEmpty()) {
                            OutlinedButton(onClick = { showStreamsSheet = true }) {
                                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Servers")
                            }
                        }
                    }
                }
            }

            is StreamResolutionState.Success -> {
                val stream = state.stream
                val streamUrl = stream.m3u8 ?: stream.embedUrl
                val lifecycleOwner = LocalLifecycleOwner.current
                val mainHandler = remember { Handler(Looper.getMainLooper()) }

                // Remember playback position across clean Anime4K ExoPlayer rebuilds, audio switches, and Watch History resume
                val resumePositionRef = remember(streamUrl, anime.id, episode.episodeNumber) {
                    val startMs = if (pendingAudioResumePosMs > 0L) {
                        val saved = pendingAudioResumePosMs
                        pendingAudioResumePosMs = 0L
                        saved
                    } else {
                        initialPositionMs.coerceAtLeast(0L)
                    }
                    mutableLongStateOf(startMs)
                }
                val resumePlayWhenReadyRef = remember(streamUrl) { mutableStateOf(true) }
                LaunchedEffect(streamUrl, anime.id, episode.episodeNumber) {
                    if (initialPositionMs >= 5_000L) {
                        aspectRatioToastMessage = "Resumed at ${formatTime(initialPositionMs)}"
                    }
                }
                var videoEffectsDisabledByError by remember(streamUrl) { mutableStateOf(false) }
                var animeRebuildGeneration by remember(streamUrl) { mutableIntStateOf(0) }
                val exoVideoEffectsHolder = remember(streamUrl) { ExoVideoEffectsHolder() }
                val animeStatusTracker = remember(streamUrl) {
                    AnimeShaderStatusTracker { status ->
                        mainHandler.post { animeEnhancementStatus = status }
                    }
                }

                // Build ExoPlayer with official Anime4K multi-pass GLSL shaders (from NuvioMobile-Enhanced).
                // When toggling Anime4K 2x Upscaler or Line Sharpening, a clean debounced rebuild
                // recreates ExoPlayer at the exact saved timestamp so turning effects OFF/ON never stops playback.
                val exoPlayer = remember(streamUrl, animeRebuildGeneration, videoEffectsDisabledByError) {
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

                    val upscaleForBuild = CatalogConfig.animeUpscaleEnabled
                    val sharpenForBuild = CatalogConfig.animeLineSharpening
                    val qualityForBuild = CatalogConfig.animeUpscaleQuality
                    val strengthForBuild = CatalogConfig.animeSharpenStrength
                    val animeVideoEffects = if (videoEffectsDisabledByError) {
                        emptyList()
                    } else {
                        AnimeShaders.exoVideoEffects(
                            context = context,
                            upscaleEnabled = upscaleForBuild,
                            lineSharpenEnabled = sharpenForBuild,
                            quality = qualityForBuild,
                            sharpenStrength = strengthForBuild,
                            statusTracker = animeStatusTracker,
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

                            if (animeVideoEffects.isNotEmpty()) {
                                try {
                                    setVideoEffects(animeVideoEffects)
                                } catch (t: Throwable) {
                                    Log.e("PlayerDialog", "Failed to attach Anime4K shaders; falling back", t)
                                    videoEffectsDisabledByError = true
                                }
                            } else {
                                animeEnhancementStatus = null
                            }

                            exoVideoEffectsHolder.update(
                                pipelineConfigured = animeVideoEffects.isNotEmpty(),
                                upscaleEnabled = upscaleForBuild,
                                lineSharpenEnabled = sharpenForBuild,
                                quality = qualityForBuild,
                                sharpenStrength = strengthForBuild,
                            )

                            setMediaItem(mediaItem)
                            val savedPos = resumePositionRef.longValue
                            if (savedPos > 0L) {
                                seekTo(savedPos)
                            }
                            prepare()
                            playWhenReady = resumePlayWhenReadyRef.value

                            addListener(object : Player.Listener {
                                override fun onIsPlayingChanged(playing: Boolean) {
                                    isPlaying = playing
                                }

                                override fun onTracksChanged(tracks: Tracks) {
                                    val parsed = extractEmbeddedAudioTracks(tracks, selectedAudioType)
                                    embeddedAudioTracks = parsed
                                    val active = parsed.firstOrNull { it.isSelected }
                                    if (active != null) {
                                        selectedEmbeddedAudioKey = active.key
                                    }
                                }

                                override fun onPlayerError(error: PlaybackException) {
                                    Log.e("PlayerDialog", "ExoPlayer error (code=${error.errorCodeName}): ${error.message}", error)
                                    if (!videoEffectsDisabledByError && (
                                            error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED ||
                                            error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                                        )
                                    ) {
                                        resumePositionRef.longValue = currentPosition.coerceAtLeast(0L)
                                        resumePlayWhenReadyRef.value = true
                                        animeEnhancementStatus = null
                                        videoEffectsDisabledByError = true
                                    }
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

                // Debounced clean ExoPlayer rebuild when Anime4K toggles, quality preset, or sharpen strength change
                LaunchedEffect(exoPlayer, animeUpscaleEnabled, lineSharpeningEnabled, animeUpscaleQuality, animeSharpenStrength) {
                    if (exoVideoEffectsHolder.matches(animeUpscaleEnabled, lineSharpeningEnabled, animeUpscaleQuality, animeSharpenStrength)) {
                        return@LaunchedEffect
                    }
                    if (!exoVideoEffectsHolder.pipelineConfigured && !animeUpscaleEnabled && !lineSharpeningEnabled) {
                        exoVideoEffectsHolder.update(false, false, false, animeUpscaleQuality, animeSharpenStrength)
                        return@LaunchedEffect
                    }
                    delay(700L)
                    resumePositionRef.longValue = exoPlayer.currentPosition.coerceAtLeast(0L)
                    resumePlayWhenReadyRef.value = exoPlayer.playWhenReady || exoPlayer.isPlaying
                    videoEffectsDisabledByError = false
                    animeRebuildGeneration += 1
                }

                // Pause/resume cleanly when screen turns OFF/ON or app goes to background/foreground,
                // while keeping video playing seamlessly when entering Picture-in-Picture mode!
                DisposableEffect(lifecycleOwner, exoPlayer) {
                    var wasPlayingOnStop = false
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> {
                                val inPipNow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                                    activity?.isInPictureInPictureMode == true
                                val pausePos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val pauseDur = exoPlayer.duration.coerceAtLeast(0L)
                                resumePositionRef.longValue = pausePos
                                latestOnSaveProgress?.invoke(pausePos, pauseDur)
                                if (!inPipNow) {
                                    wasPlayingOnStop = exoPlayer.playWhenReady || exoPlayer.isPlaying
                                    resumePlayWhenReadyRef.value = wasPlayingOnStop
                                    exoPlayer.pause()
                                }
                            }
                            Lifecycle.Event.ON_STOP -> {
                                // Fires when screen turns off, app is backgrounded without PiP, or user closes PiP window
                                wasPlayingOnStop = exoPlayer.playWhenReady || exoPlayer.isPlaying
                                val stopPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val stopDur = exoPlayer.duration.coerceAtLeast(0L)
                                resumePositionRef.longValue = stopPos
                                resumePlayWhenReadyRef.value = wasPlayingOnStop
                                latestOnSaveProgress?.invoke(stopPos, stopDur)
                                exoPlayer.pause()
                            }
                            Lifecycle.Event.ON_RESUME -> {
                                if (exoPlayer.playbackState == Player.STATE_IDLE) {
                                    val pos = resumePositionRef.longValue
                                    exoPlayer.prepare()
                                    if (pos > 0L) exoPlayer.seekTo(pos)
                                }
                                if (wasPlayingOnStop) {
                                    wasPlayingOnStop = false
                                    exoPlayer.playWhenReady = true
                                    exoPlayer.play()
                                }
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                fun resumeOrPlay() {
                    if (exoPlayer.playbackState == Player.STATE_IDLE) {
                        val pos = resumePositionRef.longValue
                        exoPlayer.prepare()
                        if (pos > 0L) exoPlayer.seekTo(pos)
                    } else if (exoPlayer.playbackState == Player.STATE_ENDED) {
                        autoPlayTriggered.value = false
                        exoPlayer.seekTo(0L)
                    }
                    resumePlayWhenReadyRef.value = true
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }

                fun pausePlayback() {
                    resumePlayWhenReadyRef.value = false
                    exoPlayer.pause()
                }

                fun enterPipMode() {
                    val act = activity ?: return
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        aspectRatioToastMessage = "Picture-in-Picture requires Android 8.0+"
                        return
                    }
                    if (!act.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                        aspectRatioToastMessage = "Picture-in-Picture is not supported on this device"
                        return
                    }
                    showControls = false
                    showStreamsSheet = false
                    showSourceSheet = false
                    showSpeedSheet = false
                    showSubtitlesSheet = false
                    showAudioSheet = false
                    showEnhanceSheet = false
                    showPictureSheet = false
                    try {
                        val vs = exoPlayer.videoSize
                        val params = buildPipParams(
                            context = context,
                            isPlaying = exoPlayer.isPlaying || exoPlayer.playWhenReady,
                            videoWidth = vs.width,
                            videoHeight = vs.height,
                            autoEnter = exoPlayer.isPlaying || exoPlayer.playWhenReady
                        )
                        act.enterPictureInPictureMode(params)
                    } catch (e: Exception) {
                        Log.w("PlayerDialog", "Failed to enter PiP mode: ${e.message}")
                        aspectRatioToastMessage = "Enable Picture-in-Picture in App Settings"
                    }
                }

                // Keep PiP RemoteActions (Play/Pause, -10s, +10s) and Android 12+ auto-enter PiP in sync
                LaunchedEffect(exoPlayer, isPlaying, effectiveInPip) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                        try {
                            val vs = exoPlayer.videoSize
                            activity.setPictureInPictureParams(
                                buildPipParams(
                                    context = context,
                                    isPlaying = isPlaying,
                                    videoWidth = vs.width,
                                    videoHeight = vs.height,
                                    autoEnter = isPlaying
                                )
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

                // Register Android 8.0–11 Home-button auto-PiP handler & PiP RemoteAction controls receiver
                DisposableEffect(activity, exoPlayer) {
                    val mainAct = activity as? MainActivity
                    mainAct?.onUserLeaveHintPipHandler = {
                        if (exoPlayer.isPlaying || exoPlayer.playWhenReady) {
                            enterPipMode()
                        }
                    }

                    val pipReceiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            if (intent?.action != ACTION_PIP_CONTROL) return
                            when (intent.getIntExtra(EXTRA_PIP_COMMAND, -1)) {
                                PIP_CMD_PLAY_PAUSE -> {
                                    if (exoPlayer.isPlaying) pausePlayback() else resumeOrPlay()
                                }
                                PIP_CMD_REWIND -> {
                                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                    resumePositionRef.longValue = target
                                }
                                PIP_CMD_FORWARD -> {
                                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                                    val target = if (dur > 0L) {
                                        (exoPlayer.currentPosition + 10_000L).coerceAtMost(dur)
                                    } else {
                                        exoPlayer.currentPosition + 10_000L
                                    }
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                    resumePositionRef.longValue = target
                                }
                            }
                        }
                    }

                    ContextCompat.registerReceiver(
                        context,
                        pipReceiver,
                        IntentFilter(ACTION_PIP_CONTROL),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )

                    onDispose {
                        mainAct?.onUserLeaveHintPipHandler = null
                        try {
                            context.unregisterReceiver(pipReceiver)
                        } catch (_: Exception) {
                        }
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
                    resumePositionRef.longValue = targetMs
                    doubleTapSeekSeconds = if (doubleTapSeekSide == side) {
                        doubleTapSeekSeconds + kotlin.math.abs(deltaSeconds)
                    } else {
                        kotlin.math.abs(deltaSeconds)
                    }
                    doubleTapSeekSide = side
                    doubleTapSeekNonce++
                }

                // Keep playback speed in sync
                LaunchedEffect(exoPlayer, playbackSpeed) {
                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                }

                // Sync initial embedded audio tracks when ExoPlayer is ready & apply manual audio track selections
                LaunchedEffect(exoPlayer) {
                    val parsed = extractEmbeddedAudioTracks(exoPlayer.currentTracks, selectedAudioType)
                    if (parsed.isNotEmpty()) {
                        embeddedAudioTracks = parsed
                        parsed.firstOrNull { it.isSelected }?.let { selectedEmbeddedAudioKey = it.key }
                    }
                }

                LaunchedEffect(exoPlayer, pendingAudioTrackSelection) {
                    val target = pendingAudioTrackSelection ?: return@LaunchedEffect
                    val builder = exoPlayer.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(TrackSelectionOverride(target.trackGroup, target.trackIndex))
                    exoPlayer.trackSelectionParameters = builder.build()
                    selectedEmbeddedAudioKey = target.key
                    embeddedAudioTracks = embeddedAudioTracks.map { it.copy(isSelected = it.key == target.key) }
                    pendingAudioTrackSelection = null
                }

                // Subtitle track selection
                LaunchedEffect(exoPlayer, subtitlesEnabled, selectedSubtitleTrack) {
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

                // Poll playback position & periodically persist Watch History progress
                LaunchedEffect(exoPlayer) {
                    var tick = 0
                    while (true) {
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val dur = exoPlayer.duration.coerceAtLeast(0L)
                        currentPositionMs = pos
                        if (pos > 0L) {
                            resumePositionRef.longValue = pos
                        }
                        durationMs = dur
                        if (tick % 16 == 0 && (pos > 2_000L || dur > 0L)) {
                            latestOnSaveProgress?.invoke(pos, dur)
                        }
                        tick++
                        delay(250)
                    }
                }

                // Fetch AniSkip Intro / Recap / Outro timestamps using the exact stream duration
                // so skip buttons trigger right when the intro/recap begins on this exact cut.
                val durationBucket = if (durationMs > 60_000L) (durationMs / 5_000L).toInt() else 0
                LaunchedEffect(anime.id, anime.malId, episode.episodeNumber, durationBucket) {
                    if (durationMs > 60_000L) {
                        skipTimes = AniSkipApi.getSkipTimes(
                            anime = anime,
                            episodeNumber = episode.episodeNumber,
                            episodeLengthSec = durationMs / 1000.0,
                        )
                    }
                }

                val playerView = remember(context) {
                    PlayerView(context).apply {
                        this.player = exoPlayer
                        useController = false
                        this.resizeMode = resizeMode
                        applyCaptionStyle(context, subtitleView, captionStyle)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }

                // Live-apply user caption customizations (font, size, color, edge, background, position)
                LaunchedEffect(playerView, captionStyle) {
                    applyCaptionStyle(context, playerView.subtitleView, captionStyle)
                }

                // Live-apply Picture / Aspect Ratio mode directly on PlayerView & ExoPlayer
                LaunchedEffect(playerView, exoPlayer, pictureMode, effectiveInPip) {
                    val targetResize = if (effectiveInPip) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        pictureMode.exoResizeMode
                    }
                    playerView.resizeMode = targetResize
                    exoPlayer.videoScalingMode = if (!effectiveInPip && pictureMode == VideoPictureMode.COVER) {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    } else {
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    }
                    playerView.requestLayout()
                }

                DisposableEffect(exoPlayer) {
                    playerView.player = exoPlayer
                    playerView.onResume()
                    onDispose {
                        val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val dur = exoPlayer.duration.coerceAtLeast(0L)
                        if (pos > 0L) resumePositionRef.longValue = pos
                        if (pos > 0L || dur > 0L) {
                            latestOnSaveProgress?.invoke(pos, dur)
                        }
                        resumePlayWhenReadyRef.value = exoPlayer.playWhenReady || exoPlayer.isPlaying
                        if (playerView.player === exoPlayer) {
                            playerView.player = null
                        }
                        exoPlayer.release()
                    }
                }

                // Common Video Surface Composable (applies Picture Mode & Zoom cleanly in both Portrait & Landscape)
                @Composable
                fun VideoSurface(modifier: Modifier = Modifier) {
                    val baseScaleX = when {
                        effectiveInPip -> 1f
                        pictureMode == VideoPictureMode.CLASSIC_4_3 -> 0.75f
                        !isLandscape && pictureMode == VideoPictureMode.COVER -> 1.18f
                        !isLandscape && pictureMode == VideoPictureMode.STRETCH -> 1.16f
                        else -> 1f
                    }
                    val baseScaleY = when {
                        effectiveInPip -> 1f
                        !isLandscape && pictureMode == VideoPictureMode.COVER -> 1.18f
                        else -> 1f
                    }
                    val zoomFactor = if (effectiveInPip) 1f else videoZoom

                    Box(
                        modifier = modifier.clipToBounds(),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            factory = {
                                (playerView.parent as? ViewGroup)?.removeView(playerView)
                                playerView
                            },
                            update = { pv ->
                                if (pv.player != exoPlayer) pv.player = exoPlayer
                                val targetResize = if (effectiveInPip) {
                                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                                } else {
                                    pictureMode.exoResizeMode
                                }
                                if (pv.resizeMode != targetResize) {
                                    pv.resizeMode = targetResize
                                    pv.requestLayout()
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = baseScaleX * zoomFactor
                                    scaleY = baseScaleY * zoomFactor
                                }
                        )
                    }
                }

                // ══════════════════════════════════════════════════════════════
                // CONDITIONAL LAYOUT: PIP FULL-BLEED VS PORTRAIT VS LANDSCAPE
                // ══════════════════════════════════════════════════════════════
                if (effectiveInPip) {
                    // Clean full-bleed video surface inside the floating Picture-in-Picture window
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        VideoSurface(modifier = Modifier.fillMaxSize())
                    }
                } else if (!isLandscape) {
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
                                                    else -> if (exoPlayer.isPlaying) pausePlayback() else resumeOrPlay()
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
                                            if (isPlaying) pausePlayback() else resumeOrPlay()
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
                                                    onClick = { enterPipMode() },
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

                            // Floating Skip Intro / Recap / Outro button (Portrait)
                            SkipSegmentOverlay(
                                segment = activeSkipSegment,
                                compact = true,
                                bottomPadding = if (showControls) 58.dp else 12.dp,
                                endPadding = 12.dp,
                                onSkip = { seg ->
                                    val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                                    val target = if (maxDur > 0L) seg.endMs.coerceAtMost(maxDur) else seg.endMs
                                    exoPlayer.seekTo(target)
                                    currentPositionMs = target
                                    resumePositionRef.longValue = target
                                    latestOnSaveProgress?.invoke(target, maxDur)
                                    aspectRatioToastMessage = seg.label.replace("Skip ", "Skipped ")
                                }
                            )
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
                                text = formattedEpisodeHeading,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = anime.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
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
                                    label = "Audio: $activeAudioShortLabel",
                                    isActive = showAudioSheet
                                ) {
                                    showAudioSheet = true
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
                                    label = "Source: $activePluginLabel",
                                    isActive = showSourceSheet
                                ) {
                                    showSourceSheet = true
                                }
                                ActionPill(
                                    icon = Icons.Default.AspectRatio,
                                    label = picturePillLabel,
                                    isActive = pictureMode != VideoPictureMode.FIT || videoZoom > 1.01f
                                ) {
                                    showPictureSheet = true
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
                                                    else -> if (exoPlayer.isPlaying) pausePlayback() else resumeOrPlay()
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

                                        // Episode & Anime Title (smooth marquee for long titles so nothing clips off-screen)
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = formattedEpisodeHeading,
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                            )
                                            Text(
                                                text = anime.title,
                                                color = Color.White.copy(alpha = 0.7f),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
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
                                                    onClick = { enterPipMode() },
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
                                                if (isPlaying) pausePlayback() else resumeOrPlay()
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

                                                // Icon 4: Audio track selector [🎵]
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (showAudioSheet) Color(0xFFA5B4FC).copy(alpha = 0.25f) else Color.Transparent)
                                                ) {
                                                    IconButton(
                                                        onClick = { showAudioSheet = true },
                                                        modifier = Modifier.size(44.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.MusicNote,
                                                            contentDescription = "Audio Track ($activeAudioShortLabel)",
                                                            tint = if (showAudioSheet) Color(0xFFA5B4FC) else Color.White,
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
                                                    onClick = { enterPipMode() },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.PictureInPicture,
                                                        contentDescription = "Picture-in-Picture",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                // Icon 2: Aspect ratio / Picture toggle [ ⛶ ]
                                                IconButton(
                                                    onClick = { cycleAspectRatio() },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.AspectRatio,
                                                        contentDescription = "Aspect Ratio",
                                                        tint = if (pictureMode != VideoPictureMode.FIT || videoZoom > 1.01f) Color(0xFFA5B4FC) else Color.White,
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

                        // Floating Skip Intro / Recap / Outro button (Landscape)
                        SkipSegmentOverlay(
                            segment = if (isControlsLocked) null else activeSkipSegment,
                            compact = false,
                            bottomPadding = if (showControls) 136.dp else 28.dp,
                            endPadding = 36.dp,
                            onSkip = { seg ->
                                val maxDur = exoPlayer.duration.coerceAtLeast(0L)
                                val target = if (maxDur > 0L) seg.endMs.coerceAtMost(maxDur) else seg.endMs
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                resumePositionRef.longValue = target
                                latestOnSaveProgress?.invoke(target, maxDur)
                                aspectRatioToastMessage = seg.label.replace("Skip ", "Skipped ")
                            }
                        )
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // FLOATING ASPECT RATIO TOAST OVERLAY
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = !effectiveInPip && aspectRatioToastMessage != null,
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
        if (!effectiveInPip && (showStreamsSheet || showSourceSheet || showSpeedSheet || showSubtitlesSheet || showAudioSheet || showEnhanceSheet || showPictureSheet)) {
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
                        showAudioSheet = false
                        showEnhanceSheet = false
                        showPictureSheet = false
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
                                        pendingAudioResumePosMs = currentPositionMs.coerceAtLeast(0L)
                                        selectedEmbeddedAudioKey = null
                                        embeddedAudioTracks = emptyList()
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
        // SUBTITLES & CAPTIONS BOTTOM SHEET (TRACKS + LIVE STYLE EDITOR)
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
                    .fillMaxHeight(if (isLandscape) 0.88f else 0.72f),
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Subtitles & Captions",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (subtitleSheetTab == 1) {
                            TextButton(
                                onClick = {
                                    CatalogConfig.resetCaptionStyle()
                                    aspectRatioToastMessage = "Caption style reset to default"
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Reset",
                                    color = Color(0xFFA5B4FC),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Segmented Tabs: Tracks | Customize Style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF282830), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("Tracks", "Customize Style").forEachIndexed { idx, tabLabel ->
                            val selected = subtitleSheetTab == idx
                            Surface(
                                onClick = { subtitleSheetTab = idx },
                                color = if (selected) Color(0xFF333C57) else Color.Transparent,
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = tabLabel,
                                        color = if (selected) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.72f),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (subtitleSheetTab == 0) {
                        // TAB 0: TRACKS
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

                        Spacer(modifier = Modifier.height(14.dp))

                        val subOptions = mutableListOf("English [CC]", "Off")
                        if (resolutionState is StreamResolutionState.Success) {
                            (resolutionState as StreamResolutionState.Success).stream.subtitles
                                .filter { sub ->
                                    !sub.url.contains("thumbnails", ignoreCase = true) &&
                                        !sub.lang.contains("thumbnail", ignoreCase = true) &&
                                        !sub.lang.contains("unknown", ignoreCase = true)
                                }
                                .forEach { sub ->
                                    if (!subOptions.contains(sub.lang)) {
                                        subOptions.add(0, sub.lang)
                                    }
                                }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                        ) {
                            items(subOptions.distinct()) { opt ->
                                val isSelected = (opt == "Off" && !subtitlesEnabled) ||
                                    (opt != "Off" && subtitlesEnabled && selectedSubtitleTrack.equals(opt, ignoreCase = true))

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
                                    border = if (isSelected) BorderStroke(1.dp, Color(0xFFA5B4FC).copy(alpha = 0.65f)) else null,
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
                    } else {
                        // TAB 1: CUSTOMIZE CAPTION STYLE (LIVE PREVIEW + CONTROLS)
                        val previewFontFamily = when (captionStyle.font) {
                            CaptionFont.GEIST -> GeistFontFamily
                            CaptionFont.SANS_SERIF -> FontFamily.SansSerif
                            CaptionFont.SERIF -> FontFamily.Serif
                            CaptionFont.MONOSPACE -> FontFamily.Monospace
                            CaptionFont.CASUAL -> FontFamily.Cursive
                        }
                        val previewShadow = when (captionStyle.edgeStyle) {
                            CaptionEdgeStyle.OUTLINE -> Shadow(color = Color.Black, offset = Offset(0f, 0f), blurRadius = 6f)
                            CaptionEdgeStyle.DROP_SHADOW -> Shadow(color = Color.Black, offset = Offset(3f, 3f), blurRadius = 5f)
                            CaptionEdgeStyle.RAISED -> Shadow(color = Color.Black, offset = Offset(0f, 3f), blurRadius = 3f)
                            CaptionEdgeStyle.NONE -> Shadow.None
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Live Caption Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(78.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF15192B), Color(0xFF232842), Color(0xFF121521))
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = when (captionStyle.position) {
                                    CaptionPosition.LOW -> Alignment.BottomCenter
                                    CaptionPosition.NORMAL -> Alignment.Center
                                    CaptionPosition.HIGH -> Alignment.TopCenter
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(captionStyle.background.argb))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "This is how your anime captions look.",
                                        color = Color(captionStyle.textColor.argb),
                                        fontFamily = previewFontFamily,
                                        fontWeight = if (captionStyle.isBold) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = captionStyle.textSize.previewSp.sp,
                                        style = MaterialTheme.typography.bodyMedium.copy(shadow = previewShadow),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Font Family
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Font Family",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionFont.entries.forEach { font ->
                                        val isSelected = captionStyle.font == font
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { CatalogConfig.updateCaptionStyle { it.copy(font = font) } },
                                            label = { Text(font.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF333C57),
                                                selectedLabelColor = Color(0xFFA5B4FC),
                                                containerColor = Color(0xFF282830),
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            // Bold Text Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Bold Captions",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Switch(
                                    checked = captionStyle.isBold,
                                    onCheckedChange = { bold -> CatalogConfig.updateCaptionStyle { it.copy(isBold = bold) } },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFFA5B4FC)
                                    )
                                )
                            }

                            // Text Size
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Text Size",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionTextSize.entries.forEach { size ->
                                        val isSelected = captionStyle.textSize == size
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { CatalogConfig.updateCaptionStyle { it.copy(textSize = size) } },
                                            label = { Text(size.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF333C57),
                                                selectedLabelColor = Color(0xFFA5B4FC),
                                                containerColor = Color(0xFF282830),
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Text Color Swatches
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Text Color",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CaptionColor.entries.forEach { col ->
                                        val isSelected = captionStyle.textColor == col
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.clickable {
                                                CatalogConfig.updateCaptionStyle { it.copy(textColor = col) }
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(col.argb))
                                                    .border(
                                                        width = if (isSelected) 2.5.dp else 1.dp,
                                                        color = if (isSelected) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.3f),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = col.label,
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = col.label,
                                                color = if (isSelected) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.65f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }

                            // Edge Style
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Edge Style",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionEdgeStyle.entries.forEach { edge ->
                                        val isSelected = captionStyle.edgeStyle == edge
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { CatalogConfig.updateCaptionStyle { it.copy(edgeStyle = edge) } },
                                            label = { Text(edge.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF333C57),
                                                selectedLabelColor = Color(0xFFA5B4FC),
                                                containerColor = Color(0xFF282830),
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }

                            // Background Box
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Background",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionBackground.entries.forEach { bg ->
                                        val isSelected = captionStyle.background == bg
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { CatalogConfig.updateCaptionStyle { it.copy(background = bg) } },
                                            label = { Text(bg.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF333C57),
                                                selectedLabelColor = Color(0xFFA5B4FC),
                                                containerColor = Color(0xFF282830),
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            // Vertical Position
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Vertical Position",
                                    color = Color.White.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CaptionPosition.entries.forEach { pos ->
                                        val isSelected = captionStyle.position == pos
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { CatalogConfig.updateCaptionStyle { it.copy(position = pos) } },
                                            label = { Text(pos.label) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = Color(0xFF333C57),
                                                selectedLabelColor = Color(0xFFA5B4FC),
                                                containerColor = Color(0xFF282830),
                                                labelColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.weight(1f)
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
        // AUDIO TRACKS & LANGUAGES BOTTOM SHEET
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showAudioSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        ) {
            val serverAudioGroups = remember(availableServers, selectedAudioType) {
                val groupedByType = availableServers
                    .groupBy { it.type.trim().lowercase().ifBlank { "sub" } }
                val list = groupedByType.map { (typeKey, serversForType) ->
                    ServerAudioOption(
                        typeKey = typeKey,
                        displayName = audioTypeDisplayName(typeKey),
                        shortName = audioTypeShortLanguage(typeKey),
                        serverCount = serversForType.size
                    )
                }.sortedBy { audioLanguageSortOrder(it.typeKey) }

                if (list.isEmpty()) {
                    listOf(
                        ServerAudioOption(
                            typeKey = selectedAudioType.lowercase(),
                            displayName = audioTypeDisplayName(selectedAudioType),
                            shortName = audioTypeShortLanguage(selectedAudioType),
                            serverCount = 1
                        )
                    )
                } else {
                    list
                }
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(if (isLandscape) 0.82f else 0.56f),
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Audio",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose an available audio language for this episode",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp)
                    ) {
                        // Section 1: Embedded multi-audio tracks inside the active HLS/video stream (instant switch)
                        if (embeddedAudioTracks.size > 1) {
                            item(key = "embedded_audio_header") {
                                Text(
                                    text = "IN-STREAM AUDIO TRACKS",
                                    color = Color(0xFFA5B4FC),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                )
                            }

                            items(embeddedAudioTracks, key = { it.key }) { track ->
                                val isSelected = track.key == selectedEmbeddedAudioKey ||
                                    (selectedEmbeddedAudioKey == null && track.isSelected)
                                Surface(
                                    onClick = {
                                        pendingAudioTrackSelection = track
                                        aspectRatioToastMessage = "Audio: ${track.displayName}"
                                        showAudioSheet = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                                    border = if (isSelected) BorderStroke(1.dp, Color(0xFFA5B4FC).copy(alpha = 0.65f)) else null,
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
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = null,
                                                tint = if (isSelected) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = track.displayName,
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                if (track.detail.isNotBlank()) {
                                                    Text(
                                                        text = track.detail,
                                                        color = Color.White.copy(alpha = 0.6f),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = Color(0xFFA5B4FC),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Section 2: Available Audio Languages from Episode Servers (Japanese, English, Indonesian, etc.)
                        item(key = "source_audio_header") {
                            Text(
                                text = if (embeddedAudioTracks.size > 1) "SOURCE AUDIO STREAMS" else "AVAILABLE AUDIO",
                                color = Color(0xFFA5B4FC),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = if (embeddedAudioTracks.size > 1) 8.dp else 2.dp, bottom = 2.dp)
                            )
                        }

                        items(serverAudioGroups, key = { "srv_audio_${it.typeKey}" }) { option ->
                            val isSelected = selectedAudioType.equals(option.typeKey, ignoreCase = true)
                            val activeTrackDetail = if (isSelected && embeddedAudioTracks.size == 1) {
                                embeddedAudioTracks.first().detail
                            } else {
                                ""
                            }
                            Surface(
                                onClick = {
                                    if (!isSelected) {
                                        pendingAudioResumePosMs = currentPositionMs.coerceAtLeast(0L)
                                        selectedEmbeddedAudioKey = null
                                        embeddedAudioTracks = emptyList()
                                        selectedAudioType = option.typeKey
                                        aspectRatioToastMessage = "Switching audio to ${option.displayName}"
                                    } else {
                                        aspectRatioToastMessage = "Audio: ${option.displayName}"
                                    }
                                    showAudioSheet = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFFA5B4FC).copy(alpha = 0.65f)) else null,
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
                                        Icon(
                                            imageVector = Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFA5B4FC) else Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = option.displayName,
                                                color = Color.White,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            val subtitleParts = listOfNotNull(
                                                "${option.serverCount} ${if (option.serverCount == 1) "server" else "servers"}",
                                                activeTrackDetail.takeIf { it.isNotBlank() }
                                            ).joinToString(" • ")
                                            Text(
                                                text = subtitleParts,
                                                color = Color.White.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
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
        // SOURCE PLUGINS BOTTOM SHEET (ONLY REAL REGISTERED KANIME PLUGINS)
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = showSourceSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
        ) {
            val installedCount = remember(availablePlugins) { availablePlugins.count { it.isInstalled } }
            val filteredPlugins = remember(availablePlugins, sourceFilterTab, extensionSearchQuery) {
                availablePlugins
                    .filter { plugin ->
                        val matchesTab = if (sourceFilterTab == "Installed") plugin.isInstalled else true
                        val matchesQuery = extensionSearchQuery.isBlank() ||
                            plugin.name.contains(extensionSearchQuery, ignoreCase = true) ||
                            plugin.id.contains(extensionSearchQuery, ignoreCase = true)
                        matchesTab && matchesQuery
                    }
                    .sortedWith(
                        compareByDescending<com.kanime.app.data.plugin.AnimeSourcePlugin> { it.isInstalled && it.isEnabled }
                            .thenByDescending { it.isInstalled }
                            .thenBy { it.name.lowercase() }
                    )
            }

            Surface(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(if (isLandscape) 0.86f else 0.62f),
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = Color(0xFFA5B4FC),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Source Plugins",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select an installed KAnime source plugin for streaming",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter tabs: Installed vs All Catalog Plugins
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "Installed" to "Installed ($installedCount)",
                            "All Plugins" to "All Plugins (${availablePlugins.size})"
                        ).forEach { (tabKey, tabLabel) ->
                            val isChosen = sourceFilterTab == tabKey
                            FilterChip(
                                selected = isChosen,
                                onClick = { sourceFilterTab = tabKey },
                                label = { Text(tabLabel) },
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

                    if (availablePlugins.size > 3) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = extensionSearchQuery,
                            onValueChange = { extensionSearchQuery = it },
                            placeholder = { Text("Search plugins...", color = Color.White.copy(alpha = 0.5f)) },
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
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (filteredPlugins.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExtensionOff,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.45f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (sourceFilterTab == "Installed") "No plugins installed yet" else "No matching plugins found",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Install or manage plugins in Settings → Video Source Plugins",
                                    color = Color.White.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                        ) {
                            items(filteredPlugins, key = { it.id }) { plugin ->
                                val isSelected = plugin.id == activeSourceId
                                val canUse = plugin.isInstalled && plugin.isEnabled
                                Surface(
                                    onClick = {
                                        if (!plugin.isInstalled) {
                                            aspectRatioToastMessage = "${plugin.name} is not installed (Settings → Plugins)"
                                            return@Surface
                                        }
                                        if (!plugin.isEnabled) {
                                            aspectRatioToastMessage = "${plugin.name} is turned off (Settings → Plugins)"
                                            return@Surface
                                        }
                                        if (!isSelected) {
                                            pendingAudioResumePosMs = currentPositionMs.coerceAtLeast(0L)
                                            selectedEmbeddedAudioKey = null
                                            embeddedAudioTracks = emptyList()
                                            selectedServer = null
                                            availableServers = emptyList()
                                            PluginManager.setActiveSource(plugin.id)
                                            aspectRatioToastMessage = "Switched source to ${plugin.name}"
                                        }
                                        showSourceSheet = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                                    border = if (isSelected) BorderStroke(1.dp, Color(0xFFA5B4FC).copy(alpha = 0.65f)) else null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = when {
                                                    isSelected && canUse -> Color(0xFF4F5D95)
                                                    canUse -> Color(0xFF37474F)
                                                    else -> Color(0xFF32323C)
                                                },
                                                shape = CircleShape,
                                                modifier = Modifier.size(38.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = if (canUse) Icons.Default.Extension else Icons.Default.ExtensionOff,
                                                        contentDescription = null,
                                                        tint = if (canUse) Color.White else Color.White.copy(alpha = 0.45f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = plugin.name,
                                                        color = if (canUse) Color.White else Color.White.copy(alpha = 0.6f),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    Text(
                                                        text = "v${plugin.installedVersion ?: plugin.version}",
                                                        color = Color(0xFFA5B4FC).copy(alpha = if (canUse) 0.9f else 0.5f),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                val statusText = when {
                                                    plugin.isInstalled && plugin.isEnabled -> "Installed • Active"
                                                    plugin.isInstalled -> "Installed • Disabled"
                                                    else -> "Not Installed"
                                                }
                                                Text(
                                                    text = "$statusText • ${plugin.author}",
                                                    color = when {
                                                        plugin.isInstalled && plugin.isEnabled -> Color(0xFF4ADE80).copy(alpha = 0.9f)
                                                        plugin.isInstalled -> Color(0xFFFBBF24).copy(alpha = 0.85f)
                                                        else -> Color.White.copy(alpha = 0.45f)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }

                                        if (isSelected && canUse) {
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
        // PICTURE & ASPECT RATIO BOTTOM SHEET
        // ══════════════════════════════════════════════════════════════
        AnimatedVisibility(
            visible = !effectiveInPip && showPictureSheet,
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 4.dp)
                            .size(width = 44.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AspectRatio,
                                contentDescription = null,
                                tint = Color(0xFFA5B4FC),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Picture & Aspect Ratio",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(
                            onClick = {
                                pictureMode = VideoPictureMode.FIT
                                videoZoom = 1.0f
                                aspectRatioToastMessage = "Picture reset to Fit (100%)"
                            }
                        ) {
                            Text("Reset", color = Color(0xFFA5B4FC), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Aspect Ratio Mode Options
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VideoPictureMode.entries.forEach { mode ->
                            val isSelected = pictureMode == mode
                            Surface(
                                onClick = {
                                    pictureMode = mode
                                    if (mode == VideoPictureMode.FIT) videoZoom = 1.0f
                                    aspectRatioToastMessage = mode.toastLabel
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF333C57) else Color(0xFF282830),
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFFA5B4FC).copy(alpha = 0.65f)) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = mode.title,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            color = Color.White.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = Color(0xFFA5B4FC),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                    // Live Video Zoom Slider (100% - 150%)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Video Zoom",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${(videoZoom * 100).toInt()}%",
                                color = Color(0xFFA5B4FC),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = videoZoom,
                            onValueChange = { videoZoom = it },
                            valueRange = 1.0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFA5B4FC),
                                activeTrackColor = Color(0xFFA5B4FC),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
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
                            text = "Anime4K Shaders",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    animeEnhancementStatus?.let { status ->
                        Surface(
                            color = Color(0xFF333C57),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFA5B4FC).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Active: ${status.detailLabel()}",
                                color = Color(0xFFA5B4FC),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Anime4K 2x Upscaler",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${animeUpscaleQuality.label} + anti-ringing Catmull-Rom",
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = animeUpscaleEnabled,
                            onCheckedChange = {
                                CatalogConfig.setAnimeUpscaleEnabled(it)
                                aspectRatioToastMessage = if (it) "Anime4K 2x (${animeUpscaleQuality.shortLabel}): ON" else "Anime4K 2x Upscaler: OFF"
                            }
                        )
                    }

                    if (animeUpscaleEnabled || lineSharpeningEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "CNN Quality Model",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimeUpscaleQuality.entries.forEach { preset ->
                                    val selected = animeUpscaleQuality == preset
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            CatalogConfig.setAnimeUpscaleQuality(preset)
                                            aspectRatioToastMessage = "Anime4K Model: ${preset.label}"
                                        },
                                        label = { Text(preset.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF333C57),
                                            selectedLabelColor = Color(0xFFA5B4FC),
                                            containerColor = Color(0xFF282830),
                                            labelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                            Text(
                                text = animeUpscaleQuality.description,
                                color = Color.White.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodySmall
                            )
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
                                text = "CNN restore, HQ darken/thin & halo-free ink sharpening",
                                color = Color.White.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = lineSharpeningEnabled,
                            onCheckedChange = {
                                CatalogConfig.setAnimeLineSharpening(it)
                                aspectRatioToastMessage = if (it) "Anime Line Sharpening (${animeSharpenStrength.label}): ON" else "Anime Line Sharpening: OFF"
                            }
                        )
                    }

                    if (lineSharpeningEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Line Sharpening Intensity",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimeSharpenStrength.entries.forEach { level ->
                                    val selected = animeSharpenStrength == level
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            CatalogConfig.setAnimeSharpenStrength(level)
                                            aspectRatioToastMessage = "Line Sharpening: ${level.label}"
                                        },
                                        label = { Text(level.label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF333C57),
                                            selectedLabelColor = Color(0xFFA5B4FC),
                                            containerColor = Color(0xFF282830),
                                            labelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

private class ExoVideoEffectsHolder {
    @Volatile
    var pipelineConfigured = false
        private set
    @Volatile
    private var upscaleEnabled = false
    @Volatile
    private var lineSharpenEnabled = false
    @Volatile
    private var quality = AnimeUpscaleQuality.AUTO
    @Volatile
    private var sharpenStrength = AnimeSharpenStrength.BALANCED

    fun update(
        pipelineConfigured: Boolean,
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        quality: AnimeUpscaleQuality,
        sharpenStrength: AnimeSharpenStrength,
    ) {
        this.pipelineConfigured = pipelineConfigured
        this.upscaleEnabled = upscaleEnabled
        this.lineSharpenEnabled = lineSharpenEnabled
        this.quality = quality
        this.sharpenStrength = sharpenStrength
    }

    fun matches(
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        quality: AnimeUpscaleQuality,
        sharpenStrength: AnimeSharpenStrength,
    ): Boolean =
        this.upscaleEnabled == upscaleEnabled &&
            this.lineSharpenEnabled == lineSharpenEnabled &&
            this.quality == quality &&
            this.sharpenStrength == sharpenStrength
}

@Composable
private fun BoxScope.SkipSegmentOverlay(
    segment: SkipSegment?,
    compact: Boolean,
    bottomPadding: androidx.compose.ui.unit.Dp,
    endPadding: androidx.compose.ui.unit.Dp,
    onSkip: (SkipSegment) -> Unit
) {
    AnimatedVisibility(
        visible = segment != null,
        enter = fadeIn() + slideInHorizontally(initialOffsetX = { it / 2 }),
        exit = fadeOut() + slideOutHorizontally(targetOffsetX = { it / 2 }),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = endPadding, bottom = bottomPadding)
            .zIndex(8f)
    ) {
        val current = segment ?: return@AnimatedVisibility
        Surface(
            onClick = { onSkip(current) },
            color = Color(0xFF1A1D2B).copy(alpha = 0.90f),
            shape = RoundedCornerShape(if (compact) 18.dp else 22.dp),
            border = BorderStroke(1.2.dp, Color(0xFFA5B4FC)),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 12.dp else 18.dp,
                    vertical = if (compact) 7.dp else 10.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = current.label,
                    tint = Color(0xFFA5B4FC),
                    modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                )
                Text(
                    text = current.label,
                    color = Color.White,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold
                )
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

// Data classes & helpers for Audio track selection
private data class EmbeddedAudioTrack(
    val key: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val displayName: String,
    val shortLanguage: String,
    val detail: String,
    val isSelected: Boolean,
    val trackGroup: TrackGroup
)

private data class ServerAudioOption(
    val typeKey: String,
    val displayName: String,
    val shortName: String,
    val serverCount: Int
)

private fun audioLanguageSortOrder(typeKey: String): Int = when (typeKey.lowercase()) {
    "sub", "ja", "jpn", "japanese" -> 0
    "dub", "en", "eng", "english" -> 1
    "id", "ind", "in", "indo", "indonesian" -> 2
    "raw" -> 3
    else -> 4
}

private fun audioTypeShortLanguage(typeKey: String): String {
    val clean = typeKey.trim().lowercase()
    return when {
        clean == "sub" || clean == "raw" || clean == "ja" || clean == "jpn" || clean.contains("japan") -> "Japanese"
        clean == "dub" || clean == "en" || clean == "eng" || clean.contains("english") -> "English"
        clean == "id" || clean == "ind" || clean == "in" || clean.contains("indo") -> "Indonesian"
        clean == "ms" || clean == "msa" || clean.contains("malay") -> "Malay"
        clean == "ko" || clean == "kor" || clean.contains("korean") -> "Korean"
        clean == "zh" || clean == "zho" || clean == "chi" || clean.contains("chin") -> "Chinese"
        clean == "es" || clean == "spa" || clean.contains("span") -> "Spanish"
        clean == "pt" || clean == "por" || clean.contains("portug") -> "Portuguese"
        clean == "fr" || clean == "fra" || clean == "fre" || clean.contains("french") -> "French"
        clean == "de" || clean == "deu" || clean == "ger" || clean.contains("german") -> "German"
        clean == "th" || clean == "tha" || clean.contains("thai") -> "Thai"
        clean == "vi" || clean == "vie" || clean.contains("viet") -> "Vietnamese"
        clean == "ar" || clean == "ara" || clean.contains("arab") -> "Arabic"
        clean == "hi" || clean == "hin" || clean.contains("hindi") -> "Hindi"
        clean == "tl" || clean == "fil" || clean.contains("tagalog") || clean.contains("filip") -> "Filipino"
        else -> {
            val localeName = Locale.forLanguageTag(clean).getDisplayLanguage(Locale.ENGLISH)
            if (localeName.isNotBlank() && !localeName.equals(clean, ignoreCase = true) && !localeName.equals("und", ignoreCase = true)) {
                localeName.replaceFirstChar { it.uppercase() }
            } else {
                clean.replaceFirstChar { it.uppercase() }.ifBlank { "Japanese" }
            }
        }
    }
}

private fun audioTypeDisplayName(typeKey: String): String {
    return when (typeKey.trim().lowercase()) {
        "sub" -> "Japanese (Original • Sub)"
        "dub" -> "English (Dub)"
        "raw" -> "Japanese (Raw)"
        "id", "ind", "in", "indo", "indonesian" -> "Indonesian"
        else -> audioTypeShortLanguage(typeKey)
    }
}

private fun formatAudioLanguageName(
    langCode: String?,
    label: String?,
    fallbackAudioType: String
): Pair<String, String> {
    val cleanLabel = label?.trim().orEmpty()
    val cleanCode = langCode?.trim()?.lowercase().orEmpty()
        .takeIf { it.isNotBlank() && it != "und" && it != "unknown" }

    if (cleanCode != null) {
        val shortLang = audioTypeShortLanguage(cleanCode)
        val display = if (cleanLabel.isNotBlank() && !cleanLabel.equals(shortLang, ignoreCase = true) && !cleanLabel.equals(cleanCode, ignoreCase = true)) {
            "$shortLang ($cleanLabel)"
        } else {
            shortLang
        }
        return Pair(display, shortLang)
    }

    if (cleanLabel.isNotBlank() && !cleanLabel.equals("und", ignoreCase = true) && !cleanLabel.equals("unknown", ignoreCase = true)) {
        val shortLang = audioTypeShortLanguage(cleanLabel)
        val display = if (cleanLabel.equals(shortLang, ignoreCase = true)) shortLang else cleanLabel
        return Pair(display, shortLang)
    }

    val fallbackShort = audioTypeShortLanguage(fallbackAudioType)
    return Pair(audioTypeDisplayName(fallbackAudioType), fallbackShort)
}

private fun extractEmbeddedAudioTracks(tracks: Tracks, fallbackAudioType: String): List<EmbeddedAudioTrack> {
    val result = mutableListOf<EmbeddedAudioTrack>()
    tracks.groups.forEachIndexed { groupIdx, group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
        val mediaGroup = group.mediaTrackGroup
        for (trackIdx in 0 until mediaGroup.length) {
            if (!group.isTrackSupported(trackIdx)) continue
            val fmt = mediaGroup.getFormat(trackIdx)
            val (displayName, shortLang) = formatAudioLanguageName(fmt.language, fmt.label, fallbackAudioType)
            val channelLabel = when (fmt.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1 Surround"
                8 -> "7.1 Surround"
                else -> if (fmt.channelCount > 0) "${fmt.channelCount}ch" else null
            }
            val codecLabel = when {
                fmt.sampleMimeType?.contains("mp4a", ignoreCase = true) == true || fmt.codecs?.startsWith("mp4a", ignoreCase = true) == true -> "AAC"
                fmt.sampleMimeType?.contains("eac3", ignoreCase = true) == true -> "Dolby Digital+"
                fmt.sampleMimeType?.contains("ac3", ignoreCase = true) == true -> "Dolby Digital"
                fmt.sampleMimeType?.contains("opus", ignoreCase = true) == true -> "Opus"
                fmt.sampleMimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
                else -> null
            }
            val bitrateLabel = if (fmt.bitrate > 0) "${fmt.bitrate / 1000} kbps" else null
            val detail = listOfNotNull(channelLabel, codecLabel, bitrateLabel).joinToString(" • ")

            result.add(
                EmbeddedAudioTrack(
                    key = "${groupIdx}_${trackIdx}_${fmt.id.orEmpty()}_${fmt.language.orEmpty()}",
                    groupIndex = groupIdx,
                    trackIndex = trackIdx,
                    displayName = displayName,
                    shortLanguage = shortLang,
                    detail = detail,
                    isSelected = group.isTrackSelected(trackIdx),
                    trackGroup = mediaGroup
                )
            )
        }
    }
    return result
}

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

private enum class VideoPictureMode(
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val toastLabel: String,
    val exoResizeMode: Int
) {
    FIT(
        shortLabel = "Fit",
        title = "Fit (Original 16:9)",
        subtitle = "Keep original video ratio with no cropping",
        toastLabel = "Picture: Fit (Original)",
        exoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    ),
    COVER(
        shortLabel = "Cover",
        title = "Cover (Zoom to Fill)",
        subtitle = "Crop edges to fill wide displays without black bars",
        toastLabel = "Picture: Cover (Fill)",
        exoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    ),
    STRETCH(
        shortLabel = "Stretch",
        title = "Stretch (Full Screen)",
        subtitle = "Stretch frame to fill entire viewport",
        toastLabel = "Picture: Stretch (Full)",
        exoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
    ),
    CLASSIC_4_3(
        shortLabel = "4:3",
        title = "Classic 4:3",
        subtitle = "Retro 4:3 framing for classic anime",
        toastLabel = "Picture: Classic 4:3",
        exoResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    )
}

private fun formatEpisodeHeading(episodeNumber: Int, rawTitle: String): String {
    val trimmed = rawTitle.trim()
    val isRedundant = trimmed.isBlank() ||
        trimmed.equals("Episode $episodeNumber", ignoreCase = true) ||
        trimmed.equals("Episode 0$episodeNumber", ignoreCase = true) ||
        trimmed.equals("Ep $episodeNumber", ignoreCase = true)
    return if (isRedundant) "Episode $episodeNumber" else "Ep $episodeNumber • $trimmed"
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
