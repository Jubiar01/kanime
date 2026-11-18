package com.kanime.app.ui.player.anime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Build
import android.util.Log
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

@UnstableApi
@RunWith(AndroidJUnit4::class)
class Anime4kDeviceComparisonTest {

    private data class ModeSpec(
        val id: String,
        val label: String,
        val upscale: Boolean,
        val sharpen: Boolean,
        val quality: AnimeUpscaleQuality,
        val strength: AnimeSharpenStrength,
    )

    private data class BenchResult(
        val spec: ModeSpec,
        val inputW: Int,
        val inputH: Int,
        val outputW: Int,
        val outputH: Int,
        val passes: Int,
        val pooledFbos: Int,
        val compileMs: Double,
        val avgFrameMs: Double,
        val fps: Double,
        val edgeAcutance: Double,
        val inkDepth: Double,
        val flatNoise: Double,
        val haloScore: Double,
    )

    @Test
    fun benchmarkAndCompareAllModesOnDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outDir = File(context.getExternalFilesDir(null), "anime4k_compare").apply {
            if (exists()) deleteRecursively()
            mkdirs()
        }

        val egl = createHeadlessEgl3Context()
        try {
            val gpuRenderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
            val gpuVendor = GLES20.glGetString(GLES20.GL_VENDOR).orEmpty()
            val gpuVersion = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
            val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"

            Log.i(TAG, "Device: $deviceModel | GPU: $gpuRenderer ($gpuVendor) | $gpuVersion")

            val modes = listOf(
                ModeSpec("00_off", "Off (Original)", false, false, AnimeUpscaleQuality.AUTO, AnimeSharpenStrength.BALANCED),
                ModeSpec("01_up_fast", "2x Upscale: Fast (60fps)", true, false, AnimeUpscaleQuality.FAST, AnimeSharpenStrength.BALANCED),
                ModeSpec("02_up_high", "2x Upscale: Balanced (HQ)", true, false, AnimeUpscaleQuality.HIGH, AnimeSharpenStrength.BALANCED),
                ModeSpec("03_up_ultra", "2x Upscale: Ultra (Denoise M)", true, false, AnimeUpscaleQuality.ULTRA, AnimeSharpenStrength.BALANCED),
                ModeSpec("04_sh_fast", "Sharpen Only: Fast (60fps)", false, true, AnimeUpscaleQuality.FAST, AnimeSharpenStrength.BALANCED),
                ModeSpec("05_sh_balanced", "Sharpen Only: Balanced (HQ)", false, true, AnimeUpscaleQuality.HIGH, AnimeSharpenStrength.BALANCED),
                ModeSpec("06_sh_crisp", "Sharpen Only: Crisp (HQ)", false, true, AnimeUpscaleQuality.HIGH, AnimeSharpenStrength.CRISP),
                ModeSpec("07_full_auto", "Full: Auto (Device Adaptive)", true, true, AnimeUpscaleQuality.AUTO, AnimeSharpenStrength.BALANCED),
                ModeSpec("08_full_fast", "Full: Fast (60fps) + Balanced", true, true, AnimeUpscaleQuality.FAST, AnimeSharpenStrength.BALANCED),
                ModeSpec("09_full_high", "Full: Balanced (HQ) + Balanced", true, true, AnimeUpscaleQuality.HIGH, AnimeSharpenStrength.BALANCED),
                ModeSpec("10_full_ultra_bal", "Full: Ultra (Denoise M) + Balanced", true, true, AnimeUpscaleQuality.ULTRA, AnimeSharpenStrength.BALANCED),
                ModeSpec("11_full_ultra_crisp", "Full: Ultra (Denoise M) + Crisp", true, true, AnimeUpscaleQuality.ULTRA, AnimeSharpenStrength.CRISP),
            )

            val results720 = runResolutionSuite(context, 1280, 720, modes, outDir, saveVisualCrops = true)
            val results1080 = runResolutionSuite(context, 1920, 1080, modes, outDir, saveVisualCrops = false)

            val report = buildString {
                appendLine("# On-Device Anime4K Benchmark & Quality Comparison")
                appendLine("- Device: $deviceModel")
                appendLine("- GPU: $gpuRenderer ($gpuVendor)")
                appendLine("- GL: $gpuVersion")
                appendLine()
                appendLine("## 720p Stream Input (1280x720)")
                appendLine(formatTable(results720))
                appendLine()
                appendLine("## 1080p Stream Input (1920x1080)")
                appendLine(formatTable(results1080))
            }

            File(outDir, "results.txt").writeText(report)
            report.lineSequence().forEach { Log.i(TAG, it) }
            assertTrue("Expected all 11 modes benchmarked at 720p", results720.size == modes.size)
            assertTrue("Expected all 11 modes benchmarked at 1080p", results1080.size == modes.size)
        } finally {
            destroyEgl(egl)
        }
    }

    private fun runResolutionSuite(
        context: Context,
        width: Int,
        height: Int,
        modes: List<ModeSpec>,
        outDir: File,
        saveVisualCrops: Boolean,
    ): List<BenchResult> {
        val sourceBitmap = createSimulatedStreamingAnimeFrame(width, height)
        val inputTexId = uploadBitmapTexture(sourceBitmap)
        val results = mutableListOf<BenchResult>()
        val cropsForGrid = mutableListOf<Pair<String, Bitmap>>()

        try {
            // Compute reference local max brightness map for halo measurement (scaled to 2560x1440 comparison space)
            val baselineScaled = Bitmap.createScaledBitmap(sourceBitmap, 1280, 720, true)
            val refLocalMax = computeLocalMaxLuma(baselineScaled)

            for (spec in modes) {
                val res = if (!spec.upscale && !spec.sharpen) {
                    val metrics = measureMetrics(sourceBitmap, refLocalMax)
                    if (saveVisualCrops) {
                        val crop = extractDetailCrop(sourceBitmap)
                        savePng(crop, File(outDir, "${spec.id}.png"))
                        cropsForGrid += spec.label to crop
                    }
                    BenchResult(
                        spec = spec,
                        inputW = width,
                        inputH = height,
                        outputW = width,
                        outputH = height,
                        passes = 0,
                        pooledFbos = 0,
                        compileMs = 0.0,
                        avgFrameMs = 0.0,
                        fps = Double.POSITIVE_INFINITY,
                        edgeAcutance = metrics.edgeAcutance,
                        inkDepth = metrics.inkDepth,
                        flatNoise = metrics.flatNoise,
                        haloScore = metrics.haloScore,
                    )
                } else {
                    benchmarkShaderMode(
                        context = context,
                        spec = spec,
                        inputTexId = inputTexId,
                        inputW = width,
                        inputH = height,
                        refLocalMax = refLocalMax,
                        outDir = outDir,
                        saveVisualCrop = saveVisualCrops,
                        cropsForGrid = cropsForGrid,
                    )
                }
                results += res
            }

            if (saveVisualCrops && cropsForGrid.isNotEmpty()) {
                val grid = buildComparisonGrid(cropsForGrid)
                savePng(grid, File(outDir, "comparison_grid_720p.png"))
                grid.recycle()
                cropsForGrid.forEach { it.second.recycle() }
            }
            baselineScaled.recycle()
        } finally {
            GlUtil.deleteTexture(inputTexId)
            sourceBitmap.recycle()
        }
        return results
    }

    private fun benchmarkShaderMode(
        context: Context,
        spec: ModeSpec,
        inputTexId: Int,
        inputW: Int,
        inputH: Int,
        refLocalMax: FloatArray,
        outDir: File,
        saveVisualCrop: Boolean,
        cropsForGrid: MutableList<Pair<String, Bitmap>>,
    ): BenchResult {
        val effects = AnimeShaders.exoVideoEffects(
            context = context,
            upscaleEnabled = spec.upscale,
            lineSharpenEnabled = spec.sharpen,
            quality = spec.quality,
            sharpenStrength = spec.strength,
        )
        val effect = effects.single() as AnimeShaderEffect
        val program = effect.toGlShaderProgram(context, false) as AnimeShaderProgram

        // Also compute exact pass + pooled FBO counts
        val dm = context.resources.displayMetrics
        val longSide = max(dm.widthPixels, dm.heightPixels)
        val shortSide = min(dm.widthPixels, dm.heightPixels)

        val t0 = System.nanoTime()
        val outSize = program.configure(inputW, inputH)
        GLES20.glFinish()
        val compileMs = (System.nanoTime() - t0) / 1_000_000.0

        val outTexId = GlUtil.createTexture(outSize.width, outSize.height, false)
        val outFboId = GlUtil.createFboForTexture(outTexId)

        var avgMs = 0.0
        var metrics = QualityMetrics(0.0, 0.0, 0.0, 0.0)
        var passCount = 0
        var pooledFboCount = 0

        try {
            // Query plan stats cleanly
            val planField = AnimeShaderProgram::class.java.getDeclaredField("plan").apply { isAccessible = true }
            val pooledField = AnimeShaderProgram::class.java.getDeclaredField("pooledTargets").apply { isAccessible = true }
            val actualPlan = planField.get(program) as MpvShaderPlan
            passCount = actualPlan.passes.size
            pooledFboCount = (pooledField.get(program) as List<*>).size

            // Warmup 3 frames
            repeat(3) {
                GlUtil.focusFramebufferUsingCurrentContext(outFboId, outSize.width, outSize.height)
                program.drawFrame(inputTexId, it * 16_666L)
                GLES20.glFinish()
            }

            // Timed 10 frames with full GPU sync
            val iterations = 10
            val startNs = System.nanoTime()
            repeat(iterations) { idx ->
                GlUtil.focusFramebufferUsingCurrentContext(outFboId, outSize.width, outSize.height)
                program.drawFrame(inputTexId, (idx + 3) * 16_666L)
                GLES20.glFinish()
            }
            avgMs = ((System.nanoTime() - startNs) / 1_000_000.0) / iterations

            // Read back rendered GPU frame for objective metrics + visual crop
            GlUtil.focusFramebufferUsingCurrentContext(outFboId, outSize.width, outSize.height)
            val outputBitmap = readFramebufferToBitmap(outSize.width, outSize.height)
            try {
                metrics = measureMetrics(outputBitmap, refLocalMax)
                if (saveVisualCrop) {
                    val crop = extractDetailCrop(outputBitmap)
                    savePng(crop, File(outDir, "${spec.id}.png"))
                    cropsForGrid += "${spec.label} (${String.format(Locale.US, "%.1fms", avgMs)})" to crop
                }
            } finally {
                outputBitmap.recycle()
            }
        } finally {
            GlUtil.deleteFbo(outFboId)
            GlUtil.deleteTexture(outTexId)
            program.release()
        }

        val fps = if (avgMs > 0.0) 1000.0 / avgMs else 0.0
        return BenchResult(
            spec = spec,
            inputW = inputW,
            inputH = inputH,
            outputW = outSize.width,
            outputH = outSize.height,
            passes = passCount,
            pooledFbos = pooledFboCount,
            compileMs = compileMs,
            avgFrameMs = avgMs,
            fps = fps,
            edgeAcutance = metrics.edgeAcutance,
            inkDepth = metrics.inkDepth,
            flatNoise = metrics.flatNoise,
            haloScore = metrics.haloScore,
        )
    }

    /**
     * Synthesizes a realistic compressed anime cel frame containing:
     * - Smooth pastel sky gradient with mild streaming noise/banding
     * - Detailed anime character eye (iris gradient, bright speculars, thin curved dark ink lashes)
     * - Flowing curved hair strands with fine cel shadows and dark ink outlines
     * - Fine diagonal speed lines & geometric lines
     * - White subtitle with dark stroke at bottom
     * Drawn at 1.5x, downscaled to target stream res, and JPEG-encoded at Q=78 to mimic real streaming anime.
     */
    private fun createSimulatedStreamingAnimeFrame(width: Int, height: Int): Bitmap {
        val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(raw)
        val s = width / 1280f

        // 1. Pastel anime sky gradient (smooth flat region for noise/banding measurement)
        val skyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(Color.rgb(98, 152, 224), Color.rgb(168, 206, 245), Color.rgb(246, 224, 212)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), skyPaint)

        // Subtle deterministic film/stream grain in sky so we can test denoise + noise floor
        val grainPaint = Paint()
        var seed = 1337
        for (y in 0 until height step (3 * s).toInt().coerceAtLeast(2)) {
            for (x in 0 until width step (4 * s).toInt().coerceAtLeast(2)) {
                seed = (seed * 1103515245 + 12345) and 0x7fffffff
                val delta = ((seed shr 16) % 9) - 4
                if (delta != 0) {
                    grainPaint.color = if (delta > 0) Color.argb(10, 255, 255, 255) else Color.argb(10, 0, 0, 0)
                    c.drawRect(x.toFloat(), y.toFloat(), x + 2f * s, y + 2f * s, grainPaint)
                }
            }
        }

        // 2. Cel-shaded anime character face region (center-right)
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(252, 225, 206) }
        val faceShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(235, 195, 174) }
        val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 22, 34)
            style = Paint.Style.STROKE
            strokeWidth = 3.2f * s
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fineInkPaint = Paint(inkPaint).apply { strokeWidth = 1.8f * s }

        val facePath = Path().apply {
            moveTo(470f * s, 120f * s)
            quadTo(440f * s, 390f * s, 560f * s, 520f * s)
            quadTo(665f * s, 565f * s, 775f * s, 505f * s)
            quadTo(875f * s, 385f * s, 855f * s, 120f * s)
            close()
        }
        c.drawPath(facePath, facePaint)

        // Cel shadow under hair bangs
        val shadowPath = Path().apply {
            moveTo(472f * s, 170f * s)
            quadTo(650f * s, 260f * s, 852f * s, 180f * s)
            lineTo(855f * s, 120f * s)
            lineTo(470f * s, 120f * s)
            close()
        }
        c.drawPath(shadowPath, faceShadowPaint)
        c.drawPath(facePath, inkPaint)

        // 3. Detailed Anime Eye (primary detail/sharpen/upscale inspection region!)
        val eyeCx = 610f * s
        val eyeCy = 340f * s
        val scleraRect = RectF(eyeCx - 74f * s, eyeCy - 46f * s, eyeCx + 74f * s, eyeCy + 46f * s)
        val scleraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(248, 249, 255) }
        c.drawOval(scleraRect, scleraPaint)

        // Iris with radial anime highlight gradient
        val irisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                eyeCx, eyeCy + 10f * s, 46f * s,
                intArrayOf(Color.rgb(92, 185, 255), Color.rgb(42, 92, 210), Color.rgb(22, 34, 96)),
                floatArrayOf(0.15f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP,
            )
        }
        c.drawOval(RectF(eyeCx - 40f * s, eyeCy - 41f * s, eyeCx + 40f * s, eyeCy + 41f * s), irisPaint)
        c.drawOval(RectF(eyeCx - 40f * s, eyeCy - 41f * s, eyeCx + 40f * s, eyeCy + 41f * s), fineInkPaint)

        // Pupil + specular highlights (tests Clamp_Highlights!)
        val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(18, 16, 40) }
        c.drawCircle(eyeCx, eyeCy, 15f * s, pupilPaint)
        val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        c.drawCircle(eyeCx - 17f * s, eyeCy - 17f * s, 10.5f * s, specPaint)
        c.drawCircle(eyeCx + 19f * s, eyeCy + 14f * s, 5.2f * s, specPaint)

        // Thick curved upper lash + fine lower lash + eyelid crease
        val lashPaint = Paint(inkPaint).apply { strokeWidth = 6.5f * s }
        val upperLash = Path().apply {
            moveTo(eyeCx - 78f * s, eyeCy - 8f * s)
            quadTo(eyeCx - 5f * s, eyeCy - 62f * s, eyeCx + 76f * s, eyeCy - 16f * s)
        }
        c.drawPath(upperLash, lashPaint)
        val lowerLash = Path().apply {
            moveTo(eyeCx - 58f * s, eyeCy + 34f * s)
            quadTo(eyeCx, eyeCy + 52f * s, eyeCx + 58f * s, eyeCy + 30f * s)
        }
        c.drawPath(lowerLash, fineInkPaint)
        val crease = Path().apply {
            moveTo(eyeCx - 66f * s, eyeCy - 42f * s)
            quadTo(eyeCx, eyeCy - 74f * s, eyeCx + 68f * s, eyeCy - 46f * s)
        }
        c.drawPath(crease, fineInkPaint)

        // 4. Flowing Anime Hair Strands with cel highlight band & sharp dark ink outlines
        val hairFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                440f * s, 60f * s, 880f * s, 420f * s,
                Color.rgb(54, 62, 118), Color.rgb(34, 38, 82),
                Shader.TileMode.CLAMP,
            )
        }
        val hairStrands = listOf(
            Path().apply {
                moveTo(455f * s, 90f * s)
                quadTo(525f * s, 215f * s, 500f * s, 320f * s)
                quadTo(560f * s, 240f * s, 585f * s, 130f * s)
                close()
            },
            Path().apply {
                moveTo(575f * s, 85f * s)
                quadTo(620f * s, 210f * s, 665f * s, 310f * s)
                quadTo(675f * s, 200f * s, 695f * s, 100f * s)
                close()
            },
            Path().apply {
                moveTo(690f * s, 90f * s)
                quadTo(765f * s, 225f * s, 748f * s, 365f * s)
                quadTo(820f * s, 255f * s, 845f * s, 110f * s)
                close()
            },
        )
        hairStrands.forEach { strand ->
            c.drawPath(strand, hairFill)
            c.drawPath(strand, inkPaint)
        }

        // Fine diagonal anime linework / hatching on right
        for (k in 0..8) {
            val offset = k * 14f * s
            c.drawLine(760f * s + offset, 330f * s, 825f * s + offset, 455f * s, fineInkPaint)
        }

        // 5. Crisp White Anime Subtitle with black outline at bottom
        val subText = "KAnime Anime4K — 2x CNN Upscale & Line Sharpening"
        val subStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(15, 15, 22)
            textSize = 36f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            style = Paint.Style.STROKE
            strokeWidth = 6f * s
            textAlign = Paint.Align.CENTER
        }
        val subFill = Paint(subStroke).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        c.drawText(subText, width * 0.5f, height - 54f * s, subStroke)
        c.drawText(subText, width * 0.5f, height - 54f * s, subFill)

        // Simulate real streaming softness + H.264/JPEG compression artifacts:
        // subtle 0.85x downscale-upscale softness + JPEG Q=78 compression
        val soft = Bitmap.createScaledBitmap(raw, (width * 0.85f).toInt(), (height * 0.85f).toInt(), true)
        val rescaled = Bitmap.createScaledBitmap(soft, width, height, true)
        soft.recycle()
        raw.recycle()

        val jpegOut = ByteArrayOutputStream()
        rescaled.compress(Bitmap.CompressFormat.JPEG, 78, jpegOut)
        rescaled.recycle()
        val bytes = jpegOut.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            .copy(Bitmap.Config.ARGB_8888, false)
    }

    private data class QualityMetrics(
        val edgeAcutance: Double,
        val inkDepth: Double,
        val flatNoise: Double,
        val haloScore: Double,
    )

    private fun computeLocalMaxLuma(ref1280x720: Bitmap): FloatArray {
        val w = ref1280x720.width
        val h = ref1280x720.height
        val px = IntArray(w * h)
        ref1280x720.getPixels(px, 0, w, 0, 0, w, h)
        val luma = FloatArray(w * h) { i -> rgbLuma(px[i]) }
        val maxMap = FloatArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - 2)
            val y1 = min(h - 1, y + 2)
            for (x in 0 until w) {
                val x0 = max(0, x - 2)
                val x1 = min(w - 1, x + 2)
                var m = 0f
                for (yy in y0..y1) {
                    val row = yy * w
                    for (xx in x0..x1) {
                        val v = luma[row + xx]
                        if (v > m) m = v
                    }
                }
                maxMap[y * w + x] = m
            }
        }
        return maxMap
    }

    private fun measureMetrics(bitmap: Bitmap, refLocalMax1280x720: FloatArray): QualityMetrics {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)

        // ROI 1: Anime eye + hair strands (x: 36%..70%, y: 20%..65%)
        val ex0 = (w * 0.36).toInt().coerceAtLeast(1)
        val ex1 = (w * 0.70).toInt().coerceAtMost(w - 2)
        val ey0 = (h * 0.20).toInt().coerceAtLeast(1)
        val ey1 = (h * 0.65).toInt().coerceAtMost(h - 2)

        var sobelSum = 0.0
        var edgeCount = 0
        val darkSamples = ArrayList<Float>((ex1 - ex0) * (ey1 - ey0) / 4)
        var haloSum = 0.0
        var haloCount = 0

        for (y in ey0..ey1) {
            val refY = ((y.toFloat() / h) * 720f).toInt().coerceIn(0, 719)
            for (x in ex0..ex1) {
                val c = rgbLuma(px[y * w + x])
                val nw = rgbLuma(px[(y - 1) * w + (x - 1)])
                val n = rgbLuma(px[(y - 1) * w + x])
                val ne = rgbLuma(px[(y - 1) * w + (x + 1)])
                val wv = rgbLuma(px[y * w + (x - 1)])
                val ev = rgbLuma(px[y * w + (x + 1)])
                val sw = rgbLuma(px[(y + 1) * w + (x - 1)])
                val s = rgbLuma(px[(y + 1) * w + x])
                val se = rgbLuma(px[(y + 1) * w + (x + 1)])

                val gx = (ne + 2f * ev + se) - (nw + 2f * wv + sw)
                val gy = (sw + 2f * s + se) - (nw + 2f * n + ne)
                val mag = sqrt((gx * gx + gy * gy).toDouble()) * 0.25
                if (mag > 0.03) {
                    sobelSum += mag
                    edgeCount++
                    val refX = ((x.toFloat() / w) * 1280f).toInt().coerceIn(0, 1279)
                    val overshoot = max(0f, c - refLocalMax1280x720[refY * 1280 + refX])
                    haloSum += overshoot
                    haloCount++
                }
                if ((x and 1) == 0 && (y and 1) == 0) {
                    darkSamples.add(c)
                }
            }
        }
        darkSamples.sort()
        val p5Index = (darkSamples.size * 0.05).toInt().coerceIn(0, darkSamples.lastIndex)
        val inkDepth = darkSamples[p5Index].toDouble()
        val edgeAcutance = if (edgeCount > 0) (sobelSum / edgeCount) * 100.0 else 0.0
        val haloScore = if (haloCount > 0) (haloSum / haloCount) * 1000.0 else 0.0

        // ROI 2: Smooth sky gradient (x: 5%..30%, y: 10%..55%) -> RMS high-pass residual
        val sx0 = (w * 0.05).toInt().coerceAtLeast(1)
        val sx1 = (w * 0.30).toInt().coerceAtMost(w - 2)
        val sy0 = (h * 0.10).toInt().coerceAtLeast(1)
        val sy1 = (h * 0.55).toInt().coerceAtMost(h - 2)
        var hpSqSum = 0.0
        var hpCount = 0
        for (y in sy0..sy1) {
            for (x in sx0..sx1) {
                val c = rgbLuma(px[y * w + x])
                val avg4 = 0.25f * (
                    rgbLuma(px[(y - 1) * w + x]) +
                        rgbLuma(px[(y + 1) * w + x]) +
                        rgbLuma(px[y * w + (x - 1)]) +
                        rgbLuma(px[y * w + (x + 1)])
                    )
                val diff = (c - avg4).toDouble()
                hpSqSum += diff * diff
                hpCount++
            }
        }
        val flatNoise = if (hpCount > 0) sqrt(hpSqSum / hpCount) * 1000.0 else 0.0

        return QualityMetrics(
            edgeAcutance = edgeAcutance,
            inkDepth = inkDepth,
            flatNoise = flatNoise,
            haloScore = haloScore,
        )
    }

    private fun extractDetailCrop(bitmap: Bitmap): Bitmap {
        // Crop tightly around the anime eye + bangs + hair ink lines (normalized 0.38..0.72 x 0.22..0.64)
        val x = (bitmap.width * 0.38f).toInt()
        val y = (bitmap.height * 0.22f).toInt()
        val cw = (bitmap.width * 0.34f).toInt().coerceAtLeast(64)
        val ch = (bitmap.height * 0.42f).toInt().coerceAtLeast(64)
        val cropped = Bitmap.createBitmap(bitmap, x, y, cw, ch)
        // Scale to uniform 520x360 panel using nearest-neighbor/point-like crisp scaling if smaller,
        // or linear if larger, so differences in sharpness are preserved faithfully
        val scaled = Bitmap.createScaledBitmap(cropped, 520, 360, false)
        cropped.recycle()
        return scaled
    }

    private fun buildComparisonGrid(panels: List<Pair<String, Bitmap>>): Bitmap {
        val cols = 3
        val rows = (panels.size + cols - 1) / cols
        val panelW = 520
        val panelH = 360
        val headerH = 44
        val pad = 12
        val totalW = cols * panelW + (cols + 1) * pad
        val totalH = rows * (panelH + headerH) + (rows + 1) * pad

        val grid = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grid)
        canvas.drawColor(Color.rgb(18, 18, 24))

        val headerBg = Paint().apply { color = Color.rgb(34, 38, 54) }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        panels.forEachIndexed { index, (label, bmp) ->
            val col = index % cols
            val row = index / cols
            val left = pad + col * (panelW + pad)
            val top = pad + row * (panelH + headerH + pad)
            canvas.drawRect(left.toFloat(), top.toFloat(), (left + panelW).toFloat(), (top + headerH).toFloat(), headerBg)
            canvas.drawText(label, left + 12f, top + 29f, textPaint)
            canvas.drawBitmap(bmp, null, Rect(left, top + headerH, left + panelW, top + headerH + panelH), null)
        }
        return grid
    }

    private fun formatTable(results: List<BenchResult>): String = buildString {
        appendLine("| Mode | Output | Passes / FBOs | GPU ms | Est. FPS | Acutance (↑) | Ink Depth (↓) | Flat Noise (↓) | Halo (↓) |")
        appendLine("|---|---:|---:|---:|---:|---:|---:|---:|---:|")
        results.forEach { r ->
            val fpsStr = if (r.fps.isInfinite()) "—" else String.format(Locale.US, "%.1f", r.fps)
            val msStr = if (r.avgFrameMs == 0.0) "0.0" else String.format(Locale.US, "%.2f", r.avgFrameMs)
            appendLine(
                String.format(
                    Locale.US,
                    "| %s | %dx%d | %d / %d | %s | %s | %.2f | %.3f | %.2f | %.2f |",
                    r.spec.label,
                    r.outputW,
                    r.outputH,
                    r.passes,
                    r.pooledFbos,
                    msStr,
                    fpsStr,
                    r.edgeAcutance,
                    r.inkDepth,
                    r.flatNoise,
                    r.haloScore,
                ),
            )
        }
    }

    private fun rgbLuma(argb: Int): Float {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun uploadBitmapTexture(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return texId
    }

    private fun readFramebufferToBitmap(width: Int, height: Int): Bitmap {
        val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
        buf.rewind()
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(buf)
        return bmp
    }

    private fun savePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private data class EglHandle(
        val display: EGLDisplay,
        val context: EGLContext,
        val surface: EGLSurface,
    )

    private fun createHeadlessEgl3Context(): EglHandle {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }
        val configAttribs = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) && numConfigs[0] > 0) {
            "eglChooseConfig failed"
        }
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        val eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }
        val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
        val surface = EGL14.eglCreatePbufferSurface(display, configs[0], pbufferAttribs, 0)
        check(surface != EGL14.EGL_NO_SURFACE) { "eglCreatePbufferSurface failed" }
        check(EGL14.eglMakeCurrent(display, surface, surface, eglContext)) { "eglMakeCurrent failed" }
        return EglHandle(display, eglContext, surface)
    }

    private fun destroyEgl(egl: EglHandle) {
        EGL14.eglMakeCurrent(egl.display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(egl.display, egl.surface)
        EGL14.eglDestroyContext(egl.display, egl.context)
        EGL14.eglTerminate(egl.display)
    }

    private companion object {
        const val TAG = "Anime4KBench"
    }
}
