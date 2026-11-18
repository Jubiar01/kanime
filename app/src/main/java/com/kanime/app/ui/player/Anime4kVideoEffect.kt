package com.kanime.app.ui.player

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.kanime.app.data.api.AnimeUpscalerMode
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Real-time GPU Anime4K Upscaler + Anime Line Sharpening effect for Media3 ExoPlayer.
 *
 * Combines:
 * 1. Contrast-Adaptive Sharpening (AMD CAS formulation) + 1.5x / 2x (up to 4K UHD) GPU supersampling
 *    to remove compression blur without amplifying noise in flat cel-shaded regions.
 * 2. Anime4K Sobel gradient line detection & dark-contour thinning to crisp blurry anime ink lines
 *    while clamping strictly to the local neighborhood to avoid white ringing halos.
 */
@UnstableApi
class Anime4kVideoEffect(
    val mode: AnimeUpscalerMode,
    val lineSharpeningEnabled: Boolean,
    val lineSharpeningStrength: Float
) : GlEffect {

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        return mode == AnimeUpscalerMode.OFF && !lineSharpeningEnabled
    }

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return Anime4kShaderProgram(
            useHdr = useHdr,
            mode = mode,
            lineSharpeningEnabled = lineSharpeningEnabled,
            lineSharpeningStrength = lineSharpeningStrength.coerceIn(0.1f, 1.0f)
        )
    }
}

@UnstableApi
private class Anime4kShaderProgram(
    useHdr: Boolean,
    private val mode: AnimeUpscalerMode,
    private val lineSharpeningEnabled: Boolean,
    private val lineSharpeningStrength: Float
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val glProgram: GlProgram
    private var texelSize = floatArrayOf(1f / 1920f, 1f / 1080f)

    init {
        try {
            glProgram = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            glProgram.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        val safeW = inputWidth.coerceAtLeast(1)
        val safeH = inputHeight.coerceAtLeast(1)
        texelSize = floatArrayOf(1f / safeW.toFloat(), 1f / safeH.toFloat())

        if (mode == AnimeUpscalerMode.OFF) {
            return Size(safeW, safeH)
        }

        // Respect both preset cap (e.g. 3840x2160 for 4K) and device GL_MAX_TEXTURE_SIZE
        val maxTexArr = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexArr, 0)
        val gpuMax = if (maxTexArr[0] > 0) maxTexArr[0] else 4096

        val capW = min(mode.maxWidth, gpuMax).toFloat()
        val capH = min(mode.maxHeight, gpuMax).toFloat()

        val desiredScale = mode.scaleFactor
        val maxAllowedScale = min(capW / safeW.toFloat(), capH / safeH.toFloat())
        val finalScale = min(desiredScale, maxAllowedScale).coerceAtLeast(1.0f)

        val outW = ((safeW * finalScale).roundToInt() and 1.inv()).coerceAtLeast(safeW)
        val outH = ((safeH * finalScale).roundToInt() and 1.inv()).coerceAtLeast(safeH)
        return Size(outW, outH)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            glProgram.use()
            glProgram.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            glProgram.setFloatsUniform("uTexelSize", texelSize)
            glProgram.setFloatUniform("uUpscaleStrength", mode.upscaleStrength)
            glProgram.setFloatUniform(
                "uLineStrength",
                if (lineSharpeningEnabled) lineSharpeningStrength else 0f
            )
            glProgram.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            glProgram.delete()
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = vec2(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uUpscaleStrength;
            uniform float uLineStrength;
            varying vec2 vTexSamplingCoord;

            float getLuma(vec3 c) {
                return dot(c, vec3(0.299, 0.587, 0.114));
            }

            void main() {
                vec2 uv = vTexSamplingCoord;
                vec2 dx = vec2(uTexelSize.x, 0.0);
                vec2 dy = vec2(0.0, uTexelSize.y);

                // 3x3 neighborhood samples
                vec4 centerSample = texture2D(uTexSampler, uv);
                vec3 c  = centerSample.rgb;
                vec3 n  = texture2D(uTexSampler, uv - dy).rgb;
                vec3 s  = texture2D(uTexSampler, uv + dy).rgb;
                vec3 w  = texture2D(uTexSampler, uv - dx).rgb;
                vec3 e  = texture2D(uTexSampler, uv + dx).rgb;
                vec3 nw = texture2D(uTexSampler, uv - dx - dy).rgb;
                vec3 ne = texture2D(uTexSampler, uv + dx - dy).rgb;
                vec3 sw = texture2D(uTexSampler, uv - dx + dy).rgb;
                vec3 se = texture2D(uTexSampler, uv + dx + dy).rgb;

                vec3 minCross = min(min(min(n, s), min(w, e)), c);
                vec3 maxCross = max(max(max(n, s), max(w, e)), c);
                vec3 minAll = min(minCross, min(min(nw, ne), min(sw, se)));
                vec3 maxAll = max(maxCross, max(max(nw, ne), max(sw, se)));

                vec3 result = c;

                // ── Pass 1: Anime4K / Contrast-Adaptive Sharpening (CAS) ──
                if (uUpscaleStrength > 0.001) {
                    vec3 minRGB = (minCross + minAll) * 0.5;
                    vec3 maxRGB = (maxCross + maxAll) * 0.5;
                    vec3 amp = clamp(min(minRGB, 1.0 - maxRGB) / max(maxRGB, vec3(0.001)), 0.0, 1.0);
                    amp = sqrt(amp);

                    // Adaptive sharpening peak tuned for cel-shaded anime (avoids noise in flat fills)
                    float peak = -1.0 / mix(9.0, 5.2, clamp(uUpscaleStrength, 0.0, 1.0));
                    vec3 weight = amp * peak;
                    vec3 cas = (c + (n + s + w + e) * weight) / (1.0 + 4.0 * weight);

                    // Subtle diagonal edge boost for 2x/4K supersampled clarity
                    vec3 diagAvg = (nw + ne + sw + se) * 0.25;
                    vec3 detail = (c - diagAvg) * (0.14 * uUpscaleStrength);
                    result = clamp(cas + detail, minAll, maxAll);
                }

                // ── Pass 2: Anime Line Sharpening (Sobel Edge + Dark Contour Thinning) ──
                if (uLineStrength > 0.001) {
                    float lC  = getLuma(c);
                    float lN  = getLuma(n);
                    float lS  = getLuma(s);
                    float lW  = getLuma(w);
                    float lE  = getLuma(e);
                    float lNW = getLuma(nw);
                    float lNE = getLuma(ne);
                    float lSW = getLuma(sw);
                    float lSE = getLuma(se);

                    // Sobel luminance gradient to isolate anime ink outlines
                    float gx = -lNW - 2.0 * lW - lSW + lNE + 2.0 * lE + lSE;
                    float gy = -lNW - 2.0 * lN - lNE + lSW + 2.0 * lS + lSE;
                    float grad = clamp(length(vec2(gx, gy)), 0.0, 1.0);

                    // Soft threshold so flat shaded colors stay completely untouched
                    float edgeMask = smoothstep(0.08, 0.42, grad);

                    // Find the darkest neighbor (anime ink lines are darker than surrounding cel fills)
                    vec3 darkest = c;
                    float minL = lC;
                    if (lN  < minL) { minL = lN;  darkest = n;  }
                    if (lS  < minL) { minL = lS;  darkest = s;  }
                    if (lW  < minL) { minL = lW;  darkest = w;  }
                    if (lE  < minL) { minL = lE;  darkest = e;  }
                    if (lNW < minL) { minL = lNW; darkest = nw; }
                    if (lNE < minL) { minL = lNE; darkest = ne; }
                    if (lSW < minL) { minL = lSW; darkest = sw; }
                    if (lSE < minL) { minL = lSE; darkest = se; }

                    // Thin blurred edges toward the dark ink contour & crisp edge contrast
                    float thinAmount = edgeMask * (0.38 * clamp(uLineStrength, 0.0, 1.0));
                    vec3 thinned = mix(result, darkest, thinAmount);

                    // Local unsharp edge pop clamped safely to [minAll, maxAll] (zero halo ringing)
                    vec3 blur = (n + s + w + e) * 0.25;
                    vec3 lineCrisp = thinned + (thinned - blur) * (0.45 * edgeMask * uLineStrength);
                    result = clamp(lineCrisp, minAll, maxAll);
                }

                gl_FragColor = vec4(clamp(result, 0.0, 1.0), centerSample.a);
            }
        """
    }
}
