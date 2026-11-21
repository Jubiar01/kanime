package com.kanime.app.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kanime.app.ui.theme.GeistFontFamily

private object LandscapePalette {
    val GlassFill = Color(0xFF1E1614).copy(alpha = 0.72f)
    val GlassFillStrong = Color(0xFF211816).copy(alpha = 0.88f)
    val GlassBorder = Color.White.copy(alpha = 0.16f)
    val SubtleDivider = Color.White.copy(alpha = 0.10f)
    val AccentPeach = Color(0xFFF3B58B)
    val AccentDarkInk = Color(0xFF1A110D)
    val WarmBadgeBg = Color(0xFF342218).copy(alpha = 0.88f)
    val WarmGlassStart = Color(0xFF5D3A24).copy(alpha = 0.82f)
    val WarmGlassEnd = Color(0xFF2B1B14).copy(alpha = 0.86f)
    val ModalTop = Color(0xFF2A1F1D).copy(alpha = 0.90f)
    val ModalBottom = Color(0xFF191210).copy(alpha = 0.93f)
    val ChipInactiveBg = Color.White.copy(alpha = 0.08f)
    val TextPrimary = Color(0xFFF7F2EF)
    val TextMuted = Color(0xFFB5A8A1)
    val TrackInactive = Color.White.copy(alpha = 0.24f)
    val TrackBuffered = Color.White.copy(alpha = 0.42f)
    val SegmentHighlight = Color(0xFFFFD8B8).copy(alpha = 0.85f)
}

data class LandscapePillOption(
    val id: String,
    val label: String,
    val isSelected: Boolean
)

data class KanimeLandscapeUiState(
    val episodeHeading: String,
    val animeTitle: String,
    val qualityBadgeText: String,
    val isVideoEnhanced: Boolean,
    val isPlaying: Boolean,
    val showControls: Boolean,
    val isControlsLocked: Boolean,
    val showPlaybackSettings: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val bufferedPositionMs: Long = 0L,
    val seekSegments: List<LongRange> = emptyList(),
    val qualityLabel: String = "Quality",
    val subtitleLabel: String = "Subtitles",
    val playbackSpeed: Float = 1.0f,
    val hasPreviousEpisode: Boolean = false,
    val hasNextEpisode: Boolean = false,
    val isSubtitlesEnabled: Boolean = true,
    val showSyncSubtitlePill: Boolean = false,
    val syncSubtitleLabel: String = "Sync line",
    val isAspectRatioModified: Boolean = false,
    val qualityOptions: List<LandscapePillOption> = emptyList(),
    val subtitleOptions: List<LandscapePillOption> = emptyList(),
    val speedOptions: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
    val audioOptions: List<LandscapePillOption> = emptyList(),
    val enhancementSubtitle: String = "Improve video quality (if available)",
    val gestureType: String? = null,
    val gestureLevel: Float = 0f
)

private data class LandscapeResponsiveMetrics(
    val isCompact: Boolean,
    val isTablet: Boolean,
    val useScrollableBottomBar: Boolean,
    val stackModalRows: Boolean,
    val horizontalPadding: Dp,
    val topVerticalPadding: Dp,
    val bottomVerticalPadding: Dp,
    val iconCircleSize: Dp,
    val iconSize: Dp,
    val bottomPillHeight: Dp,
    val centerPlaySize: Dp,
    val centerSpacing: Dp,
    val modalMaxWidth: Dp,
    val modalMaxHeight: Dp
)

@Composable
private fun rememberLandscapeMetrics(maxWidth: Dp, maxHeight: Dp): LandscapeResponsiveMetrics {
    val fontScale = LocalDensity.current.fontScale
    val isCompact = maxWidth < 680.dp || maxHeight < 370.dp
    val isTablet = maxWidth >= 960.dp && maxHeight >= 560.dp
    val useScrollableBottomBar = maxWidth < 760.dp || fontScale >= 1.15f
    val stackModalRows = maxWidth < 580.dp || fontScale >= 1.25f

    return LandscapeResponsiveMetrics(
        isCompact = isCompact,
        isTablet = isTablet,
        useScrollableBottomBar = useScrollableBottomBar,
        stackModalRows = stackModalRows,
        horizontalPadding = when {
            isTablet -> 32.dp
            isCompact -> 12.dp
            else -> 22.dp
        },
        topVerticalPadding = if (isCompact) 10.dp else 16.dp,
        bottomVerticalPadding = if (isCompact) 10.dp else 18.dp,
        iconCircleSize = when {
            isCompact -> 36.dp
            isTablet -> 46.dp
            else -> 42.dp
        },
        iconSize = when {
            isCompact -> 18.dp
            isTablet -> 22.dp
            else -> 20.dp
        },
        bottomPillHeight = if (isCompact) 38.dp else 44.dp,
        centerPlaySize = when {
            isCompact -> 58.dp
            isTablet -> 78.dp
            else -> 68.dp
        },
        centerSpacing = when {
            isCompact -> 24.dp
            isTablet -> 44.dp
            else -> 34.dp
        },
        modalMaxWidth = if (isTablet) 700.dp else 640.dp,
        modalMaxHeight = (maxHeight - if (isCompact) 118.dp else 150.dp).coerceAtLeast(190.dp)
    )
}

@Composable
fun KanimeLandscapePlayerScreen(
    state: KanimeLandscapeUiState,
    videoSurface: @Composable BoxScope.() -> Unit,
    onBack: () -> Unit,
    onToggleControls: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onVerticalDragStart: (isLeftSide: Boolean) -> Unit,
    onVerticalDragDelta: (dragAmount: Float) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleLock: () -> Unit,
    onTogglePlaybackSettings: () -> Unit,
    onDismissPlaybackSettings: () -> Unit,
    onQualityBadgeClick: () -> Unit,
    onEnhanceIconClick: () -> Unit,
    onPictureSheetClick: () -> Unit,
    onCycleAspectRatioClick: () -> Unit,
    onQualityChipClick: () -> Unit,
    onSubtitlesChipClick: () -> Unit,
    onSpeedChipClick: () -> Unit,
    onAudioRowClick: () -> Unit,
    onSyncSubtitleClick: () -> Unit,
    onPipClick: () -> Unit,
    onExitFullscreenClick: () -> Unit,
    onSelectQualityOption: (LandscapePillOption) -> Unit,
    onSelectSubtitleOption: (LandscapePillOption) -> Unit,
    onSelectSpeedOption: (Float) -> Unit,
    onSelectAudioOption: (LandscapePillOption) -> Unit,
    onToggleEnhancementSwitch: (Boolean) -> Unit,
    videoOverlayExtras: @Composable BoxScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val metrics = rememberLandscapeMetrics(maxWidth = maxWidth, maxHeight = maxHeight)

        // 1. Full-bleed video surface
        videoSurface()

        // 2. Gesture capture layer (tap, double-tap seek, brightness & volume swipe)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.isControlsLocked, state.showPlaybackSettings) {
                    detectTapGestures(
                        onTap = {
                            when {
                                state.showPlaybackSettings -> onDismissPlaybackSettings()
                                else -> onToggleControls()
                            }
                        },
                        onDoubleTap = { offset ->
                            if (!state.isControlsLocked && !state.showPlaybackSettings) {
                                when {
                                    offset.x < size.width * 0.45f -> onDoubleTapLeft()
                                    offset.x > size.width * 0.55f -> onDoubleTapRight()
                                    else -> onPlayPauseToggle()
                                }
                            }
                        }
                    )
                }
                .pointerInput(state.isControlsLocked, state.showPlaybackSettings) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (state.isControlsLocked || state.showPlaybackSettings) return@detectVerticalDragGestures
                            if (offset.x < size.width * 0.45f) {
                                onVerticalDragStart(true)
                            } else if (offset.x > size.width * 0.55f) {
                                onVerticalDragStart(false)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (state.isControlsLocked || state.showPlaybackSettings) return@detectVerticalDragGestures
                            change.consume()
                            onVerticalDragDelta(dragAmount)
                        }
                    )
                }
        )

        // 3. Brightness / Volume vertical glass indicator
        androidx.compose.animation.AnimatedVisibility(
            visible = state.gestureType != null,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(if (state.gestureType == "brightness") Alignment.CenterStart else Alignment.CenterEnd)
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = if (metrics.isCompact) 20.dp else 40.dp)
        ) {
            LandscapeGestureIndicator(
                isBrightness = state.gestureType == "brightness",
                level = state.gestureLevel,
                compact = metrics.isCompact
            )
        }

        // 4. Main Fullscreen HUD Overlay (Top Bar + Center Controls / Playback Settings + Bottom Scrubber & Pills)
        androidx.compose.animation.AnimatedVisibility(
            visible = state.showControls || state.showPlaybackSettings,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.62f),
                            0.24f to Color.Black.copy(alpha = if (state.showPlaybackSettings) 0.42f else 0.12f),
                            0.74f to Color.Black.copy(alpha = if (state.showPlaybackSettings) 0.46f else 0.14f),
                            1.0f to Color.Black.copy(alpha = 0.78f)
                        )
                    )
            ) {
                if (state.isControlsLocked) {
                    // Minimal unlock button when controls are locked
                    LandscapeGlassCircle(
                        icon = Icons.Rounded.Lock,
                        contentDescription = "Unlock controls",
                        onClick = onToggleLock,
                        size = 48.dp,
                        iconSize = 22.dp,
                        highlighted = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(top = metrics.topVerticalPadding, end = metrics.horizontalPadding)
                    )
                } else {
                    // 4A. Top Floating Bar (Back + Episode Title | Quality Badge + Sparkles + Picture + Aspect Ratio)
                    LandscapeTopBar(
                        episodeHeading = state.episodeHeading,
                        animeTitle = state.animeTitle,
                        qualityBadgeText = state.qualityBadgeText,
                        isVideoEnhanced = state.isVideoEnhanced,
                        isAspectRatioModified = state.isAspectRatioModified,
                        metrics = metrics,
                        onBack = onBack,
                        onQualityBadgeClick = onQualityBadgeClick,
                        onEnhanceIconClick = onEnhanceIconClick,
                        onPictureSheetClick = onPictureSheetClick,
                        onCycleAspectRatioClick = onCycleAspectRatioClick,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(
                                horizontal = metrics.horizontalPadding,
                                vertical = metrics.topVerticalPadding
                            )
                    )

                    // 4B. Center Transport Controls (visible when Playback Settings modal is closed)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !state.showPlaybackSettings,
                        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180)),
                        exit = fadeOut(tween(140)),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        LandscapeCenterTransport(
                            isPlaying = state.isPlaying,
                            hasPreviousEpisode = state.hasPreviousEpisode,
                            hasNextEpisode = state.hasNextEpisode,
                            metrics = metrics,
                            onPreviousEpisode = onPreviousEpisode,
                            onRewind10 = onRewind10,
                            onPlayPauseToggle = onPlayPauseToggle,
                            onForward10 = onForward10,
                            onNextEpisode = onNextEpisode
                        )
                    }

                    // 4C. Center Frosted-Glass "Playback Settings" Modal (matches screenshot centerpiece)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.showPlaybackSettings,
                        enter = fadeIn(tween(190)) + scaleIn(initialScale = 0.96f, animationSpec = tween(190)),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.97f, animationSpec = tween(150)),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(
                                horizontal = metrics.horizontalPadding,
                                vertical = if (metrics.isCompact) 42.dp else 64.dp
                            )
                    ) {
                        PlaybackSettingsGlassModal(
                            state = state,
                            metrics = metrics,
                            onDismiss = onDismissPlaybackSettings,
                            onLockControls = onToggleLock,
                            onOpenStreamsSheet = onQualityChipClick,
                            onOpenSubtitlesSheet = onSubtitlesChipClick,
                            onOpenAudioSheet = onAudioRowClick,
                            onOpenEnhanceSheet = onEnhanceIconClick,
                            onSelectQualityOption = onSelectQualityOption,
                            onSelectSubtitleOption = onSelectSubtitleOption,
                            onSelectSpeedOption = onSelectSpeedOption,
                            onSelectAudioOption = onSelectAudioOption,
                            onToggleEnhancementSwitch = onToggleEnhancementSwitch
                        )
                    }

                    // 4D. Bottom Scrubber + Floating Action Pill Dock
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                            .padding(
                                start = metrics.horizontalPadding,
                                end = metrics.horizontalPadding,
                                bottom = metrics.bottomVerticalPadding
                            ),
                        verticalArrangement = Arrangement.spacedBy(if (metrics.isCompact) 8.dp else 12.dp)
                    ) {
                        LandscapeSeekBar(
                            currentPositionMs = state.currentPositionMs,
                            durationMs = state.durationMs,
                            bufferedPositionMs = state.bufferedPositionMs,
                            segments = state.seekSegments,
                            compact = metrics.isCompact,
                            onSeekTo = onSeekTo
                        )

                        LandscapeBottomActionRow(
                            state = state,
                            metrics = metrics,
                            onTogglePlaybackSettings = onTogglePlaybackSettings,
                            onQualityChipClick = onQualityChipClick,
                            onSubtitlesChipClick = onSubtitlesChipClick,
                            onSpeedChipClick = onSpeedChipClick,
                            onSyncSubtitleClick = onSyncSubtitleClick,
                            onNextEpisodeClick = onNextEpisode,
                            onPipClick = onPipClick,
                            onExitFullscreenClick = onExitFullscreenClick
                        )
                    }
                }
            }
        }

        // 5. Extras slot (DoubleTapSeekOverlay & AniSkip SkipSegmentOverlay)
        videoOverlayExtras()
    }
}

@Composable
private fun LandscapeTopBar(
    episodeHeading: String,
    animeTitle: String,
    qualityBadgeText: String,
    isVideoEnhanced: Boolean,
    isAspectRatioModified: Boolean,
    metrics: LandscapeResponsiveMetrics,
    onBack: () -> Unit,
    onQualityBadgeClick: () -> Unit,
    onEnhanceIconClick: () -> Unit,
    onPictureSheetClick: () -> Unit,
    onCycleAspectRatioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Back Circle + Episode Title & Series Name
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LandscapeGlassCircle(
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Exit fullscreen",
                onClick = onBack,
                size = metrics.iconCircleSize,
                iconSize = metrics.iconSize
            )

            Spacer(modifier = Modifier.width(if (metrics.isCompact) 10.dp else 14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episodeHeading,
                    color = LandscapePalette.TextPrimary,
                    fontFamily = GeistFontFamily,
                    fontSize = if (metrics.isCompact) 15.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
                Text(
                    text = animeTitle,
                    color = LandscapePalette.TextMuted,
                    fontFamily = GeistFontFamily,
                    fontSize = if (metrics.isCompact) 11.sp else 13.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }

        // Right: Quality / Enhancement Badge + Sparkles + Picture Mode + Aspect Ratio
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (metrics.isCompact) 6.dp else 9.dp)
        ) {
            Box(
                modifier = Modifier
                    .height(if (metrics.isCompact) 34.dp else 38.dp)
                    .clip(CircleShape)
                    .background(if (isVideoEnhanced) LandscapePalette.WarmBadgeBg else LandscapePalette.GlassFill)
                    .border(
                        width = 1.dp,
                        color = if (isVideoEnhanced) LandscapePalette.AccentPeach.copy(alpha = 0.42f) else LandscapePalette.GlassBorder,
                        shape = CircleShape
                    )
                    .clickable(onClick = onQualityBadgeClick)
                    .padding(horizontal = if (metrics.isCompact) 11.dp else 15.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = qualityBadgeText,
                    color = if (isVideoEnhanced) LandscapePalette.AccentPeach else LandscapePalette.TextPrimary,
                    fontFamily = GeistFontFamily,
                    fontSize = if (metrics.isCompact) 11.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            LandscapeGlassCircle(
                icon = Icons.Rounded.AutoAwesome,
                contentDescription = "Anime4K Enhancement",
                onClick = onEnhanceIconClick,
                size = metrics.iconCircleSize,
                iconSize = metrics.iconSize,
                highlighted = isVideoEnhanced
            )

            LandscapeGlassCircle(
                icon = Icons.Rounded.FeaturedVideo,
                contentDescription = "Picture & Aspect Ratio",
                onClick = onPictureSheetClick,
                size = metrics.iconCircleSize,
                iconSize = metrics.iconSize,
                highlighted = isAspectRatioModified
            )

            LandscapeGlassCircle(
                icon = Icons.Rounded.CropFree,
                contentDescription = "Cycle Aspect Ratio",
                onClick = onCycleAspectRatioClick,
                size = metrics.iconCircleSize,
                iconSize = metrics.iconSize
            )
        }
    }
}

@Composable
private fun LandscapeCenterTransport(
    isPlaying: Boolean,
    hasPreviousEpisode: Boolean,
    hasNextEpisode: Boolean,
    metrics: LandscapeResponsiveMetrics,
    onPreviousEpisode: () -> Unit,
    onRewind10: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onForward10: () -> Unit,
    onNextEpisode: () -> Unit
) {
    val sideIconSize = if (metrics.isCompact) 26.dp else 32.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.centerSpacing)
    ) {
        if (!metrics.isCompact) {
            IconButton(
                onClick = onPreviousEpisode,
                enabled = hasPreviousEpisode
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous episode",
                    tint = Color.White.copy(alpha = if (hasPreviousEpisode) 0.92f else 0.34f),
                    modifier = Modifier.size(sideIconSize)
                )
            }
        }

        IconButton(onClick = onRewind10) {
            Icon(
                imageVector = Icons.Rounded.Replay10,
                contentDescription = "Rewind 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(sideIconSize)
            )
        }

        Box(
            modifier = Modifier
                .size(metrics.centerPlaySize)
                .clip(CircleShape)
                .background(LandscapePalette.GlassFillStrong)
                .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                .clickable(onClick = onPlayPauseToggle),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = isPlaying,
                animationSpec = tween(160),
                label = "LandscapePlayPause"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(if (metrics.isCompact) 30.dp else 38.dp)
                )
            }
        }

        IconButton(onClick = onForward10) {
            Icon(
                imageVector = Icons.Rounded.Forward10,
                contentDescription = "Forward 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(sideIconSize)
            )
        }

        if (!metrics.isCompact) {
            IconButton(
                onClick = onNextEpisode,
                enabled = hasNextEpisode
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next episode",
                    tint = Color.White.copy(alpha = if (hasNextEpisode) 0.92f else 0.34f),
                    modifier = Modifier.size(sideIconSize)
                )
            }
        }
    }
}

@Composable
private fun PlaybackSettingsGlassModal(
    state: KanimeLandscapeUiState,
    metrics: LandscapeResponsiveMetrics,
    onDismiss: () -> Unit,
    onLockControls: () -> Unit,
    onOpenStreamsSheet: () -> Unit,
    onOpenSubtitlesSheet: () -> Unit,
    onOpenAudioSheet: () -> Unit,
    onOpenEnhanceSheet: () -> Unit,
    onSelectQualityOption: (LandscapePillOption) -> Unit,
    onSelectSubtitleOption: (LandscapePillOption) -> Unit,
    onSelectSpeedOption: (Float) -> Unit,
    onSelectAudioOption: (LandscapePillOption) -> Unit,
    onToggleEnhancementSwitch: (Boolean) -> Unit
) {
    val modalShape = RoundedCornerShape(24.dp)
    val contentPadding = if (metrics.isCompact) 14.dp else 20.dp

    // Intercept taps inside the modal so tapping the card doesn't dismiss it
    Box(
        modifier = Modifier
            .fillMaxWidth(if (metrics.isCompact) 0.96f else 0.88f)
            .widthIn(max = metrics.modalMaxWidth)
            .heightIn(max = metrics.modalMaxHeight)
            .clip(modalShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(LandscapePalette.ModalTop, LandscapePalette.ModalBottom)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.20f), modalShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = contentPadding, vertical = if (metrics.isCompact) 12.dp else 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = LandscapePalette.AccentPeach,
                        modifier = Modifier.size(if (metrics.isCompact) 22.dp else 26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Playback Settings",
                            color = LandscapePalette.TextPrimary,
                            fontFamily = GeistFontFamily,
                            fontSize = if (metrics.isCompact) 16.sp else 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Customize your viewing experience",
                            color = LandscapePalette.TextMuted,
                            fontFamily = GeistFontFamily,
                            fontSize = if (metrics.isCompact) 11.sp else 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LandscapeGlassCircle(
                        icon = Icons.Rounded.LockOpen,
                        contentDescription = "Lock screen controls",
                        onClick = onLockControls,
                        size = 34.dp,
                        iconSize = 17.dp
                    )
                    LandscapeGlassCircle(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close playback settings",
                        onClick = onDismiss,
                        size = 34.dp,
                        iconSize = 18.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (metrics.isCompact) 10.dp else 14.dp))
            HorizontalDivider(color = LandscapePalette.SubtleDivider, thickness = 1.dp)

            // Scrollable settings rows (ensures short landscape phones & split-screen never clip)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Video Quality
                SettingsOptionRow(
                    icon = Icons.Rounded.Tv,
                    title = "Video Quality",
                    subtitle = "Choose the best quality for your connection",
                    stacked = metrics.stackModalRows,
                    compact = metrics.isCompact,
                    onLabelClick = onOpenStreamsSheet
                ) {
                    state.qualityOptions.forEach { option ->
                        SettingsChoicePill(
                            label = option.label,
                            selected = option.isSelected,
                            compact = metrics.isCompact,
                            onClick = { onSelectQualityOption(option) }
                        )
                    }
                }

                HorizontalDivider(color = LandscapePalette.SubtleDivider, thickness = 0.8.dp)

                // 2. Subtitles
                SettingsOptionRow(
                    icon = Icons.Rounded.ClosedCaption,
                    title = "Subtitles",
                    subtitle = "Select subtitle language",
                    stacked = metrics.stackModalRows,
                    compact = metrics.isCompact,
                    onLabelClick = onOpenSubtitlesSheet
                ) {
                    state.subtitleOptions.forEach { option ->
                        SettingsChoicePill(
                            label = option.label,
                            selected = option.isSelected,
                            compact = metrics.isCompact,
                            onClick = { onSelectSubtitleOption(option) }
                        )
                    }
                }

                HorizontalDivider(color = LandscapePalette.SubtleDivider, thickness = 0.8.dp)

                // 3. Playback Speed
                SettingsOptionRow(
                    icon = Icons.Rounded.Speed,
                    title = "Playback Speed",
                    subtitle = "Adjust the playback speed",
                    stacked = metrics.stackModalRows,
                    compact = metrics.isCompact
                ) {
                    state.speedOptions.forEach { speed ->
                        val isSelected = kotlin.math.abs(state.playbackSpeed - speed) < 0.01f
                        SettingsChoicePill(
                            label = "${trimLandscapeSpeed(speed)}x",
                            selected = isSelected,
                            compact = metrics.isCompact,
                            onClick = { onSelectSpeedOption(speed) }
                        )
                    }
                }

                HorizontalDivider(color = LandscapePalette.SubtleDivider, thickness = 0.8.dp)

                // 4. Audio Track
                SettingsOptionRow(
                    icon = Icons.Rounded.MusicNote,
                    title = "Audio Track",
                    subtitle = "Choose audio language",
                    stacked = metrics.stackModalRows,
                    compact = metrics.isCompact,
                    onLabelClick = onOpenAudioSheet
                ) {
                    state.audioOptions.forEach { option ->
                        SettingsChoicePill(
                            label = option.label,
                            selected = option.isSelected,
                            compact = metrics.isCompact,
                            onClick = { onSelectAudioOption(option) }
                        )
                    }
                }

                HorizontalDivider(color = LandscapePalette.SubtleDivider, thickness = 0.8.dp)

                // 5. Enhancement Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenEnhanceSheet)
                        .padding(vertical = if (metrics.isCompact) 9.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = LandscapePalette.AccentPeach,
                            modifier = Modifier.size(if (metrics.isCompact) 20.dp else 22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Enhancement",
                                color = LandscapePalette.TextPrimary,
                                fontFamily = GeistFontFamily,
                                fontSize = if (metrics.isCompact) 13.sp else 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.enhancementSubtitle,
                                color = LandscapePalette.TextMuted,
                                fontFamily = GeistFontFamily,
                                fontSize = if (metrics.isCompact) 11.sp else 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Switch(
                        checked = state.isVideoEnhanced,
                        onCheckedChange = onToggleEnhancementSwitch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = LandscapePalette.AccentPeach,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.14f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.22f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    stacked: Boolean,
    compact: Boolean,
    onLabelClick: (() -> Unit)? = null,
    pillsContent: @Composable RowScope.() -> Unit
) {
    val vPad = if (compact) 9.dp else 12.dp
    if (stacked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = vPad),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (onLabelClick != null) it.clickable(onClick = onLabelClick) else it }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LandscapePalette.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        color = LandscapePalette.TextPrimary,
                        fontFamily = GeistFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = LandscapePalette.TextMuted,
                        fontFamily = GeistFontFamily,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = pillsContent
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(0.44f)
                    .padding(end = 12.dp)
                    .let { if (onLabelClick != null) it.clickable(onClick = onLabelClick) else it }
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = LandscapePalette.TextPrimary,
                    modifier = Modifier.size(if (compact) 20.dp else 22.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = LandscapePalette.TextPrimary,
                        fontFamily = GeistFontFamily,
                        fontSize = if (compact) 13.sp else 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = LandscapePalette.TextMuted,
                        fontFamily = GeistFontFamily,
                        fontSize = if (compact) 10.sp else 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier
                    .weight(0.56f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = pillsContent
            )
        }
    }
}

@Composable
private fun SettingsChoicePill(
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (selected) LandscapePalette.AccentPeach else LandscapePalette.ChipInactiveBg
    val borderColor = if (selected) LandscapePalette.AccentPeach else LandscapePalette.GlassBorder
    val contentColor = if (selected) LandscapePalette.AccentDarkInk else LandscapePalette.TextPrimary

    Row(
        modifier = Modifier
            .heightIn(min = if (compact) 30.dp else 34.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 11.dp else 14.dp,
                vertical = if (compact) 5.dp else 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (compact) 13.dp else 15.dp)
            )
        }
        Text(
            text = label,
            color = contentColor,
            fontFamily = GeistFontFamily,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun LandscapeSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    segments: List<LongRange>,
    compact: Boolean,
    onSeekTo: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }

    val liveFraction = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayedFraction = if (isDragging) dragFraction else liveFraction
    val displayedPosMs = if (isDragging) (dragFraction * safeDuration).toLong() else currentPositionMs

    val timeStyle = TextStyle(
        fontFamily = GeistFontFamily,
        fontSize = if (compact) 11.sp else 13.sp,
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)
    ) {
        Text(
            text = formatLandscapeTime(displayedPosMs),
            color = LandscapePalette.TextPrimary,
            style = timeStyle,
            maxLines = 1
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(safeDuration) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                        onSeekTo((frac * safeDuration).toLong())
                    }
                }
                .pointerInput(safeDuration) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / trackWidthPx).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            onSeekTo((dragFraction * safeDuration).toLong())
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isDragging) 6.dp else 4.dp)
            ) {
                val trackHeight = size.height
                val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                // Base inactive track
                drawRoundRect(
                    color = LandscapePalette.TrackInactive,
                    size = size,
                    cornerRadius = radius
                )

                // Buffered range
                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = LandscapePalette.TrackBuffered,
                        size = Size(size.width * bufferedFraction, trackHeight),
                        cornerRadius = radius
                    )
                }

                // AniSkip segment markers (recap / intro / outro)
                segments.forEach { range ->
                    val startFrac = (range.first.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val endFrac = (range.last.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    if (endFrac > startFrac) {
                        drawRoundRect(
                            color = LandscapePalette.SegmentHighlight,
                            topLeft = Offset(size.width * startFrac, 0f),
                            size = Size(size.width * (endFrac - startFrac), trackHeight),
                            cornerRadius = radius
                        )
                    }
                }

                // Active warm peach fill
                val activeWidth = (size.width * displayedFraction).coerceIn(0f, size.width)
                drawRoundRect(
                    color = LandscapePalette.AccentPeach,
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = radius
                )

                // Glowing thumb while scrubbing or active
                val thumbRadius = if (isDragging) 8.dp.toPx() else 5.5.dp.toPx()
                drawCircle(
                    color = LandscapePalette.AccentPeach,
                    radius = thumbRadius,
                    center = Offset(activeWidth, trackHeight / 2f)
                )
            }
        }

        Text(
            text = formatLandscapeTime(durationMs),
            color = LandscapePalette.TextMuted,
            style = timeStyle,
            maxLines = 1
        )
    }
}

@Composable
private fun LandscapeBottomActionRow(
    state: KanimeLandscapeUiState,
    metrics: LandscapeResponsiveMetrics,
    onTogglePlaybackSettings: () -> Unit,
    onQualityChipClick: () -> Unit,
    onSubtitlesChipClick: () -> Unit,
    onSpeedChipClick: () -> Unit,
    onSyncSubtitleClick: () -> Unit,
    onNextEpisodeClick: () -> Unit,
    onPipClick: () -> Unit,
    onExitFullscreenClick: () -> Unit
) {
    val pillSpacing = if (metrics.isCompact) 8.dp else 10.dp

    @Composable
    fun LeftControlsGroup() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(pillSpacing)
        ) {
            // Sliders (Playback Settings) + Warm-Highlighted Quality Dropdown grouped like the screenshot
            Row(
                modifier = Modifier
                    .height(metrics.bottomPillHeight)
                    .clip(CircleShape)
                    .background(LandscapePalette.GlassFill)
                    .border(1.dp, LandscapePalette.GlassBorder, CircleShape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (state.showPlaybackSettings) LandscapePalette.AccentPeach.copy(alpha = 0.22f)
                            else Color.Transparent
                        )
                        .clickable(onClick = onTogglePlaybackSettings)
                        .padding(horizontal = if (metrics.isCompact) 12.dp else 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Playback Settings",
                        tint = if (state.showPlaybackSettings) LandscapePalette.AccentPeach else LandscapePalette.TextPrimary,
                        modifier = Modifier.size(metrics.iconSize)
                    )
                }

                // Warm amber Quality dropdown pill
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(LandscapePalette.WarmGlassStart, LandscapePalette.WarmGlassEnd)
                            )
                        )
                        .border(1.dp, LandscapePalette.AccentPeach.copy(alpha = 0.55f), CircleShape)
                        .clickable(onClick = onQualityChipClick)
                        .padding(horizontal = if (metrics.isCompact) 12.dp else 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = LandscapePalette.AccentPeach,
                        modifier = Modifier.size(if (metrics.isCompact) 16.dp else 18.dp)
                    )
                    Text(
                        text = state.qualityLabel,
                        color = LandscapePalette.AccentPeach,
                        fontFamily = GeistFontFamily,
                        fontSize = if (metrics.isCompact) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = LandscapePalette.AccentPeach.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Subtitles dropdown pill
            LandscapeDropdownPill(
                icon = Icons.Rounded.ClosedCaption,
                label = state.subtitleLabel,
                height = metrics.bottomPillHeight,
                compact = metrics.isCompact,
                active = state.isSubtitlesEnabled,
                onClick = onSubtitlesChipClick
            )

            // Speed dropdown pill
            LandscapeDropdownPill(
                icon = Icons.Rounded.Speed,
                label = "Speed ${trimLandscapeSpeed(state.playbackSpeed)}x",
                height = metrics.bottomPillHeight,
                compact = metrics.isCompact,
                active = kotlin.math.abs(state.playbackSpeed - 1.0f) > 0.01f,
                onClick = onSpeedChipClick
            )

            // Optional one-tap subtitle sync pill when KOpenSubtitle is active
            if (state.showSyncSubtitlePill) {
                Row(
                    modifier = Modifier
                        .height(metrics.bottomPillHeight)
                        .clip(CircleShape)
                        .background(LandscapePalette.WarmBadgeBg)
                        .border(1.dp, LandscapePalette.AccentPeach.copy(alpha = 0.45f), CircleShape)
                        .clickable(onClick = onSyncSubtitleClick)
                        .padding(horizontal = if (metrics.isCompact) 11.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = LandscapePalette.AccentPeach,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = state.syncSubtitleLabel,
                        color = LandscapePalette.AccentPeach,
                        fontFamily = GeistFontFamily,
                        fontSize = if (metrics.isCompact) 11.sp else 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }

    @Composable
    fun RightControlsGroup() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(pillSpacing)
        ) {
            // Next Episode frosted pill
            val nextAlpha = if (state.hasNextEpisode) 1f else 0.42f
            Row(
                modifier = Modifier
                    .height(metrics.bottomPillHeight)
                    .clip(CircleShape)
                    .background(LandscapePalette.GlassFill)
                    .border(1.dp, LandscapePalette.GlassBorder, CircleShape)
                    .clickable(enabled = state.hasNextEpisode, onClick = onNextEpisodeClick)
                    .padding(horizontal = if (metrics.isCompact) 13.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = LandscapePalette.TextPrimary.copy(alpha = nextAlpha),
                    modifier = Modifier.size(if (metrics.isCompact) 18.dp else 20.dp)
                )
                Text(
                    text = "Next Episode",
                    color = LandscapePalette.TextPrimary.copy(alpha = nextAlpha),
                    fontFamily = GeistFontFamily,
                    fontSize = if (metrics.isCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Twin PiP + Exit Fullscreen capsule (matches bottom-right pill in screenshot)
            Row(
                modifier = Modifier
                    .height(metrics.bottomPillHeight)
                    .clip(CircleShape)
                    .background(LandscapePalette.GlassFill)
                    .border(1.dp, LandscapePalette.GlassBorder, CircleShape)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (metrics.isCompact) 34.dp else 38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPipClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PictureInPictureAlt,
                        contentDescription = "Picture-in-Picture",
                        tint = LandscapePalette.TextPrimary,
                        modifier = Modifier.size(metrics.iconSize)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(if (metrics.isCompact) 34.dp else 38.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onExitFullscreenClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = LandscapePalette.TextPrimary,
                        modifier = Modifier.size(metrics.iconSize + 2.dp)
                    )
                }
            }
        }
    }

    if (metrics.useScrollableBottomBar) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(pillSpacing)
        ) {
            LeftControlsGroup()
            RightControlsGroup()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LeftControlsGroup()
            RightControlsGroup()
        }
    }
}

@Composable
private fun LandscapeDropdownPill(
    icon: ImageVector,
    label: String,
    height: Dp,
    compact: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(height)
            .clip(CircleShape)
            .background(LandscapePalette.GlassFill)
            .border(
                width = 1.dp,
                color = if (active) LandscapePalette.AccentPeach.copy(alpha = 0.32f) else LandscapePalette.GlassBorder,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 11.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = LandscapePalette.TextPrimary,
            modifier = Modifier.size(if (compact) 16.dp else 18.dp)
        )
        Text(
            text = label,
            color = LandscapePalette.TextPrimary,
            fontFamily = GeistFontFamily,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = LandscapePalette.TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun LandscapeGlassCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (highlighted) LandscapePalette.WarmBadgeBg else LandscapePalette.GlassFill)
            .border(
                width = 1.dp,
                color = if (highlighted) LandscapePalette.AccentPeach.copy(alpha = 0.48f) else LandscapePalette.GlassBorder,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (highlighted) LandscapePalette.AccentPeach else LandscapePalette.TextPrimary,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun LandscapeGestureIndicator(
    isBrightness: Boolean,
    level: Float,
    compact: Boolean
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = LandscapePalette.GlassFillStrong,
        border = BorderStroke(1.dp, LandscapePalette.GlassBorder)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 14.dp)
        ) {
            Icon(
                imageVector = if (isBrightness) Icons.Rounded.BrightnessMedium else Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = LandscapePalette.AccentPeach,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(if (compact) 80.dp else 108.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(level.coerceIn(0f, 1f))
                        .background(LandscapePalette.AccentPeach)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${(level * 100).toInt()}%",
                color = LandscapePalette.TextPrimary,
                fontFamily = GeistFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun formatLandscapeTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun trimLandscapeSpeed(speed: Float): String {
    return if (speed % 1f == 0f) "%.1f".format(speed) else speed.toString()
}