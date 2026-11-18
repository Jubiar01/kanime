package com.kanime.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kanime.app.R
import com.kanime.app.data.api.WatchHistoryEntry
import com.kanime.app.data.model.Anime
import com.kanime.app.data.repository.AnimeRepository
import com.kanime.app.ui.components.AnimeCard
import com.kanime.app.ui.components.EmptyStateView
import com.kanime.app.ui.components.GlassChip
import com.kanime.app.ui.components.GlassSurface
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.KanimeWindowWidthSizeClass
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.theme.rememberBoundedImageRequest
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel
import com.kanime.app.ui.viewmodel.NavTab

private enum class WatchlistTab {
    SAVED, HISTORY
}

@Composable
fun WatchlistScreen(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance
) {
    val watchlistAnime = remember(uiState.savedWatchlist, uiState.watchlistIds) {
        if (uiState.savedWatchlist.isNotEmpty()) {
            uiState.savedWatchlist
        } else {
            viewModel.allAnime.filter { uiState.watchlistIds.contains(it.id) }
        }
    }

    WatchlistScreenContent(
        watchlistAnime = watchlistAnime,
        historyItems = uiState.watchHistory,
        onOpenAnime = { viewModel.openAnime(it) },
        onResumeHistory = { viewModel.resumeFromHistory(it) },
        onRemoveHistory = { viewModel.removeHistoryItem(it) },
        onClearHistory = { viewModel.clearWatchHistory() },
        onNavigateExplore = { viewModel.selectTab(NavTab.EXPLORE) },
        onNavigateHome = { viewModel.selectTab(NavTab.HOME) },
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding
    )
}

@Composable
internal fun WatchlistScreenContent(
    watchlistAnime: List<Anime>,
    historyItems: List<WatchHistoryEntry>,
    onOpenAnime: (Anime) -> Unit,
    onResumeHistory: (WatchHistoryEntry) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onNavigateExplore: () -> Unit,
    onNavigateHome: () -> Unit,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance
) {
    val windowSize = LocalWindowSizeClass.current
    val horizontalPad = when (windowSize.widthSizeClass) {
        KanimeWindowWidthSizeClass.Compact -> KanimeSpacing.lg
        KanimeWindowWidthSizeClass.Medium -> KanimeSpacing.xl
        KanimeWindowWidthSizeClass.Expanded -> KanimeSpacing.xxl
    }
    val minCellSize = when (windowSize.widthSizeClass) {
        KanimeWindowWidthSizeClass.Compact -> 138.dp
        KanimeWindowWidthSizeClass.Medium -> 152.dp
        KanimeWindowWidthSizeClass.Expanded -> 164.dp
    }

    var activeTab by remember { mutableStateOf(WatchlistTab.SAVED) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topContentPadding)
    ) {
        // Minimal Header + Segmented Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.library_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (activeTab == WatchlistTab.HISTORY && historyItems.isNotEmpty()) {
                TextButton(onClick = { showClearHistoryDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_clear))
                }
            }
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
        ) {
            GlassChip(
                label = "${stringResource(R.string.library_saved)} (${watchlistAnime.size})",
                leadingIcon = Icons.Default.Bookmark,
                selected = activeTab == WatchlistTab.SAVED,
                onClick = { activeTab = WatchlistTab.SAVED }
            )
            GlassChip(
                label = "${stringResource(R.string.library_history)} (${historyItems.size})",
                leadingIcon = Icons.Default.History,
                selected = activeTab == WatchlistTab.HISTORY,
                onClick = { activeTab = WatchlistTab.HISTORY }
            )
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        when (activeTab) {
            WatchlistTab.SAVED -> {
                if (watchlistAnime.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            icon = Icons.Default.BookmarkBorder,
                            title = stringResource(R.string.library_empty_saved_title),
                            subtitle = stringResource(R.string.library_empty_saved_body),
                            actionLabel = stringResource(R.string.library_empty_saved_cta),
                            onAction = onNavigateExplore
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = minCellSize),
                        contentPadding = PaddingValues(
                            start = horizontalPad,
                            end = horizontalPad,
                            top = KanimeSpacing.xs,
                            bottom = bottomContentPadding
                        ),
                        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.lg),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(watchlistAnime, key = { it.id }, contentType = { "anime_card" }) { anime ->
                            AnimeCard(
                                anime = anime,
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onOpenAnime(anime) }
                            )
                        }
                    }
                }
            }

            WatchlistTab.HISTORY -> {
                if (historyItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = bottomContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateView(
                            icon = Icons.Default.History,
                            title = stringResource(R.string.library_empty_history_title),
                            subtitle = stringResource(R.string.library_empty_history_body),
                            actionLabel = stringResource(R.string.library_empty_history_cta),
                            onAction = onNavigateHome
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = horizontalPad,
                            end = horizontalPad,
                            top = KanimeSpacing.xs,
                            bottom = bottomContentPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(historyItems, key = { "${it.anime.id}_${it.episodeNumber}" }, contentType = { "history_card" }) { entry ->
                            WatchHistoryCard(
                                entry = entry,
                                onResumeClick = { onResumeHistory(entry) },
                                onDetailsClick = { onOpenAnime(entry.anime) },
                                onRemoveClick = { onRemoveHistory(entry.anime.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text(stringResource(R.string.library_clear_history_title)) },
            text = { Text(stringResource(R.string.library_clear_history_body)) },
            confirmButton = {
                Button(onClick = {
                    onClearHistory()
                    showClearHistoryDialog = false
                }) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun WatchHistoryCard(
    entry: WatchHistoryEntry,
    onResumeClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onResumeClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KanimeSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 16:9 Thumbnail with Glass Play Pill & Progress Track
            Box(
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                val thumbRequest = rememberBoundedImageRequest(
                    url = entry.episodeThumbnail.ifBlank { entry.anime.bannerUrl.ifBlank { entry.anime.coverUrl } },
                    widthDp = 128.dp,
                    heightDp = 72.dp
                )
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = entry.anime.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp),
                    shape = CircleShape,
                    overMedia = true,
                    showShadow = false
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.action_resume),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                GlassSurface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = PillShape,
                    overMedia = true,
                    showShadow = false
                ) {
                    Text(
                        text = stringResource(R.string.episode_label, entry.episodeNumber),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                if (entry.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.45f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(entry.progressFraction.coerceIn(0.04f, 1f))
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(KanimeSpacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.anime.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRemoveClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.cd_remove_history),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                val progressLabel = when {
                    entry.isNearlyFinished -> stringResource(R.string.history_completed)
                    entry.positionMs > 0L && entry.durationMs > 0L ->
                        "${formatDurationMs(entry.positionMs)} / ${formatDurationMs(entry.durationMs)}"
                    entry.positionMs > 0L -> formatDurationMs(entry.positionMs)
                    else -> stringResource(R.string.history_started)
                }
                Text(
                    text = "$progressLabel · ${formatRelativeTime(entry.watchedAtMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = onResumeClick,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = PillShape,
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(if (entry.isNearlyFinished) R.string.action_replay else R.string.action_resume),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    TextButton(
                        onClick = onDetailsClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_details),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

private fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format("%02d:%02d", mins, secs)
}

private fun formatRelativeTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Now"
    val diffSec = ((System.currentTimeMillis() - timestampMs) / 1000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Now"
        diffSec < 3600L -> "${diffSec / 60L}m"
        diffSec < 86400L -> "${diffSec / 3600L}h"
        diffSec < 172800L -> "1d"
        else -> "${diffSec / 86400L}d"
    }
}

@Preview(name = "Library Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Library Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Library Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Preview(name = "Library Dark Foldable", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.FOLDABLE)
@Composable
private fun WatchlistScreenPreview() {
    KAnimeTheme(animateTransition = false) {
        KanimeAmbientBackdrop {
            WatchlistScreenContent(
                watchlistAnime = AnimeRepository.sampleAnimes,
                historyItems = emptyList(),
                onOpenAnime = {},
                onResumeHistory = {},
                onRemoveHistory = {},
                onClearHistory = {},
                onNavigateExplore = {},
                onNavigateHome = {}
            )
        }
    }
}
