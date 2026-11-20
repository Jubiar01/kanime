package com.kanime.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kanime.app.R
import com.kanime.app.data.model.Anime
import com.kanime.app.data.repository.AnimeRepository
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeGlass
import com.kanime.app.ui.theme.KanimeMotion
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.theme.rememberBoundedImageRequest
import java.util.Locale

/**
 * Clean minimalist poster card with:
 * - Bounded-size Coil image decoding (2:3 poster)
 * - Lightweight solid-translucent overlay pills (no nested GlassSurface/blur/shadow overhead)
 * - 2-line aligned Geist title (grows naturally at 200% font scale)
 * - Status-aware locale-formatted release date
 * - Press spring scale + focus/hover ring + merged accessibility semantics
 */
@Composable
fun AnimeCard(
    anime: Anime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val glass = KanimeGlass.tokens
    val colorScheme = MaterialTheme.colorScheme

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = KanimeMotion.pressSpring(),
        label = "AnimeCardScale"
    )

    val formattedScore = remember(anime.score, locale) {
        if (anime.score > 0.0) String.format(locale, "%.1f", anime.score) else null
    }
    val isAiring = remember(anime.status) { AnimeDateFormatter.isAiringStatus(anime.status) }
    val isUpcoming = remember(anime.status) { AnimeDateFormatter.isUpcomingStatus(anime.status) }
    val tbaLabel = stringResource(R.string.card_tba)
    val releaseDateText = remember(anime.aired, anime.season, anime.year, isUpcoming, locale, tbaLabel) {
        AnimeDateFormatter.formatReleaseDate(
            aired = anime.aired,
            season = anime.season,
            year = anime.year,
            locale = locale
        ) ?: if (isUpcoming) tbaLabel else null
    }

    // High-contrast dark-espresso pill colors derived strictly from M3 roles (contrast >= 9:1 over bright or dark posters)
    val pillBg = if (glass.isDark) {
        colorScheme.surfaceContainerLowest.copy(alpha = 0.82f)
    } else {
        colorScheme.inverseSurface.copy(alpha = 0.84f)
    }
    val pillFg = if (glass.isDark) {
        colorScheme.onSurface
    } else {
        colorScheme.inverseOnSurface
    }
    val starTint = if (glass.isDark) {
        colorScheme.tertiary
    } else {
        colorScheme.inversePrimary
    }

    val scoreCd = formattedScore?.let { stringResource(R.string.card_cd_score, it) }
    val episodesCd = if (anime.episodes > 0) stringResource(R.string.card_cd_episodes, anime.episodes) else null
    val dateCd = releaseDateText?.let { stringResource(R.string.card_cd_released, it) }
    val airingCd = if (isAiring) stringResource(R.string.card_cd_airing) else null
    val cardSemantics = remember(anime.title, scoreCd, episodesCd, dateCd, airingCd) {
        listOfNotNull(anime.title, airingCd, scoreCd, episodesCd, dateCd).joinToString(", ")
    }

    val coverRequest = rememberBoundedImageRequest(
        url = anime.coverUrl,
        widthDp = 172.dp,
        heightDp = 258.dp
    )

    val focusBorder = when {
        isFocused -> BorderStroke(2.dp, colorScheme.primary)
        isHovered -> BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.55f))
        else -> null
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.large)
            .then(if (focusBorder != null) Modifier.border(focusBorder, MaterialTheme.shapes.large) else Modifier)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .semantics(mergeDescendants = true) {
                contentDescription = cardSemantics
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colorScheme.primary),
                role = Role.Button,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.large)
                .background(colorScheme.surfaceContainerHigh)
                .border(
                    BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    MaterialTheme.shapes.large
                )
        ) {
            AsyncImage(
                model = coverRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Lightweight single-layer Score Pill (top-start)
            if (formattedScore != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(KanimeSpacing.sm)
                        .clip(PillShape)
                        .background(pillBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = starTint,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formattedScore,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = pillFg
                    )
                }
            }

            // Lightweight single-layer Episode Pill (bottom-end)
            if (anime.episodes > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(KanimeSpacing.sm)
                        .clip(PillShape)
                        .background(pillBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.card_ep_count, anime.episodes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = pillFg
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(KanimeSpacing.sm))

        // 2-line aligned title (minLines = 2 keeps row/grid baselines level; scales cleanly at 200% font scale)
        Text(
            text = anime.title,
            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // Quiet single-line status-aware release date
        if (releaseDateText != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isAiring) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                    )
                }
                Text(
                    text = releaseDateText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================
// Previews — Short title, Long title, Missing date, Upcoming, Airing,
// Light / Dark, and 200% Font Scale
// ============================================================================

@Composable
private fun AnimeCardGalleryPreviewBody() {
    val base = AnimeRepository.sampleAnimes.first()
    val items = listOf(
        base.copy(id = "short", title = "Frieren", aired = "Sep 29, 2023 to Mar 22, 2024", status = "Completed"),
        base.copy(
            id = "long",
            title = "That Time I Got Reincarnated as a Slime: Visions of Coleus",
            aired = "Oct 2024 to ?",
            status = "Airing"
        ),
        base.copy(id = "upcoming", title = "Dandadan Season 2", aired = null, season = "", year = 0, status = "Upcoming"),
        base.copy(id = "nodate", title = "Solo Leveling", aired = null, season = "", year = 0, status = "Completed")
    )
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items.take(2).forEach { anime ->
                AnimeCard(
                    anime = anime,
                    modifier = Modifier.width(148.dp),
                    onClick = {}
                )
            }
        }
    }
}

@Preview(name = "AnimeCard Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "AnimeCard Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "AnimeCard Font 2.0x Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 2.0f, device = Devices.PHONE)
@Composable
private fun AnimeCardVariantsPreview() {
    KAnimeTheme(animateTransition = false) {
        AnimeCardGalleryPreviewBody()
    }
}

@Preview(name = "AnimeCard Upcoming & Missing Date Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Composable
private fun AnimeCardUpcomingMissingPreview() {
    val base = AnimeRepository.sampleAnimes.first()
    KAnimeTheme(animateTransition = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimeCard(
                    anime = base.copy(id = "up", title = "Witch Hat Atelier", aired = null, season = "", year = 0, status = "Upcoming"),
                    modifier = Modifier.width(148.dp),
                    onClick = {}
                )
                AnimeCard(
                    anime = base.copy(id = "nd", title = "Unknown OVA", aired = "?", season = "", year = 0, status = "Completed"),
                    modifier = Modifier.width(148.dp),
                    onClick = {}
                )
            }
        }
    }
}
