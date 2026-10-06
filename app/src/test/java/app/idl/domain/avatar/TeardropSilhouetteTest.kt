package app.idl.domain.avatar

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The avatar face is the brand teardrop (D-45), not a hand-copied outline.
 * AP-1: deviation stays within 2 units, and the temporary neck sits inside the chin.
 */
class TeardropSilhouetteTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `face matches the brand teardrop within 2 units`() {
        val canonical = canonicalSegments()
        val face = polyline(PathData.parse(part("face").commands), samplesPerCurve = 50)
        assertTrue("expected at least 200 face samples, was ${face.size}", face.size >= 200)
        val deviation = hausdorff(sampleCurves(canonical, 50), face)
        assertTrue("face deviates from the brand path by $deviation", deviation <= 2.0)
        val chin = face.maxOf { it.second }
        assertTrue("chin bottom $chin should be about 912", chin in 908.0..916.0)
    }

    @Test fun `outline is the face offset inward by about 36 units`() {
        val outline = PathData.parse(part("outline").commands)
        val subpaths = subpaths(outline)
        assertEquals(2, subpaths.size)
        val outer = polyline(subpaths[0], 40)
        val inner = polyline(subpaths[1], 40)
        val distances = inner.map { distanceToPolyline(it, outer) }
        assertTrue("inner ring closer than 28 (${distances.min()})", distances.min() >= 28.0)
        assertTrue("inner ring farther than 44 (${distances.max()})", distances.max() <= 44.0)
    }

    @Test fun `temporary neck starts inside the chin and stays at most 240 wide`() {
        val picture = picture()
        val neckIndex = picture.parts.indexOfFirst { it.id == "neck" }
        val faceIndex = picture.parts.indexOfFirst { it.id == "face" }
        assertTrue(neckIndex >= 0 && faceIndex > neckIndex)
        assertEquals(40, picture.parts[neckIndex].zBand)

        val neck = polyline(PathData.parse(picture.parts[neckIndex].commands), 30)
        val face = polyline(PathData.parse(picture.parts[faceIndex].commands), 40)
        val neckTop = neck.minOf { it.second }
        val chin = face.maxOf { it.second }
        assertTrue("neck top $neckTop should start at or above y 860", neckTop <= 860.0)
        assertTrue("neck top is only ${chin - neckTop} inside the chin", chin - neckTop >= 20.0)

        var y = neckTop
        val neckBottom = neck.maxOf { it.second }
        while (y <= neckBottom) {
            val span = spanAt(neck, y)
            if (span != null) {
                val width = span.second - span.first
                assertTrue("neck width $width at y $y", width <= 240.0)
            }
            y += 10.0
        }

        val atTop = spanAt(neck, neckTop + 20.0)
        val faceAtTop = spanAt(face, neckTop + 20.0)
        assertTrue(atTop != null && faceAtTop != null)
        assertTrue(atTop!!.first - faceAtTop!!.first >= 20.0)
        assertTrue(faceAtTop.second - atTop.second >= 20.0)
    }

    private fun picture(): VectorPicture {
        val file = File(repoRoot(), "app/src/main/assets/packs/emoji_core/v2/pictures/base_teardrop.json")
        return VectorPicture.parse(file.readText())
    }

    private fun part(id: String) = picture().parts.first { it.id == id }

    private fun canonicalSegments(): List<Curve> {
        val root = json.parseToJsonElement(
            File(repoRoot(), "config/teardrop_silhouette.json").readText(),
        ).jsonObject
        val map = root.getValue("mapTo1024").jsonObject
        val scale = map.getValue("scaleNumerator").jsonPrimitive.double /
            map.getValue("scaleDenominator").jsonPrimitive.double
        val originX = map.getValue("originX").jsonPrimitive.double
        val originY = map.getValue("originY").jsonPrimitive.double
        val translateX = map.getValue("translateX").jsonPrimitive.double
        val translateY = map.getValue("translateY").jsonPrimitive.double
        fun project(point: kotlinx.serialization.json.JsonArray): Pt {
            val x = point[0].jsonPrimitive.double
            val y = point[1].jsonPrimitive.double
            return (translateX + (x - originX) * scale) to (translateY + (y - originY) * scale)
        }

        val curves = mutableListOf<Curve>()
        var cursor: Pt? = null
        for (segment in root.getValue("path").jsonArray) {
            val obj = segment.jsonObject
            val points = obj["points"]?.jsonArray?.map { project(it.jsonArray) }.orEmpty()
            when (obj.getValue("op").jsonPrimitive.content) {
                "M" -> cursor = points[0]
                "Z" -> Unit
                "L" -> {
                    curves += Curve.Line(cursor!!, points[0])
                    cursor = points[0]
                }
                "Q" -> {
                    curves += Curve.Quad(cursor!!, points[0], points[1])
                    cursor = points[1]
                }
                "C" -> {
                    curves += Curve.Cubic(cursor!!, points[0], points[1], points[2])
                    cursor = points[2]
                }
            }
        }
        return curves
    }

    private fun sampleCurves(curves: List<Curve>, samplesPerCurve: Int): List<Pt> =
        curves.flatMap { curve ->
            (0 until samplesPerCurve).map { i -> curve.at(i.toDouble() / samplesPerCurve) }
        }

    private fun subpaths(ops: List<PathOp>): List<List<PathOp>> {
        val paths = mutableListOf<MutableList<PathOp>>()
        for (op in ops) {
            if (op is PathOp.MoveTo) paths += mutableListOf(op) else paths.last() += op
        }
        return paths
    }

    private fun polyline(ops: List<PathOp>, samplesPerCurve: Int): List<Pt> {
        val points = mutableListOf<Pt>()
        var cursor = 0.0 to 0.0
        var start = cursor
        fun addCurve(at: (Double) -> Pt, end: Pt) {
            for (i in 1..samplesPerCurve) points += at(i.toDouble() / samplesPerCurve)
            cursor = end
        }
        for (op in ops) {
            when (op) {
                is PathOp.MoveTo -> {
                    cursor = op.x.toDouble() to op.y.toDouble()
                    start = cursor
                    points += cursor
                }
                is PathOp.LineTo -> {
                    val end = op.x.toDouble() to op.y.toDouble()
                    val from = cursor
                    addCurve({ t -> lerp(from, end, t) }, end)
                }
                is PathOp.QuadTo -> {
                    val c = op.x1.toDouble() to op.y1.toDouble()
                    val end = op.x.toDouble() to op.y.toDouble()
                    val from = cursor
                    addCurve({ t -> quad(from, c, end, t) }, end)
                }
                is PathOp.CubicTo -> {
                    val c1 = op.x1.toDouble() to op.y1.toDouble()
                    val c2 = op.x2.toDouble() to op.y2.toDouble()
                    val end = op.x.toDouble() to op.y.toDouble()
                    val from = cursor
                    addCurve({ t -> cubic(from, c1, c2, end, t) }, end)
                }
                PathOp.Close -> {
                    val end = start
                    val from = cursor
                    if (from != end) addCurve({ t -> lerp(from, end, t) }, end)
                }
            }
        }
        return points
    }

    private fun hausdorff(a: List<Pt>, b: List<Pt>): Double {
        val forward = a.maxOf { distanceToPolyline(it, b) }
        val backward = b.maxOf { distanceToPolyline(it, a) }
        return max(forward, backward)
    }

    private fun distanceToPolyline(point: Pt, line: List<Pt>): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until line.lastIndex) best = min(best, distanceToSegment(point, line[i], line[i + 1]))
        return best
    }

    private fun distanceToSegment(point: Pt, a: Pt, b: Pt): Double {
        val dx = b.first - a.first
        val dy = b.second - a.second
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else ((point.first - a.first) * dx + (point.second - a.second) * dy) / lengthSq
        val clamped = t.coerceIn(0.0, 1.0)
        return hypot(point.first - (a.first + dx * clamped), point.second - (a.second + dy * clamped))
    }

    private fun spanAt(line: List<Pt>, y: Double): Pair<Double, Double>? {
        val hits = mutableListOf<Double>()
        for (i in 0 until line.lastIndex) {
            val a = line[i]
            val b = line[i + 1]
            val y0 = a.second
            val y1 = b.second
            if ((y0 - y) * (y1 - y) > 0.0 || y0 == y1) continue
            val t = (y - y0) / (y1 - y0)
            if (t in 0.0..1.0) hits += a.first + (b.first - a.first) * t
        }
        if (hits.size < 2) return null
        return hits.min() to hits.max()
    }

    private fun lerp(a: Pt, b: Pt, t: Double): Pt =
        (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)

    private fun quad(p0: Pt, p1: Pt, p2: Pt, t: Double): Pt {
        val u = 1.0 - t
        return (u * u * p0.first + 2 * u * t * p1.first + t * t * p2.first) to
            (u * u * p0.second + 2 * u * t * p1.second + t * t * p2.second)
    }

    private fun cubic(p0: Pt, p1: Pt, p2: Pt, p3: Pt, t: Double): Pt {
        val u = 1.0 - t
        return (
            u * u * u * p0.first + 3 * u * u * t * p1.first + 3 * u * t * t * p2.first + t * t * t * p3.first
            ) to (
            u * u * u * p0.second + 3 * u * u * t * p1.second + 3 * u * t * t * p2.second + t * t * t * p3.second
            )
    }
}

private typealias Pt = Pair<Double, Double>

private sealed interface Curve {
    fun at(t: Double): Pt

    data class Line(val a: Pt, val b: Pt) : Curve {
        override fun at(t: Double) = (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
    }

    data class Quad(val a: Pt, val b: Pt, val c: Pt) : Curve {
        override fun at(t: Double): Pt {
            val u = 1.0 - t
            return (u * u * a.first + 2 * u * t * b.first + t * t * c.first) to
                (u * u * a.second + 2 * u * t * b.second + t * t * c.second)
        }
    }

    data class Cubic(val a: Pt, val b: Pt, val c: Pt, val d: Pt) : Curve {
        override fun at(t: Double): Pt {
            val u = 1.0 - t
            return (
                u * u * u * a.first + 3 * u * u * t * b.first + 3 * u * t * t * c.first + t * t * t * d.first
                ) to (
                u * u * u * a.second + 3 * u * u * t * b.second + 3 * u * t * t * c.second + t * t * t * d.second
                )
        }
    }
}
