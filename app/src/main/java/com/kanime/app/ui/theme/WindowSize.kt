package com.kanime.app.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

enum class KanimeWindowWidthSizeClass {
    Compact,  // < 600dp (phones in portrait)
    Medium,   // 600..839dp (foldables unfolded, small tablets, phones in landscape)
    Expanded  // >= 840dp (tablets, large landscape)
}

@Immutable
data class KanimeWindowSize(
    val widthSizeClass: KanimeWindowWidthSizeClass,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val isLandscape: Boolean
) {
    val useNavigationRail: Boolean
        get() = widthSizeClass != KanimeWindowWidthSizeClass.Compact || isLandscape

    val useTwoPaneDetail: Boolean
        get() = widthSizeClass != KanimeWindowWidthSizeClass.Compact
}

val LocalWindowSizeClass = compositionLocalOf {
    KanimeWindowSize(
        widthSizeClass = KanimeWindowWidthSizeClass.Compact,
        screenWidthDp = 390,
        screenHeightDp = 844,
        isLandscape = false
    )
}

@Composable
fun rememberKanimeWindowSize(): KanimeWindowSize {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE || widthDp > heightDp
    val widthClass = when {
        widthDp < 600 -> KanimeWindowWidthSizeClass.Compact
        widthDp < 840 -> KanimeWindowWidthSizeClass.Medium
        else -> KanimeWindowWidthSizeClass.Expanded
    }
    return KanimeWindowSize(
        widthSizeClass = widthClass,
        screenWidthDp = widthDp,
        screenHeightDp = heightDp,
        isLandscape = isLandscape
    )
}
