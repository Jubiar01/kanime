package com.kanime.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices 
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kanime.app.R
import com.kanime.app.data.api.EpisodeProgress
import com.kanime.app.data.model.Episode
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeGlass
import com.kanime.app.ui.theme.KanimeMotion
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.PillShape
import com.kanime.app.ui.theme.rememberBoundedImageRequest
import java.util.Locale

/**
 * Clean episode row card using M3 surfaceContainerLow with bounded-size Coil decoding,
 * lightweight play badge, and consistent locale-formatted release date.
 */
@Composable
fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: EpisodeProgress? = null
) {
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val glass = KanimeGlass.tokens
    val colorScheme = MaterialTheme.colorScheme

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = KanimeMotion.pressSpring(),
        label = "EpisodeCardScale"
    )

    val thumbRequest = rememberBoundedImageRequest(
        url = episode.thumbnail,
        widthDp = 120.dp,
        heightDp = 68.dp
    )

    val isWatched = progress?.isWatched == true
    val inProgress = progress?.isInProgress == true
    val minutesLeft = if (inProgress) ((progress?.remainingMs ?: 0L) / 60_000L).toInt().coerceAtLeast(1) else 0
    val minutesLeftText = if (inProgress) stringResource(R.string.episode_minutes_left, minutesLeft) else null

    val metaText = remember(episode.duration, episode.aired, locale, minutesLeftText) {
        buildList {
            if (minutesLeftText != null) add(minutesLeftText)
            else if (episode.duration.isNotBlank()) add(episode.duration)
            AnimeDateFormatter.formatEpisodeDate(episode.aired, locale)?.let { add(it) }
        }.joinToString(" · ")
    }

    val playBadgeBg = if (glass.isDark) {
        colorScheme.surfaceContainerLowest.copy(alpha = 0.78f)
    } else {
        colorScheme.inverseSurface.copy(alpha = 0.80f)
    }
    val playBadgeFg = if (glass.isDark) {
        colorScheme.onSurface
    } else {
        colorScheme.inverseOnSurface
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = colorScheme.primary),
                role = Role.Button,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KanimeSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
                    .background(colorScheme.surfaceContainerHighest)
            ) {
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Watched episodes are dimmed and get a check instead of the play badge
                if (isWatched) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorScheme.scrim.copy(alpha = 0.50f))
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isWatched) colorScheme.primaryContainer.copy(alpha = 0.92f) else playBadgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWatched) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = stringResource(if (isWatched) R.string.episode_watched else R.string.action_play),
                        tint = if (isWatched) colorScheme.onPrimaryContainer else playBadgeFg,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Thin progress bar along the bottom edge of the thumbnail
                if (inProgress && progress != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(colorScheme.scrim.copy(alpha = 0.45f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.fraction.coerceIn(0f, 1f))
                                .background(colorScheme.primary)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.episode_label, episode.episodeNumber),
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (episode.isFiller) {
                        Surface(
                            color = colorScheme.tertiaryContainer,
                            shape = PillShape
                        ) {
                            Text(
                                text = stringResource(R.string.badge_filler),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (episode.isRecap) {
                        Surface(
                            color = colorScheme.secondaryContainer,
                            shape = PillShape
                        ) {
                            Text(
                                text = stringResource(R.string.badge_recap),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isWatched) colorScheme.onSurfaceVariant else colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (metaText.isNotBlank()) {
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(name = "EpisodeCard Light", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "EpisodeCard Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Composable
private fun EpisodeCardPreview() {
    KAnimeTheme(animateTransition = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                EpisodeCard(
                    episode = Episode(
                        id = "ep_1",
                        episodeNumber = 1,
                        title = "The Journey's End",
                        duration = "24m",
                        thumbnail = "",
                        aired = "2023-09-29",
                        isFiller = true
                    ),
                    onClick = {}
                )
            }
        }
    }
}
