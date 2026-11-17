package com.kanime.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kanime.app.data.model.Anime
import com.kanime.app.data.repository.AnimeRepository
import com.kanime.app.ui.components.AnimeCard
import com.kanime.app.ui.components.HeroBanner
import com.kanime.app.ui.components.SectionHeader
import com.kanime.app.ui.viewmodel.AnimeUiState
import com.kanime.app.ui.viewmodel.AnimeViewModel

@Composable
fun HomeScreen(
    viewModel: AnimeViewModel,
    uiState: AnimeUiState,
    onSeeAllClick: (String) -> Unit
) {
    val trending = uiState.trendingAnime
    val topAiring = uiState.topAiringAnime
    val seasonal = uiState.seasonalAnime
    val featured = trending.firstOrNull() ?: viewModel.allAnime.firstOrNull() ?: AnimeRepository.sampleAnimes.first()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Hero Featured Banner
        item {
            Box(modifier = Modifier.padding(16.dp)) {
                HeroBanner(
                    anime = featured,
                    isInWatchlist = uiState.watchlistIds.contains(featured.id),
                    onWatchClick = { viewModel.openAnime(featured) },
                    onToggleWatchlist = { viewModel.toggleWatchlist(featured.id) }
                )
            }
        }

        // Trending Now Section
        item {
            SectionHeader(
                title = "🔥 Trending Now",
                onActionClick = { onSeeAllClick("Trending") }
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trending, key = { it.id }) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { viewModel.openAnime(anime) }
                    )
                }
            }
        }

        // Top Airing Section
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(
                title = "⚡ Top Airing",
                onActionClick = { onSeeAllClick("Top Airing") }
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(topAiring, key = { it.id }) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { viewModel.openAnime(anime) }
                    )
                }
            }
        }

        // Popular This Season
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SectionHeader(
                title = "🌸 Popular This Season",
                onActionClick = { onSeeAllClick("Seasonal") }
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(seasonal, key = { it.id }) { anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { viewModel.openAnime(anime) }
                    )
                }
            }
        }
    }
}
