package com.kanime.app.ui.player.anime

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.util.Log

enum class GpuTier(val label: String) {
    LOW("Low / Efficiency GPU"),
    MEDIUM("Mid-High GPU"),
    HIGH("Flagship GPU"),
}

/**
 * Classifies the Android device's GPU tier from its live GL_RENDERER string and memory class
 * so Anime4K automatically targets smooth, lag-free FPS on any phone.
 */
internal object DeviceGpuProfiler {
    private const val TAG = "DeviceGpuProfiler"

    @Volatile
    private var cachedRenderer: String? = null

    @Volatile
    private var cachedTier: GpuTier? = null

    fun detectCurrentGlTier(context: Context): GpuTier {
        val renderer = runCatching { GLES20.glGetString(GLES20.GL_RENDERER).orEmpty() }.getOrDefault("")
        if (renderer.isNotBlank() && renderer == cachedRenderer && cachedTier != null) {
            return cachedTier!!
        }
        val isLowRam = isLowMemoryDevice(context)
        val tier = classify(renderer = renderer, isLowRam = isLowRam)
        if (renderer.isNotBlank()) {
            cachedRenderer = renderer
            cachedTier = tier
            Log.i(TAG, "Detected GPU '$renderer' (lowRam=$isLowRam) -> $tier")
        }
        return tier
    }

    fun classify(renderer: String, isLowRam: Boolean = false): GpuTier {
        if (isLowRam) return GpuTier.LOW
        val r = renderer.lowercase()
        if (r.isBlank()) return GpuTier.MEDIUM

        // Qualcomm Adreno
        val adrenoMatch = Regex("adreno[^0-9]*(\\d{3})").find(r)
        if (adrenoMatch != null) {
            val num = adrenoMatch.groupValues[1].toIntOrNull() ?: 600
            return when {
                num >= 740 -> GpuTier.HIGH       // 740 (8 Gen 2), 750 (8 Gen 3), 830 (8 Elite)
                num >= 650 -> GpuTier.MEDIUM     // 650/660/680/710/720/725/730/732/735
                else -> GpuTier.LOW              // 5xx, 610-642
            }
        }

        // ARM Immortalis (ray-tracing flagship Mali)
        if ("immortalis" in r) {
            val gMatch = Regex("g(\\d{3})").find(r)
            val num = gMatch?.groupValues?.get(1)?.toIntOrNull() ?: 715
            return if (num >= 720) GpuTier.HIGH else GpuTier.MEDIUM
        }

        // ARM Mali
        val maliGMatch = Regex("mali-g(\\d{2,3})").find(r)
        if (maliGMatch != null) {
            val num = maliGMatch.groupValues[1].toIntOrNull() ?: 57
            return when {
                num >= 720 -> GpuTier.HIGH
                num >= 610 || num in 77..79 -> GpuTier.MEDIUM  // G610, G615, G710, G715, G77, G78
                else -> GpuTier.LOW                             // G51, G52, G57, G68, G72, G76
            }
        }
        if ("mali-t" in r || "mali-4" in r) return GpuTier.LOW

        // Samsung Xclipse (AMD RDNA mobile)
        val xclipseMatch = Regex("xclipse\\s*(\\d{3})").find(r)
        if (xclipseMatch != null) {
            val num = xclipseMatch.groupValues[1].toIntOrNull() ?: 920
            return if (num >= 940) GpuTier.HIGH else GpuTier.MEDIUM
        }

        // Huawei Maleoon
        val maleoonMatch = Regex("maleoon\\s*(\\d{3})").find(r)
        if (maleoonMatch != null) {
            val num = maleoonMatch.groupValues[1].toIntOrNull() ?: 910
            return if (num >= 920) GpuTier.HIGH else GpuTier.MEDIUM
        }

        // PowerVR / IMG / Software emulators
        if ("powervr" in r || "rogue" in r || "swiftshader" in r || "llvmpipe" in r) {
            return GpuTier.LOW
        }

        // Desktop / Apple Silicon / High-end unknown
        if ("nvidia" in r || "radeon" in r || "geforce" in r || "apple" in r) {
            return GpuTier.HIGH
        }

        return GpuTier.MEDIUM
    }

    fun maxUpscaleTarget(tier: GpuTier): Pair<Int, Int> = when (tier) {
        GpuTier.LOW -> 1920 to 1080
        GpuTier.MEDIUM -> AnimeShaders.MINIMUM_UPSCALE_LONG_SIDE to AnimeShaders.MINIMUM_UPSCALE_SHORT_SIDE
        GpuTier.HIGH -> AnimeShaders.MINIMUM_UPSCALE_LONG_SIDE to AnimeShaders.MINIMUM_UPSCALE_SHORT_SIDE
    }

    private fun isLowMemoryDevice(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        if (am.isLowRamDevice) return true
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val totalGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        totalGb < 4.5
    }.getOrDefault(false)
}
