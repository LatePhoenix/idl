package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Test

class PathDataTest {
    @Test fun `absolute and relative commands become absolute ops`() {
        assertEquals(
            listOf(PathOp.MoveTo(10f, 20f), PathOp.LineTo(30f, 40f)),
            PathData.parse("M 10 20 L 30 40"),
        )
        assertEquals(
            listOf(PathOp.MoveTo(10f, 20f), PathOp.LineTo(15f, 26f)),
            PathData.parse("m 10 20 l 5 6"),
        )
        assertEquals(
            listOf(PathOp.MoveTo(0f, 0f), PathOp.LineTo(10f, 0f), PathOp.LineTo(10f, 4f)),
            PathData.parse("M0 0 H 10 v 4"),
        )
        assertEquals(
            listOf(PathOp.MoveTo(1f, 2f), PathOp.LineTo(0f, 2f), PathOp.LineTo(0f, 9f)),
            PathData.parse("M1,2 h-1 V9"),
        )
    }

    @Test fun `implicit repeats turn extra pairs into the same command`() {
        assertEquals(
            listOf(PathOp.MoveTo(0f, 0f), PathOp.LineTo(10f, 10f)),
            PathData.parse("M 0 0 10 10"),
        )
        assertEquals(
            listOf(
                PathOp.MoveTo(0f, 0f),
                PathOp.CubicTo(0f, 0f, 1f, 1f, 2f, 2f),
                PathOp.CubicTo(3f, 3f, 4f, 4f, 5f, 5f),
            ),
            PathData.parse("M0 0 C 0 0 1 1 2 2 3 3 4 4 5 5"),
        )
    }

    @Test fun `smooth cubic and quad reflect the previous control point`() {
        assertEquals(
            listOf(
                PathOp.MoveTo(0f, 0f),
                PathOp.CubicTo(0f, 0f, 10f, 10f, 20f, 20f),
                PathOp.CubicTo(30f, 30f, 40f, 40f, 50f, 50f),
            ),
            PathData.parse("M 0 0 C 0 0 10 10 20 20 S 40 40 50 50"),
        )
        assertEquals(
            listOf(
                PathOp.MoveTo(0f, 0f),
                PathOp.QuadTo(10f, 0f, 20f, 20f),
                PathOp.QuadTo(30f, 40f, 40f, 40f),
            ),
            PathData.parse("M 0 0 Q 10 0 20 20 T 40 40"),
        )
        assertEquals(
            listOf(
                PathOp.MoveTo(0f, 0f),
                PathOp.LineTo(10f, 10f),
                PathOp.QuadTo(10f, 10f, 20f, 20f),
            ),
            PathData.parse("M 0 0 L 10 10 T 20 20"),
        )
    }

    @Test fun `compact numbers commas and exponents parse`() {
        assertEquals(listOf(PathOp.MoveTo(-0.5f, 0.5f)), PathData.parse("M-.5.5"))
        assertEquals(listOf(PathOp.MoveTo(0.001f, 2f)), PathData.parse("M1e-3,2"))
        assertEquals(listOf(PathOp.MoveTo(10f, -5f)), PathData.parse("M10-5"))
        assertEquals(listOf(PathOp.MoveTo(0f, 0f), PathOp.Close), PathData.parse("M 0,0 Z"))
    }

    @Test fun `arcs non-finite numbers and unknown letters are rejected`() {
        assertRejected("M 0 0 A 1 1 0 0 0 2 2")
        assertRejected("M 1e999 0")
        assertRejected("M NaN 0")
        assertRejected("M 0 0 B 1 1")
    }

    private fun assertRejected(d: String) {
        try {
            PathData.parse(d)
            throw AssertionError("expected rejection of $d")
        } catch (error: IllegalArgumentException) {
            assertEquals(true, error.message?.startsWith("path:") == true)
        }
    }
}
