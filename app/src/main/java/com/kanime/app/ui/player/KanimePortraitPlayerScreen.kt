package com.kanime.app.ui.player

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanime.app.ui.theme.GeistFontFamily
import kotlinx.coroutines.launch

private object PlayerPalette {
    val BgTop = Color(0xFF1E1311)
    val BgBottom = Color(0xFF100B0A)
    val GlassSurface = Color(0xFF281E1C).copy(alpha = 0.76f)
    val GlassBorder = Color.White.copy(alpha = 0.14f)
    val AccentPeach = Color(0xFFF3B58B)
    val AccentWarmStart = Color(0xFF6B442B).copy(alpha = 0.85f)
    val AccentWarmEnd = Color(0xFF2F1D15).copy(alpha = 0.90f)
    val RatingBg = Color(0xFF3A271B).copy(alpha = 0.88f)
    val TextPrimary = Color(0xFFF7F2EF)
    val TextMuted = Color(0xFFA79992)
    val DockBg = Color(0xFF1E1514).copy(alpha = 0.96f)
}

data class PlayerEpisodeUiItem(
    val id: String,
    val number: Int,
    val title: String,
    val durationLabel: String,
    val airDateLabel: String,
    val thumbnailUrl: String
)

data class KanimePlayerUiState(
    val animeTitle: String,
    val animePosterUrl: String,
    val rating: String,
    val currentEpisode: PlayerEpisodeUiItem,
    val episodes: List<PlayerEpisodeUiItem>,
    val isPlaying: Boolean,
    val showControls: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val currentQualityLabel: String = "Quality",
    val currentSubtitleLabel: String = "Subtitles",
    val playbackSpeed: Float = 1.0f,
    val hasNextEpisode: Boolean = true
)

@Composable
fun KanimePortraitPlayerScreen(
    state: KanimePlayerUiState,
    videoSurface: @Composable BoxScope.() -> Unit,
    onBack: () -> Unit,
    onCastClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onToggleControls: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onQualityClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onEpisodeListClick: () -> Unit = {},
    onNextEpisodeClick: () -> Unit,
    onEpisodeSelect: (PlayerEpisodeUiItem) -> Unit,
    onDoubleTapLeft: () -> Unit = onRewind10,
    onDoubleTapRight: () -> Unit = onForward10,
    onRatingBadgeClick: () -> Unit = {},
    videoOverlayExtras: @Composable BoxScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PlayerPalette.BgTop, PlayerPalette.BgBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 1. Top Frosted Pill App Bar
            PlayerTopGlassBar(
                onBack = onBack,
                onCastClick = onCastClick,
                onSettingsClick = onSettingsClick,
                onFullscreenClick = onFullscreenClick,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )

            // 2. 16:9 Video Viewport + Overlay Chrome
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                videoSurface()

                // Tap to toggle controls, double-tap left/right to seek -10s/+10s
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onToggleControls() },
                                onDoubleTap = { offset ->
                                    when {
                                        offset.x < size.width * 0.45f -> onDoubleTapLeft()
                                        offset.x > size.width * 0.55f -> onDoubleTapRight()
                                        else -> onPlayPauseToggle()
                                    }
                                }
                            )
                        }
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = state.showControls,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(160)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    VideoControlsOverlay(
                        isPlaying = state.isPlaying,
                        currentPositionMs = state.currentPositionMs,
                        durationMs = state.durationMs,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onRewind10 = onRewind10,
                        onForward10 = onForward10,
                        onSeekTo = onSeekTo
                    )
                }

                // Slot for DoubleTapSeekOverlay & SkipSegmentOverlay (AniSkip)
                videoOverlayExtras()
            }

            // 3. Scrollable Metadata, Controls & Episodes
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 112.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "header") {
                    EpisodeHeaderSection(
                        episodeNumber = state.currentEpisode.number,
                        episodeTitle = state.currentEpisode.title,
                        animeTitle = state.animeTitle,
                        rating = state.rating,
                        onRatingBadgeClick = onRatingBadgeClick
                    )
                }

                item(key = "quick_chips") {
                    QuickControlChipsRow(
                        qualityLabel = state.currentQualityLabel,
                        subtitleLabel = state.currentSubtitleLabel,
                        speedLabel = "Speed ${trimSpeed(state.playbackSpeed)}x",
                        onQualityClick = onQualityClick,
                        onSubtitlesClick = onSubtitlesClick,
                        onSpeedClick = onSpeedClick
                    )
                }

                item(key = "primary_actions") {
                    PrimaryActionButtonsRow(
                        hasNextEpisode = state.hasNextEpisode,
                        onEpisodeListClick = {
                            onEpisodeListClick()
                            coroutineScope.launch {
                                listState.animateScrollToItem(3)
                            }
                        },
                        onNextEpisodeClick = onNextEpisodeClick
                    )
                }

                item(key = "episodes_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Episodes",
                            color = PlayerPalette.TextPrimary,
                            fontFamily = GeistFontFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${state.episodes.size} ${if (state.episodes.size == 1) "Episode" else "Episodes"}",
                            color = PlayerPalette.AccentPeach,
                            fontFamily = GeistFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                items(
                    items = state.episodes,
                    key = { it.id }
                ) { episode ->
                    EpisodeRowCard(
                        episode = episode,
                        isPlaying = episode.number == state.currentEpisode.number,
                        onClick = { onEpisodeSelect(episode) }
                    )
                }
            }
        }

        // 4. Sticky Bottom "Now Playing" Bar
        NowPlayingDock(
            posterUrl = state.animePosterUrl,
            episodeNumber = state.currentEpisode.number,
            episodeTitle = state.currentEpisode.title,
            hasNextEpisode = state.hasNextEpisode,
            onNextEpisodeClick = onNextEpisodeClick,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}

@Composable
private fun PlayerTopGlassBar(
    onBack: () -> Unit,
    onCastClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(PlayerPalette.GlassSurface)
            .border(1.dp, PlayerPalette.GlassBorder, CircleShape)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconCircle(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "Back",
            onClick = onBack
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = PlayerPalette.AccentPeach, fontWeight = FontWeight.Bold)) {
                    append("K")
                }
                withStyle(SpanStyle(color = PlayerPalette.TextPrimary, fontWeight = FontWeight.SemiBold)) {
                    append("Anime")
                }
            },
            fontFamily = GeistFontFamily,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassIconCircle(
                icon = Icons.Rounded.Cast,
                contentDescription = "Cast",
                onClick = onCastClick
            )
            GlassIconCircle(
                icon = Icons.Rounded.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )
            GlassIconCircle(
                icon = Icons.Rounded.Fullscreen,
                contentDescription = "Fullscreen",
                onClick = onFullscreenClick
            )
        }
    }
}

@Composable
private fun GlassIconCircle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = PlayerPalette.TextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun VideoControlsOverlay(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onRewind10: () -> Unit,
    onForward10: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val liveProgress = (currentPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    val displayedProgress = if (isScrubbing) scrubFraction else liveProgress
    val displayedPositionMs = if (isScrubbing) (scrubFraction * safeDuration).toLong() else currentPositionMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.35f),
                    0.45f to Color.Black.copy(alpha = 0.16f),
                    1.0f to Color.Black.copy(alpha = 0.68f)
                )
            )
    ) {
        // Center transport controls
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(38.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onRewind10) {
                Icon(
                    imageVector = Icons.Rounded.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(onClick = onPlayPauseToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(onClick = onForward10) {
                Icon(
                    imageVector = Icons.Rounded.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Bottom timeline scrubber
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = formatPlaybackTime(displayedPositionMs),
                color = Color.White,
                fontFamily = GeistFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Slider(
                value = displayedProgress,
                onValueChange = { fraction ->
                    isScrubbing = true
                    scrubFraction = fraction
                },
                onValueChangeFinished = {
                    isScrubbing = false
                    onSeekTo((scrubFraction * safeDuration).toLong())
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PlayerPalette.AccentPeach,
                    activeTrackColor = PlayerPalette.AccentPeach,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                )
            )

            Text(
                text = formatPlaybackTime(durationMs),
                color = Color.White,
                fontFamily = GeistFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EpisodeHeaderSection(
    episodeNumber: Int,
    episodeTitle: String,
    animeTitle: String,
    rating: String,
    onRatingBadgeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = formatPortraitEpisodeHeading(episodeNumber, episodeTitle),
                color = PlayerPalette.TextPrimary,
                fontFamily = GeistFontFamily,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = animeTitle,
                color = PlayerPalette.TextMuted,
                fontFamily = GeistFontFamily,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (rating.isNotBlank()) {
            Surface(
                onClick = onRatingBadgeClick,
                shape = CircleShape,
                color = PlayerPalette.RatingBg,
                border = BorderStroke(1.dp, PlayerPalette.AccentPeach.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = PlayerPalette.AccentPeach,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = rating,
                        color = PlayerPalette.AccentPeach,
                        fontFamily = GeistFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickControlChipsRow(
    qualityLabel: String,
    subtitleLabel: String,
    speedLabel: String,
    onQualityClick: () -> Unit,
    onSubtitlesClick: () -> Unit,
    onSpeedClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ControlDropdownChip(
            icon = Icons.Rounded.Hd,
            label = qualityLabel,
            onClick = onQualityClick,
            modifier = Modifier.weight(1f)
        )
        ControlDropdownChip(
            icon = Icons.Rounded.ClosedCaption,
            label = subtitleLabel,
            onClick = onSubtitlesClick,
            modifier = Modifier.weight(1.08f)
        )
        ControlDropdownChip(
            icon = Icons.Rounded.Speed,
            label = speedLabel,
            onClick = onSpeedClick,
            modifier = Modifier.weight(1.12f)
        )
    }
}

@Composable
private fun ControlDropdownChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(PlayerPalette.GlassSurface)
            .border(1.dp, PlayerPalette.GlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = PlayerPalette.TextPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = PlayerPalette.TextPrimary,
            fontFamily = GeistFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = PlayerPalette.TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun PrimaryActionButtonsRow(
    hasNextEpisode: Boolean,
    onEpisodeListClick: () -> Unit,
    onNextEpisodeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Highlighted Warm Amber Pill ("Episode List")
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(PlayerPalette.AccentWarmStart, PlayerPalette.AccentWarmEnd)
                    )
                )
                .border(1.dp, PlayerPalette.AccentPeach.copy(alpha = 0.70f), CircleShape)
                .clickable(onClick = onEpisodeListClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    contentDescription = null,
                    tint = PlayerPalette.TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Episode List",
                    color = PlayerPalette.TextPrimary,
                    fontFamily = GeistFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Frosted Dark Pill ("Next Episode")
        val nextAlpha = if (hasNextEpisode) 1f else 0.45f
        Box(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(CircleShape)
                .background(PlayerPalette.GlassSurface)
                .border(1.dp, PlayerPalette.GlassBorder, CircleShape)
                .clickable(enabled = hasNextEpisode, onClick = onNextEpisodeClick),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = PlayerPalette.TextPrimary.copy(alpha = nextAlpha),
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = "Next Episode",
                    color = PlayerPalette.TextPrimary.copy(alpha = nextAlpha),
                    fontFamily = GeistFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun EpisodeRowCard(
    episode: PlayerEpisodeUiItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val cardShape = RoundedCornerShape(18.dp)
    val cardBackground = if (isPlaying) {
        Brush.horizontalGradient(
            listOf(PlayerPalette.AccentWarmStart, PlayerPalette.AccentWarmEnd)
        )
    } else {
        Brush.horizontalGradient(
            listOf(PlayerPalette.GlassSurface, PlayerPalette.GlassSurface)
        )
    }
    val borderColor = if (isPlaying) {
        PlayerPalette.AccentPeach.copy(alpha = 0.85f)
    } else {
        PlayerPalette.GlassBorder
    }

    val metaLine = remember(episode.durationLabel, episode.airDateLabel) {
        listOf(episode.durationLabel.trim(), episode.airDateLabel.trim())
            .filter { it.isNotBlank() }
            .joinToString(" • ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardBackground)
            .border(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = borderColor,
                shape = cardShape
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.thumbnailUrl,
            contentDescription = "Episode ${episode.number}",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(126.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Ep ${episode.number}",
                color = PlayerPalette.AccentPeach,
                fontFamily = GeistFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = episode.title.ifBlank { "Episode ${episode.number}" },
                color = PlayerPalette.TextPrimary,
                fontFamily = GeistFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (metaLine.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = PlayerPalette.TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = metaLine,
                        color = PlayerPalette.TextMuted,
                        fontFamily = GeistFontFamily,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play episode ${episode.number}",
                tint = PlayerPalette.TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NowPlayingDock(
    posterUrl: String,
    episodeNumber: Int,
    episodeTitle: String,
    hasNextEpisode: Boolean,
    onNextEpisodeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PlayerPalette.DockBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = "Now playing poster",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(56.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Now Playing",
                    color = PlayerPalette.AccentPeach,
                    fontFamily = GeistFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatPortraitEpisodeHeading(episodeNumber, episodeTitle),
                    color = PlayerPalette.TextPrimary,
                    fontFamily = GeistFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(1.dp)
                    .height(28.dp)
                    .background(Color.White.copy(alpha = 0.14f))
            )

            val nextAlpha = if (hasNextEpisode) 1f else 0.45f
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, PlayerPalette.GlassBorder, CircleShape)
                    .clickable(enabled = hasNextEpisode, onClick = onNextEpisodeClick)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = PlayerPalette.TextPrimary.copy(alpha = nextAlpha),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Next Episode",
                    color = PlayerPalette.TextPrimary.copy(alpha = nextAlpha),
                    fontFamily = GeistFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatPortraitEpisodeHeading(episodeNumber: Int, rawTitle: String): String {
    val trimmed = rawTitle.trim()
    val isRedundant = trimmed.isBlank() ||
        trimmed.equals("Episode $episodeNumber", ignoreCase = true) ||
        trimmed.equals("Episode 0$episodeNumber", ignoreCase = true) ||
        trimmed.equals("Ep $episodeNumber", ignoreCase = true)
    return if (isRedundant) "Episode $episodeNumber" else "Ep $episodeNumber • $trimmed"
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

private fun trimSpeed(speed: Float): String {
    return if (speed % 1f == 0f) "%.1f".format(speed) else speed.toString()
}