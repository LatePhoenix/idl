package app.idl.domain.avatar

/**
 * SVG path `d` parser for picture JSON. Output is absolute move, line, quad, cubic, and close.
 * Arc commands are rejected. The result is absolute commands the renderer can replay later.
 */
object PathData {
    fun parse(d: String): List<PathOp> = Parser(d).parse()

    private class Parser(private val src: String) {
        private var i = 0
        private var cx = 0.0
        private var cy = 0.0
        private var sx = 0.0
        private var sy = 0.0
        private var prevCtrlX = 0.0
        private var prevCtrlY = 0.0
        private var cubicSmooth = false
        private var quadSmooth = false

        fun parse(): List<PathOp> {
            val out = mutableListOf<PathOp>()
            var cmd: Char? = null
            while (true) {
                skipSeparators()
                if (i >= src.length) break
                val c = src[i]
                if (c.isLetter()) {
                    if (c !in COMMANDS) fail("unknown command $c")
                    if (c == 'A' || c == 'a') fail("arcs are not supported")
                    cmd = c
                    i++
                } else if (cmd == null || cmd == 'Z' || cmd == 'z') {
                    fail("expected a command")
                }
                when (cmd) {
                    'M', 'm' -> {
                        val abs = cmd == 'M'
                        val x = coord(abs, cx)
                        val y = coord(abs, cy)
                        moveTo(out, x, y)
                        cmd = if (abs) 'L' else 'l'
                    }
                    'L', 'l' -> line(out, cmd == 'L')
                    'H', 'h' -> {
                        val x = coord(cmd == 'H', cx)
                        lineTo(out, x, cy)
                    }
                    'V', 'v' -> {
                        val y = coord(cmd == 'V', cy)
                        lineTo(out, cx, y)
                    }
                    'C', 'c' -> cubic(out, cmd == 'C', smooth = false)
                    'S', 's' -> cubic(out, cmd == 'S', smooth = true)
                    'Q', 'q' -> quad(out, cmd == 'Q', smooth = false)
                    'T', 't' -> quad(out, cmd == 'T', smooth = true)
                    'Z', 'z' -> {
                        out += PathOp.Close
                        cx = sx
                        cy = sy
                        clearSmooth()
                    }
                }
            }
            return out
        }

        private fun moveTo(out: MutableList<PathOp>, x: Double, y: Double) {
            cx = x
            cy = y
            sx = x
            sy = y
            clearSmooth()
            out += PathOp.MoveTo(x.toFloat(), y.toFloat())
        }

        private fun line(out: MutableList<PathOp>, absolute: Boolean) {
            val x = coord(absolute, cx)
            val y = coord(absolute, cy)
            lineTo(out, x, y)
        }

        private fun lineTo(out: MutableList<PathOp>, x: Double, y: Double) {
            cx = x
            cy = y
            clearSmooth()
            out += PathOp.LineTo(x.toFloat(), y.toFloat())
        }

        private fun cubic(out: MutableList<PathOp>, absolute: Boolean, smooth: Boolean) {
            val x1: Double
            val y1: Double
            if (smooth) {
                x1 = if (cubicSmooth) 2 * cx - prevCtrlX else cx
                y1 = if (cubicSmooth) 2 * cy - prevCtrlY else cy
            } else {
                x1 = coord(absolute, cx)
                y1 = coord(absolute, cy)
            }
            val x2 = coord(absolute, cx)
            val y2 = coord(absolute, cy)
            val x = coord(absolute, cx)
            val y = coord(absolute, cy)
            prevCtrlX = x2
            prevCtrlY = y2
            cubicSmooth = true
            quadSmooth = false
            cx = x
            cy = y
            out += PathOp.CubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
        }

        private fun quad(out: MutableList<PathOp>, absolute: Boolean, smooth: Boolean) {
            val x1: Double
            val y1: Double
            if (smooth) {
                x1 = if (quadSmooth) 2 * cx - prevCtrlX else cx
                y1 = if (quadSmooth) 2 * cy - prevCtrlY else cy
            } else {
                x1 = coord(absolute, cx)
                y1 = coord(absolute, cy)
            }
            val x = coord(absolute, cx)
            val y = coord(absolute, cy)
            prevCtrlX = x1
            prevCtrlY = y1
            quadSmooth = true
            cubicSmooth = false
            cx = x
            cy = y
            out += PathOp.QuadTo(x1.toFloat(), y1.toFloat(), x.toFloat(), y.toFloat())
        }

        private fun clearSmooth() {
            cubicSmooth = false
            quadSmooth = false
        }

        private fun coord(absolute: Boolean, origin: Double): Double {
            val value = number()
            return if (absolute) value else origin + value
        }

        private fun number(): Double {
            skipSeparators()
            if (i >= src.length) fail("expected a number")
            val start = i
            val c = src[i]
            if (c == '+' || c == '-') i++
            var sawDigit = false
            var sawDot = false
            while (i < src.length) {
                val ch = src[i]
                when {
                    ch.isDigit() -> {
                        sawDigit = true
                        i++
                    }
                    ch == '.' && !sawDot -> {
                        sawDot = true
                        i++
                    }
                    else -> break
                }
            }
            if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                val mark = i
                i++
                if (i < src.length && (src[i] == '+' || src[i] == '-')) i++
                val exp = i
                while (i < src.length && src[i].isDigit()) i++
                if (i == exp) i = mark
            }
            if (!sawDigit || i == start) fail("expected a number")
            val value = src.substring(start, i).toDoubleOrNull() ?: fail("expected a number")
            if (!value.isFinite()) fail("non-finite number")
            return value
        }

        private fun skipSeparators() {
            while (i < src.length && (src[i].isWhitespace() || src[i] == ',')) i++
        }

        private fun fail(message: String): Nothing =
            throw IllegalArgumentException("path: $message at $i")
    }

    private val COMMANDS = setOf('M', 'm', 'L', 'l', 'H', 'h', 'V', 'v', 'C', 'c', 'S', 's', 'Q', 'q', 'T', 't', 'Z', 'z', 'A', 'a')
}

sealed interface PathOp {
    data class MoveTo(val x: Float, val y: Float) : PathOp
    data class LineTo(val x: Float, val y: Float) : PathOp
    data class QuadTo(val x1: Float, val y1: Float, val x: Float, val y: Float) : PathOp
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : PathOp
    data object Close : PathOp
}
