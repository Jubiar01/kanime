package com.kanime.app.ui.player

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.kanime.app.R

enum class CaptionFont(val id: String, val label: String) {
    GEIST("geist", "Geist"),
    SANS_SERIF("sans_serif", "Sans-Serif"),
    SERIF("serif", "Serif"),
    MONOSPACE("monospace", "Monospace"),
    CASUAL("casual", "Casual");

    companion object {
        fun fromId(id: String?): CaptionFont = entries.firstOrNull { it.id == id } ?: GEIST
    }
}

enum class CaptionTextSize(
    val id: String,
    val label: String,
    val fractionalSize: Float,
    val previewSp: Float
) {
    SMALL("small", "Small", 0.042f, 12f),
    MEDIUM("medium", "Medium", 0.054f, 15f),
    LARGE("large", "Large", 0.068f, 18f),
    EXTRA_LARGE("xl", "XL", 0.084f, 21f);

    companion object {
        fun fromId(id: String?): CaptionTextSize = entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class CaptionColor(val id: String, val label: String, val argb: Int) {
    WHITE("white", "White", 0xFFFFFFFF.toInt()),
    YELLOW("yellow", "Yellow", 0xFFFFE066.toInt()),
    CYAN("cyan", "Cyan", 0xFF70FBF7.toInt()),
    GREEN("green", "Green", 0xFF86EFAC.toInt()),
    PINK("pink", "Pink", 0xFFFFA6C9.toInt());

    companion object {
        fun fromId(id: String?): CaptionColor = entries.firstOrNull { it.id == id } ?: WHITE
    }
}

enum class CaptionEdgeStyle(val id: String, val label: String, val compatEdgeType: Int) {
    OUTLINE("outline", "Outline", CaptionStyleCompat.EDGE_TYPE_OUTLINE),
    DROP_SHADOW("shadow", "Shadow", CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW),
    RAISED("raised", "Raised", CaptionStyleCompat.EDGE_TYPE_RAISED),
    NONE("none", "None", CaptionStyleCompat.EDGE_TYPE_NONE);

    companion object {
        fun fromId(id: String?): CaptionEdgeStyle = entries.firstOrNull { it.id == id } ?: OUTLINE
    }
}

enum class CaptionBackground(val id: String, val label: String, val argb: Int) {
    NONE("none", "None", AndroidColor.TRANSPARENT),
    TRANSLUCENT("translucent", "Translucent", 0x88000000.toInt()),
    SOLID_BLACK("solid_black", "Black Box", 0xDD000000.toInt());

    companion object {
        fun fromId(id: String?): CaptionBackground = entries.firstOrNull { it.id == id } ?: NONE
    }
}

enum class CaptionPosition(val id: String, val label: String, val bottomPaddingFraction: Float) {
    LOW("low", "Low", 0.045f),
    NORMAL("normal", "Normal", 0.08f),
    HIGH("high", "High", 0.145f);

    companion object {
        fun fromId(id: String?): CaptionPosition = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}

data class CaptionStyleSettings(
    val font: CaptionFont = CaptionFont.GEIST,
    val textSize: CaptionTextSize = CaptionTextSize.MEDIUM,
    val textColor: CaptionColor = CaptionColor.WHITE,
    val edgeStyle: CaptionEdgeStyle = CaptionEdgeStyle.OUTLINE,
    val background: CaptionBackground = CaptionBackground.NONE,
    val position: CaptionPosition = CaptionPosition.NORMAL,
    val isBold: Boolean = true
)

@UnstableApi
fun applyCaptionStyle(context: Context, subtitleView: SubtitleView?, style: CaptionStyleSettings) {
    subtitleView ?: return
    val typefaceStyle = if (style.isBold) Typeface.BOLD else Typeface.NORMAL
    val typeface = when (style.font) {
        CaptionFont.GEIST -> {
            val fontRes = if (style.isBold) R.font.geist_bold else R.font.geist_medium
            ResourcesCompat.getFont(context, fontRes) ?: Typeface.create(Typeface.SANS_SERIF, typefaceStyle)
        }
        CaptionFont.SANS_SERIF -> Typeface.create(Typeface.SANS_SERIF, typefaceStyle)
        CaptionFont.SERIF -> Typeface.create(Typeface.SERIF, typefaceStyle)
        CaptionFont.MONOSPACE -> Typeface.create(Typeface.MONOSPACE, typefaceStyle)
        CaptionFont.CASUAL -> Typeface.create("casual", typefaceStyle)
    }

    val compatStyle = CaptionStyleCompat(
        style.textColor.argb,
        style.background.argb,
        AndroidColor.TRANSPARENT,
        style.edgeStyle.compatEdgeType,
        AndroidColor.BLACK,
        typeface
    )
    subtitleView.setApplyEmbeddedStyles(false)
    subtitleView.setApplyEmbeddedFontSizes(false)
    subtitleView.setStyle(compatStyle)
    subtitleView.setFractionalTextSize(style.textSize.fractionalSize)
    subtitleView.setBottomPaddingFraction(style.position.bottomPaddingFraction)
}
