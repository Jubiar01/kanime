package com.kanime.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import com.kanime.app.ui.components.HeroBannerCarousel
import com.kanime.app.ui.components.HeroBannerSkeleton
import com.kanime.app.ui.components.SectionHeader
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.KanimeWindowWidthSizeClass
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel

@Composable
fun HomeScreen(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    onSeeAllClick: (String) -> Unit,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance
) {
    HomeScreenContent(
        trending = uiState.trendingAnime,
        topAiring = uiState.topAiringAnime,
        seasonal = uiState.seasonalAnime,
        watchlistIds = uiState.watchlistIds,
        onOpenAnime = { viewModel.openAnime(it) },
        onToggleWatchlist = { viewModel.toggleWatchlist(it) },
        onSeeAllClick = onSeeAllClick,
        topContentPadding = topContentPadding,
        bottomContentPadding = bottomContentPadding
    )
}

@Composable
internal fun HomeScreenContent(
    trending: List<Anime>,
    topAiring: List<Anime>,
    seasonal: List<Anime>,
    watchlistIds: Set<String>,
    onOpenAnime: (Anime) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onSeeAllClick: (String) -> Unit,
    topContentPadding: Dp = 12.dp,
    bottomContentPadding: Dp = KanimeSpacing.floatingBarClearance
) {
    val windowSize = LocalWindowSizeClass.current
    val horizontalPad = when (windowSize.widthSizeClass) {
        KanimeWindowWidthSizeClass.Compact -> KanimeSpacing.lg
        KanimeWindowWidthSizeClass.Medium -> KanimeSpacing.xl
        KanimeWindowWidthSizeClass.Expanded -> KanimeSpacing.xxl
    }
    val cardWidth = when (windowSize.widthSizeClass) {
        KanimeWindowWidthSizeClass.Compact -> 142.dp
        KanimeWindowWidthSizeClass.Medium -> 158.dp
        KanimeWindowWidthSizeClass.Expanded -> 172.dp
    }

    val heroItems = remember(seasonal, trending) {
        val base = if (seasonal.isNotEmpty()) seasonal else trending
        base.take(10)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topContentPadding, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(KanimeSpacing.lg)
    ) {
        item(key = "hero_carousel") {
            if (heroItems.isEmpty()) {
                Box(modifier = Modifier.padding(horizontal = horizontalPad)) {
                    HeroBannerSkeleton()
                }
            } else {
                HeroBannerCarousel(
                    animes = heroItems,
                    watchlistIds = watchlistIds,
                    onWatchClick = onOpenAnime,
                    onToggleWatchlist = onToggleWatchlist
                )
            }
        }

        item(key = "row_trending") {
            AnimeHorizontalSection(
                title = stringResource(R.string.home_trending),
                animes = trending,
                cardWidth = cardWidth,
                horizontalPad = horizontalPad,
                onAnimeClick = onOpenAnime,
                onActionClick = { onSeeAllClick("Trending") }
            )
        }

        item(key = "row_airing") {
            AnimeHorizontalSection(
                title = stringResource(R.string.home_top_airing),
                animes = topAiring,
                cardWidth = cardWidth,
                horizontalPad = horizontalPad,
                onAnimeClick = onOpenAnime,
                onActionClick = { onSeeAllClick("Top Airing") }
            )
        }

        item(key = "row_seasonal") {
            AnimeHorizontalSection(
                title = stringResource(R.string.home_seasonal),
                animes = seasonal,
                cardWidth = cardWidth,
                horizontalPad = horizontalPad,
                onAnimeClick = onOpenAnime,
                onActionClick = { onSeeAllClick("Seasonal") }
            )
        }
    }
}

@Composable
private fun AnimeHorizontalSection(
    title: String,
    animes: List<Anime>,
    cardWidth: Dp,
    horizontalPad: Dp,
    onAnimeClick: (Anime) -> Unit,
    onActionClick: () -> Unit
) {
    SectionHeader(
        title = title,
        onActionClick = onActionClick,
        modifier = Modifier.padding(horizontal = (horizontalPad - KanimeSpacing.lg).coerceAtLeast(0.dp))
    )
    Spacer(modifier = Modifier.height(KanimeSpacing.xs))
    LazyRow(
        contentPadding = PaddingValues(horizontal = horizontalPad),
        horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md)
    ) {
        if (animes.isEmpty()) {
            items(4) {
                AnimeCardSkeleton(modifier = Modifier.width(cardWidth))
            }
        } else {
            items(animes, key = { it.id }) { anime ->
                AnimeCard(
                    anime = anime,
                    modifier = Modifier.width(cardWidth),
                    onClick = { onAnimeClick(anime) }
                )
            }
        }
    }
}

@Preview(name = "Home Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Home Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Home Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Preview(name = "Home Dark Foldable", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.FOLDABLE)
@Composable
private fun HomeScreenPreview() {
    val samples = AnimeRepository.sampleAnimes
    KAnimeTheme(animateTransition = false) {
        KanimeAmbientBackdrop {
            HomeScreenContent(
                trending = samples,
                topAiring = samples,
                seasonal = samples,
                watchlistIds = setOf(samples.first().id),
                onOpenAnime = {},
                onToggleWatchlist = {},
                onSeeAllClick = {}
            )
        }
    }
}
