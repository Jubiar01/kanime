package com.kanime.app.ui.player.anime

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class AnimeUpscaleQuality(
    val id: String,
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    AUTO(
        id = "auto",
        label = "Auto (Smooth FPS)",
        shortLabel = "Auto",
        description = "Adapts to your phone's GPU & FPS live to prevent lag",
    ),
    FAST(
        id = "fast",
        label = "Fast (60fps)",
        shortLabel = "Fast",
        description = "Lightweight CNN-S + fast sharpening — 60+ FPS & low battery",
    ),
    HIGH(
        id = "high",
        label = "Balanced (HQ)",
        shortLabel = "HQ",
        description = "Deep CNN-M 2x + highlight clamp — crisp & smooth 30–60 FPS",
    ),
    ULTRA(
        id = "ultra",
        label = "Ultra (Denoise M)",
        shortLabel = "M+",
        description = "Deep CNN-M 2x + stream denoiser + highlight clamp",
    );

    companion object {
        fun fromId(id: String?): AnimeUpscaleQuality =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
    }
}

enum class AnimeSharpenStrength(
    val id: String,
    val label: String,
    val strength: Float,
    val darkOvershoot: Float,
    val brightOvershoot: Float,
    val lineDarken: Float,
) {
    SUBTLE(
        id = "subtle",
        label = "Subtle",
        strength = 2.6f,
        darkOvershoot = 0.18f,
        brightOvershoot = 0.08f,
        lineDarken = 0.08f,
    ),
    BALANCED(
        id = "balanced",
        label = "Balanced",
        strength = 4.2f,
        darkOvershoot = 0.28f,
        brightOvershoot = 0.12f,
        lineDarken = 0.14f,
    ),
    CRISP(
        id = "crisp",
        label = "Crisp",
        strength = 6.2f,
        darkOvershoot = 0.38f,
        brightOvershoot = 0.16f,
        lineDarken = 0.22f,
    );

    companion object {
        fun fromId(id: String?): AnimeSharpenStrength =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCED
    }
}

internal enum class EffectiveShaderTier(val badge: String) {
    ULTRA("M+"),
    HIGH("HQ"),
    FAST("Fast"),
    ULTRA_LIGHT("Lite"),
}

internal data class ShaderTierCandidate(
    val tier: EffectiveShaderTier,
    val label: String,
    val ordered: List<MpvUserShaderPass>,
    val sharpen: List<MpvUserShaderPass>,
    val upscale: List<MpvUserShaderPass>,
    val maxTargetLongSide: Int,
    val maxTargetShortSide: Int,
)

/**
 * Anime4K v4 shader pipeline manager supporting both Media3 ExoPlayer (OpenGL ES 3.0 multi-pass
 * via [exoVideoEffects]) and libmpv (via [mpvShaderPaths] & [mpvStatus]).
 *
 * Performance & adaptive FPS features:
 * - [AnimeUpscaleQuality.AUTO] detects the device's GPU tier (Low / Mid / Flagship) and stream
 *   resolution (720p vs 1080p) to select a smooth real-time shader ladder, and steps down
 *   seamlessly in-place via non-blocking OpenGL ES 3.0 GPU fences if frames ever fall behind.
 * - Native-resolution adaptive line sharpening runs before 2x upscaling (4x fewer pixel fetches
 *   on 1080p) so CNN 2x cleanly super-samples sharpened ink lines at high FPS.
 */
internal object AnimeShaders {
    private const val TAG = "AnimeShaders"
    private const val ASSET_DIR = "anime4k"
    private const val CLAMP_HIGHLIGHTS_SHADER = "Anime4K_Clamp_Highlights.glsl"
    private const val RESTORE_S_SHADER = "Anime4K_Restore_CNN_S.glsl"
    private const val RESTORE_M_SHADER = "Anime4K_Restore_CNN_M.glsl"
    private const val DARKEN_SHADER = "Anime4K_Darken_HQ.glsl"
    private const val THIN_SHADER = "Anime4K_Thin_HQ.glsl"
    private const val UPSCALE_S_SHADER = "Anime4K_Upscale_CNN_x2_S.glsl"
    private const val UPSCALE_M_SHADER = "Anime4K_Upscale_CNN_x2_M.glsl"
    private const val UPSCALE_DENOISE_M_SHADER = "Anime4K_Upscale_Denoise_CNN_x2_M.glsl"
    private const val FINAL_SHARPEN_SHADER = "Nuvio_Line_Sharpen.glsl"
    private const val UPSCALE_CONDITION = "//!WHEN OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *"
    const val MINIMUM_UPSCALE_LONG_SIDE = 2560
    const val MINIMUM_UPSCALE_SHORT_SIDE = 1440

    private val UPSCALE_SHADERS = setOf(UPSCALE_S_SHADER, UPSCALE_M_SHADER, UPSCALE_DENOISE_M_SHADER)

    fun shaderNames(
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        quality: AnimeUpscaleQuality = AnimeUpscaleQuality.AUTO,
        highResInput: Boolean = false,
    ): List<String> {
        val tier = when (quality) {
            AnimeUpscaleQuality.AUTO -> EffectiveShaderTier.HIGH
            AnimeUpscaleQuality.FAST -> EffectiveShaderTier.FAST
            AnimeUpscaleQuality.HIGH -> EffectiveShaderTier.HIGH
            AnimeUpscaleQuality.ULTRA -> EffectiveShaderTier.ULTRA
        }
        return shaderNamesForTier(
            upscaleEnabled = upscaleEnabled,
            lineSharpenEnabled = lineSharpenEnabled,
            tier = tier,
            highResInput = highResInput,
        )
    }

    internal fun shaderNamesForTier(
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        tier: EffectiveShaderTier,
        highResInput: Boolean,
    ): List<String> = buildList {
        if (!upscaleEnabled && !lineSharpenEnabled) return@buildList
        when (tier) {
            EffectiveShaderTier.ULTRA_LIGHT -> {
                if (lineSharpenEnabled) add(FINAL_SHARPEN_SHADER)
                if (upscaleEnabled) add(UPSCALE_S_SHADER)
            }
            EffectiveShaderTier.FAST -> {
                if (lineSharpenEnabled) {
                    add(CLAMP_HIGHLIGHTS_SHADER)
                    add(RESTORE_S_SHADER)
                    add(FINAL_SHARPEN_SHADER)
                }
                if (upscaleEnabled) add(UPSCALE_S_SHADER)
            }
            EffectiveShaderTier.HIGH -> {
                add(CLAMP_HIGHLIGHTS_SHADER)
                if (lineSharpenEnabled) {
                    if (highResInput && upscaleEnabled) {
                        // 1080p full chain: Restore_S + Darken_HQ + native-res Sharpen + Upscale_M
                        // Keeps 1080p full chain ~35-45 FPS on mid-high mobile GPUs!
                        add(RESTORE_S_SHADER)
                        add(DARKEN_SHADER)
                    } else if (highResInput) {
                        // 1080p sharpen-only: Restore_M + Darken_HQ + Adaptive Sharpen (~50 FPS)
                        add(RESTORE_M_SHADER)
                        add(DARKEN_SHADER)
                    } else {
                        // <=720p: full Restore_M + Darken_HQ + Thin_HQ (~50 FPS)
                        add(RESTORE_M_SHADER)
                        add(DARKEN_SHADER)
                        add(THIN_SHADER)
                    }
                    add(FINAL_SHARPEN_SHADER)
                }
                if (upscaleEnabled) add(UPSCALE_M_SHADER)
            }
            EffectiveShaderTier.ULTRA -> {
                add(CLAMP_HIGHLIGHTS_SHADER)
                if (lineSharpenEnabled) {
                    if (highResInput && upscaleEnabled) {
                        // 1080p full chain with Denoise-M: Restore_S + Darken_HQ + native-res Sharpen
                        add(RESTORE_S_SHADER)
                        add(DARKEN_SHADER)
                    } else {
                        add(RESTORE_M_SHADER)
                        add(DARKEN_SHADER)
                        add(THIN_SHADER)
                    }
                    add(FINAL_SHARPEN_SHADER)
                }
                if (upscaleEnabled) add(UPSCALE_DENOISE_M_SHADER)
            }
        }
    }

    /**
     * Resolves the ordered adaptive fallback ladder for a specific device GPU tier and stream size.
     */
    internal fun resolveTierLadder(
        requestedQuality: AnimeUpscaleQuality,
        gpuTier: GpuTier,
        highResInput: Boolean,
    ): List<EffectiveShaderTier> = when (requestedQuality) {
        AnimeUpscaleQuality.AUTO -> when (gpuTier) {
            GpuTier.HIGH -> listOf(
                EffectiveShaderTier.ULTRA,
                EffectiveShaderTier.HIGH,
                EffectiveShaderTier.FAST,
                EffectiveShaderTier.ULTRA_LIGHT,
            )
            GpuTier.MEDIUM -> if (highResInput) {
                listOf(
                    EffectiveShaderTier.HIGH,
                    EffectiveShaderTier.FAST,
                    EffectiveShaderTier.ULTRA_LIGHT,
                )
            } else {
                listOf(
                    EffectiveShaderTier.ULTRA,
                    EffectiveShaderTier.HIGH,
                    EffectiveShaderTier.FAST,
                    EffectiveShaderTier.ULTRA_LIGHT,
                )
            }
            GpuTier.LOW -> listOf(
                EffectiveShaderTier.FAST,
                EffectiveShaderTier.ULTRA_LIGHT,
            )
        }
        AnimeUpscaleQuality.FAST -> listOf(EffectiveShaderTier.FAST)
        AnimeUpscaleQuality.HIGH -> listOf(EffectiveShaderTier.HIGH)
        AnimeUpscaleQuality.ULTRA -> listOf(EffectiveShaderTier.ULTRA)
    }

    internal fun buildCandidates(
        context: Context,
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        requestedQuality: AnimeUpscaleQuality,
        sharpenStrength: AnimeSharpenStrength,
        gpuTier: GpuTier,
        inputWidth: Int,
        inputHeight: Int,
    ): List<ShaderTierCandidate> {
        val highResInput = max(inputWidth, inputHeight) >= 1600
        val ladder = resolveTierLadder(requestedQuality, gpuTier, highResInput)
        val (gpuMaxLong, gpuMaxShort) = DeviceGpuProfiler.maxUpscaleTarget(gpuTier)
        val isAuto = requestedQuality == AnimeUpscaleQuality.AUTO

        return ladder.map { tier ->
            val names = shaderNamesForTier(
                upscaleEnabled = upscaleEnabled,
                lineSharpenEnabled = lineSharpenEnabled,
                tier = tier,
                highResInput = highResInput,
            )
            val ordered = mutableListOf<MpvUserShaderPass>()
            val sharpen = mutableListOf<MpvUserShaderPass>()
            val upscale = mutableListOf<MpvUserShaderPass>()
            names.forEach { name ->
                val passes = parsedShader(context, name, sharpenStrength)
                ordered += passes
                if (name in UPSCALE_SHADERS) {
                    upscale += passes
                } else if (name != CLAMP_HIGHLIGHTS_SHADER) {
                    sharpen += passes
                }
            }
            val (targetLong, targetShort) = if (tier == EffectiveShaderTier.ULTRA_LIGHT || gpuTier == GpuTier.LOW) {
                min(gpuMaxLong, 1920) to min(gpuMaxShort, 1080)
            } else {
                gpuMaxLong to gpuMaxShort
            }
            val label = if (isAuto) "Auto•${tier.badge}" else tier.badge
            ShaderTierCandidate(
                tier = tier,
                label = label,
                ordered = ordered,
                sharpen = sharpen,
                upscale = upscale,
                maxTargetLongSide = targetLong,
                maxTargetShortSide = targetShort,
            )
        }
    }

    /**
     * Exports selected Anime4K GLSL shaders to app-private files for libmpv `--glsl-shaders=...`.
     */
    fun mpvShaderPaths(
        context: Context,
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        quality: AnimeUpscaleQuality = AnimeUpscaleQuality.AUTO,
        sharpenStrength: AnimeSharpenStrength = AnimeSharpenStrength.BALANCED,
    ): List<String> {
        val names = shaderNames(upscaleEnabled, lineSharpenEnabled, quality)
        if (names.isEmpty()) return emptyList()
        val directory = File(context.filesDir, ASSET_DIR)
        return names.mapNotNull { name ->
            runCatching {
                val bytes = shaderSource(context, name, sharpenStrength).toByteArray()
                directory.mkdirs()
                val file = File(directory, name)
                if (!file.exists() || !file.readBytes().contentEquals(bytes)) {
                    file.writeBytes(bytes)
                }
                file.absolutePath
            }.onFailure { error ->
                Log.w(TAG, "Unable to prepare $name for libmpv", error)
            }.getOrNull()
        }
    }

    /**
     * Builds the Media3 ExoPlayer [Effect] list executing official Anime4K multi-pass GLSL shaders
     * with device-aware GPU tiering and live non-blocking FPS governance in [AnimeUpscaleQuality.AUTO].
     */
    @UnstableApi
    fun exoVideoEffects(
        context: Context,
        upscaleEnabled: Boolean,
        lineSharpenEnabled: Boolean,
        quality: AnimeUpscaleQuality = AnimeUpscaleQuality.AUTO,
        sharpenStrength: AnimeSharpenStrength = AnimeSharpenStrength.BALANCED,
        statusTracker: AnimeShaderStatusTracker? = null,
    ): List<Effect> {
        if (!upscaleEnabled && !lineSharpenEnabled) return emptyList()
        return runCatching {
            val (longSide, shortSide) = displaySize(context)
            listOf<Effect>(
                AnimeShaderEffect(
                    appContext = context.applicationContext,
                    upscaleEnabled = upscaleEnabled,
                    lineSharpenEnabled = lineSharpenEnabled,
                    requestedQuality = quality,
                    sharpenStrength = sharpenStrength,
                    displayLongSide = longSide,
                    displayShortSide = shortSide,
                    statusTracker = statusTracker,
                ),
            )
        }.onFailure { error ->
            Log.w(TAG, "Unable to prepare anime shaders for ExoPlayer", error)
        }.getOrDefault(emptyList())
    }

    fun mpvStatus(
        context: Context,
        shaderPaths: List<String>,
        videoWidth: Int,
        videoHeight: Int,
        outputWidth: Int,
        outputHeight: Int,
        quality: AnimeUpscaleQuality = AnimeUpscaleQuality.AUTO,
        sharpenStrength: AnimeSharpenStrength = AnimeSharpenStrength.BALANCED,
    ): AnimeEnhancementStatus? {
        val upscaleEnabled = shaderPaths.any { path -> UPSCALE_SHADERS.any { path.endsWith("/$it") } }
        val lineSharpenEnabled = shaderPaths.any {
            it.endsWith("/$RESTORE_S_SHADER") || it.endsWith("/$RESTORE_M_SHADER") || it.endsWith("/$FINAL_SHARPEN_SHADER")
        }
        if (!upscaleEnabled && !lineSharpenEnabled) return null
        if (videoWidth <= 0 || videoHeight <= 0 || outputWidth <= 0 || outputHeight <= 0) return null
        return runCatching {
            val candidate = buildCandidates(
                context = context,
                upscaleEnabled = upscaleEnabled,
                lineSharpenEnabled = lineSharpenEnabled,
                requestedQuality = quality,
                sharpenStrength = sharpenStrength,
                gpuTier = GpuTier.MEDIUM,
                inputWidth = videoWidth,
                inputHeight = videoHeight,
            ).first()
            val input = MpvTextureSize(videoWidth, videoHeight)
            MpvUserShaderParser
                .plan(candidate.ordered, input, MpvTextureSize(outputWidth, outputHeight))
                .toAnimeEnhancementStatus(input, candidate.sharpen, candidate.upscale, candidate.label)
        }.getOrNull()
    }

    private val parsedShaders = mutableMapOf<String, List<MpvUserShaderPass>>()

    @Synchronized
    private fun parsedShader(
        context: Context,
        name: String,
        sharpenStrength: AnimeSharpenStrength,
    ): List<MpvUserShaderPass> {
        val cacheKey = if (name == FINAL_SHARPEN_SHADER) "$name:${sharpenStrength.id}" else name
        return parsedShaders.getOrPut(cacheKey) {
            MpvUserShaderParser.parse(shaderSource(context, name, sharpenStrength))
        }
    }

    private fun shaderSource(
        context: Context,
        name: String,
        sharpenStrength: AnimeSharpenStrength,
    ): String {
        val source = context.assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        return when {
            name in UPSCALE_SHADERS -> withMinimumUpscaleTarget(source)
            name == FINAL_SHARPEN_SHADER -> withSharpenStrength(source, sharpenStrength)
            else -> source.replace("\r\n", "\n")
        }
    }

    fun withMinimumUpscaleTarget(source: String): String {
        val long = MINIMUM_UPSCALE_LONG_SIDE
        val short = MINIMUM_UPSCALE_SHORT_SIDE
        val condition = "$UPSCALE_CONDITION " +
            "$long MAIN.w / 1.200 > $short MAIN.h / 1.200 > * + " +
            "$short MAIN.w / 1.200 > $long MAIN.h / 1.200 > * + 0 >"
        val normalized = source.replace("\r\n", "\n")
        return normalized.replace(UPSCALE_CONDITION + "\n", condition + "\n")
    }

    fun withSharpenStrength(source: String, strength: AnimeSharpenStrength): String {
        val normalized = source.replace("\r\n", "\n")
        return normalized
            .replace(
                Regex("#define NUVIO_SHARPEN_STRENGTH [0-9.]+"),
                String.format(Locale.US, "#define NUVIO_SHARPEN_STRENGTH %.2f", strength.strength),
            )
            .replace(
                Regex("#define NUVIO_DARK_OVERSHOOT [0-9.]+"),
                String.format(Locale.US, "#define NUVIO_DARK_OVERSHOOT %.2f", strength.darkOvershoot),
            )
            .replace(
                Regex("#define NUVIO_BRIGHT_OVERSHOOT [0-9.]+"),
                String.format(Locale.US, "#define NUVIO_BRIGHT_OVERSHOOT %.2f", strength.brightOvershoot),
            )
            .replace(
                Regex("#define NUVIO_LINE_DARKEN [0-9.]+"),
                String.format(Locale.US, "#define NUVIO_LINE_DARKEN %.2f", strength.lineDarken),
            )
    }

    private fun displaySize(context: Context): Pair<Int, Int> {
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
            if (bounds != null) {
                bounds.width() to bounds.height()
            } else {
                context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
            }
        } else {
            context.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        return max(width, height) to min(width, height)
    }
}
