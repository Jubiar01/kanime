package com.kanime.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kanime.app.R
import com.kanime.app.data.model.Anime
import com.kanime.app.data.repository.AnimeRepository
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeGlass
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.KanimeWindowWidthSizeClass
import com.kanime.app.ui.theme.LocalWindowSizeClass
import com.kanime.app.ui.theme.PillShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Minimalist editorial Hero Carousel with GPU-only scroll parallax and
 * mode-adaptive scrim + liquid glass controls.
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

    val windowSize = LocalWindowSizeClass.current
    val items = remember(animes) { animes.take(10) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { items.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(items.size, isDragged) {
        if (items.size <= 1 || isDragged) return@LaunchedEffect
        while (true) {
            delay(6_000L)
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

    val horizontalPadding = when (windowSize.widthSizeClass) {
        KanimeWindowWidthSizeClass.Compact -> KanimeSpacing.lg
        KanimeWindowWidthSizeClass.Medium -> KanimeSpacing.xl
        KanimeWindowWidthSizeClass.Expanded -> KanimeSpacing.xxl
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            pageSpacing = KanimeSpacing.md,
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
                        val scale = lerp(0.96f, 1.0f, 1f - absOffset)
                        scaleX = scale
                        scaleY = scale
                        alpha = lerp(0.78f, 1.0f, 1f - absOffset)
                    }
            )
        }

        if (items.size > 1) {
            Spacer(modifier = Modifier.height(KanimeSpacing.md))
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
                            .height(5.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
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
    pageOffsetProvider: (() -> Float)? = null
) {
    val context = LocalContext.current
    val glass = KanimeGlass.tokens
    val colorScheme = MaterialTheme.colorScheme
    val windowSize = LocalWindowSizeClass.current
    val cardShape = MaterialTheme.shapes.extraLarge

    val bannerHeight = when {
        windowSize.widthSizeClass == KanimeWindowWidthSizeClass.Expanded -> 380.dp
        windowSize.isLandscape -> 280.dp
        else -> 340.dp
    }

    val bannerModel = remember(anime.bannerUrl, anime.coverUrl) {
        val url = anime.bannerUrl.ifBlank { anime.coverUrl }
        ImageRequest.Builder(context)
            .data(url)
            .crossfade(240)
            .build()
    }

    // Bottom scrim fades cleanly into background so onSurface text achieves > 10:1 contrast in both modes
    val bottomScrim = remember(colorScheme.background) {
        Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.38f to Color.Transparent,
            0.66f to colorScheme.background.copy(alpha = 0.72f),
            0.86f to colorScheme.background.copy(alpha = 0.94f),
            1.0f to colorScheme.background
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(bannerHeight)
            .shadow(
                elevation = glass.shadowElevation,
                shape = cardShape,
                ambientColor = glass.shadowColor,
                spotColor = glass.shadowColor
            )
            .clip(cardShape)
            .background(colorScheme.surfaceContainerHigh)
            .border(BorderStroke(1.dp, glass.borderBrush), cardShape)
            .clickable(onClick = onWatchClick)
    ) {
        AsyncImage(
            model = bannerModel,
            contentDescription = anime.title,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val offset = pageOffsetProvider?.invoke() ?: 0f
                    scaleX = 1.08f
                    scaleY = 1.08f
                    translationX = (-offset * 34.dp.toPx()).coerceIn(-48f, 48f)
                },
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bottomScrim)
        )

        // Minimal top floating pills: Rank (optional) & Score
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(KanimeSpacing.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (rank != null) {
                GlassSurface(
                    shape = PillShape,
                    overMedia = true,
                    showShadow = false
                ) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            if (anime.score > 0.0) {
                GlassSurface(
                    shape = PillShape,
                    overMedia = true,
                    showShadow = false
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = String.format("%.1f", anime.score),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Bottom Editorial Content Stack: Title + 1-line Meta + Play/Save Actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(KanimeSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(KanimeSpacing.sm)
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val metaLine = remember(anime.year, anime.episodes, anime.genres) {
                buildList {
                    if (anime.year > 0) add(anime.year.toString())
                    if (anime.episodes > 0) add("${anime.episodes} Ep")
                    anime.genres.take(2).forEach { g ->
                        if (g.isNotBlank()) add(g)
                    }
                }.joinToString(" · ")
            }
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.widthIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(KanimeSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(
                    text = stringResource(R.string.hero_play),
                    icon = Icons.Default.PlayArrow,
                    style = GlassButtonStyle.Primary,
                    onClick = onWatchClick,
                    modifier = Modifier.weight(1f)
                )

                GlassButton(
                    text = stringResource(if (isInWatchlist) R.string.hero_saved else R.string.hero_save),
                    icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    style = if (isInWatchlist) GlassButtonStyle.Tonal else GlassButtonStyle.Glass,
                    onClick = onToggleWatchlist
                )
            }
        }
    }
}

@Preview(name = "HeroBanner Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "HeroBanner Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "HeroBanner Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Composable
private fun HeroBannerPreview() {
    KAnimeTheme(animateTransition = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                HeroBanner(
                    anime = AnimeRepository.sampleAnimes.first(),
                    rank = 1,
                    isInWatchlist = false,
                    onWatchClick = {},
                    onToggleWatchlist = {}
                )
            }
        }
    }
}
