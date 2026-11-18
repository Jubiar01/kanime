package com.kanime.app

import com.kanime.app.ui.player.anime.AnimeEnhancementStatus
import com.kanime.app.ui.player.anime.AnimeShaderEffect
import com.kanime.app.ui.player.anime.AnimeShaderStatusTracker
import com.kanime.app.ui.player.anime.AnimeShaders
import com.kanime.app.ui.player.anime.AnimeSharpenStrength
import com.kanime.app.ui.player.anime.AnimeUpscaleQuality
import com.kanime.app.ui.player.anime.DeviceGpuProfiler
import com.kanime.app.ui.player.anime.EffectiveShaderTier
import com.kanime.app.ui.player.anime.GpuTier
import com.kanime.app.ui.player.anime.MpvTextureSize
import com.kanime.app.ui.player.anime.MpvUserShaderParser
import com.kanime.app.ui.player.anime.MpvUserShaderPass
import com.kanime.app.ui.player.anime.animePassFragmentShader
import com.kanime.app.ui.player.anime.planTargetSlots
import com.kanime.app.ui.player.anime.toAnimeEnhancementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MpvUserShaderTest {
    private fun assetFile(name: String): File {
        val candidates = listOf(
            File("src/main/assets/anime4k/$name"),
            File("app/src/main/assets/anime4k/$name"),
        )
        return candidates.first { it.exists() }
    }

    private fun shader(name: String): List<MpvUserShaderPass> =
        MpvUserShaderParser.parse(assetFile(name).readText())

    private fun upscaleShader(name: String): List<MpvUserShaderPass> =
        MpvUserShaderParser.parse(AnimeShaders.withMinimumUpscaleTarget(assetFile(name).readText()))

    private val clampHighlights = shader("Anime4K_Clamp_Highlights.glsl")
    private val upscaleS = upscaleShader("Anime4K_Upscale_CNN_x2_S.glsl")
    private val upscaleM = upscaleShader("Anime4K_Upscale_CNN_x2_M.glsl")
    private val upscaleDenoiseM = upscaleShader("Anime4K_Upscale_Denoise_CNN_x2_M.glsl")
    private val restoreS = shader("Anime4K_Restore_CNN_S.glsl")
    private val restoreM = shader("Anime4K_Restore_CNN_M.glsl")
    private val finalSharpen = shader("Nuvio_Line_Sharpen.glsl")
    private val darken = shader("Anime4K_Darken_HQ.glsl")
    private val thin = shader("Anime4K_Thin_HQ.glsl")

    @Test
    fun parsesAllBundledAnime4kShaders() {
        assertEquals(3, clampHighlights.size)
        assertEquals("PREKERNEL", clampHighlights.last().hook)
        assertEquals(5, upscaleS.size)
        assertEquals(9, upscaleM.size)
        assertEquals(9, upscaleDenoiseM.size)
        assertEquals(4, restoreS.size)
        assertEquals(8, restoreM.size)
        assertEquals(5, darken.size)
        assertEquals(8, thin.size)
        assertEquals(1, finalSharpen.size)
        assertEquals(listOf("MAIN", "conv2d_last_tf"), upscaleM.last().binds)
        assertEquals("MAIN", upscaleM.last().save)
        assertEquals("conv2d_last_tf.w 2 *", upscaleM.last().width)
        assertTrue(upscaleM.all { it.hook == "MAIN" && it.body.contains("vec4 hook()") })
        assertTrue(upscaleDenoiseM.all { it.hook == "MAIN" && it.body.contains("vec4 hook()") })
        assertTrue(restoreM.none { it.body.contains("//!") })
    }

    @Test
    fun plansFullHighChainWithPrekernelHighlightClamp() {
        val chain = clampHighlights + restoreM + darken + thin + upscaleM + finalSharpen
        val plan = AnimeShaderEffect.planFor(chain, 1920, 1080, 2712, 1220)
        assertEquals(chain.size, plan.passes.size)
        // PREKERNEL clamp pass must execute LAST at upscaled 3840x2160 resolution
        assertEquals("PREKERNEL", plan.passes.last().pass.hook)
        assertEquals(listOf("MAIN", "STATSMAX"), plan.passes.last().binds)
        assertEquals(MpvTextureSize(3840, 2160), plan.passes.last().size)
        assertEquals(MpvTextureSize(2560, 1440), plan.outputSize)
        assertEquals(
            AnimeEnhancementStatus(true, true, 1920, 1080, 2560, 1440),
            plan.toAnimeEnhancementStatus(MpvTextureSize(1920, 1080), restoreM + darken + thin + finalSharpen, upscaleM),
        )
        assertTrue(animePassFragmentShader(plan.passes.last()).contains("clamp(hook().rgb, 0.0, 1.0)"))
    }

    @Test
    fun fboPoolReusesTexturesWithoutReadWriteAliasing() {
        val chain = clampHighlights + restoreM + darken + thin + upscaleDenoiseM + finalSharpen
        val plan = AnimeShaderEffect.planFor(chain, 1920, 1080, 2712, 1220)
        val slots = planTargetSlots(plan.passes, plan.outputSize)
        assertEquals(plan.passes.size, slots.size)

        // Verify zero read/write aliasing across every pass
        val activeSlotByName = mutableMapOf<String, Int>("MAIN" to -1)
        plan.passes.forEachIndexed { index, planned ->
            val writeSlot = slots[index]?.slotIndex ?: -2
            planned.binds.forEach { bindName ->
                val readSlot = activeSlotByName[bindName] ?: -1
                if (readSlot >= 0 && writeSlot >= 0) {
                    assertNotEquals(
                        "Pass $index (${planned.pass.description}) aliased read '$bindName' and write '${planned.target}' on slot $writeSlot",
                        readSlot,
                        writeSlot,
                    )
                }
            }
            if (writeSlot >= 0) {
                activeSlotByName[planned.target] = writeSlot
            }
        }

        // Verify dramatic VRAM reduction compared to unpooled per-pass FBO allocation
        val uniqueSlots = slots.filterNotNull().map { it.slotIndex }.toSet().size
        assertTrue("Expected <= 14 pooled FBOs for ${plan.passes.size} passes, got $uniqueSlots", uniqueSlots <= 14)
    }

    @Test
    fun tunesSharpenStrengthDefinesCleanly() {
        val raw = assetFile("Nuvio_Line_Sharpen.glsl").readText()
        val crisp = AnimeShaders.withSharpenStrength(raw, AnimeSharpenStrength.CRISP)
        assertTrue(crisp.contains("#define NUVIO_SHARPEN_STRENGTH 6.20"))
        assertTrue(crisp.contains("#define NUVIO_DARK_OVERSHOOT 0.38"))
        assertTrue(crisp.contains("#define NUVIO_BRIGHT_OVERSHOOT 0.16"))
        assertTrue(crisp.contains("#define NUVIO_LINE_DARKEN 0.22"))
        val parsed = MpvUserShaderParser.parse(crisp)
        assertEquals(1, parsed.size)
        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_M.glsl",
                "Anime4K_Darken_HQ.glsl",
                "Anime4K_Thin_HQ.glsl",
                "Nuvio_Line_Sharpen.glsl",
                "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
            ),
            AnimeShaders.shaderNames(true, true, AnimeUpscaleQuality.ULTRA, highResInput = false),
        )
        assertEquals(
            listOf(
                "Anime4K_Clamp_Highlights.glsl",
                "Anime4K_Restore_CNN_S.glsl",
                "Anime4K_Darken_HQ.glsl",
                "Nuvio_Line_Sharpen.glsl",
                "Anime4K_Upscale_Denoise_CNN_x2_M.glsl",
            ),
            AnimeShaders.shaderNames(true, true, AnimeUpscaleQuality.ULTRA, highResInput = true),
        )
    }

    @Test
    fun classifiesDeviceGpusAndBuildsAdaptiveTierLadders() {
        assertEquals(GpuTier.HIGH, DeviceGpuProfiler.classify("Adreno (TM) 830"))
        assertEquals(GpuTier.HIGH, DeviceGpuProfiler.classify("Adreno (TM) 740"))
        assertEquals(GpuTier.HIGH, DeviceGpuProfiler.classify("Immortalis-G720 MC12"))
        assertEquals(GpuTier.MEDIUM, DeviceGpuProfiler.classify("Mali-G615 MC6"))
        assertEquals(GpuTier.MEDIUM, DeviceGpuProfiler.classify("Adreno (TM) 725"))
        assertEquals(GpuTier.LOW, DeviceGpuProfiler.classify("Mali-G57 MC2"))
        assertEquals(GpuTier.LOW, DeviceGpuProfiler.classify("Adreno (TM) 619"))
        assertEquals(GpuTier.LOW, DeviceGpuProfiler.classify("Adreno (TM) 750", isLowRam = true))

        assertEquals(
            listOf(EffectiveShaderTier.HIGH, EffectiveShaderTier.FAST, EffectiveShaderTier.ULTRA_LIGHT),
            AnimeShaders.resolveTierLadder(AnimeUpscaleQuality.AUTO, GpuTier.MEDIUM, highResInput = true),
        )
        assertEquals(
            listOf(EffectiveShaderTier.FAST, EffectiveShaderTier.ULTRA_LIGHT),
            AnimeShaders.resolveTierLadder(AnimeUpscaleQuality.AUTO, GpuTier.LOW, highResInput = true),
        )
        assertEquals(
            listOf(EffectiveShaderTier.ULTRA, EffectiveShaderTier.HIGH, EffectiveShaderTier.FAST, EffectiveShaderTier.ULTRA_LIGHT),
            AnimeShaders.resolveTierLadder(AnimeUpscaleQuality.AUTO, GpuTier.HIGH, highResInput = true),
        )
    }

    @Test
    fun statusTrackerIgnoresStaleClears() {
        val updates = mutableListOf<AnimeEnhancementStatus?>()
        val tracker = AnimeShaderStatusTracker { updates += it }
        val oldProgram = Any()
        val newProgram = Any()
        val status = AnimeEnhancementStatus(true, false, 1920, 1080, 1920, 1080)
        tracker.publish(oldProgram, status)
        tracker.publish(newProgram, status)
        tracker.clear(oldProgram)
        assertEquals(listOf<AnimeEnhancementStatus?>(status), updates)
        tracker.clear(newProgram)
        assertEquals(listOf(status, null), updates)
    }

    @Test
    fun evaluatesMpvExpressions() {
        val sizes = mapOf("MAIN" to (1280f to 720f), "OUTPUT" to (2560f to 1440f))
        val lookup = { name: String, dimension: Char ->
            sizes[name]?.let { if (dimension == 'w') it.first else it.second }
        }
        assertEquals(1f, MpvUserShaderParser.evaluate("OUTPUT.w MAIN.w / 1.200 > OUTPUT.h MAIN.h / 1.200 > *", lookup))
        assertEquals(2560f, MpvUserShaderParser.evaluate("MAIN.width 2 *", lookup))
        assertEquals(0f, MpvUserShaderParser.evaluate("1 !", lookup))
        assertNull(MpvUserShaderParser.evaluate("MISSING.w", lookup))
    }
}
