package com.kanime.app.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kanime.app.ui.theme.KAnimeTheme
import com.kanime.app.ui.theme.KanimeAmbientBackdrop
import com.kanime.app.ui.theme.KanimeGlass
import com.kanime.app.ui.theme.KanimeSpacing
import com.kanime.app.ui.theme.PillShape

/**
 * Reusable liquid glass container derived strictly from Material 3 colorScheme roles
 * via [KanimeGlass]. Uses frosted blur on API 31+ and crisp semi-transparent M3
 * surfaceContainer fallback on API 24–30 or when reduced transparency is active.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    overMedia: Boolean = false,
    accentTint: Color? = null,
    showShadow: Boolean = true,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    contentColor: Color = KanimeGlass.tokens.contentColor,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = KanimeGlass.tokens
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (onClick != null && isPressed && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "GlassPressScale"
    )

    val baseFill = when {
        overMedia -> glass.overlayFill
        glass.useRealBlur -> glass.fill
        else -> glass.fallbackFill
    }

    val shadowMod = if (showShadow) {
        Modifier.shadow(
            elevation = glass.shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = glass.shadowColor,
            spotColor = glass.shadowColor
        )
    } else {
        Modifier
    }

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = MaterialTheme.colorScheme.primary),
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(shadowMod)
            .clip(shape)
            .then(clickMod),
        contentAlignment = Alignment.Center
    ) {
        // Layer 1: Translucent frosted backdrop (blurred on API 31+ when transparency is enabled)
        val frostModifier = Modifier
            .matchParentSize()
            .background(baseFill)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accentTint ?: glass.tint,
                        (accentTint ?: glass.tint).copy(alpha = 0.25f)
                    )
                )
            )
            .let { mod ->
                if (glass.useRealBlur && !overMedia) {
                    mod.blur(glass.blurRadius)
                } else {
                    mod
                }
            }
        Box(modifier = frostModifier)

        // Layer 2: Soft top-edge inner specular highlight + 1dp directional border
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = glass.topHighlight,
                        start = Offset(size.width * 0.12f, 1.dp.toPx()),
                        end = Offset(size.width * 0.88f, 1.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .border(
                    border = BorderStroke(1.dp, glass.borderBrush),
                    shape = shape
                )
        )

        // Layer 3: Crisp foreground content
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

enum class GlassButtonStyle {
    Primary,
    Tonal,
    Glass
}

/**
 * Pill-shaped button with spring press animation and 48dp minimum touch target.
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: GlassButtonStyle = GlassButtonStyle.Glass,
    overMedia: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ButtonScale"
    )

    when (style) {
        GlassButtonStyle.Primary -> {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = PillShape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = modifier
                    .heightIn(min = KanimeSpacing.minTouchTarget)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        GlassButtonStyle.Tonal -> {
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                shape = PillShape,
                interactionSource = interactionSource,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                modifier = modifier
                    .heightIn(min = KanimeSpacing.minTouchTarget)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        GlassButtonStyle.Glass -> {
            GlassSurface(
                modifier = modifier.heightIn(min = KanimeSpacing.minTouchTarget),
                shape = PillShape,
                overMedia = overMedia,
                onClick = onClick,
                enabled = enabled,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Circular floating glass icon button with 48dp accessible touch target.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overMedia: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 48.dp
) {
    val contentTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val accent = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else null

    GlassSurface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(size),
        shape = CircleShape,
        overMedia = overMedia,
        accentTint = accent,
        onClick = onClick,
        enabled = enabled,
        contentColor = contentTint
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Liquid glass pill chip for filters, categories, and segmented selections.
 */
@Composable
fun GlassChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    overMedia: Boolean = false
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    if (selected) {
        Surface(
            onClick = onClick,
            shape = PillShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = contentColor,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
            modifier = modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 38.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        GlassSurface(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = 38.dp),
            shape = PillShape,
            overMedia = overMedia,
            showShadow = false,
            onClick = onClick,
            contentColor = contentColor
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Floating translucent glass top bar respecting status bar and cutout insets.
 */
@Composable
fun GlassTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = KanimeSpacing.lg, vertical = KanimeSpacing.sm)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            shape = PillShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = KanimeSpacing.sm, vertical = KanimeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (navigationIcon != null) {
                        navigationIcon()
                        Spacer(modifier = Modifier.width(4.dp))
                    } else {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    title()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    content = actions
                )
            }
        }
    }
}

data class GlassNavItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

/**
 * Floating pill-shaped liquid glass bottom navigation bar for Compact screens.
 * Clear primary-colored active pill with minimal text clutter.
 */
@Composable
fun GlassBottomBar(
    items: List<GlassNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = KanimeSpacing.lg, vertical = KanimeSpacing.md),
        contentAlignment = Alignment.Center
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            shape = PillShape
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val isSelected = item.id == selectedId
                    val itemInteraction = remember { MutableInteractionSource() }
                    val isPressed by itemInteraction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.94f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "NavItemScale"
                    )

                    val pillBg = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }
                    val iconTint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .weight(if (isSelected) 1.35f else 1f)
                            .heightIn(min = KanimeSpacing.minTouchTarget)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .clip(PillShape)
                            .background(pillBg)
                            .clickable(
                                interactionSource = itemInteraction,
                                indication = ripple(color = MaterialTheme.colorScheme.primary),
                                role = Role.Tab,
                                onClick = { onSelect(item.id) }
                            )
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (item.badgeCount > 0 && !isSelected) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = if (item.badgeCount > 99) "99+" else "${item.badgeCount}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = iconTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            if (isSelected) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Floating vertical liquid glass navigation rail for Medium / Expanded screens & landscape.
 */
@Composable
fun GlassNavigationRail(
    items: List<GlassNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = KanimeSpacing.md, top = KanimeSpacing.md, bottom = KanimeSpacing.md)
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxHeight()
                .width(76.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = KanimeSpacing.lg, horizontal = KanimeSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                if (header != null) {
                    header()
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items.forEach { item ->
                        val isSelected = item.id == selectedId
                        val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        val tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(bg)
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { onSelect(item.id) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (item.badgeCount > 0 && !isSelected) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = if (item.badgeCount > 99) "99+" else "${item.badgeCount}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (footer != null) {
                    footer()
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * Liquid glass modal sheet overlay with scrim dismissal and drag handle.
 */
@Composable
fun GlassSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    maxHeightFraction: Float = 0.80f,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = KanimeGlass.tokens
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(glass.sheetScrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            GlassSurface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(maxHeightFraction),
                shape = sheetShape
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 12.dp, bottom = 8.dp)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(PillShape)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    content()
                }
            }
        }
    }
}

/**
 * Theme-adaptive shimmer skeleton using Material 3 surfaceContainer* roles.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "KanimeShimmer")
    val translate by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerOffset"
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + 280f, 180f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

@Composable
fun AnimeCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(14.dp),
            shape = PillShape
        )
        Spacer(modifier = Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .height(11.dp),
            shape = PillShape
        )
    }
}

@Composable
fun HeroBannerSkeleton(modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp),
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * Minimal friendly empty or error state with a single clear action.
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = KanimeSpacing.xl, vertical = KanimeSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        GlassSurface(
            modifier = Modifier.size(68.dp),
            shape = CircleShape,
            showShadow = false
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(KanimeSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(KanimeSpacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(KanimeSpacing.lg))
            GlassButton(
                text = actionLabel,
                onClick = onAction,
                style = GlassButtonStyle.Primary
            )
        }
    }
}

// ============================================================================
// Previews — Light, Dark, Phone & Tablet
// ============================================================================

@Composable
private fun GlassKitPreviewBody() {
    val navItems = listOf(
        GlassNavItem("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        GlassNavItem("explore", "Explore", Icons.Filled.Explore, Icons.Outlined.Explore),
        GlassNavItem("library", "Library", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder, badgeCount = 4),
        GlassNavItem("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    )
    KanimeAmbientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassTopBar(
                title = {
                    Text(
                        text = "Kanime",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(label = "All", selected = true, onClick = {})
                GlassChip(label = "Airing", selected = false, onClick = {})
                GlassChip(label = "Action", selected = false, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton(text = "Play", icon = Icons.Default.PlayArrow, style = GlassButtonStyle.Primary, onClick = {})
                GlassButton(text = "Save", style = GlassButtonStyle.Glass, onClick = {})
            }
            Spacer(modifier = Modifier.weight(1f))
            GlassBottomBar(items = navItems, selectedId = "home", onSelect = {})
        }
    }
}

@Preview(name = "Glass Kit Light Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.PHONE)
@Preview(name = "Glass Kit Dark Phone", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PHONE)
@Preview(name = "Glass Kit Light Tablet", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO, device = Devices.TABLET)
@Composable
private fun GlassKitPreview() {
    KAnimeTheme(animateTransition = false) {
        GlassKitPreviewBody()
    }
}
