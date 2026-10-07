package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OklchTest {
    @Test fun `white and black map to expected lightness`() {
        val white = Oklch.fromArgb(0xFFFFFFFF.toInt())
        val black = Oklch.fromArgb(0xFF000000.toInt())
        assertTrue(abs(white.l - 1.0) < 0.01)
        assertTrue(white.c < 0.02)
        assertTrue(abs(black.l) < 0.01)
    }

    @Test fun `opaque round trip keeps sRGB within one channel`() {
        val samples = listOf(
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFF336699.toInt(),
            0xFFFFC83D.toInt(),
            0xFF5B3A29.toInt(),
            0xFF9E9E9E.toInt(),
            0xFF112233.toInt(),
        )
        for (argb in samples) {
            val back = Oklch.fromArgb(argb).toOpaqueArgb()
            assertChannelClose(argb, back)
        }
    }

    @Test fun `shadow is darker and highlight is lighter than primary`() {
        val primary = 0xFF336699.toInt()
        val shadow = Oklch.deriveShadow(primary)
        val highlight = Oklch.deriveHighlight(primary)
        assertTrue(Oklch.fromArgb(shadow).l < Oklch.fromArgb(primary).l)
        assertTrue(Oklch.fromArgb(highlight).l > Oklch.fromArgb(primary).l)
        assertEquals(Oklch.deriveShadow(primary), Oklch.deriveShadow(primary))
        assertEquals(Oklch.deriveHighlight(primary), Oklch.deriveHighlight(primary))
    }

    @Test fun `hat primary derives the importer shadow and highlight`() {
        // Shared with tools/import_art.py derive_hex("#3A6EA5").
        assertEquals(0xFF104B82.toInt(), Oklch.deriveShadow(0xFF3A6EA5.toInt()))
        assertEquals(0xFF5D8CBF.toInt(), Oklch.deriveHighlight(0xFF3A6EA5.toInt()))
    }

    @Test fun `derivation formula matches the documented deltas`() {
        val primary = 0xFFC68642.toInt()
        val src = Oklch.fromArgb(primary)
        val expectedShadow = Oklch.toOpaqueArgb(
            src.copy(l = (src.l - 0.12).coerceIn(0.0, 1.0), c = src.c * 1.05),
        )
        val expectedHighlight = Oklch.toOpaqueArgb(
            src.copy(l = (src.l + 0.10).coerceIn(0.0, 1.0), c = src.c * 0.9),
        )
        assertEquals(expectedShadow, Oklch.deriveShadow(primary))
        assertEquals(expectedHighlight, Oklch.deriveHighlight(primary))
    }

    private fun assertChannelClose(expected: Int, actual: Int) {
        fun ch(v: Int, shift: Int) = (v shr shift) and 0xFF
        for (shift in listOf(16, 8, 0)) {
            assertTrue(
                "channel@$shift: ${ch(expected, shift)} vs ${ch(actual, shift)}",
                abs(ch(expected, shift) - ch(actual, shift)) <= 1,
            )
        }
    }
}
