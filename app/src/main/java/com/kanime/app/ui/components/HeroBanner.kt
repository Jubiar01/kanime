package com.kanime.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kanime.app.data.model.Anime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Smooth GPU-accelerated Top 10 Popular This Season hero carousel.
 * All scroll-driven scale, alpha, and subtle banner parallax animations run exclusively inside
 * [graphicsLayer] (draw phase) with zero recomposition/relayout during swipes for 60-120fps
 * smoothness across budget and flagship devices.
 */
@Composable
fun HeroBannerCarousel(
    animes: List<Anime>,
    watchlistIds: Set<String>,
    onWatchClick: (Anime) -> Unit,
    onToggleWatchlist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (animes.isEmpty()) return

    val items = remember(animes) { animes.take(10) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { items.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val coroutineScope = rememberCoroutineScope()

    // Smooth auto-advance that pauses immediately while the user drags or flings
    LaunchedEffect(items.size, isDragged) {
        if (items.size <= 1 || isDragged) return@LaunchedEffect
        while (true) {
            delay(5_500L)
            if (!pagerState.isScrollInProgress && items.size > 1) {
                val nextPage = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(
                    page = nextPage,
                    animationSpec = tween(
                        durationMillis = 620,
                        easing = FastOutSlowInEasing
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.35f
            ),
            key = { index -> items[index].id },
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val anime = items[page]
            HeroBanner(
                anime = anime,
                rank = page + 1,
                isInWatchlist = watchlistIds.contains(anime.id),
                onWatchClick = { onWatchClick(anime) },
                onToggleWatchlist = { onToggleWatchlist(anime.id) },
                pageOffsetProvider = {
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val absOffset = rawOffset.absoluteValue.coerceIn(0f, 1f)
                        val scale = lerp(0.94f, 1.0f, 1f - absOffset)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.70f, 1.0f, 1f - absOffset)
                    }
            )
        }

        if (items.size > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { idx ->
                    val isSelected = pagerState.currentPage == idx
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 22.dp else 6.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "BannerDotWidth"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
                                }
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = idx,
                                        animationSpec = tween(
                                            durationMillis = 480,
                                            easing = FastOutSlowInEasing
                                        )
                                    )
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun HeroBanner(
    anime: Anime,
    isInWatchlist: Boolean,
    onWatchClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    pageOffsetProvider: (() -> Float)? = null,
) {
    val context = LocalContext.current
    val bannerModel = remember(anime.bannerUrl, anime.coverUrl) {
        val url = anime.bannerUrl.ifBlank { anime.coverUrl }
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(220)
            .build()
    }
    val gradientBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.25f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.55f),
                Color.Black.copy(alpha = 0.94f)
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(272.dp)
            .clickable(onClick = onWatchClick),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle GPU-only parallax on the banner artwork
            AsyncImage(
                model = bannerModel,
                contentDescription = anime.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val offset = pageOffsetProvider?.invoke() ?: 0f
                        // Slight overscan prevents edges from showing during parallax
                        scaleX = 1.08f
                        scaleY = 1.08f
                        translationX = (-offset * 36.dp.toPx()).coerceIn(-48f, 48f)
                    },
                contentScale = ContentScale.Crop
            )

            // Gradient Overlay for crisp text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
            )

            // Top-left Top 10 Popular This Season Rank Badge
            if (rank != null) {
                val isTopThree = rank <= 3
                Surface(
                    color = if (isTopThree) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                    } else {
                        Color.Black.copy(alpha = 0.68f)
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isTopThree) {
                            Color.White.copy(alpha = 0.35f)
                        } else {
                            Color.White.copy(alpha = 0.22f)
                        }
                    ),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "#$rank",
                            color = if (isTopThree) Color(0xFFFFD54F) else Color(0xFFA5B4FC),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "POPULAR THIS SEASON",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bottom Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                // Status tag + Japanese subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = anime.status.uppercase(),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (anime.japaneseTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = anime.japaneseTitle,
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title or TMDB transparent logo
                if (!anime.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = anime.logoUrl,
                        contentDescription = anime.title,
                        modifier = Modifier
                            .height(50.dp)
                            .widthIn(max = 220.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                } else {
                    Text(
                        text = anime.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Rating & Metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB400),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (anime.score > 0.0) String.format("%.1f", anime.score) else "N/A",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    val seasonMeta = listOfNotNull(
                        "${anime.season} ${anime.year}".trim().takeIf { it.isNotBlank() && anime.year > 0 },
                        anime.genres.take(2).joinToString(", ").takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    Text(
                        text = seasonMeta,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Watch Now", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onToggleWatchlist,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Watchlist",
                            tint = if (isInWatchlist) MaterialTheme.colorScheme.primary else Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isInWatchlist) "Added" else "My List")
                    }
                }
            }
        }
    }
}
