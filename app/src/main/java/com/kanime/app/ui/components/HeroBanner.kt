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
import androidx.compose.ui.unit.sp
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
 * [graphicsLayer] (draw phase) with zero recomposition/relayout during swipes.
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
            delay(5_800L)
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
                        val scale = lerp(0.95f, 1.0f, 1f - absOffset)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.72f, 1.0f, 1f - absOffset)
                    }
            )
        }

        if (items.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(items.size) { idx ->
                        val isSelected = pagerState.currentPage == idx
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 6.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "BannerDotWidth"
                        )
                        Box(
                            modifier = Modifier
                                .height(5.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
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

                Text(
                    text = String.format("%02d / %02d", pagerState.currentPage + 1, items.size),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                )
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
            .crossfade(240)
            .build()
    }
    val verticalScrim = remember {
        Brush.verticalGradient(
            0.0f to Color(0xFF0A0C14).copy(alpha = 0.42f),
            0.26f to Color.Transparent,
            0.52f to Color(0xFF0A0C14).copy(alpha = 0.48f),
            0.78f to Color(0xFF0A0C14).copy(alpha = 0.88f),
            1.0f to Color(0xFF0A0C14).copy(alpha = 0.98f)
        )
    }
    val sideScrim = remember {
        Brush.horizontalGradient(
            0.0f to Color(0xFF0A0C14).copy(alpha = 0.50f),
            0.55f to Color.Transparent,
            1.0f to Color(0xFF0A0C14).copy(alpha = 0.18f)
        )
    }

    val statusDotColor = when {
        anime.status.contains("Air", ignoreCase = true) -> Color(0xFF22C55E)
        anime.status.contains("Upcom", ignoreCase = true) -> Color(0xFF08D9D6)
        else -> Color(0xFFA5B4FC)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(352.dp)
            .clickable(onClick = onWatchClick),
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle GPU-only parallax on the hero artwork
            AsyncImage(
                model = bannerModel,
                contentDescription = anime.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val offset = pageOffsetProvider?.invoke() ?: 0f
                        scaleX = 1.09f
                        scaleY = 1.09f
                        translationX = (-offset * 38.dp.toPx()).coerceIn(-52f, 52f)
                    },
                contentScale = ContentScale.Crop
            )

            // Dual-layer cinematic scrim for crisp readability on any frame
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(sideScrim)
                    .background(verticalScrim)
            )

            // Top Floating Metadata Row: Rank (left) + Score & Status (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (rank != null) {
                    val isTopThree = rank <= 3
                    Surface(
                        color = if (isTopThree) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
                        } else {
                            Color(0xFF0E111B).copy(alpha = 0.72f)
                        },
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color.White.copy(alpha = if (isTopThree) 0.34f else 0.18f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "#$rank",
                                color = if (isTopThree) Color(0xFFFFE082) else Color(0xFFA5B4FC),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TOP SEASONAL",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Glassmorphic Status + Rating pill
                Surface(
                    color = Color(0xFF0E111B).copy(alpha = 0.72f),
                    shape = RoundedCornerShape(100.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = anime.status.uppercase(),
                            color = Color.White.copy(alpha = 0.92f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (anime.score > 0.0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(10.dp)
                                    .background(Color.White.copy(alpha = 0.25f))
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFB400),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format("%.1f", anime.score),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Bottom Cinematic Content Stack
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title or transparent TMDB logo
                if (!anime.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = anime.logoUrl,
                        contentDescription = anime.title,
                        modifier = Modifier
                            .height(54.dp)
                            .widthIn(max = 240.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                } else {
                    Text(
                        text = anime.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Compact Metadata Line (Season/Year • Episodes • Genres)
                val metaParts = remember(anime.season, anime.year, anime.episodes, anime.genres) {
                    buildList {
                        val seasonYear = "${anime.season} ${anime.year}".trim()
                        if (seasonYear.isNotBlank() && anime.year > 0) add(seasonYear)
                        if (anime.episodes > 0) add("${anime.episodes} Eps")
                        anime.genres.take(3).forEach { g ->
                            if (g.isNotBlank()) add(g)
                        }
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Clean 2-line synopsis teaser
                val cleanSynopsis = remember(anime.synopsis) {
                    anime.synopsis.replace(Regex("\\s+"), " ").trim()
                }
                if (cleanSynopsis.isNotBlank()) {
                    Text(
                        text = cleanSynopsis,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Action CTAs: Primary Watch Now + Glassmorphic My List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onWatchClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Watch Now",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        onClick = onToggleWatchlist,
                        shape = RoundedCornerShape(14.dp),
                        color = if (isInWatchlist) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        } else {
                            Color.White.copy(alpha = 0.14f)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isInWatchlist) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                            } else {
                                Color.White.copy(alpha = 0.28f)
                            }
                        ),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Watchlist",
                                tint = if (isInWatchlist) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isInWatchlist) "In List" else "My List",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
