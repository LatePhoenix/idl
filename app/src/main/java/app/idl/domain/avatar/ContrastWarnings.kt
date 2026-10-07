package app.idl.domain.avatar

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Non-blocking contrast hints for the editor (AP-9). Pure domain; no Android.
 * Thresholds are intentional and stable — do not tune per screenshot.
 */
object ContrastWarnings {
    /** OKLab ΔE-ish distance below which two slots read as the same color. */
    const val SIMILAR_DISTANCE = 0.08

    /** WCAG contrast ratio below which a fill is weak against the outline. */
    const val OUTLINE_RATIO = 3.0

    data class Warning(
        val code: String,
        val message: String,
    )

    fun evaluate(slots: Map<String, Int>): List<Warning> {
        val out = ArrayList<Warning>(4)
        similar(slots, "hair.primary", "face.primary", "hair_matches_skin",
            "Hair is very close to skin.")?.let(out::add)
        similar(slots, "beard.primary", "face.primary", "beard_matches_skin",
            "Facial hair is very close to skin.")?.let(out::add)
        similar(slots, "top.primary", "bg.primary", "top_matches_background",
            "Top is very close to the background.")?.let(out::add)
        outline(slots, "face.primary", "face_outline_low",
            "Skin has low contrast against the outline.")?.let(out::add)
        outline(slots, "hair.primary", "hair_outline_low",
            "Hair has low contrast against the outline.")?.let(out::add)
        return out
    }

    /** True when [hex] has enough contrast against [outlineArgb] for a swatch chip. */
    fun contrastsWithOutline(hex: String, outlineArgb: Int, minRatio: Double = OUTLINE_RATIO): Boolean {
        val color = ColorSlots.parseHex(hex) ?: return false
        return contrastRatio(color, outlineArgb) >= minRatio
    }

    private fun similar(
        slots: Map<String, Int>,
        a: String,
        b: String,
        code: String,
        message: String,
    ): Warning? {
        val left = slots[a] ?: return null
        val right = slots[b] ?: return null
        return if (oklabDistance(left, right) < SIMILAR_DISTANCE) Warning(code, message) else null
    }

    private fun outline(
        slots: Map<String, Int>,
        fillSlot: String,
        code: String,
        message: String,
    ): Warning? {
        val fill = slots[fillSlot] ?: return null
        val outline = slots["outline"] ?: return null
        return if (contrastRatio(fill, outline) < OUTLINE_RATIO) Warning(code, message) else null
    }

    private fun oklabDistance(a: Int, b: Int): Double {
        val ca = Oklch.fromArgb(a)
        val cb = Oklch.fromArgb(b)
        val aA = ca.c * kotlin.math.cos(Math.toRadians(ca.h))
        val aB = ca.c * kotlin.math.sin(Math.toRadians(ca.h))
        val bA = cb.c * kotlin.math.cos(Math.toRadians(cb.h))
        val bB = cb.c * kotlin.math.sin(Math.toRadians(cb.h))
        val dL = ca.l - cb.l
        val dA = aA - bA
        val dB = aB - bB
        return kotlin.math.sqrt(dL * dL + dA * dA + dB * dB)
    }

    fun contrastRatio(a: Int, b: Int): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
