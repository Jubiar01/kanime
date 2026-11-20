package com.kanime.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs

/**
 * Soft top + bottom gradient scrims for the player HUD. Replaces the old flat 40% black wash so the
 * video stays visible in the middle while the glass controls keep their contrast at the edges.
 */
@Composable
fun PlayerScrims(
    modifier: Modifier = Modifier,
    locked: Boolean = false
) {
    val scrim = MaterialTheme.colorScheme.scrim
    val strength = if (locked) 0.08f else 1f
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to scrim.copy(alpha = 0.70f * strength),
                            0.28f to scrim.copy(alpha = 0.12f * strength),
                            0.50f to Color.Transparent,
                            0.66f to scrim.copy(alpha = 0.10f * strength),
                            1.00f to scrim.copy(alpha = 0.82f * strength)
                        )
                    )
                )
            }
    )
}

/**
 * Round icon button for the HUD. Sits flat on glass (no extra container) and gets a soft primary
 * tint when [active]. Always at least 44dp wide for comfortable touch.
 */
@Composable
fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    active: Boolean = false,
    enabled: Boolean = true,
    tint: Color? = null
) {
    val scheme = MaterialTheme.colorScheme
    val targetTint = when {
        !enabled -> scheme.onSurface.copy(alpha = 0.32f)
        tint != null -> tint
        active -> scheme.primary
        else -> scheme.onSurface
    }
    val targetBg = if (active && enabled) scheme.primary.copy(alpha = 0.22f) else Color.Transparent
    val resolvedTint by animateColorAsState(targetTint, label = "PlayerIconTint")
    val background by animateColorAsState(targetBg, label = "PlayerIconBg")

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "PlayerIconPress"
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = resolvedTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Seek bar with elapsed / remaining time built in (custom-drawn, so it does not depend on the Slider API).
 *
 *  - Only seeks when the finger is lifted (no stuttering through every drag step on network streams).
 *  - While scrubbing, the left time follows the thumb and the right label shows how far you are
 *    jumping (+0:23 / −1:05). A soft haptic tick marks grab and release.
 *  - Tap the right-hand time to switch between total duration and time remaining.
 *  - [bufferedProvider] draws how far the stream has buffered as a faint layer under the progress.
 *  - [segments] (intro / recap / outro in ms) are drawn as lighter marks on the track.
 *  - Exposes progress semantics so TalkBack can adjust it.
 */
@Composable
fun PlayerSeekBar(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    segments: List<LongRange> = emptyList(),
    bufferedProvider: () -> Long = { 0L }
) {
    val duration = durationProvider().coerceAtLeast(0L)
    val position = positionProvider().coerceAtLeast(0L)
    val buffered = bufferedProvider().coerceAtLeast(0L)

    var scrub by remember { mutableStateOf<Float?>(null) }
    var showRemaining by remember { mutableStateOf(false) }
    val active = scrub != null
    val haptic = LocalHapticFeedback.current

    val fraction = scrub ?: if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0L) (buffered.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    val shownMs = if (duration > 0L) (fraction * duration).toLong() else 0L
    val deltaMs = if (active) shownMs - position else 0L

    val thumbSize by animateDpAsState(
        targetValue = if (active) 20.dp else 14.dp,
        animationSpec = spring(),
        label = "SeekThumb"
    )
    val trackHeight by animateDpAsState(
        targetValue = if (active) 6.dp else 4.dp,
        animationSpec = spring(),
        label = "SeekTrack"
    )

    val latestDuration by rememberUpdatedState(duration)
    val latestOnSeek by rememberUpdatedState(onSeek)

    val primary = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
    val bufferedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
    val segmentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val onSurface = MaterialTheme.colorScheme.onSurface

    val rightText = when {
        active -> (if (deltaMs >= 0L) "+" else "\u2212") + formatPlayerTime(abs(deltaMs))
        showRemaining -> "\u2212" + formatPlayerTime((duration - shownMs).coerceAtLeast(0L))
        else -> formatPlayerTime(duration)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatPlayerTime(shownMs),
            color = if (active) primary else onSurface,
            style = timeStyle,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 40.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
                .height(40.dp)
                .semantics {
                    contentDescription = "Seek bar"
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { target ->
                        if (latestDuration > 0L) {
                            latestOnSeek((target.coerceIn(0f, 1f) * latestDuration).toLong())
                            true
                        } else {
                            false
                        }
                    }
                }
                .pointerInput(Unit) {
                    val inset = 10.dp.toPx()
                    fun fractionAt(x: Float): Float {
                        val usable = (size.width - 2f * inset).coerceAtLeast(1f)
                        return ((x - inset) / usable).coerceIn(0f, 1f)
                    }
                    detectTapGestures(
                        onTap = { offset ->
                            if (latestDuration > 0L) {
                                latestOnSeek((fractionAt(offset.x) * latestDuration).toLong())
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    val inset = 10.dp.toPx()
                    fun fractionAt(x: Float): Float {
                        val usable = (size.width - 2f * inset).coerceAtLeast(1f)
                        return ((x - inset) / usable).coerceIn(0f, 1f)
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scrub = fractionAt(offset.x)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            scrub = fractionAt(change.position.x)
                        },
                        onDragEnd = {
                            val target = scrub
                            if (target != null && latestDuration > 0L) {
                                latestOnSeek((target * latestDuration).toLong())
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scrub = null
                        },
                        onDragCancel = { scrub = null }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val inset = 10.dp.toPx()
                val usable = (size.width - 2f * inset).coerceAtLeast(1f)
                val h = trackHeight.toPx()
                val top = (size.height - h) / 2f
                val radius = CornerRadius(h / 2f, h / 2f)

                drawRoundRect(
                    color = inactive,
                    topLeft = Offset(inset, top),
                    size = Size(usable, h),
                    cornerRadius = radius
                )
                if (bufferedFraction > 0f) {
                    drawRoundRect(
                        color = bufferedColor,
                        topLeft = Offset(inset, top),
                        size = Size(usable * bufferedFraction, h),
                        cornerRadius = radius
                    )
                }
                if (duration > 0L) {
                    segments.forEach { range ->
                        val s = (range.first.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        val e = (range.last.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        if (e > s) {
                            drawRoundRect(
                                color = segmentColor,
                                topLeft = Offset(inset + usable * s, top),
                                size = Size(usable * (e - s), h),
                                cornerRadius = radius
                            )
                        }
                    }
                }
                if (fraction > 0f) {
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(inset, top),
                        size = Size(usable * fraction, h),
                        cornerRadius = radius
                    )
                }
                val thumbCenter = Offset(inset + usable * fraction, size.height / 2f)
                if (active) {
                    drawCircle(
                        color = primary.copy(alpha = 0.22f),
                        radius = thumbSize.toPx() * 0.9f,
                        center = thumbCenter
                    )
                }
                drawCircle(
                    color = primary,
                    radius = thumbSize.toPx() / 2f,
                    center = thumbCenter
                )
            }
        }

        Text(
            text = rightText,
            color = if (active) primary else onSurface.copy(alpha = 0.72f),
            style = timeStyle,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 44.dp)
                .clip(CircleShape)
                .clickable(enabled = !active, role = Role.Button) { showRemaining = !showRemaining }
                .padding(horizontal = 4.dp, vertical = 6.dp)
        )
    }
}

/** 05:07 for short videos, 1:05:07 once past an hour. */
fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
