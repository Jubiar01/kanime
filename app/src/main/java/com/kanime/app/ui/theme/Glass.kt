package com.kanime.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mode-aware liquid glass tokens derived strictly from Material 3 [ColorScheme] roles.
 * Zero hardcoded hex colors.
 */
@Immutable
data class KanimeGlassTokens(
    val isDark: Boolean,
    val fill: Color,
    val tint: Color,
    val fallbackFill: Color,
    val overlayFill: Color,
    val contentColor: Color,
    val secondaryContentColor: Color,
    val borderStart: Color,
    val borderEnd: Color,
    val topHighlight: Color,
    val shadowColor: Color,
    val shadowElevation: Dp,
    val shadowOffsetY: Dp,
    val blurRadius: Dp,
    val ambientTop: Color,
    val ambientBottom: Color,
    val ambientGlowPrimary: Color,
    val ambientGlowSecondary: Color,
    val posterScrimTop: Color,
    val posterScrimMid: Color,
    val posterScrimBottom: Color,
    val sheetScrim: Color,
    val reducedTransparency: Boolean
) {
    val useRealBlur: Boolean
        get() = !reducedTransparency && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val borderBrush: Brush
        get() = Brush.linearGradient(
            colors = listOf(
                borderStart,
                borderEnd,
                borderStart.copy(alpha = borderStart.alpha * 0.38f)
            )
        )
}

fun kanimeGlassTokensFor(
    colorScheme: ColorScheme,
    isDark: Boolean,
    reducedTransparency: Boolean = false
): KanimeGlassTokens {
    return if (isDark) {
        KanimeGlassTokens(
            isDark = true,
            // Dark glass: translucent surfaceContainerHigh + faint onSurface sheen
            fill = colorScheme.surfaceContainerHigh.copy(alpha = 0.62f),
            tint = colorScheme.onSurface.copy(alpha = 0.10f),
            fallbackFill = colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            overlayFill = colorScheme.surfaceContainerLowest.copy(alpha = 0.72f),
            contentColor = colorScheme.onSurface,
            secondaryContentColor = colorScheme.onSurfaceVariant,
            borderStart = colorScheme.onSurface.copy(alpha = 0.35f),
            borderEnd = colorScheme.onSurface.copy(alpha = 0.05f),
            topHighlight = colorScheme.onSurface.copy(alpha = 0.20f),
            shadowColor = colorScheme.scrim.copy(alpha = 0.34f),
            shadowElevation = 10.dp,
            shadowOffsetY = 4.dp,
            blurRadius = 28.dp,
            ambientTop = colorScheme.background,
            ambientBottom = colorScheme.surfaceContainerLowest,
            ambientGlowPrimary = colorScheme.primary.copy(alpha = 0.12f),
            ambientGlowSecondary = colorScheme.primaryContainer.copy(alpha = 0.18f),
            posterScrimTop = colorScheme.background.copy(alpha = 0.36f),
            posterScrimMid = colorScheme.background.copy(alpha = 0.42f),
            posterScrimBottom = colorScheme.background.copy(alpha = 0.94f),
            sheetScrim = colorScheme.scrim.copy(alpha = 0.60f),
            reducedTransparency = reducedTransparency
        )
    } else {
        KanimeGlassTokens(
            isDark = false,
            // Light glass: warm frosted white with subtle coffee container tint so it never looks flat
            fill = colorScheme.surfaceContainerLowest.copy(alpha = 0.68f),
            tint = colorScheme.primaryContainer.copy(alpha = 0.22f),
            fallbackFill = colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
            overlayFill = colorScheme.surfaceContainerLowest.copy(alpha = 0.84f),
            contentColor = colorScheme.onSurface,
            secondaryContentColor = colorScheme.onSurfaceVariant,
            borderStart = colorScheme.surfaceContainerLowest,
            borderEnd = colorScheme.primary.copy(alpha = 0.14f),
            topHighlight = colorScheme.surfaceContainerLowest.copy(alpha = 0.92f),
            // Warm coffee-tinted shadow — never gray or black
            shadowColor = colorScheme.primary.copy(alpha = 0.13f),
            shadowElevation = 14.dp,
            shadowOffsetY = 5.dp,
            blurRadius = 32.dp,
            ambientTop = colorScheme.background,
            ambientBottom = colorScheme.surfaceContainerLow,
            ambientGlowPrimary = colorScheme.primaryContainer.copy(alpha = 0.48f),
            ambientGlowSecondary = colorScheme.secondaryContainer.copy(alpha = 0.32f),
            posterScrimTop = colorScheme.scrim.copy(alpha = 0.24f),
            posterScrimMid = colorScheme.scrim.copy(alpha = 0.34f),
            posterScrimBottom = colorScheme.background.copy(alpha = 0.96f),
            sheetScrim = colorScheme.scrim.copy(alpha = 0.36f),
            reducedTransparency = reducedTransparency
        )
    }
}

val LocalKanimeGlass = compositionLocalOf {
    kanimeGlassTokensFor(
        colorScheme = DarkKanimeColorScheme,
        isDark = true,
        reducedTransparency = false
    )
}

object KanimeGlass {
    val tokens: KanimeGlassTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalKanimeGlass.current
}

/**
 * Detects reduced-transparency / low-end / power-save conditions so glass layers
 * gracefully degrade to crisp semi-transparent M3 surfaces without blur overhead.
 */
@Composable
fun rememberReducedTransparency(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        isReducedTransparencyEnabled(context)
    }
}

private fun isReducedTransparencyEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val lowRam = am?.isLowRamDevice == true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val powerSave = pm?.isPowerSaveMode == true
        val reduceTransparency = Settings.Secure.getInt(
            context.contentResolver,
            "accessibility_reduce_transparency",
            0
        ) == 1
        val highTextContrast = Settings.Secure.getInt(
            context.contentResolver,
            "high_text_contrast_enabled",
            0
        ) == 1
        lowRam || powerSave || reduceTransparency || highTextContrast
    } catch (_: Exception) {
        false
    }
}

/**
 * Subtle warm ambient backdrop behind the app shell so floating glass layers
 * always have soft tonal warmth to refract without muddying content.
 * Uses pre-softened multi-stop radial gradients cached in [drawWithCache]
 * (zero full-screen RenderEffect blur pass, zero scroll recomposition).
 */
@Composable
fun KanimeAmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val glass = KanimeGlass.tokens
    val transparentBg = MaterialTheme.colorScheme.background.copy(alpha = 0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val w = size.width
                val h = size.height
                val baseBrush = Brush.verticalGradient(
                    colors = listOf(glass.ambientTop, glass.ambientBottom)
                )
                val topCenter = Offset(w * 0.16f, h * 0.08f)
                val topRadius = (w.coerceAtLeast(h) * 0.62f).coerceAtLeast(1f)
                val topGlow = Brush.radialGradient(
                    0.0f to glass.ambientGlowPrimary,
                    0.45f to glass.ambientGlowPrimary.copy(alpha = glass.ambientGlowPrimary.alpha * 0.42f),
                    1.0f to transparentBg,
                    center = topCenter,
                    radius = topRadius
                )
                val bottomCenter = Offset(w * 0.88f, h * 0.90f)
                val bottomRadius = (w.coerceAtLeast(h) * 0.56f).coerceAtLeast(1f)
                val bottomGlow = Brush.radialGradient(
                    0.0f to glass.ambientGlowSecondary,
                    0.50f to glass.ambientGlowSecondary.copy(alpha = glass.ambientGlowSecondary.alpha * 0.38f),
                    1.0f to transparentBg,
                    center = bottomCenter,
                    radius = bottomRadius
                )
                val midCenter = Offset(w * 0.74f, h * 0.42f)
                val midRadius = (w.coerceAtLeast(h) * 0.34f).coerceAtLeast(1f)
                val midGlow = Brush.radialGradient(
                    0.0f to glass.ambientGlowPrimary.copy(alpha = glass.ambientGlowPrimary.alpha * 0.55f),
                    1.0f to transparentBg,
                    center = midCenter,
                    radius = midRadius
                )
                onDrawBehind {
                    drawRect(brush = baseBrush)
                    drawCircle(brush = topGlow, radius = topRadius, center = topCenter)
                    drawCircle(brush = midGlow, radius = midRadius, center = midCenter)
                    drawCircle(brush = bottomGlow, radius = bottomRadius, center = bottomCenter)
                }
            },
        content = content
    )
}
