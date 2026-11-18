package com.kanime.app.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Configures a single Coil 3 ImageLoader with bounded memory + disk caches
 * so images never trigger repeated full-resolution decodes or cache thrashing.
 */
object KanimeImageLoader {
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appCtx = context.applicationContext
            SingletonImageLoader.setSafe {
                ImageLoader.Builder(appCtx)
                    .memoryCache {
                        MemoryCache.Builder()
                            .maxSizePercent(appCtx, 0.20)
                            .build()
                    }
                    .diskCache {
                        DiskCache.Builder()
                            .directory(File(appCtx.cacheDir, "kanime_image_cache").toOkioPath())
                            .maxSizeBytes(100L * 1024L * 1024L)
                            .build()
                    }
                    .crossfade(200)
                    .build()
            }
            initialized = true
        }
    }
}

/**
 * Builds a remembered Coil [ImageRequest] bounded to target card/banner pixel dimensions
 * with a stable memory-cache key so full-resolution source images are downsampled on decode.
 */
@Composable
fun rememberBoundedImageRequest(
    url: String?,
    widthDp: Dp,
    heightDp: Dp
): ImageRequest {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { widthDp.roundToPx().coerceAtLeast(64) }
    val heightPx = with(density) { heightDp.roundToPx().coerceAtLeast(64) }
    return remember(url, widthPx, heightPx) {
        ImageRequest.Builder(context)
            .data(url)
            .size(widthPx, heightPx)
            .memoryCacheKey(url?.takeIf { it.isNotBlank() }?.let { "${it}_${widthPx}x${heightPx}" })
            .crossfade(180)
            .build()
    }
}
