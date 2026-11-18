package com.kanime.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp

/**
 * Centralized motion specs for consistent, cheap transitions across Kanime.
 */
object KanimeMotion {
    fun pressSpring() = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun dotSpring() = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun pageTween(reducedMotion: Boolean) = tween<Float>(
        durationMillis = if (reducedMotion) 0 else 360,
        easing = FastOutSlowInEasing
    )

    fun fadeInTween(reducedMotion: Boolean) = tween<Float>(
        durationMillis = if (reducedMotion) 0 else 180
    )

    fun fadeOutTween(reducedMotion: Boolean) = tween<Float>(
        durationMillis = if (reducedMotion) 0 else 140
    )
}

@Composable
fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}
