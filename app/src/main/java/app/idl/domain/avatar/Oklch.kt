package app.idl.domain.avatar

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OKLCH color space helpers (Björn Ottosson OKLab). Pure Kotlin, no Android.
 * Used to derive shadow and highlight from a primary (AP-9 / §3.5).
 */
data class Oklch(
    /** Lightness in 0..1. */
    val l: Double,
    /** Chroma ≥ 0. */
    val c: Double,
    /** Hue in degrees, 0..360. Meaningful only when [c] > 0. */
    val h: Double,
) {
    fun toOpaqueArgb(): Int = Oklch.toOpaqueArgb(this)

    companion object {
        fun fromArgb(argb: Int): Oklch {
            val r = srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
            val g = srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
            val b = srgbToLinear((argb and 0xFF) / 255.0)
            val l_ = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m_ = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s_ = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
            val L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
            val a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
            val bLab = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
            val C = sqrt(a * a + bLab * bLab)
            val H = Math.toDegrees(atan2(bLab, a)).let { if (it < 0) it + 360.0 else it }
            return Oklch(L, C, H)
        }

        fun toOpaqueArgb(color: Oklch): Int {
            val hRad = Math.toRadians(color.h)
            val a = color.c * cos(hRad)
            val bLab = color.c * sin(hRad)
            val l_ = color.l + 0.3963377774 * a + 0.2158037573 * bLab
            val m_ = color.l - 0.1055613458 * a - 0.0638541728 * bLab
            val s_ = color.l - 0.0894841775 * a - 1.2914855480 * bLab
            val l = l_ * l_ * l_
            val m = m_ * m_ * m_
            val s = s_ * s_ * s_
            val r = linearToSrgb(+4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s)
            val g = linearToSrgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s)
            val b = linearToSrgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s)
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        /** Shadow: L −0.12, chroma ×1.05. Highlight: L +0.10, chroma ×0.9. Clamped to sRGB. */
        fun deriveShadow(primaryArgb: Int): Int {
            val src = fromArgb(primaryArgb)
            return toOpaqueArgb(src.copy(l = (src.l - 0.12).coerceIn(0.0, 1.0), c = src.c * 1.05))
        }

        fun deriveHighlight(primaryArgb: Int): Int {
            val src = fromArgb(primaryArgb)
            return toOpaqueArgb(src.copy(l = (src.l + 0.10).coerceIn(0.0, 1.0), c = src.c * 0.9))
        }

        private fun srgbToLinear(c: Double): Double =
            if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

        private fun linearToSrgb(c: Double): Int {
            val clipped = c.coerceIn(0.0, 1.0)
            val encoded = if (clipped <= 0.0031308) {
                12.92 * clipped
            } else {
                1.055 * clipped.pow(1.0 / 2.4) - 0.055
            }
            return (encoded * 255.0).toInt().coerceIn(0, 255)
        }
    }
}
