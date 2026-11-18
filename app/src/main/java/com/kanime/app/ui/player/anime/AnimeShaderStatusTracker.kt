package com.kanime.app.ui.player.anime

data class AnimeEnhancementStatus(
    val lineSharpen: Boolean,
    val upscale: Boolean,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val outputWidth: Int,
    val outputHeight: Int,
    val tierLabel: String = "",
) {
    fun badgeLabel(): String {
        val tierSuffix = if (tierLabel.isNotBlank()) " $tierLabel" else ""
        return when {
            upscale && lineSharpen -> "A4K 2x$tierSuffix • SHARP"
            upscale -> "A4K 2x$tierSuffix"
            lineSharpen -> "A4K SHARP$tierSuffix"
            else -> "1080p"
        }
    }

    fun detailLabel(): String = buildString {
        if (upscale) append("2x Upscale")
        if (upscale && lineSharpen) append(" + ")
        if (lineSharpen) append("Line Sharpen")
        if (tierLabel.isNotBlank()) append(" [$tierLabel]")
        if (sourceWidth > 0 && outputWidth > 0) {
            append(" (${sourceWidth}×${sourceHeight} → ${outputWidth}×${outputHeight})")
        }
    }
}

internal class AnimeShaderStatusTracker(
    private val onChanged: (AnimeEnhancementStatus?) -> Unit,
) {
    private var owner: Any? = null
    private var status: AnimeEnhancementStatus? = null

    @Synchronized
    fun publish(source: Any, status: AnimeEnhancementStatus?) {
        owner = source
        if (this.status == status) return
        this.status = status
        onChanged(status)
    }

    @Synchronized
    fun clear(source: Any) {
        if (owner !== source) return
        owner = null
        if (status == null) return
        status = null
        onChanged(null)
    }
}

internal fun MpvShaderPlan.toAnimeEnhancementStatus(
    input: MpvTextureSize,
    sharpenPasses: List<MpvUserShaderPass>,
    upscalePasses: List<MpvUserShaderPass>,
    tierLabel: String = "",
): AnimeEnhancementStatus? {
    val lineSharpen = passes.any { it.pass in sharpenPasses }
    val upscale = passes.any { it.pass in upscalePasses }
    if (!lineSharpen && !upscale) return null
    return AnimeEnhancementStatus(
        lineSharpen = lineSharpen,
        upscale = upscale,
        sourceWidth = input.width,
        sourceHeight = input.height,
        outputWidth = outputSize.width,
        outputHeight = outputSize.height,
        tierLabel = tierLabel,
    )
}
