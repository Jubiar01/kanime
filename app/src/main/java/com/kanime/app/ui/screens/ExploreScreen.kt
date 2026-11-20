package com.kanime.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kanime.app.R
import com.kanime.app.data.model.Anime
import com.kanime.app.data.repository.AnimeRepository
import com.kanime.app.ui.components.AnimeCard
import com.kanime.app.ui.components.AnimeCardSkeleton
import com.kanime.app.ui.components.EmptyStateView
import com.kanime.app.ui.components.GlassChip
import com.kanime.app.ui.components.GlassSurface
import com.kanime.app.ui.components.rememberSharedShimmerPhase
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.KanimeWindowWidthSizeClass
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel

@Composable
fun ExploreScreen(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance
) {
    val isQueryActive = uiState.searchQuery.isNotBlank()
    val baseCatalog = if (uiState.exploreCatalog.isNotEmpty()) uiState.exploreCatalog else viewModel.allAnime
    val displayList = if (isQueryActive) uiState.searchResults else baseCatalog
    val isLoading = if (isQueryActive) uiState.isSearching else uiState.isBrowsingCatalog

    ExploreScreenContent(
        searchQuery = uiState.searchQuery,
        selectedGenre = uiState.selectedGenre,
        selectedStatus = uiState.selectedStatus,
        genres = viewModel.genres,
        displayList = displayList,
        isLoading = isLoading,
        onQueryChange = { viewModel.updateSearch(it) },
        onSelectGenre = { viewModel.selectGenre(it) },
        onSelectStatus = { viewModel.selectStatus(it) },
        onOpenAnime = { viewModel.openAnime(it) },
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding
    )
}

@Composable
internal fun ExploreScreenContent(
    searchQuery: String,
    selectedGenre: String,
    selectedStatus: String,
    genres: List<String>,
    displayList: List<Anime>,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectGenre: (String) -> Unit,
    onSelectStatus: (String) -> Unit,
    onOpenAnime: (Anime) -> Unit,
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

    val isQueryActive = searchQuery.isNotBlank()
    val statuses = remember { listOf("All", "Airing", "Completed", "Upcoming") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(top = topContentPadding)
    ) {
        // Prominent Liquid Glass Search Field
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad)
                .heightIn(min = 56.dp),
            shape = PillShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.explore_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Filter Chips (Status + Genres) when browsing
        if (!isQueryActive) {
            Spacer(modifier = Modifier.height(KanimeSpacing.md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
            ) {
                statuses.forEach { status ->
                    val label = when (status) {
                        "All" -> stringResource(R.string.filter_all)
                        "Airing" -> stringResource(R.string.filter_airing)
                        "Completed" -> stringResource(R.string.filter_completed)
                        "Upcoming" -> stringResource(R.string.filter_upcoming)
                        else -> status
                    }
                    GlassChip(
                        label = label,
                        selected = selectedStatus == status,
                        onClick = { onSelectStatus(status) }
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(20.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                genres.filter { it != "All" }.forEach { genre ->
                    GlassChip(
                        label = genre,
                        selected = selectedGenre == genre,
                        onClick = {
                            onSelectGenre(if (selectedGenre == genre) "All" else genre)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.md))

        // Compact Result Counter
        if (displayList.isNotEmpty()) {
            Text(
                text = if (isQueryActive) {
                    stringResource(R.string.explore_results_count, displayList.size)
                } else {
                    stringResource(R.string.explore_catalog_count, displayList.size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = horizontalPad)
            )
            Spacer(modifier = Modifier.height(KanimeSpacing.sm))
        }

        // Adaptive Content Area
        when {
            isLoading && displayList.isEmpty() -> {
                val sharedShimmer = rememberSharedShimmerPhase()
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
                    items(8, contentType = { "skeleton" }) {
                        AnimeCardSkeleton(
                            modifier = Modifier.fillMaxWidth(),
                            phaseState = sharedShimmer
                        )
                    }
                }
            }

            displayList.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomContentPadding),
                    contentAlignment = Alignment.Center
                ) {
                    val hasFilters = isQueryActive || selectedGenre != "All" || selectedStatus != "All"
                    EmptyStateView(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.explore_empty_title),
                        subtitle = if (isQueryActive) {
                            stringResource(R.string.explore_empty_query, searchQuery)
                        } else {
                            stringResource(R.string.explore_empty_catalog)
                        },
                        actionLabel = if (hasFilters) stringResource(R.string.explore_reset_filters) else null,
                        onAction = if (hasFilters) {
                            {
                                onQueryChange("")
                                onSelectGenre("All")
                                onSelectStatus("All")
                            }
                        } else null
                    )
                }
            }

            else -> {
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
                    items(displayList, key = { it.id }, contentType = { "anime_card" }) { anime ->
                        AnimeCard(
                            anime = anime,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            onClick = { onOpenAnime(anime) }
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Explore Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Explore Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Explore Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Preview(name = "Explore Dark Foldable", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.FOLDABLE)
@Composable
private fun ExploreScreenPreview() {
    KAnimeTheme(animateTransition = false) {
        KanimeAmbientBackdrop {
            ExploreScreenContent(
                searchQuery = "",
                selectedGenre = "Action",
                selectedStatus = "All",
                genres = AnimeRepository.genres,
                displayList = AnimeRepository.sampleAnimes,
                isLoading = false,
                onQueryChange = {},
                onSelectGenre = {},
                onSelectStatus = {},
                onOpenAnime = {}
            )
        }
    }
}
