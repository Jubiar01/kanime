package com.kanime.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape system tuned for minimalist liquid glass surfaces.
 */
val KanimeShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val PillShape = CircleShape

/**
 * Consistent 4/8dp spatial grid tokens.
 */
object KanimeSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    val minTouchTarget = 48.dp
    val floatingBarClearance = 104.dp
    val maxContentWidth = 1120.dp
}
