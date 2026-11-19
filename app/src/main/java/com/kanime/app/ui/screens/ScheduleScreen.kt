package com.kanime.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kanime.app.R
import com.kanime.app.data.api.AniListApi
import com.kanime.app.data.api.WatchHistoryStore
import com.kanime.app.ui.components.GlassButton
import com.kanime.app.ui.components.GlassButtonStyle
import com.kanime.app.ui.components.GlassChip
import com.kanime.app.ui.components.ShimmerBox
import com.kanime.app.ui.notify.rememberEpisodeAlertsToggle
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.KanimeWindowWidthSizeClass
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.rememberBoundedImageRequest
import com.kanime.app.ui.viewmodel.AnimeViewModel
import com.kanime.app.ui.viewmodel.ScheduleViewModel
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class ScheduleDay(val startMs: Long, val endMs: Long)

private fun buildScheduleDays(windowStartMs: Long): List<ScheduleDay> {
    val cal = Calendar.getInstance().apply { timeInMillis = windowStartMs }
    return List(7) {
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        ScheduleDay(start, cal.timeInMillis)
    }
}

private fun AniListApi.AniListAiringEntry.airingAtMs(): Long = airingAtSec * 1000L

/**
 * Release calendar: what airs each day this week (in the viewer's local time), with the viewer's saved
 * anime highlighted, an "only my list" filter, and a switch for new-episode alerts.
 */
@Composable
fun ScheduleScreen(
    animeViewModel: AnimeViewModel,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance,
    scheduleViewModel: ScheduleViewModel = viewModel()
) {
    val state by scheduleViewModel.state.collectAsStateWithLifecycle()
    val watchlist by WatchHistoryStore.watchlistFlow.collectAsStateWithLifecycle()
    val watchlistMalIds = remember(watchlist) { watchlist.mapNotNull { it.malId }.toSet() }
    val (alertsEnabled, setAlertsEnabled) = rememberEpisodeAlertsToggle()

    LaunchedEffect(Unit) { scheduleViewModel.refreshIfStale() }

    val windowSize = LocalWindowSizeClass.current
    val horizontalPad = when (windowSize.widthSizeClass) {
        KanimeWindowWidthSizeClass.Compact -> KanimeSpacing.lg
        KanimeWindowWidthSizeClass.Medium -> KanimeSpacing.xl
        KanimeWindowWidthSizeClass.Expanded -> KanimeSpacing.xxl
    }

    var onlyMyList by rememberSaveable { mutableStateOf(false) }
    var selectedDay by rememberSaveable { mutableIntStateOf(0) }

    val days = remember(state.windowStartMs) { buildScheduleDays(state.windowStartMs) }
    val locale = Locale.getDefault()
    val timeFormat = remember(locale) { DateFormat.getTimeInstance(DateFormat.SHORT, locale) }
    val weekdayFormat = remember(locale) { SimpleDateFormat("EEE", locale) }
    val dayNumberFormat = remember(locale) { SimpleDateFormat("d", locale) }

    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }

    val dayEntries = remember(state.entries, days, selectedDay, onlyMyList, watchlistMalIds) {
        val day = days.getOrNull(selectedDay)
        if (day == null) {
            emptyList()
        } else {
            state.entries.filter { e ->
                val ms = e.airingAtMs()
                ms >= day.startMs && ms < day.endMs && (!onlyMyList || e.malId in watchlistMalIds)
            }
        }
    }
    val daysWithSaved = remember(state.entries, days, watchlistMalIds) {
        days.indices.filter { i ->
            val day = days[i]
            state.entries.any { e ->
                val ms = e.airingAtMs()
                ms >= day.startMs && ms < day.endMs && e.malId in watchlistMalIds
            }
        }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topContentPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.schedule_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            GlassChip(
                label = stringResource(if (alertsEnabled) R.string.schedule_alerts_on else R.string.schedule_alerts_off),
                selected = alertsEnabled,
                onClick = { setAlertsEnabled(!alertsEnabled) },
                leadingIcon = if (alertsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone
            )
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        // Day strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
        ) {
            itemsIndexed(days) { index, day ->
                val label = if (index == 0) {
                    stringResource(R.string.schedule_today)
                } else {
                    weekdayFormat.format(Date(day.startMs))
                }
                ScheduleDayChip(
                    label = label,
                    dayNumber = dayNumberFormat.format(Date(day.startMs)),
                    selected = index == selectedDay,
                    hasSavedAnime = index in daysWithSaved,
                    onClick = { selectedDay = index }
                )
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
                label = stringResource(R.string.schedule_filter_all),
                selected = !onlyMyList,
                onClick = { onlyMyList = false }
            )
            GlassChip(
                label = stringResource(R.string.schedule_filter_my_list),
                selected = onlyMyList,
                onClick = { onlyMyList = true }
            )
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        when {
            state.isLoading && state.entries.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad),
                    verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                ) {
                    repeat(4) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp)
                        )
                    }
                }
            }

            state.hasError && state.entries.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = KanimeSpacing.xl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
                ) {
                    Text(
                        text = stringResource(R.string.schedule_error),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    GlassButton(
                        text = stringResource(R.string.schedule_retry),
                        icon = Icons.Default.Refresh,
                        style = GlassButtonStyle.Tonal,
                        onClick = { scheduleViewModel.load() }
                    )
                }
            }

            dayEntries.isEmpty() -> {
                Text(
                    text = stringResource(if (onlyMyList) R.string.schedule_empty_my_list else R.string.schedule_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPad, vertical = KanimeSpacing.xl)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = horizontalPad,
                        end = horizontalPad,
                        bottom = bottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
                ) {
                    items(dayEntries, key = { "${it.anilistId}_${it.episode}" }, contentType = { "schedule_item" }) { entry ->
                        ScheduleItemCard(
                            entry = entry,
                            inWatchlist = entry.malId in watchlistMalIds,
                            nowMs = nowMs,
                            timeFormat = timeFormat,
                            onClick = { animeViewModel.openAnimeByMalId(entry.malId, entry.title, entry.cover) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleDayChip(
    label: String,
    dayNumber: String,
    selected: Boolean,
    hasSavedAnime: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Surface(
        shape = shape,
        color = if (selected) colorScheme.primaryContainer else colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (selected) colorScheme.primary.copy(alpha = 0.6f) else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .clip(shape)
            .clickable(role = Role.Tab, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .width(62.dp)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = dayNumber,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (selected) colorScheme.onPrimaryContainer else colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasSavedAnime) {
                            if (selected) colorScheme.onPrimaryContainer else colorScheme.primary
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    )
            )
        }
    }
}

@Composable
private fun ScheduleItemCard(
    entry: AniListApi.AniListAiringEntry,
    inWatchlist: Boolean,
    nowMs: Long,
    timeFormat: DateFormat,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val airingMs = entry.airingAtMs()
    val hasAired = airingMs <= nowMs
    val minutesUntil = ((airingMs - nowMs) / 60_000L).toInt()
    val timeText = remember(airingMs, timeFormat) { timeFormat.format(Date(airingMs)) }

    val request = rememberBoundedImageRequest(url = entry.cover, widthDp = 64.dp, heightDp = 92.dp)

    val episodeText = entry.totalEpisodes
        ?.let { stringResource(R.string.schedule_episode_total, entry.episode, it) }
        ?: stringResource(R.string.episode_label, entry.episode)

    Surface(
        shape = shape,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (inWatchlist) colorScheme.primary.copy(alpha = 0.55f) else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KanimeSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(92.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = entry.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(KanimeSpacing.md))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$episodeText · $timeText",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasAired) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.schedule_aired),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                } else if (minutesUntil in 1 until 24 * 60) {
                    val countdown = if (minutesUntil >= 60) {
                        stringResource(R.string.schedule_in_hours_minutes, minutesUntil / 60, minutesUntil % 60)
                    } else {
                        stringResource(R.string.schedule_in_minutes, minutesUntil)
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.primaryContainer
                    ) {
                        Text(
                            text = countdown,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (inWatchlist) {
                Spacer(modifier = Modifier.width(KanimeSpacing.sm))
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
