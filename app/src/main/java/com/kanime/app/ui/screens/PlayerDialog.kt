@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.kanime.app.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import com.kanime.app.ui.player.KanimeLandscapePlayerScreen
import com.kanime.app.ui.player.KanimeLandscapeUiState
import com.kanime.app.ui.player.KanimePortraitPlayerScreen
import com.kanime.app.ui.player.KanimePlayerUiState
import com.kanime.app.ui.player.LandscapePillOption
import com.kanime.app.ui.player.PlayerEpisodeUiItem
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.res.stringResource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kanime.app.R
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
import com.kanime.app.data.plugin.PluginSubtitle
import com.kanime.app.ui.player.CaptionBackground
import com.kanime.app.ui.player.CaptionColor
import com.kanime.app.ui.player.CaptionEdgeStyle
import com.kanime.app.ui.player.CaptionFont
import com.kanime.app.ui.player.CaptionPosition
import com.kanime.app.ui.player.CaptionTextSize
import com.kanime.app.ui.player.PlayerIconButton
import com.kanime.app.ui.player.PlayerScrims
import com.kanime.app.ui.player.PlayerSeekBar
import com.kanime.app.ui.player.SubtitleOffsetHelper
import com.kanime.app.ui.player.applyCaptionStyle
import com.kanime.app.ui.player.anime.AnimeEnhancementStatus
import com.kanime.app.ui.player.anime.AnimeShaderStatusTracker
import com.kanime.app.ui.player.anime.AnimeShaders
import com.kanime.app.ui.player.anime.AnimeSharpenStrength
import com.kanime.app.ui.player.anime.AnimeUpscaleQuality
import com.kanime.app.ui.components.GlassSurface
import com.kanime.app.ui.theme.GeistFontFamily
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.theme.ThemePreferences
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface StreamResolutionState {
    data class Loading(val message: String) : StreamResolutionState
    data class Success(val stream: PluginStream) : StreamResolutionState
    data class Error(val message: String) : StreamResolutionState
}

private fun detectSubtitleMimeType(url: String): String {
    val clean = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
        clean.endsWith(".m3u8") || url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
        clean.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        clean.endsWith(".ass") || clean.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        clean.endsWith(".srt") ||
            url.contains("opensubtitles", ignoreCase = true) ||
            url.contains("strem.io", ignoreCase = true) ||
            url.contains("subencoding-", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
        else -> MimeTypes.TEXT_VTT
    }
}

private fun subtitleLanguageCode(label: String): String {
    val l = label.trim().lowercase(Locale.ROOT)
    return when {
        l.startsWith("eng") || l == "en" -> "en"
        l.startsWith("spa") || l == "es" -> "es"
        l.startsWith("por") || l == "pt" || l == "pob" -> "pt"
        l.startsWith("fre") || l.startsWith("fra") || l == "fr" -> "fr"
        l.startsWith("ind") || l == "id" -> "id"
        l.startsWith("mal") || l.startsWith("msa") || l == "ms" -> "ms"
        l.startsWith("ger") || l.startsWith("deu") || l == "de" -> "de"
        l.startsWith("ita") || l == "it" -> "it"
        l.startsWith("ara") || l == "ar" -> "ar"
        l.startsWith("jap") || l.startsWith("jpn") || l == "ja" -> "ja"
        l.startsWith("kor") || l == "ko" -> "ko"
        l.startsWith("chi") || l.startsWith("zho") || l == "zh" -> "zh"
        l.startsWith("vie") || l == "vi" -> "vi"
        l.startsWith("tha") || l == "th" -> "th"
        l.startsWith("rus") || l == "ru" -> "ru"
        l.startsWith("tur") || l == "tr" -> "tr"
        l.startsWith("pol") || l == "pl" -> "pl"
        l.startsWith("dut") || l.startsWith("nld") || l == "nl" -> "nl"
        l.startsWith("fil") || l.startsWith("tgl") || l == "tl" -> "tl"
        else -> l.take(3).ifBlank { "en" }
    }
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

    val allPlugins by PluginManager.plugins.collectAsStateWithLifecycle()
    val availablePlugins = remember(allPlugins) { allPlugins.filter { it.isVideoPlugin } }
    val activeSourceId by PluginManager.activeSourceId.collectAsStateWithLifecycle()

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
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPlaybackSettingsModal by remember { mutableStateOf(false) }

    val activeAudioShortLabel = remember(embeddedAudioTracks, selectedEmbeddedAudioKey, selectedAudioType) {
        val activeEmbedded = embeddedAudioTracks.firstOrNull { it.key == selectedEmbeddedAudioKey }
            ?: embeddedAudioTracks.firstOrNull { it.isSelected }
        activeEmbedded?.shortLanguage ?: audioTypeShortLanguage(selectedAudioType)
    }
    var subtitlesEnabled by remember { mutableStateOf(true) }
    var selectedSubtitleTrack by remember { mutableStateOf("English [CC]") }
    var embeddedTextTrackLabels by remember(anime.id, episode.episodeNumber) { mutableStateOf<List<String>>(emptyList()) }
    val latestSubtitlesEnabled by rememberUpdatedState(subtitlesEnabled)
    var useOpenSubtitle by remember { mutableStateOf(PluginManager.isUseOpenSubtitleInPlayer(context)) }
    var openSubtitleTracks by remember(anime.id, episode.episodeNumber) { mutableStateOf<List<PluginSubtitle>>(emptyList()) }
    var isLoadingOpenSubtitles by remember(anime.id, episode.episodeNumber) { mutableStateOf(false) }
    var openSubtitleFetched by remember(anime.id, episode.episodeNumber) { mutableStateOf(false) }
    var subtitleDelayMs by remember(anime.id, episode.episodeNumber) { mutableLongStateOf(0L) }
    var subtitleScale by remember(anime.id, episode.episodeNumber) { mutableDoubleStateOf(1.0) }
    var isAutoSyncing by remember(anime.id, episode.episodeNumber) { mutableStateOf(false) }
    var autoSyncedKey by remember(anime.id, episode.episodeNumber) { mutableStateOf("") }
    val subtitlePluginEntry = remember(allPlugins) { allPlugins.firstOrNull { it.isSubtitlePlugin } }
    val activeSubtitlePlugin = remember(allPlugins) {
        allPlugins.firstOrNull { it.isSubtitlePlugin && it.isInstalled && it.isEnabled }
    }
    var subtitleSheetTab by remember { mutableIntStateOf(0) } // 0 = Tracks, 1 = Style
    val captionStyle by CatalogConfig.captionStyleFlow.collectAsStateWithLifecycle()
    var autoSelectStream by remember { mutableStateOf(true) }
    var extensionSearchQuery by remember { mutableStateOf("") }
    var sourceFilterTab by remember { mutableStateOf("Installed") } // "Installed" or "All Plugins"

    val currentActivePlugin = remember(availablePlugins, activeSourceId) {
        availablePlugins.firstOrNull { it.id == activeSourceId } ?: availablePlugins.firstOrNull()
    }
    val activePluginLabel = currentActivePlugin?.name ?: "No Plugin"

    // Persisted Anime4K 2x Upscaler & Line Sharpening settings
    val animeUpscaleEnabled by CatalogConfig.animeUpscaleEnabledFlow.collectAsStateWithLifecycle()
    val lineSharpeningEnabled by CatalogConfig.animeLineSharpeningFlow.collectAsStateWithLifecycle()
    val animeUpscaleQuality by CatalogConfig.animeUpscaleQualityFlow.collectAsStateWithLifecycle()
    val animeSharpenStrength by CatalogConfig.animeSharpenStrengthFlow.collectAsStateWithLifecycle()
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

    // Reference for PiP action triggered from the Settings bottom sheet
    var enterPipTrigger by remember { mutableStateOf<(() -> Unit)?>(null) }

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
    val activeSkipSegment by remember(anime.id, episode.episodeNumber) {
        derivedStateOf { skipTimes.activeSegmentAt(currentPositionMs) }
    }

    // Subtitle auto-sync (KOpenSubtitle 1.0.3+)
    fun runSubtitleAutoSync(manual: Boolean) {
        val plugin = activeSubtitlePlugin ?: return
        val track = openSubtitleTracks.firstOrNull { it.lang.equals(selectedSubtitleTrack, ignoreCase = true) }
        if (track == null) {
            if (manual) aspectRatioToastMessage = "Select an OpenSubtitles track first"
            return
        }
        val intro = skipTimes.intro
        val outro = skipTimes.outro
        if (intro == null && outro == null) {
            if (manual) aspectRatioToastMessage = "No intro/outro data for this episode yet"
            return
        }
        if (isAutoSyncing) return
        coroutineScope.launch {
            isAutoSyncing = true
            val res = PluginBridge.analyzeSync(
                context = context,
                url = track.url,
                opStartMs = intro?.startMs ?: 0L,
                opEndMs = intro?.endMs ?: 0L,
                edStartMs = outro?.startMs ?: 0L,
                edEndMs = outro?.endMs ?: 0L,
                authority = plugin.providerAuthority,
                durationMs = durationMs
            )
            isAutoSyncing = false
            if (res == null) {
                if (manual) aspectRatioToastMessage = "Update KOpenSubtitle to 1.0.4 for better auto-sync"
                return@launch
            }
            val usable = res.ok && (res.confidence == "high" || res.confidence == "medium")
            if (!usable) {
                if (manual) aspectRatioToastMessage = "Couldn't auto-sync this subtitle — adjust manually"
                return@launch
            }
            if (res.offsetMs == 0L && res.scale == 1.0) {
                if (manual) aspectRatioToastMessage = "Subtitles are already in sync"
                return@launch
            }
            subtitleScale = res.scale
            subtitleDelayMs = res.offsetMs.coerceIn(-60_000L, 60_000L)
            val sec = subtitleDelayMs / 1000.0
            aspectRatioToastMessage = "Subtitles auto-synced " + String.format(Locale.US, "%+.1fs", sec) +
                (if (res.scale != 1.0) " (fps fix)" else "") +
                (if (res.confidence != "high") " · fine-tune if needed" else "")
        }
    }

    // Marks drawn on the seek bar (recap / intro / outro)
    val seekSegments = remember(skipTimes) {
        listOfNotNull(skipTimes.recap, skipTimes.intro, skipTimes.outro)
            .filter { it.endMs > it.startMs }
            .map { it.startMs..it.endMs }
    }

    // "Sync to line": aligns nearest subtitle cue to current playback position
    fun syncSubtitleToLine() {
        val stream = (resolutionState as? StreamResolutionState.Success)?.stream
        val track = openSubtitleTracks.firstOrNull { it.lang.equals(selectedSubtitleTrack, ignoreCase = true) }
            ?: stream?.subtitles?.firstOrNull { it.lang.equals(selectedSubtitleTrack, ignoreCase = true) }
        if (track == null) {
            aspectRatioToastMessage = "Select an external subtitle track first"
            return
        }
        val tapPositionMs = currentPositionMs
        coroutineScope.launch {
            val starts = SubtitleOffsetHelper.loadCueStarts(track.url, stream?.referer)
            if (starts.isEmpty()) {
                aspectRatioToastMessage = "Couldn't read this subtitle file"
                return@launch
            }
            val heardAtMs = (tapPositionMs - 250L).coerceAtLeast(0L)
            val scale = subtitleScale
            val currentDelay = subtitleDelayMs
            var bestIndex = -1
            var bestDistance = Long.MAX_VALUE
            for (i in starts.indices) {
                val shownAt = (starts[i] * scale).toLong() + currentDelay
                val distance = kotlin.math.abs(shownAt - heardAtMs)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestIndex = i
                }
            }
            if (bestIndex < 0 || bestDistance > 30_000L) {
                aspectRatioToastMessage = "No subtitle line near this moment — try again at a line"
                return@launch
            }
            val newDelay = heardAtMs - (starts[bestIndex] * scale).toLong()
            subtitleDelayMs = newDelay.coerceIn(-60_000L, 60_000L)
            aspectRatioToastMessage = "Subtitles synced " + String.format(Locale.US, "%+.1fs", subtitleDelayMs / 1000.0)
        }
    }

    LaunchedEffect(openSubtitleTracks, selectedSubtitleTrack, skipTimes, useOpenSubtitle) {
        if (!useOpenSubtitle || openSubtitleTracks.isEmpty() || skipTimes.isEmpty) return@LaunchedEffect
        val track = openSubtitleTracks.firstOrNull { it.lang.equals(selectedSubtitleTrack, ignoreCase = true) }
            ?: return@LaunchedEffect
        if (autoSyncedKey == track.url) return@LaunchedEffect
        if (autoSyncedKey.isNotEmpty()) {
            subtitleDelayMs = 0L
            subtitleScale = 1.0
        } else if (subtitleDelayMs != 0L || subtitleScale != 1.0) {
            return@LaunchedEffect
        }
        autoSyncedKey = track.url
        runSubtitleAutoSync(manual = false)
    }

    // Audio & Brightness Gestures
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

    LaunchedEffect(gestureType) {
        if (gestureType != null) {
            delay(1500)
            gestureType = null
        }
    }

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
    LaunchedEffect(
        showControls,
        isPlaying,
        isControlsLocked,
        showStreamsSheet,
        showSourceSheet,
        showSpeedSheet,
        showSubtitlesSheet,
        showAudioSheet,
        showEnhanceSheet,
        showPictureSheet,
        showSettingsSheet,
        showPlaybackSettingsModal
    ) {
        if (
            showControls &&
            isPlaying &&
            !isControlsLocked &&
            !showStreamsSheet &&
            !showSourceSheet &&
            !showSpeedSheet &&
            !showSubtitlesSheet &&
            !showAudioSheet &&
            !showEnhanceSheet &&
            !showPictureSheet &&
            !showSettingsSheet &&
            !showPlaybackSettingsModal
        ) {
            delay(5000)
            showControls = false
        }
    }

    // Resolve stream via the active installed plugin through PluginBridge
    var resolveJob by remember { mutableStateOf<Job?>(null) }
    fun resolveStream(serverToUse: PluginServer? = null) {
        resolveJob?.cancel()
        resolveJob = coroutineScope.launch {
            PluginManager.checkPluginStatus(context)
            val videoPlugins = PluginManager.plugins.value.filter { it.isVideoPlugin }
            val activePlugin = videoPlugins.firstOrNull { it.id == activeSourceId }
                ?: videoPlugins.firstOrNull { it.isInstalled && it.isEnabled }
                ?: videoPlugins.firstOrNull()

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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                resolutionState = StreamResolutionState.Error("${activePlugin.name} stream extraction error: ${e.message}")
            }
        }
    }

    LaunchedEffect(anime.id, episode.episodeNumber) {
        resolutionState = StreamResolutionState.Loading("Loading episode ${episode.episodeNumber}...")
    }

    LaunchedEffect(anime.id, episode.episodeNumber, selectedAudioType, activeSourceId) {
        resolveStream()
    }

    // Fetch online subtitles asynchronously in the background when "Use KOpenSubtitle" is enabled
    LaunchedEffect(anime.id, episode.episodeNumber, useOpenSubtitle, activeSubtitlePlugin?.providerAuthority) {
        val subPlugin = activeSubtitlePlugin
        if (!useOpenSubtitle || subPlugin == null) return@LaunchedEffect
        if (openSubtitleFetched) {
            if (openSubtitleTracks.isNotEmpty() && subtitlesEnabled) {
                val hasCurrentOpenSub = openSubtitleTracks.any { it.lang.equals(selectedSubtitleTrack, ignoreCase = true) }
                if (!hasCurrentOpenSub) {
                    selectedSubtitleTrack = (openSubtitleTracks.firstOrNull { it.isDefault } ?: openSubtitleTracks.first()).lang
                }
            }
            return@LaunchedEffect
        }
        isLoadingOpenSubtitles = true
        try {
            val fetched = PluginBridge.getOnlineSubtitles(
                context = context,
                title = anime.title,
                episode = episode.episodeNumber,
                englishTitle = anime.englishTitle,
                romajiTitle = anime.romajiTitle,
                year = anime.year,
                malId = anime.malId,
                anilistId = anime.anilistId,
                kitsuId = anime.kitsuId,
                mediaType = anime.type,
                episodeTitle = episode.title,
                episodeAired = episode.aired,
                runtimeMin = Regex("""\d+""").find(episode.duration.ifBlank { anime.duration })?.value?.toIntOrNull() ?: 0,
                authority = subPlugin.providerAuthority
            )
            openSubtitleTracks = fetched
            openSubtitleFetched = true
            if (fetched.isNotEmpty() && subtitlesEnabled) {
                selectedSubtitleTrack = (fetched.firstOrNull { it.isDefault } ?: fetched.first()).lang
            }
        } finally {
            isLoadingOpenSubtitles = false
        }
    }

    // Native back press handling: close floating Playback Settings first, then exit landscape, then close player
    BackHandler {
        when {
            showPlaybackSettingsModal -> showPlaybackSettingsModal = false
            isLandscape -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> onDismiss()
        }
    }

    // Immersive mode & display cutout handling
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

    val dynamicColor by ThemePreferences.dynamicColorFlow.collectAsStateWithLifecycle()

    KAnimeTheme(
        darkTheme = true,
        dynamicColor = dynamicColor,
        updateSystemBars = false,
        animateTransition = false
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim)
        ) {
            when (val state = resolutionState) {
                is StreamResolutionState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .safeDrawingPadding()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = anime.title,
                            color = MaterialTheme.colorScheme.onSurface,
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
                            color = MaterialTheme.colorScheme.primary,
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 340.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = activePluginLabel,
                                color = MaterialTheme.colorScheme.primary,
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
                            .verticalScroll(rememberScrollState())
                            .safeDrawingPadding()
                            .padding(horizontal = 24.dp, vertical = 24.dp),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 420.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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

                    val exoPlayer = remember(streamUrl, animeRebuildGeneration, videoEffectsDisabledByError) {
                        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36")
                            .setAllowCrossProtocolRedirects(true)
                            .setConnectTimeoutMs(15000)
                            .setReadTimeoutMs(15000)
                            .setDefaultRequestProperties(
                                mapOf(
                                    "Referer" to (stream.referer ?: "https://anikototv.bz/"),
                                    "Origin" to (stream.referer?.trimEnd('/') ?: "https://anikototv.bz")
                                )
                            )

                        val validSourceSubtitles = stream.subtitles.filter { sub ->
                            sub.url.isNotBlank() &&
                                !sub.url.contains("thumbnails", ignoreCase = true) &&
                                !sub.lang.contains("thumbnail", ignoreCase = true) &&
                                !sub.lang.contains("unknown", ignoreCase = true)
                        }
                        val initialSubtitles = if (useOpenSubtitle && activeSubtitlePlugin != null && openSubtitleTracks.isNotEmpty()) {
                            (openSubtitleTracks + validSourceSubtitles).distinctBy { it.url }
                        } else {
                            validSourceSubtitles
                        }

                        val subtitleConfigs = initialSubtitles.map { sub ->
                            val langCode = subtitleLanguageCode(sub.lang)
                            MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                                .setMimeType(detectSubtitleMimeType(sub.url))
                                .setLanguage(langCode)
                                .setLabel(sub.lang)
                                .setSelectionFlags(if (sub.isDefault || langCode == "en") C.SELECTION_FLAG_DEFAULT else 0)
                                .build()
                        }

                        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
                        val mediaSourceFactory = DefaultMediaSourceFactory(context)
                            .setDataSourceFactory(dataSourceFactory)

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
                                val streamMimeType = when {
                                    streamUrl.contains(".m3u8") || stream.type.equals("hls", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                                    stream.type.equals("mp4", ignoreCase = true) || streamUrl.contains(".mp4") || streamUrl.contains("googlevideo.com") -> MimeTypes.VIDEO_MP4
                                    else -> null
                                }

                                val mediaItem = MediaItem.Builder()
                                    .setUri(Uri.parse(streamUrl))
                                    .setMimeType(streamMimeType)
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

                                        val textLabels = mutableListOf<String>()
                                        for (group in tracks.groups) {
                                            if (group.type == C.TRACK_TYPE_TEXT) {
                                                val tg = group.mediaTrackGroup
                                                for (i in 0 until tg.length) {
                                                    val fmt = tg.getFormat(i)
                                                    val lbl = fmt.label?.takeIf { it.isNotBlank() }
                                                        ?: fmt.language?.takeIf { it.isNotBlank() && it != "und" }?.let { audioTypeShortLanguage(it) }
                                                        ?: "Embedded Subtitles"
                                                    if (!textLabels.contains(lbl)) textLabels.add(lbl)
                                                }
                                            }
                                        }
                                        embeddedTextTrackLabels = textLabels

                                        if (!latestSubtitlesEnabled) {
                                            val p = trackSelectionParameters.buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .setIgnoredTextSelectionFlags(
                                                    C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT
                                                )
                                                .build()
                                            if (trackSelectionParameters != p) {
                                                trackSelectionParameters = p
                                            }
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

                    val effectiveOpenSubsActive = useOpenSubtitle && activeSubtitlePlugin != null
                    val hasSubShift = subtitleDelayMs != 0L || subtitleScale != 1.0
                    val activeDelayTrackKey = if (hasSubShift) selectedSubtitleTrack else ""
                    val currentSubSignature = remember(effectiveOpenSubsActive, openSubtitleTracks, subtitleDelayMs, subtitleScale, activeDelayTrackKey) {
                        val urls = if (effectiveOpenSubsActive) openSubtitleTracks.joinToString("|") { it.url } else ""
                        "$effectiveOpenSubsActive::$urls::$subtitleDelayMs::$activeDelayTrackKey" +
                            if (subtitleScale != 1.0) "::s$subtitleScale" else ""
                    }
                    val appliedSubSignatureRef = remember(exoPlayer) {
                        val initUrls = if (effectiveOpenSubsActive) openSubtitleTracks.joinToString("|") { it.url } else ""
                        mutableStateOf("$effectiveOpenSubsActive::$initUrls::0::")
                    }

                    LaunchedEffect(exoPlayer, streamUrl, currentSubSignature) {
                        if (currentSubSignature == appliedSubSignatureRef.value) return@LaunchedEffect
                        delay(350L)
                        val validSourceSubtitles = stream.subtitles.filter { sub ->
                            sub.url.isNotBlank() &&
                                !sub.url.contains("thumbnails", ignoreCase = true) &&
                                !sub.lang.contains("thumbnail", ignoreCase = true) &&
                                !sub.lang.contains("unknown", ignoreCase = true)
                        }
                        val mergedSubtitles = if (effectiveOpenSubsActive && openSubtitleTracks.isNotEmpty()) {
                            (openSubtitleTracks + validSourceSubtitles).distinctBy { it.url }
                        } else {
                            validSourceSubtitles
                        }
                        val targetShiftTrack = mergedSubtitles.firstOrNull {
                            it.lang.equals(selectedSubtitleTrack, ignoreCase = true)
                        } ?: mergedSubtitles.firstOrNull { it.isDefault } ?: mergedSubtitles.firstOrNull()

                        val updatedConfigs = mergedSubtitles.map { sub ->
                            val langCode = subtitleLanguageCode(sub.lang)
                            val subUri = if ((subtitleDelayMs != 0L || subtitleScale != 1.0) && sub == targetShiftTrack) {
                                SubtitleOffsetHelper.resolveOffsetSubtitleUri(
                                    context = context,
                                    url = sub.url,
                                    referer = stream.referer,
                                    offsetMs = subtitleDelayMs,
                                    scale = subtitleScale
                                )
                            } else {
                                Uri.parse(sub.url)
                            }
                            MediaItem.SubtitleConfiguration.Builder(subUri)
                                .setMimeType(detectSubtitleMimeType(sub.url))
                                .setLanguage(langCode)
                                .setLabel(sub.lang)
                                .setSelectionFlags(if (sub.isDefault || langCode == "en") C.SELECTION_FLAG_DEFAULT else 0)
                                .build()
                        }

                        val updatedMediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(streamUrl))
                            .setMimeType(if (streamUrl.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else null)
                            .setSubtitleConfigurations(updatedConfigs)
                            .build()

                        val savedPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val wasPlaying = exoPlayer.playWhenReady || exoPlayer.isPlaying
                        exoPlayer.setMediaItem(updatedMediaItem, savedPos)
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = wasPlaying
                        appliedSubSignatureRef.value = currentSubSignature
                    }

                    // Debounced clean ExoPlayer rebuild when Anime4K toggles change
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

                    // Pause/resume cleanly across lifecycle changes (while preserving PiP playback)
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
                        showSettingsSheet = false
                        showPlaybackSettingsModal = false
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

                    SideEffect {
                        enterPipTrigger = { enterPipMode() }
                    }

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

                    LaunchedEffect(exoPlayer, playbackSpeed) {
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    }

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

                    LaunchedEffect(exoPlayer, subtitlesEnabled, selectedSubtitleTrack, embeddedTextTrackLabels) {
                        val builder = exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        if (!subtitlesEnabled || selectedSubtitleTrack == "Off") {
                            builder
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .setIgnoredTextSelectionFlags(
                                    C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_AUTOSELECT
                                )
                        } else {
                            builder
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setIgnoredTextSelectionFlags(0)
                            val targetLang = subtitleLanguageCode(selectedSubtitleTrack)
                            builder.setPreferredTextLanguage(targetLang)
                            builder.setSelectUndeterminedTextLanguage(true)

                            var exactMatch: Pair<TrackGroup, Int>? = null
                            var fallbackMatch: Pair<TrackGroup, Int>? = null
                            for (group in exoPlayer.currentTracks.groups) {
                                if (group.type == C.TRACK_TYPE_TEXT) {
                                    val trackGroup = group.mediaTrackGroup
                                    for (i in 0 until trackGroup.length) {
                                        val format = trackGroup.getFormat(i)
                                        val label = format.label ?: format.language ?: "Embedded Subtitles"
                                        if (label.equals(selectedSubtitleTrack, ignoreCase = true)) {
                                            exactMatch = trackGroup to i
                                            break
                                        }
                                        if (fallbackMatch == null && (
                                                label.contains(selectedSubtitleTrack, ignoreCase = true) ||
                                                    selectedSubtitleTrack.equals("Embedded Subtitles", ignoreCase = true) ||
                                                    (selectedSubtitleTrack.startsWith("Eng", ignoreCase = true) && (format.language == "en" || format.language == "eng"))
                                                )
                                        ) {
                                            fallbackMatch = trackGroup to i
                                        }
                                    }
                                    if (exactMatch != null) break
                                }
                            }
                            (exactMatch ?: fallbackMatch)?.let { (tg, idx) ->
                                builder.setOverrideForType(TrackSelectionOverride(tg, idx))
                            }
                        }
                        exoPlayer.trackSelectionParameters = builder.build()
                    }

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

                    LaunchedEffect(playerView, captionStyle) {
                        applyCaptionStyle(context, playerView.subtitleView, captionStyle)
                    }

                    LaunchedEffect(playerView, subtitlesEnabled, selectedSubtitleTrack) {
                        val showCaptions = subtitlesEnabled && selectedSubtitleTrack != "Off"
                        playerView.subtitleView?.apply {
                            if (!showCaptions) {
                                setCues(emptyList())
                                alpha = 0f
                                visibility = View.GONE
                            } else {
                                alpha = 1f
                                visibility = View.VISIBLE
                            }
                        }
                    }

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
                                    val showCaptions = subtitlesEnabled && selectedSubtitleTrack != "Off"
                                    pv.subtitleView?.let { sv ->
                                        val targetVis = if (showCaptions) View.VISIBLE else View.GONE
                                        val targetAlpha = if (showCaptions) 1f else 0f
                                        if (sv.visibility != targetVis || sv.alpha != targetAlpha) {
                                            if (!showCaptions) sv.setCues(emptyList())
                                            sv.alpha = targetAlpha
                                            sv.visibility = targetVis
                                        }
                                    }
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim)
                        ) {
                            VideoSurface(modifier = Modifier.fillMaxSize())
                        }
                    } else if (!isLandscape) {
                        // ──────────────────────────────────────────────────────────
                        // PORTRAIT MODE — Wired to KanimePortraitPlayerScreen
                        // ──────────────────────────────────────────────────────────
                        val portraitEpisodes = remember(anime.episodeList, anime.coverUrl, anime.duration, durationMs) {
                            val fallbackDuration = anime.duration.ifBlank {
                                if (durationMs > 0L) formatTime(durationMs) else "24:00"
                            }
                            anime.episodeList.map { ep ->
                                PlayerEpisodeUiItem(
                                    id = "${ep.episodeNumber}_${ep.id}",
                                    number = ep.episodeNumber,
                                    title = ep.title.ifBlank { "Episode ${ep.episodeNumber}" },
                                    durationLabel = ep.duration.ifBlank { fallbackDuration },
                                    airDateLabel = ep.aired.orEmpty(),
                                    thumbnailUrl = ep.thumbnail.ifBlank { anime.coverUrl }
                                )
                            }
                        }

                        val currentPortraitEpisode = remember(portraitEpisodes, episode, anime.coverUrl, anime.duration, durationMs) {
                            portraitEpisodes.firstOrNull { it.number == episode.episodeNumber }
                                ?: PlayerEpisodeUiItem(
                                    id = "${episode.episodeNumber}_${episode.id}",
                                    number = episode.episodeNumber,
                                    title = episode.title.ifBlank { "Episode ${episode.episodeNumber}" },
                                    durationLabel = episode.duration.ifBlank {
                                        if (durationMs > 0L) formatTime(durationMs) else anime.duration.ifBlank { "24:00" }
                                    },
                                    airDateLabel = episode.aired.orEmpty(),
                                    thumbnailUrl = episode.thumbnail.ifBlank { anime.coverUrl }
                                )
                        }

                        val portraitQualityChipLabel = remember(selectedServer, qualityBadgeText, isVideoEnhanced) {
                            when {
                                isVideoEnhanced -> "A4K"
                                selectedServer != null -> selectedServer!!.name
                                else -> "Quality"
                            }
                        }

                        val portraitSubtitleChipLabel = remember(subtitlesEnabled, selectedSubtitleTrack) {
                            if (!subtitlesEnabled || selectedSubtitleTrack == "Off") {
                                "Off"
                            } else {
                                "Subtitles"
                            }
                        }

                        // Tip: If your Anime data class has a rating/score field (e.g. anime.rating),
                        // replace qualityBadgeText below with anime.rating.ifBlank { qualityBadgeText }
                        val portraitUiState = KanimePlayerUiState(
                            animeTitle = anime.title,
                            animePosterUrl = anime.coverUrl,
                            rating = qualityBadgeText,
                            currentEpisode = currentPortraitEpisode,
                            episodes = portraitEpisodes,
                            isPlaying = isPlaying,
                            showControls = showControls,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            currentQualityLabel = portraitQualityChipLabel,
                            currentSubtitleLabel = portraitSubtitleChipLabel,
                            playbackSpeed = playbackSpeed,
                            hasNextEpisode = onNextEpisode != null
                        )

                        KanimePortraitPlayerScreen(
                            state = portraitUiState,
                            videoSurface = {
                                VideoSurface(modifier = Modifier.fillMaxSize())
                            },
                            videoOverlayExtras = {
                                DoubleTapSeekOverlay(
                                    side = doubleTapSeekSide,
                                    seconds = doubleTapSeekSeconds,
                                    compact = true
                                )
                                SkipSegmentOverlay(
                                    segment = activeSkipSegment,
                                    compact = true,
                                    bottomPadding = if (showControls) 56.dp else 12.dp,
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
                            },
                            onBack = onDismiss,
                            onCastClick = {
                                aspectRatioToastMessage = "Searching for Google Cast devices..."
                            },
                            onSettingsClick = { showSettingsSheet = true },
                            onFullscreenClick = {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            },
                            onToggleControls = { showControls = !showControls },
                            onDoubleTapLeft = { performDoubleTapSeek(-10) },
                            onDoubleTapRight = { performDoubleTapSeek(+10) },
                            onPlayPauseToggle = {
                                if (isPlaying) pausePlayback() else resumeOrPlay()
                            },
                            onRewind10 = { performDoubleTapSeek(-10) },
                            onForward10 = { performDoubleTapSeek(+10) },
                            onSeekTo = { targetMs ->
                                exoPlayer.seekTo(targetMs)
                                currentPositionMs = targetMs
                                resumePositionRef.longValue = targetMs
                            },
                            onRatingBadgeClick = { showEnhanceSheet = true },
                            onQualityClick = { showStreamsSheet = true },
                            onSubtitlesClick = { showSubtitlesSheet = true },
                            onSpeedClick = { showSpeedSheet = true },
                            onNextEpisodeClick = { onNextEpisode?.invoke() },
                            onEpisodeSelect = { selected ->
                                anime.episodeList
                                    .firstOrNull { it.episodeNumber == selected.number }
                                    ?.let { ep ->
                                        if (ep.episodeNumber != episode.episodeNumber) {
                                            onSelectEpisode?.invoke(ep)
                                        }
                                    }
                            }
                        )
                    } else {
                        // ──────────────────────────────────────────────────────────
                        // LANDSCAPE MODE — Wired to KanimeLandscapePlayerScreen
                        // ──────────────────────────────────────────────────────────
                        val landscapeQualityOptions = remember(availableServers, selectedServer, autoSelectStream, selectedAudioType) {
                            val matchingServers = availableServers
                                .filter { it.type.equals(selectedAudioType, ignoreCase = true) }
                                .ifEmpty { availableServers }
                                .distinctBy { it.name }
                                .take(4)

                            val list = mutableListOf<LandscapePillOption>()
                            list.add(
                                LandscapePillOption(
                                    id = "auto",
                                    label = "Auto",
                                    isSelected = autoSelectStream && selectedServer == null
                                )
                            )
                            if (matchingServers.isNotEmpty()) {
                                matchingServers.forEach { srv ->
                                    list.add(
                                        LandscapePillOption(
                                            id = "srv:${srv.sourceId}",
                                            label = srv.name,
                                            isSelected = selectedServer?.sourceId == srv.sourceId
                                        )
                                    )
                                }
                            } else {
                                list.add(
                                    LandscapePillOption(
                                        id = "preset:1080p",
                                        label = "1080p",
                                        isSelected = !autoSelectStream
                                    )
                                )
                            }
                            list
                        }

                        val landscapeSubtitleOptions = remember(
                            resolutionState,
                            useOpenSubtitle,
                            activeSubtitlePlugin,
                            openSubtitleTracks,
                            embeddedTextTrackLabels,
                            subtitlesEnabled,
                            selectedSubtitleTrack
                        ) {
                            val sourceSubs = (resolutionState as? StreamResolutionState.Success)?.stream?.subtitles
                                ?.filter { sub ->
                                    sub.url.isNotBlank() &&
                                        !sub.url.contains("thumbnails", ignoreCase = true) &&
                                        !sub.lang.contains("thumbnail", ignoreCase = true) &&
                                        !sub.lang.contains("unknown", ignoreCase = true)
                                }
                                ?: emptyList()

                            val trackLabels = mutableListOf<String>()
                            if (useOpenSubtitle && activeSubtitlePlugin != null) {
                                openSubtitleTracks.forEach { sub ->
                                    if (trackLabels.none { it.equals(sub.lang, ignoreCase = true) }) {
                                        trackLabels.add(sub.lang)
                                    }
                                }
                            }
                            sourceSubs.forEach { sub ->
                                if (trackLabels.none { it.equals(sub.lang, ignoreCase = true) }) {
                                    trackLabels.add(sub.lang)
                                }
                            }
                            embeddedTextTrackLabels.forEach { lbl ->
                                if (trackLabels.none { it.equals(lbl, ignoreCase = true) }) {
                                    trackLabels.add(lbl)
                                }
                            }
                            if (trackLabels.isEmpty()) {
                                trackLabels.add("English [CC]")
                            }

                            buildList {
                                add(
                                    LandscapePillOption(
                                        id = "Off",
                                        label = "Off",
                                        isSelected = !subtitlesEnabled
                                    )
                                )
                                trackLabels.take(5).forEach { rawLabel ->
                                    val shortLabel = rawLabel
                                        .substringBefore(" (")
                                        .substringBefore(" [")
                                        .substringBefore(" -")
                                        .trim()
                                        .ifBlank { rawLabel }
                                    add(
                                        LandscapePillOption(
                                            id = rawLabel,
                                            label = shortLabel,
                                            isSelected = subtitlesEnabled && selectedSubtitleTrack.equals(rawLabel, ignoreCase = true)
                                        )
                                    )
                                }
                            }
                        }

                        val landscapeAudioOptions = remember(embeddedAudioTracks, selectedEmbeddedAudioKey, availableServers, selectedAudioType) {
                            if (embeddedAudioTracks.size > 1) {
                                embeddedAudioTracks.map { track ->
                                    LandscapePillOption(
                                        id = "emb:${track.key}",
                                        label = track.shortLanguage,
                                        isSelected = track.key == selectedEmbeddedAudioKey ||
                                            (selectedEmbeddedAudioKey == null && track.isSelected)
                                    )
                                }
                            } else {
                                val serverTypes = availableServers
                                    .map { it.type.trim().lowercase().ifBlank { "sub" } }
                                    .distinct()
                                    .sortedBy { audioLanguageSortOrder(it) }
                                    .ifEmpty { listOf(selectedAudioType.lowercase()) }

                                serverTypes.map { typeKey ->
                                    LandscapePillOption(
                                        id = "srv:$typeKey",
                                        label = audioTypeShortLanguage(typeKey),
                                        isSelected = selectedAudioType.equals(typeKey, ignoreCase = true)
                                    )
                                }
                            }
                        }

                        val landscapeQualityChipLabel = remember(selectedServer, isVideoEnhanced) {
                            when {
                                selectedServer != null -> selectedServer!!.name
                                isVideoEnhanced -> "4K"
                                else -> "Quality"
                            }
                        }

                        val landscapeUiState = KanimeLandscapeUiState(
                            episodeHeading = formattedEpisodeHeading,
                            animeTitle = anime.title,
                            qualityBadgeText = qualityBadgeText,
                            isVideoEnhanced = isVideoEnhanced,
                            isPlaying = isPlaying,
                            showControls = showControls,
                            isControlsLocked = isControlsLocked,
                            showPlaybackSettings = showPlaybackSettingsModal,
                            currentPositionMs = currentPositionMs,
                            durationMs = durationMs,
                            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
                            seekSegments = seekSegments,
                            qualityLabel = landscapeQualityChipLabel,
                            subtitleLabel = if (subtitlesEnabled && selectedSubtitleTrack != "Off") "Subtitles" else "Off",
                            playbackSpeed = playbackSpeed,
                            hasPreviousEpisode = onPreviousEpisode != null,
                            hasNextEpisode = onNextEpisode != null,
                            isSubtitlesEnabled = subtitlesEnabled,
                            showSyncSubtitlePill = useOpenSubtitle && subtitlesEnabled && openSubtitleTracks.isNotEmpty(),
                            syncSubtitleLabel = stringResource(R.string.player_subtitle_sync_line),
                            isAspectRatioModified = pictureMode != VideoPictureMode.FIT || videoZoom > 1.01f,
                            qualityOptions = landscapeQualityOptions,
                            subtitleOptions = landscapeSubtitleOptions,
                            audioOptions = landscapeAudioOptions,
                            enhancementSubtitle = animeEnhancementStatus?.detailLabel()
                                ?: "Improve video quality (if available)",
                            gestureType = gestureType,
                            gestureLevel = gestureLevel
                        )

                        KanimeLandscapePlayerScreen(
                            state = landscapeUiState,
                            videoSurface = {
                                VideoSurface(modifier = Modifier.fillMaxSize())
                            },
                            videoOverlayExtras = {
                                DoubleTapSeekOverlay(
                                    side = doubleTapSeekSide,
                                    seconds = doubleTapSeekSeconds,
                                    compact = false
                                )
                                SkipSegmentOverlay(
                                    segment = if (isControlsLocked || showPlaybackSettingsModal) null else activeSkipSegment,
                                    compact = false,
                                    bottomPadding = if (showControls) 120.dp else 24.dp,
                                    endPadding = 28.dp,
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
                            },
                            onBack = {
                                showPlaybackSettingsModal = false
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            },
                            onToggleControls = {
                                if (!isControlsLocked) {
                                    showControls = !showControls
                                } else {
                                    showControls = true
                                }
                            },
                            onDoubleTapLeft = { performDoubleTapSeek(-10) },
                            onDoubleTapRight = { performDoubleTapSeek(+10) },
                            onVerticalDragStart = { isLeftSide ->
                                if (isLeftSide) {
                                    gestureType = "brightness"
                                    gestureLevel = brightness
                                } else {
                                    gestureType = "volume"
                                    gestureLevel = volume.toFloat() / maxVolume.toFloat()
                                }
                            },
                            onVerticalDragDelta = { dragAmount ->
                                if (gestureType == "brightness") {
                                    brightness = (brightness - (dragAmount / 350f)).coerceIn(0.01f, 1.0f)
                                    gestureLevel = brightness
                                    activity?.window?.attributes = activity.window.attributes.apply {
                                        screenBrightness = brightness
                                    }
                                } else if (gestureType == "volume") {
                                    val step = -(dragAmount / 25f).toInt()
                                    val newVol = (volume + step).coerceIn(0, maxVolume)
                                    if (newVol != volume) {
                                        volume = newVol
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                                        gestureLevel = volume.toFloat() / maxVolume.toFloat()
                                    }
                                }
                            },
                            onPlayPauseToggle = {
                                if (isPlaying) pausePlayback() else resumeOrPlay()
                            },
                            onRewind10 = { performDoubleTapSeek(-10) },
                            onForward10 = { performDoubleTapSeek(+10) },
                            onPreviousEpisode = { onPreviousEpisode?.invoke() },
                            onNextEpisode = { onNextEpisode?.invoke() },
                            onSeekTo = { newPos ->
                                exoPlayer.seekTo(newPos)
                                currentPositionMs = newPos
                                resumePositionRef.longValue = newPos
                            },
                            onToggleLock = {
                                showPlaybackSettingsModal = false
                                isControlsLocked = !isControlsLocked
                            },
                            onTogglePlaybackSettings = {
                                showPlaybackSettingsModal = !showPlaybackSettingsModal
                                if (showPlaybackSettingsModal) {
                                    showControls = true
                                }
                            },
                            onDismissPlaybackSettings = {
                                showPlaybackSettingsModal = false
                            },
                            onQualityBadgeClick = {
                                showPlaybackSettingsModal = false
                                showEnhanceSheet = true
                            },
                            onEnhanceIconClick = {
                                showPlaybackSettingsModal = false
                                showEnhanceSheet = true
                            },
                            onPictureSheetClick = {
                                showPlaybackSettingsModal = false
                                showPictureSheet = true
                            },
                            onCycleAspectRatioClick = {
                                cycleAspectRatio()
                            },
                            onQualityChipClick = {
                                showPlaybackSettingsModal = false
                                showStreamsSheet = true
                            },
                            onSubtitlesChipClick = {
                                showPlaybackSettingsModal = false
                                showSubtitlesSheet = true
                            },
                            onSpeedChipClick = {
                                showPlaybackSettingsModal = false
                                showSpeedSheet = true
                            },
                            onAudioRowClick = {
                                showPlaybackSettingsModal = false
                                showAudioSheet = true
                            },
                            onSyncSubtitleClick = {
                                syncSubtitleToLine()
                            },
                            onPipClick = {
                                enterPipMode()
                            },
                            onExitFullscreenClick = {
                                showPlaybackSettingsModal = false
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            },
                            onSelectQualityOption = { opt ->
                                when {
                                    opt.id == "auto" -> {
                                        autoSelectStream = true
                                        pendingAudioResumePosMs = currentPositionMs.coerceAtLeast(0L)
                                        selectedServer = null
                                        resolveStream()
                                        aspectRatioToastMessage = "Video Quality: Auto"
                                    }
                                    opt.id.startsWith("srv:") -> {
                                        val sourceId = opt.id.removePrefix("srv:")
                                        val srv = availableServers.firstOrNull { it.sourceId == sourceId }
                                        if (srv != null && selectedServer?.sourceId != srv.sourceId) {
                                            autoSelectStream = false
                                            pendingAudioResumePosMs = currentPositionMs.coerceAtLeast(0L)
                                            selectedEmbeddedAudioKey = null
                                            embeddedAudioTracks = emptyList()
                                            selectedServer = srv
                                            resolveStream(srv)
                                            aspectRatioToastMessage = "Switching server to ${srv.name}"
                                        }
                                    }
                                    else -> {
                                        showPlaybackSettingsModal = false
                                        showStreamsSheet = true
                                    }
                                }
                            },
                            onSelectSubtitleOption = { opt ->
                                if (opt.id == "Off") {
                                    subtitlesEnabled = false
                                    aspectRatioToastMessage = "Subtitles: Off"
                                } else {
                                    subtitlesEnabled = true
                                    selectedSubtitleTrack = opt.id
                                    aspectRatioToastMessage = "Subtitles: ${opt.label}"
                                }
                            },
                            onSelectSpeedOption = { spd ->
                                playbackSpeed = spd
                                aspectRatioToastMessage = "Playback Speed: ${spd}x"
                            },
                            onSelectAudioOption = { opt ->
                                when {
                                    opt.id.startsWith("emb:") -> {
                                        val key = opt.id.removePrefix("emb:")
                                        embeddedAudioTracks.firstOrNull { it.key == key }?.let { track ->
                                            pendingAudioTrackSelection = track
                                            aspectRatioToastMessage = "Audio: ${track.displayName}"
                                        }
                                    }
                                    opt.id.startsWith("srv:") -> {
                                        val typeKey = opt.id.removePrefix("srv:")
                                        if (!selectedAudioType.equals(typeKey, ignoreCase = true)) {
                                            pendingAudioResumePosMs = currentPositionMs.coerceAtLeast(0L)
                                            selectedEmbeddedAudioKey = null
                                            embeddedAudioTracks = emptyList()
                                            selectedAudioType = typeKey
                                            aspectRatioToastMessage = "Switching audio to ${opt.label}"
                                        }
                                    }
                                }
                            },
                            onToggleEnhancementSwitch = { enabled ->
                                CatalogConfig.setAnimeUpscaleEnabled(enabled)
                                if (!enabled) {
                                    CatalogConfig.setAnimeLineSharpening(false)
                                }
                                aspectRatioToastMessage = if (enabled) {
                                    "Anime4K 2x (${animeUpscaleQuality.shortLabel}): ON"
                                } else {
                                    "Video Enhancement: OFF"
                                }
                            }
                        )
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // FLOATING TOAST OVERLAY (Quality / Aspect Ratio / Sync / Speed)
            // ══════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = !effectiveInPip && aspectRatioToastMessage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 70.dp)
                    .zIndex(25f)
            ) {
                GlassSurface(
                    shape = PillShape,
                    overMedia = true
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = aspectRatioToastMessage ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // BOTTOM SHEETS BACKDROP
            // ══════════════════════════════════════════════════════════════
            if (!effectiveInPip && (showStreamsSheet || showSourceSheet || showSpeedSheet || showSubtitlesSheet || showAudioSheet || showEnhanceSheet || showPictureSheet || showSettingsSheet)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(15f)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.65f))
                        .clickable {
                            showStreamsSheet = false
                            showSourceSheet = false
                            showSpeedSheet = false
                            showSubtitlesSheet = false
                            showAudioSheet = false
                            showEnhanceSheet = false
                            showPictureSheet = false
                            showSettingsSheet = false
                        }
                )
            }

            // ══════════════════════════════════════════════════════════════
            // PLAYER SETTINGS HUB BOTTOM SHEET (Top Bar Settings Gear)
            // ══════════════════════════════════════════════════════════════
            AnimatedVisibility(
                visible = !effectiveInPip && showSettingsSheet,
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 4.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Player Settings",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        PlayerSettingsRow(
                            icon = Icons.Default.AutoAwesome,
                            title = "Anime4K Shaders",
                            subtitle = if (isVideoEnhanced) qualityBadgeText else "Off",
                            highlighted = isVideoEnhanced,
                            onClick = {
                                showSettingsSheet = false
                                showEnhanceSheet = true
                            }
                        )

                        PlayerSettingsRow(
                            icon = Icons.Default.Headphones,
                            title = "Audio Track",
                            subtitle = activeAudioShortLabel,
                            onClick = {
                                showSettingsSheet = false
                                showAudioSheet = true
                            }
                        )

                        PlayerSettingsRow(
                            icon = Icons.Default.Extension,
                            title = "Source Plugin",
                            subtitle = activePluginLabel,
                            onClick = {
                                showSettingsSheet = false
                                showSourceSheet = true
                            }
                        )

                        PlayerSettingsRow(
                            icon = Icons.Default.AspectRatio,
                            title = "Picture & Aspect Ratio",
                            subtitle = picturePillLabel,
                            onClick = {
                                showSettingsSheet = false
                                showPictureSheet = true
                            }
                        )

                        PlayerSettingsRow(
                            icon = Icons.Default.PictureInPicture,
                            title = "Picture-in-Picture",
                            subtitle = "Float video in a mini window",
                            onClick = {
                                showSettingsSheet = false
                                enterPipTrigger?.invoke()
                            }
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Auto-Play Next Episode",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (autoPlayNext) "Enabled" else "Disabled",
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Switch(
                                    checked = autoPlayNext,
                                    onCheckedChange = { nextState ->
                                        onToggleAutoPlayNext?.invoke(nextState)
                                        aspectRatioToastMessage = if (nextState) "Auto-Play Next Episode enabled" else "Auto-Play Next Episode disabled"
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // ══════════════════════════════════════════════════════════════
            // STREAMS BOTTOM SHEET
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
                        .fillMaxHeight(if (isLandscape) 0.90f else 0.65f),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 8.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )

                        Text(
                            text = "Streams",
                            color = MaterialTheme.colorScheme.onSurface,
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
                                    text = "Auto select stream",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Pick the stream most like your last choice for this title",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(
                                checked = autoSelectStream,
                                onCheckedChange = { autoSelectStream = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
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
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Icon(
                                            Icons.Default.KeyboardArrowUp,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
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
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = "${srv.name} - ${srv.type.replaceFirstChar { it.uppercase() }} - 1080p",
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
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
                        .fillMaxHeight(if (isLandscape) 0.90f else 0.74f),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 8.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Subtitles & Captions",
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Tracks", "Customize Style").forEachIndexed { idx, tabLabel ->
                                val selected = subtitleSheetTab == idx
                                Surface(
                                    onClick = { subtitleSheetTab = idx },
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    shape = RoundedCornerShape(9.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = tabLabel,
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (subtitleSheetTab == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Enable Subtitles",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (subtitlesEnabled) "Captions are currently visible" else "Captions are turned off",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = subtitlesEnabled,
                                    onCheckedChange = { subtitlesEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val canUseOpenSub = activeSubtitlePlugin != null
                            val openSubStatusText = when {
                                subtitlePluginEntry == null || !subtitlePluginEntry.isInstalled ->
                                    stringResource(R.string.player_kopensubtitle_not_installed)
                                !subtitlePluginEntry.isEnabled ->
                                    stringResource(R.string.player_kopensubtitle_disabled)
                                isLoadingOpenSubtitles ->
                                    stringResource(R.string.player_kopensubtitle_loading)
                                useOpenSubtitle && openSubtitleTracks.isNotEmpty() ->
                                    stringResource(R.string.player_kopensubtitle_loaded, openSubtitleTracks.size)
                                useOpenSubtitle && openSubtitleFetched && openSubtitleTracks.isEmpty() ->
                                    stringResource(R.string.player_kopensubtitle_empty)
                                else ->
                                    stringResource(R.string.player_kopensubtitle_desc)
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.player_use_kopensubtitle),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (isLoadingOpenSubtitles) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Text(
                                            text = openSubStatusText,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Switch(
                                        checked = canUseOpenSub && useOpenSubtitle,
                                        enabled = canUseOpenSub,
                                        onCheckedChange = { enabled ->
                                            useOpenSubtitle = enabled
                                            PluginManager.setUseOpenSubtitleInPlayer(context, enabled)
                                            if (enabled) {
                                                subtitlesEnabled = true
                                            } else {
                                                val sourceSub = (resolutionState as? StreamResolutionState.Success)
                                                    ?.stream?.subtitles
                                                    ?.firstOrNull { sub ->
                                                        sub.url.isNotBlank() &&
                                                            !sub.url.contains("thumbnails", ignoreCase = true) &&
                                                            !sub.lang.contains("thumbnail", ignoreCase = true) &&
                                                            !sub.lang.contains("unknown", ignoreCase = true)
                                                    }
                                                if (selectedSubtitleTrack.contains("OpenSubtitles", ignoreCase = true)) {
                                                    selectedSubtitleTrack = sourceSub?.lang ?: embeddedTextTrackLabels.firstOrNull() ?: "English [CC]"
                                                }
                                                subtitleDelayMs = 0L
                                                subtitleScale = 1.0
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }
                            }

                            val sourceValidSubtitles = remember(resolutionState) {
                                (resolutionState as? StreamResolutionState.Success)?.stream?.subtitles
                                    ?.filter { sub ->
                                        sub.url.isNotBlank() &&
                                            !sub.url.contains("thumbnails", ignoreCase = true) &&
                                            !sub.lang.contains("thumbnail", ignoreCase = true) &&
                                            !sub.lang.contains("unknown", ignoreCase = true)
                                    }
                                    ?: emptyList()
                            }
                            val hasExternalSubtitles = sourceValidSubtitles.isNotEmpty() ||
                                (canUseOpenSub && useOpenSubtitle && openSubtitleTracks.isNotEmpty())

                            if (subtitlesEnabled && hasExternalSubtitles) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = stringResource(R.string.player_subtitle_sync),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = stringResource(R.string.player_subtitle_sync_hint),
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            val delaySec = subtitleDelayMs / 1000.0
                                            val delayLabel = if (subtitleDelayMs > 0L) {
                                                String.format(Locale.US, "+%.1fs", delaySec)
                                            } else {
                                                String.format(Locale.US, "%.1fs", delaySec)
                                            }
                                            val hasShift = subtitleDelayMs != 0L || subtitleScale != 1.0
                                            Box(
                                                modifier = Modifier
                                                    .clip(PillShape)
                                                    .background(
                                                        if (hasShift) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                                    )
                                                    .clickable(enabled = hasShift) {
                                                        subtitleDelayMs = 0L
                                                        subtitleScale = 1.0
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = if (hasShift) "$delayLabel · ${stringResource(R.string.player_subtitle_sync_reset)}" else delayLabel,
                                                    color = if (hasShift) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            listOf(-1000L to "-1s", -100L to "-0.1s", 100L to "+0.1s", 1000L to "+1s").forEach { (step, label) ->
                                                FilledTonalButton(
                                                    onClick = { subtitleDelayMs = (subtitleDelayMs + step).coerceIn(-60_000L, 60_000L) },
                                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(38.dp)
                                                ) {
                                                    Text(
                                                        text = label,
                                                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = { syncSubtitleToLine() },
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                            ) {
                                                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.player_subtitle_sync_line),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                            FilledTonalButton(
                                                onClick = { runSubtitleAutoSync(manual = true) },
                                                enabled = !isAutoSyncing,
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(40.dp)
                                            ) {
                                                if (isAutoSyncing) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidth = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.player_subtitle_auto_sync),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        Text(
                                            text = stringResource(R.string.player_subtitle_sync_line_hint),
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }

                            if (sourceValidSubtitles.isEmpty() && embeddedTextTrackLabels.isEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.player_hardsub_notice),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val subOptions = mutableListOf<String>()
                            if (canUseOpenSub && useOpenSubtitle) {
                                openSubtitleTracks.forEach { sub ->
                                    if (subOptions.none { it.equals(sub.lang, ignoreCase = true) }) {
                                        subOptions.add(sub.lang)
                                    }
                                }
                            }
                            sourceValidSubtitles.forEach { sub ->
                                if (subOptions.none { it.equals(sub.lang, ignoreCase = true) }) {
                                    subOptions.add(sub.lang)
                                }
                            }
                            embeddedTextTrackLabels.forEach { lbl ->
                                if (subOptions.none { it.equals(lbl, ignoreCase = true) }) {
                                    subOptions.add(lbl)
                                }
                            }
                            if (subOptions.isEmpty()) {
                                subOptions.add("English [CC]")
                            }
                            subOptions.add("Off")

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
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
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
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = opt,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }

                                            if (isSelected) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            val previewFontFamily = when (captionStyle.font) {
                                CaptionFont.GEIST -> GeistFontFamily
                                CaptionFont.SANS_SERIF -> FontFamily.SansSerif
                                CaptionFont.SERIF -> FontFamily.Serif
                                CaptionFont.MONOSPACE -> FontFamily.Monospace
                                CaptionFont.CASUAL -> FontFamily.Cursive
                            }
                            val previewShadow = when (captionStyle.edgeStyle) {
                                CaptionEdgeStyle.OUTLINE -> Shadow(color = MaterialTheme.colorScheme.scrim, offset = Offset(0f, 0f), blurRadius = 6f)
                                CaptionEdgeStyle.DROP_SHADOW -> Shadow(color = MaterialTheme.colorScheme.scrim, offset = Offset(3f, 3f), blurRadius = 5f)
                                CaptionEdgeStyle.RAISED -> Shadow(color = MaterialTheme.colorScheme.scrim, offset = Offset(0f, 3f), blurRadius = 3f)
                                CaptionEdgeStyle.NONE -> Shadow.None
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(78.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.background)
                                            )
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
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

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Font Family",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
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
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Bold Captions",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Switch(
                                        checked = captionStyle.isBold,
                                        onCheckedChange = { bold -> CatalogConfig.updateCaptionStyle { it.copy(isBold = bold) } },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.onSurface,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Text Size",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
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
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Text Color",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
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
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                            shape = CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = col.label,
                                                            tint = MaterialTheme.colorScheme.scrim,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = col.label,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Edge Style",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
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
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Background",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
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
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Vertical Position",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
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
                                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
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
                        .fillMaxHeight(if (isLandscape) 0.88f else 0.58f),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 8.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Audio",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Choose an available audio language for this episode",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 16.dp)
                        ) {
                            if (embeddedAudioTracks.size > 1) {
                                item(key = "embedded_audio_header") {
                                    Text(
                                        text = "IN-STREAM AUDIO TRACKS",
                                        color = MaterialTheme.colorScheme.primary,
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
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
                                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = track.displayName,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                    if (track.detail.isNotBlank()) {
                                                        Text(
                                                            text = track.detail,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item(key = "source_audio_header") {
                                Text(
                                    text = if (embeddedAudioTracks.size > 1) "SOURCE AUDIO STREAMS" else "AVAILABLE AUDIO",
                                    color = MaterialTheme.colorScheme.primary,
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
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
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
                                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = option.displayName,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                val subtitleParts = listOfNotNull(
                                                    "${option.serverCount} ${if (option.serverCount == 1) "server" else "servers"}",
                                                    activeTrackDetail.takeIf { it.isNotBlank() }
                                                ).joinToString(" • ")
                                                Text(
                                                    text = subtitleParts,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
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
            }

            // ══════════════════════════════════════════════════════════════
            // SOURCE PLUGINS BOTTOM SHEET
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
                        .fillMaxHeight(if (isLandscape) 0.90f else 0.65f),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 8.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Extension,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Source Plugins",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select an installed KAnime source plugin for streaming",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(12.dp))

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
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        labelColor = MaterialTheme.colorScheme.onSurface
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
                                placeholder = { Text("Search plugins...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = if (sourceFilterTab == "Installed") "No plugins installed yet" else "No matching plugins found",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Install or manage plugins in Settings → Video Source Plugins",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
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
                                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
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
                                                        isSelected && canUse -> MaterialTheme.colorScheme.primaryContainer
                                                        canUse -> MaterialTheme.colorScheme.secondaryContainer
                                                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                                                    },
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = if (canUse) Icons.Default.Extension else Icons.Default.ExtensionOff,
                                                            contentDescription = null,
                                                            tint = if (canUse) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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
                                                            color = if (canUse) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                        Text(
                                                            text = "v${plugin.installedVersion ?: plugin.version}",
                                                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (canUse) 0.9f else 0.5f),
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
                                                            plugin.isInstalled && plugin.isEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                                            plugin.isInstalled -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                                        },
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }

                                            if (isSelected && canUse) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 8.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )

                        Text(
                            text = "Playback Speed",
                            color = MaterialTheme.colorScheme.onSurface,
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
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 4.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
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
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Picture & Aspect Ratio",
                                    color = MaterialTheme.colorScheme.onSurface,
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
                                Text("Reset", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            }
                        }

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
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)) else null,
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
                                                color = MaterialTheme.colorScheme.onSurface,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Selected",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Video Zoom",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${(videoZoom * 100).toInt()}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = videoZoom,
                                onValueChange = { videoZoom = it },
                                valueRange = 1.0f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
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
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(vertical = 4.dp)
                                .size(width = 44.dp, height = 4.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Anime4K Shaders",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        animeEnhancementStatus?.let { status ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Active: ${status.detailLabel()}",
                                    color = MaterialTheme.colorScheme.primary,
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
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${animeUpscaleQuality.label} + anti-ringing Catmull-Rom",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
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
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                labelColor = MaterialTheme.colorScheme.onSurface
                                            ),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = animeUpscaleQuality.description,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Anime Line Sharpening",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "CNN restore, HQ darken/thin & halo-free ink sharpening",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
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
                                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                                labelColor = MaterialTheme.colorScheme.onSurface
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
}

@Composable
private fun PlayerSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (highlighted) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp)
            )
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
        GlassSurface(
            onClick = { onSkip(current) },
            shape = PillShape,
            overMedia = true,
            accentTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 14.dp else 18.dp,
                    vertical = if (compact) 9.dp else 12.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = current.label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 18.dp else 22.dp)
                )
                Text(
                    text = current.label,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
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
        GlassSurface(
            shape = PillShape,
            overMedia = true
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 16.dp else 20.dp,
                    vertical = if (compact) 10.dp else 14.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (side == "left") Icons.Default.FastRewind else Icons.Default.FastForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(if (compact) 22.dp else 26.dp)
                )
                Text(
                    text = if (side == "left") "-${seconds}s" else "+${seconds}s",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val contentTint = if (isActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    GlassSurface(
        onClick = onClick,
        shape = PillShape,
        showShadow = false,
        accentTint = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else null,
        contentColor = contentTint
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = contentTint,
                style = MaterialTheme.typography.labelMedium,
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

@Preview(name = "Player HUD Light Host Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Player HUD Dark Host Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Player HUD Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.TABLET)
@Composable
private fun PlayerHudPreview() {
    KAnimeTheme(darkTheme = true, dynamicColor = false, animateTransition = false) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.scrim)
                .padding(16.dp)
        ) {
            GlassSurface(
                shape = CircleShape,
                overMedia = true,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Pause",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(34.dp)
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionPill(icon = Icons.Default.Subtitles, label = "Subtitles", isActive = true, onClick = {})
                ActionPill(icon = Icons.Default.Speed, label = "1.0x", isActive = false, onClick = {})
            }
        }
    }
}