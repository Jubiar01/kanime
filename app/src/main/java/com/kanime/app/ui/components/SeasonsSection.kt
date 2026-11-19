package com.kanime.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kanime.app.R
import com.kanime.app.data.model.AnimeSeasonEntry
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.rememberBoundedImageRequest

/**
 * "Seasons" row for the anime detail screen: every season / movie / OVA of the franchise as a poster card
 * (or a compact chip) so a long series can be navigated in one tap. The anime being viewed is highlighted
 * and scrolled into view; tapping another entry opens it.
 */
@Composable
fun SeasonsSection(
    seasons: List<AnimeSeasonEntry>,
    onSelect: (AnimeSeasonEntry) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = KanimeSpacing.lg
) {
    var showPosters by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val currentIndex = remember(seasons) { seasons.indexOfFirst { it.isCurrent }.coerceAtLeast(0) }

    LaunchedEffect(seasons) {
        if (currentIndex > 0) listState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = KanimeSpacing.sm, bottom = KanimeSpacing.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.detail_seasons),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            GlassChip(
                label = stringResource(
                    if (showPosters) R.string.detail_seasons_posters else R.string.detail_seasons_compact
                ),
                selected = false,
                onClick = { showPosters = !showPosters },
                leadingIcon = if (showPosters) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList
            )
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.sm))

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(if (showPosters) KanimeSpacing.md else KanimeSpacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            items(seasons, key = { it.malId }, contentType = { if (showPosters) "poster" else "chip" }) { season ->
                if (showPosters) {
                    SeasonPosterCard(season = season, onClick = { onSelect(season) })
                } else {
                    GlassChip(
                        label = season.label,
                        selected = season.isCurrent,
                        onClick = { onSelect(season) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonPosterCard(
    season: AnimeSeasonEntry,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val selected = season.isCurrent

    val borderColor by animateColorAsState(
        targetValue = if (selected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.55f),
        label = "SeasonBorder"
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colorScheme.onSurface else colorScheme.onSurfaceVariant,
        label = "SeasonLabel"
    )
    val request = rememberBoundedImageRequest(
        url = season.coverUrl,
        widthDp = 112.dp,
        heightDp = 168.dp
    )

    val meta = listOfNotNull(
        season.episodes?.let { stringResource(R.string.detail_season_episodes, it) },
        season.year?.toString()
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .width(112.dp)
            .clip(shape)
            .semantics(mergeDescendants = true) {
                contentDescription = "${season.label}, ${season.title}"
                this.selected = selected
            }
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(shape)
                .background(colorScheme.surfaceContainerHigh)
                .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), shape)
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.sm))

        Text(
            text = season.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (meta.isNotEmpty()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
