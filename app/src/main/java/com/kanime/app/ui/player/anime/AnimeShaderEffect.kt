package com.kanime.app.ui.player.anime

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class TargetSlotSpec(
    val slotIndex: Int,
    val size: MpvTextureSize,
    val isHighPrecision: Boolean,
)

/**
 * Liveness-based FBO/texture pool planner.
 * Reuses intermediate GPU textures once their last consumer pass has executed, cutting VRAM
 * usage by ~65-75% while guaranteeing no pass ever reads and writes the same texture.
 */
internal fun planTargetSlots(
    passes: List<MpvPlannedPass>,
    outputSize: MpvTextureSize,
): List<TargetSlotSpec?> {
    if (passes.isEmpty()) return emptyList()
    val mainName = "MAIN"
    val activeVersion = mutableMapOf<String, Int>(mainName to -1)
    val lastRead = mutableMapOf<Int, Int>()

    passes.forEachIndexed { index, planned ->
        planned.binds.forEach { bindName ->
            val version = activeVersion[bindName] ?: -1
            if (version >= 0) {
                lastRead[version] = index
            }
        }
        activeVersion[planned.target] = index
    }

    val lastPassIndex = passes.lastIndex
    val lastPass = passes[lastPassIndex]
    val lastWritesDirectToOutput = lastPass.target == mainName && lastPass.size == outputSize
    if (!lastWritesDirectToOutput) {
        val finalMainVersion = activeVersion[mainName] ?: -1
        if (finalMainVersion >= 0) {
            lastRead[finalMainVersion] = passes.size
        }
    }

    val freeSlots = mutableListOf<TargetSlotSpec>()
    val liveVersions = mutableMapOf<Int, TargetSlotSpec>()
    var nextSlotIndex = 0
    val assigned = arrayOfNulls<TargetSlotSpec>(passes.size)

    passes.forEachIndexed { index, planned ->
        val writesDirect = index == lastPassIndex && lastWritesDirectToOutput
        if (!writesDirect) {
            val isHighPrecision = planned.target != mainName
            val reusedIndex = freeSlots.indexOfFirst {
                it.size == planned.size && it.isHighPrecision == isHighPrecision
            }
            val slot = if (reusedIndex >= 0) {
                freeSlots.removeAt(reusedIndex)
            } else {
                TargetSlotSpec(
                    slotIndex = nextSlotIndex++,
                    size = planned.size,
                    isHighPrecision = isHighPrecision,
                )
            }
            assigned[index] = slot
            liveVersions[index] = slot
        }

        val expired = liveVersions.keys.filter { version ->
            val lr = lastRead[version]
            lr == null || lr <= index
        }
        expired.forEach { version ->
            liveVersions.remove(version)?.let { freeSlots.add(it) }
        }
    }
    return assigned.toList()
}

@UnstableApi
internal class AnimeShaderEffect(
    private val appContext: Context,
    private val upscaleEnabled: Boolean,
    private val lineSharpenEnabled: Boolean,
    private val requestedQuality: AnimeUpscaleQuality,
    private val sharpenStrength: AnimeSharpenStrength,
    private val displayLongSide: Int,
    private val displayShortSide: Int,
    private val statusTracker: AnimeShaderStatusTracker? = null,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        AnimeShaderProgram(
            appContext = appContext,
            upscaleEnabled = upscaleEnabled,
            lineSharpenEnabled = lineSharpenEnabled,
            requestedQuality = requestedQuality,
            sharpenStrength = sharpenStrength,
            displayLongSide = displayLongSide,
            displayShortSide = displayShortSide,
            useHdr = useHdr,
            statusTracker = statusTracker,
        )

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean =
        !upscaleEnabled && !lineSharpenEnabled

    companion object {
        fun planFor(
            passes: List<MpvUserShaderPass>,
            inputWidth: Int,
            inputHeight: Int,
            displayLongSide: Int,
            displayShortSide: Int,
            minUpscaleLongSide: Int = AnimeShaders.MINIMUM_UPSCALE_LONG_SIDE,
            minUpscaleShortSide: Int = AnimeShaders.MINIMUM_UPSCALE_SHORT_SIDE,
        ): MpvShaderPlan {
            val input = MpvTextureSize(inputWidth, inputHeight)
            val plan = MpvUserShaderParser.plan(
                passes = passes,
                input = input,
                output = fitToDisplay(input, displayLongSide, displayShortSide),
            )
            val target = targetSize(input, displayLongSide, displayShortSide, minUpscaleLongSide, minUpscaleShortSide)
            val output = plan.outputSize
            val upscaledPastTarget = output.width > input.width &&
                (output.width > target.width || output.height > target.height)
            if (!upscaledPastTarget) return plan
            return plan.copy(outputSize = if (target.width >= input.width) target else input)
        }

        fun targetSize(
            input: MpvTextureSize,
            displayLongSide: Int,
            displayShortSide: Int,
            minUpscaleLongSide: Int = AnimeShaders.MINIMUM_UPSCALE_LONG_SIDE,
            minUpscaleShortSide: Int = AnimeShaders.MINIMUM_UPSCALE_SHORT_SIDE,
        ): MpvTextureSize {
            val display = fitToDisplay(input, displayLongSide, displayShortSide)
            val minimum = fitToDisplay(input, minUpscaleLongSide, minUpscaleShortSide)
            return if (minimum.width.toLong() * minimum.height > display.width.toLong() * display.height) minimum else display
        }

        fun fitToDisplay(input: MpvTextureSize, displayLongSide: Int, displayShortSide: Int): MpvTextureSize {
            if (input.width <= 0 || input.height <= 0 || displayLongSide <= 0 || displayShortSide <= 0) {
                return input
            }
            val landscape = input.width >= input.height
            val displayWidth = if (landscape) displayLongSide else displayShortSide
            val displayHeight = if (landscape) displayShortSide else displayLongSide
            val scale = min(
                displayWidth.toFloat() / input.width.toFloat(),
                displayHeight.toFloat() / input.height.toFloat(),
            )
            return MpvTextureSize(
                width = max(1, (input.width * scale).roundToInt()),
                height = max(1, (input.height * scale).roundToInt()),
            )
        }
    }
}

@UnstableApi
internal class AnimeShaderProgram(
    private val appContext: Context,
    private val upscaleEnabled: Boolean,
    private val lineSharpenEnabled: Boolean,
    private val requestedQuality: AnimeUpscaleQuality,
    private val sharpenStrength: AnimeSharpenStrength,
    private val displayLongSide: Int,
    private val displayShortSide: Int,
    private val useHdr: Boolean,
    private val statusTracker: AnimeShaderStatusTracker?,
) : BaseGlShaderProgram(useHdr, OUTPUT_FRAME_CAPACITY) {

    private class CompiledPass(
        val program: Int,
        val positionLocation: Int,
        val samplerLocations: List<Int>,
        val sizeLocations: List<Int>,
        val ptLocations: List<Int>,
        val tapOffsetLocation: Int,
    )

    private class Target(val texId: Int, val fboId: Int, val size: MpvTextureSize)

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(QUAD)
            position(0)
        }

    private var candidates: List<ShaderTierCandidate> = emptyList()
    private var activeTierIndex: Int = 0
    private var fixedOutputSize = MpvTextureSize(0, 0)
    private var plan = MpvShaderPlan(emptyList(), MpvTextureSize(0, 0))
    private val compiled = mutableMapOf<MpvUserShaderPass, CompiledPass>()
    private val pooledTargets = mutableListOf<Target>()
    private val passTargets = mutableListOf<Target?>()
    private var copyPass: CompiledPass? = null
    private var inputSize = MpvTextureSize(0, 0)
    private var disabled = false

    // Live non-blocking GPU FPS governor (active whenever multiple fallback tiers exist, i.e. AUTO)
    private var pendingFence: Long = 0L
    private var framesSinceTierChange: Int = 0
    private val missRing = BooleanArray(GOVERNOR_WINDOW_FRAMES)
    private var missRingCursor: Int = 0
    private var missRingFilled: Int = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        clearPendingFence()
        releaseTargets()
        inputSize = MpvTextureSize(inputWidth, inputHeight)
        fixedOutputSize = inputSize
        plan = MpvShaderPlan(emptyList(), inputSize)
        candidates = emptyList()
        activeTierIndex = 0
        resetGovernor()

        if (!disabled && !useHdr && isGlSupported()) {
            try {
                val gpuTier = DeviceGpuProfiler.detectCurrentGlTier(appContext)
                candidates = AnimeShaders.buildCandidates(
                    context = appContext,
                    upscaleEnabled = upscaleEnabled,
                    lineSharpenEnabled = lineSharpenEnabled,
                    requestedQuality = requestedQuality,
                    sharpenStrength = sharpenStrength,
                    gpuTier = gpuTier,
                    inputWidth = inputWidth,
                    inputHeight = inputHeight,
                )
                if (candidates.isNotEmpty()) {
                    activateTier(index = 0, lockOutputSize = true)
                }
            } catch (error: Exception) {
                Log.w(TAG, "Anime shader setup failed, passing frames through", error)
                disabled = true
                releaseTargets()
                candidates = emptyList()
                plan = MpvShaderPlan(emptyList(), inputSize)
                fixedOutputSize = inputSize
            }
        }
        publishActiveStatus()
        if (copyPass == null) {
            copyPass = try {
                compile(COPY_VERTEX_SHADER, RESAMPLE_FRAGMENT_SHADER, listOf(MAIN))
            } catch (error: IllegalStateException) {
                throw VideoFrameProcessingException(error)
            }
        }
        return Size(fixedOutputSize.width, fixedOutputSize.height)
    }

    private fun activateTier(index: Int, lockOutputSize: Boolean) {
        val candidate = candidates[index]
        releaseTargets()
        val rawPlan = AnimeShaderEffect.planFor(
            passes = candidate.ordered,
            inputWidth = inputSize.width,
            inputHeight = inputSize.height,
            displayLongSide = min(displayLongSide, candidate.maxTargetLongSide).coerceAtLeast(1),
            displayShortSide = min(displayShortSide, candidate.maxTargetShortSide).coerceAtLeast(1),
            minUpscaleLongSide = candidate.maxTargetLongSide,
            minUpscaleShortSide = candidate.maxTargetShortSide,
        )
        val effectiveOutput = if (lockOutputSize) {
            fixedOutputSize = rawPlan.outputSize
            rawPlan.outputSize
        } else {
            fixedOutputSize
        }
        val finalPlan = rawPlan.copy(outputSize = effectiveOutput)
        finalPlan.passes.forEach { planned ->
            if (planned.pass !in compiled) {
                compiled[planned.pass] = compile(ANIME_PASS_VERTEX_SHADER, animePassFragmentShader(planned), planned.binds)
            }
        }
        val slotPlan = planTargetSlots(finalPlan.passes, finalPlan.outputSize)
        val slotByIndex = mutableMapOf<Int, Target>()
        slotPlan.forEach { spec ->
            if (spec == null) {
                passTargets += null
            } else {
                val target = slotByIndex.getOrPut(spec.slotIndex) {
                    val texId = GlUtil.createTexture(spec.size.width, spec.size.height, spec.isHighPrecision)
                    Target(texId = texId, fboId = GlUtil.createFboForTexture(texId), size = spec.size).also {
                        pooledTargets += it
                    }
                }
                passTargets += target
            }
        }
        activeTierIndex = index
        plan = finalPlan
        resetGovernor()
        Log.d(
            TAG,
            "Anime4K active [${candidate.label}]: ${inputSize.width}x${inputSize.height} -> " +
                "${plan.outputSize.width}x${plan.outputSize.height} (${plan.passes.size} passes, ${pooledTargets.size} pooled FBOs)",
        )
    }

    private fun publishActiveStatus() {
        val candidate = candidates.getOrNull(activeTierIndex)
        val status = if (candidate != null) {
            plan.toAnimeEnhancementStatus(inputSize, candidate.sharpen, candidate.upscale, candidate.label)
        } else {
            null
        }
        statusTracker?.publish(this, status)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            val canAdaptDown = !disabled && candidates.size > 1 && activeTierIndex < candidates.lastIndex
            var gpuMissedPreviousFrame = false
            if (pendingFence != 0L) {
                val waitResult = GLES30.glClientWaitSync(pendingFence, 0, 0L)
                GLES30.glDeleteSync(pendingFence)
                pendingFence = 0L
                gpuMissedPreviousFrame = (waitResult == GLES30.GL_TIMEOUT_EXPIRED)
            }

            if (canAdaptDown && framesSinceTierChange >= GOVERNOR_WARMUP_FRAMES) {
                recordGovernorSample(gpuMissedPreviousFrame)
                if (shouldStepDown()) {
                    val nextIndex = activeTierIndex + 1
                    val nextLabel = candidates[nextIndex].label
                    Log.i(
                        TAG,
                        "GPU frame budget exceeded (${countRecentMisses()}/$GOVERNOR_WINDOW_FRAMES frames) — " +
                            "stepping down seamlessly to $nextLabel",
                    )
                    try {
                        activateTier(index = nextIndex, lockOutputSize = false)
                        publishActiveStatus()
                    } catch (error: Exception) {
                        Log.w(TAG, "Adaptive step-down failed, keeping current tier", error)
                        resetGovernor()
                    }
                }
            }
            framesSinceTierChange++

            val outputFbo = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, outputFbo, 0)
            GLES20.glDisable(GLES20.GL_BLEND)
            val textures = mutableMapOf(MAIN to (inputTexId to inputSize))
            var mainWrittenToOutput = false
            plan.passes.forEachIndexed { index, planned ->
                val target = passTargets[index]
                if (target == null) {
                    GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], planned.size.width, planned.size.height)
                    mainWrittenToOutput = true
                } else {
                    GlUtil.focusFramebufferUsingCurrentContext(target.fboId, planned.size.width, planned.size.height)
                }
                draw(compiled.getValue(planned.pass), planned.binds.map { textures.getValue(it) })
                if (target != null) {
                    textures[planned.target] = target.texId to target.size
                }
            }
            if (!mainWrittenToOutput) {
                val main = textures.getValue(MAIN)
                val output = plan.outputSize
                GlUtil.focusFramebufferUsingCurrentContext(outputFbo[0], output.width, output.height)
                val useBicubic = if (main.second == output) 0f to 0f else 1f to 1f
                draw(checkNotNull(copyPass), listOf(main), useBicubic)
            }

            if (!disabled && candidates.size > 1 && activeTierIndex < candidates.lastIndex) {
                pendingFence = GLES30.glFenceSync(GLES30.GL_SYNC_GPU_COMMANDS_COMPLETE, 0)
            }
            GlUtil.checkGlError()
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error, presentationTimeUs)
        }
    }

    private fun resetGovernor() {
        framesSinceTierChange = 0
        missRing.fill(false)
        missRingCursor = 0
        missRingFilled = 0
    }

    private fun recordGovernorSample(missed: Boolean) {
        missRing[missRingCursor] = missed
        missRingCursor = (missRingCursor + 1) % GOVERNOR_WINDOW_FRAMES
        if (missRingFilled < GOVERNOR_WINDOW_FRAMES) missRingFilled++
    }

    private fun countRecentMisses(): Int {
        var count = 0
        for (i in 0 until missRingFilled) {
            if (missRing[i]) count++
        }
        return count
    }

    private fun shouldStepDown(): Boolean =
        missRingFilled >= GOVERNOR_MIN_SAMPLES && countRecentMisses() >= GOVERNOR_MISS_THRESHOLD

    private fun clearPendingFence() {
        if (pendingFence != 0L) {
            runCatching { GLES30.glDeleteSync(pendingFence) }
            pendingFence = 0L
        }
    }

    override fun release() {
        statusTracker?.clear(this)
        clearPendingFence()
        super.release()
        try {
            releaseTargets()
            compiled.values.forEach { GLES20.glDeleteProgram(it.program) }
            compiled.clear()
            copyPass?.let { GLES20.glDeleteProgram(it.program) }
            copyPass = null
        } catch (error: GlUtil.GlException) {
            throw VideoFrameProcessingException(error)
        }
    }

    private fun releaseTargets() {
        pooledTargets.forEach { target ->
            GlUtil.deleteFbo(target.fboId)
            GlUtil.deleteTexture(target.texId)
        }
        pooledTargets.clear()
        passTargets.clear()
    }

    private fun draw(
        pass: CompiledPass,
        inputs: List<Pair<Int, MpvTextureSize>>,
        tapOffset: Pair<Float, Float> = 0f to 0f,
    ) {
        GLES20.glUseProgram(pass.program)
        if (pass.tapOffsetLocation >= 0) {
            GLES20.glUniform2f(pass.tapOffsetLocation, tapOffset.first, tapOffset.second)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        inputs.forEachIndexed { unit, (texId, size) ->
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            GLES20.glUniform1i(pass.samplerLocations[unit], unit)
            if (pass.sizeLocations[unit] >= 0) {
                GLES20.glUniform2f(pass.sizeLocations[unit], size.width.toFloat(), size.height.toFloat())
            }
            if (pass.ptLocations[unit] >= 0) {
                GLES20.glUniform2f(pass.ptLocations[unit], 1f / size.width.toFloat(), 1f / size.height.toFloat())
            }
        }
        quad.position(0)
        GLES20.glEnableVertexAttribArray(pass.positionLocation)
        GLES20.glVertexAttribPointer(pass.positionLocation, 2, GLES20.GL_FLOAT, false, 0, quad)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pass.positionLocation)
    }

    private fun compile(vertexSource: String, fragmentSource: String, binds: List<String>): CompiledPass {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = try {
            compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        } catch (error: Exception) {
            GLES20.glDeleteShader(vertex)
            throw error
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IllegalStateException("Anime shader link failed: $log")
        }
        return CompiledPass(
            program = program,
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition"),
            samplerLocations = binds.map { GLES20.glGetUniformLocation(program, "${it}_raw") },
            sizeLocations = binds.map { GLES20.glGetUniformLocation(program, "${it}_size") },
            ptLocations = binds.map { GLES20.glGetUniformLocation(program, "${it}_pt") },
            tapOffsetLocation = GLES20.glGetUniformLocation(program, "uTapOffset"),
        )
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Anime shader compile failed: $log")
        }
        return shader
    }

    private fun isGlSupported(): Boolean {
        val version = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
        val match = Regex("OpenGL ES (\\d+)\\.(\\d+)").find(version) ?: return false
        val major = match.groupValues[1].toInt()
        val minor = match.groupValues[2].toInt()
        if (major < 3) return false
        if (major > 3 || minor >= 2) return true
        val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
        return "GL_EXT_color_buffer_half_float" in extensions || "GL_EXT_color_buffer_float" in extensions
    }

    private companion object {
        const val TAG = "AnimeShaderEffect"
        const val OUTPUT_FRAME_CAPACITY = 3
        const val MAIN = "MAIN"
        const val GOVERNOR_WARMUP_FRAMES = 8
        const val GOVERNOR_WINDOW_FRAMES = 12
        const val GOVERNOR_MIN_SAMPLES = 6
        const val GOVERNOR_MISS_THRESHOLD = 5

        val QUAD = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )

        const val COPY_VERTEX_SHADER = "attribute vec2 aPosition;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vTexCoord = aPosition * 0.5 + 0.5;\n" +
            "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "}\n"

        // Hardware-bilinear 9-tap exact Catmull-Rom bicubic resampler (folds the 4 positive inner
        // weights of 16-tap Catmull-Rom into 1 bilinear tap per axis -> 44% fewer 4K texture
        // fetches, mathematically identical sharpness + anti-ringing clamp). Fast 1-tap when 1:1.
        const val RESAMPLE_FRAGMENT_SHADER = "#ifdef GL_FRAGMENT_PRECISION_HIGH\n" +
            "#define HP highp\n" +
            "#else\n" +
            "#define HP mediump\n" +
            "#endif\n" +
            "precision mediump float;\n" +
            "varying HP vec2 vTexCoord;\n" +
            "uniform sampler2D MAIN_raw;\n" +
            "uniform HP vec2 MAIN_size;\n" +
            "uniform HP vec2 MAIN_pt;\n" +
            "uniform vec2 uTapOffset;\n" +
            "vec4 catmullRomWeights(float f) {\n" +
            "    float f2 = f * f;\n" +
            "    float f3 = f2 * f;\n" +
            "    return vec4(\n" +
            "        -0.5 * f3 + f2 - 0.5 * f,\n" +
            "         1.5 * f3 - 2.5 * f2 + 1.0,\n" +
            "        -1.5 * f3 + 2.0 * f2 + 0.5 * f,\n" +
            "         0.5 * f3 - 0.5 * f2\n" +
            "    );\n" +
            "}\n" +
            "void main() {\n" +
            "    if (uTapOffset.x <= 0.0) {\n" +
            "        gl_FragColor = vec4(clamp(texture2D(MAIN_raw, vTexCoord).rgb, 0.0, 1.0), 1.0);\n" +
            "        return;\n" +
            "    }\n" +
            "    HP vec2 pixelPos = vTexCoord * MAIN_size - 0.5;\n" +
            "    HP vec2 f = fract(pixelPos);\n" +
            "    HP vec2 base = (floor(pixelPos) + 0.5) * MAIN_pt;\n" +
            "    vec4 wx = catmullRomWeights(float(f.x));\n" +
            "    vec4 wy = catmullRomWeights(float(f.y));\n" +
            "    float wx12 = wx.y + wx.z;\n" +
            "    float wy12 = wy.y + wy.z;\n" +
            "    HP float x0 = base.x - MAIN_pt.x;\n" +
            "    HP float x1 = base.x + (wx.z / wx12) * MAIN_pt.x;\n" +
            "    HP float x2 = base.x + 2.0 * MAIN_pt.x;\n" +
            "    HP float y0 = base.y - MAIN_pt.y;\n" +
            "    HP float y1 = base.y + (wy.z / wy12) * MAIN_pt.y;\n" +
            "    HP float y2 = base.y + 2.0 * MAIN_pt.y;\n" +
            "    vec3 c00 = texture2D(MAIN_raw, vec2(x0, y0)).rgb;\n" +
            "    vec3 c10 = texture2D(MAIN_raw, vec2(x1, y0)).rgb;\n" +
            "    vec3 c20 = texture2D(MAIN_raw, vec2(x2, y0)).rgb;\n" +
            "    vec3 c01 = texture2D(MAIN_raw, vec2(x0, y1)).rgb;\n" +
            "    vec3 c11 = texture2D(MAIN_raw, vec2(x1, y1)).rgb;\n" +
            "    vec3 c21 = texture2D(MAIN_raw, vec2(x2, y1)).rgb;\n" +
            "    vec3 c02 = texture2D(MAIN_raw, vec2(x0, y2)).rgb;\n" +
            "    vec3 c12 = texture2D(MAIN_raw, vec2(x1, y2)).rgb;\n" +
            "    vec3 c22 = texture2D(MAIN_raw, vec2(x2, y2)).rgb;\n" +
            "    vec3 row0 = c00 * wx.x + c10 * wx12 + c20 * wx.w;\n" +
            "    vec3 row1 = c01 * wx.x + c11 * wx12 + c21 * wx.w;\n" +
            "    vec3 row2 = c02 * wx.x + c12 * wx12 + c22 * wx.w;\n" +
            "    vec3 sum = row0 * wy.x + row1 * wy12 + row2 * wy.w;\n" +
            "    vec3 lo = min(c11, min(min(c10, c12), min(c01, c21)));\n" +
            "    vec3 hi = max(c11, max(max(c10, c12), max(c01, c21)));\n" +
            "    gl_FragColor = vec4(clamp(clamp(sum, lo, hi), 0.0, 1.0), 1.0);\n" +
            "}\n"
    }
}

internal const val ANIME_PASS_VERTEX_SHADER = "#version 300 es\n" +
    "in vec2 aPosition;\n" +
    "out vec2 vTexCoord;\n" +
    "void main() {\n" +
    "    vTexCoord = aPosition * 0.5 + 0.5;\n" +
    "    gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
    "}\n"

internal fun animePassFragmentShader(planned: MpvPlannedPass): String = buildString {
    val requiresHighpFloat = planned.pass.body.contains("fract(")
    append("#version 300 es\n")
    append(if (requiresHighpFloat) "precision highp float;\n" else "precision mediump float;\n")
    append("precision highp int;\n")
    append("in highp vec2 vTexCoord;\n")
    append("out vec4 nuvioFragColor;\n")
    append("highp vec2 nuvioPos;\n")
    planned.binds.forEach { name ->
        append("uniform sampler2D ${name}_raw;\n")
        append("uniform highp vec2 ${name}_size;\n")
        append("uniform highp vec2 ${name}_pt;\n")
        append("#define ${name}_pos nuvioPos\n")
        append("#define ${name}_mul 1.0\n")
        append("#define ${name}_tex(pos) texture(${name}_raw, vec2((pos).x, 1.0 - (pos).y))\n")
        append("#define ${name}_texOff(off) ${name}_tex(${name}_pos + ${name}_pt * vec2(off))\n")
    }
    if ("MAIN" in planned.binds) {
        append("#define HOOKED_raw MAIN_raw\n")
        append("#define HOOKED_size MAIN_size\n")
        append("#define HOOKED_pt MAIN_pt\n")
        append("#define HOOKED_pos MAIN_pos\n")
        append("#define HOOKED_mul MAIN_mul\n")
        append("#define HOOKED_tex(pos) MAIN_tex(pos)\n")
        append("#define HOOKED_texOff(off) MAIN_texOff(off)\n")
    }
    append(planned.pass.body)
    append("\nvoid main() {\n")
    append("    nuvioPos = vec2(vTexCoord.x, 1.0 - vTexCoord.y);\n")
    if (planned.target == "MAIN") {
        append("    nuvioFragColor = vec4(clamp(hook().rgb, 0.0, 1.0), 1.0);\n")
    } else {
        append("    nuvioFragColor = hook();\n")
    }
    append("}\n")
}
