package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import app.idl.domain.avatar.AssetCategory
import app.idl.domain.avatar.AssetDef
import app.idl.domain.avatar.AssetManifest
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.AssetRender
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.Framing
import app.idl.domain.avatar.ItemTransform
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.ClipBy
import app.idl.domain.avatar.VectorFill
import app.idl.domain.avatar.VectorPart
import app.idl.domain.avatar.VectorPicture
import app.idl.domain.avatar.VectorStroke
import app.idl.domain.avatar.config
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.request
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class VectorRendererTest {
    private val json = checkNotNull(javaClass.classLoader).getResource("pictures/mark_test_dot.json")!!.readText()

    @Test fun `a picture is parsed once per content version and the cached path is not mutated`() {
        val reads = AtomicInteger()
        val cache = VectorPictureCache {
            reads.incrementAndGet()
            Thread.sleep(20)
            json
        }
        val asset = dot()
        val loaded = arrayOfNulls<VectorPictureCache.PicturePaths>(8)
        val threads = loaded.indices.map { index -> thread { loaded[index] = cache.get(asset) } }
        threads.forEach { it.join() }
        assertEquals(1, reads.get())
        val first = checkNotNull(loaded.first())
        assertTrue(loaded.all { it === first })
        val path = first.parts.single()
        val before = RectF().also { path.computeBounds(it, true) }
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        CanvasVectorAssetRenderer().draw(
            Canvas(bitmap),
            first.picture,
            mapOf("accessory.primary" to 0xFFFF3B30.toInt()),
            ItemTransform(),
            ItemTransform(translateX = 12f, rotationDeg = 20f, scale = 1.1f, flipHorizontal = true),
            64f,
            first.parts,
            first.clips,
            partIndex = 0,
        )
        val after = RectF().also { path.computeBounds(it, true) }
        assertEquals(before.left, after.left, 0.01f)
        assertEquals(before.top, after.top, 0.01f)
        assertEquals(before.right, after.right, 0.01f)
        assertEquals(before.bottom, after.bottom, 0.01f)
        assertSame(path, cache.get(asset)!!.parts.single())
        assertEquals(null, cache.get(asset.copy(contentVersion = 2)))
        assertEquals(2, reads.get())
    }

    @Test fun `a stroke is drawn in the slot color`() {
        val picture = VectorPicture(
            schemaVersion = 2,
            id = "stroke_probe",
            contentVersion = 1,
            viewBox = 1024,
            parts = listOf(
                VectorPart(
                    id = "line",
                    zBand = 40,
                    fill = VectorFill(slot = "face.primary"),
                    commands = "M 100 512 L 900 512",
                    stroke = VectorStroke(slot = "outline", width = 32f),
                    clipBy = listOf(ClipBy(mask = "occlude.hair_top", mode = "difference")),
                ),
            ),
        )
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        CanvasVectorAssetRenderer().draw(
            Canvas(bitmap),
            picture,
            mapOf("face.primary" to 0xFFFFC83D.toInt(), "outline" to 0xFF7A4E00.toInt()),
            ItemTransform(),
            1024f,
        )
        val mid = bitmap.getPixel(500, 512)
        assertTrue((mid ushr 16) and 0xFF > 80 && (mid shr 8) and 0xFF < 120 && (mid and 0xFF) < 40)
        assertEquals(0, bitmap.getPixel(500, 400))
    }

    @Test fun `a missing picture logs the asset id only`() {
        ShadowLog.clear()
        val registry = AssetRegistry(coreRegistry().manifests + AssetManifest(
            packId = "test_pictures",
            version = 1,
            assets = listOf(dot().copy(render = AssetRender(type = "vector", file = "pictures/missing.json"))),
        ))
        val resolved = AvatarResolver(registry).resolve(
            request(config().copy(itemIds = mapOf("hair" to listOf("mark_test_dot"))), target = RenderTarget.PROFILE, sizePx = 128),
        )
        val bitmap = AvatarRenderer.bitmap(resolved, registry, VectorPictureCache { throw java.io.FileNotFoundException(it) }, 128)
        assertEquals(128, bitmap.width)
        val lines = ShadowLog.getLogs().filter { it.msg.contains("avatar.vector_missing") }
        assertEquals(
            listOf(
                "avatar.vector_missing asset=mark_test_dot",
                "avatar.vector_missing asset=top_crew_tee",
            ),
            lines.map { it.msg },
        )
    }

    @Test fun `a test picture draws on top of the procedural avatar`() {
        val registry = AssetRegistry(coreRegistry().manifests + AssetManifest(
            packId = "test_pictures",
            version = 1,
            assets = listOf(dot()),
        ))
        val withDot = AvatarResolver(registry).resolve(
            request(config().copy(itemIds = mapOf("hair" to listOf("mark_test_dot"))), target = RenderTarget.STANDARD_WIDGET, sizePx = 256),
        )
        val plain = AvatarResolver(registry).resolve(
            request(config(), target = RenderTarget.STANDARD_WIDGET, sizePx = 256),
        )
        val cache = VectorPictureCache { json }
        val dotted = AvatarRenderer.bitmap(withDot, registry, cache, 256)
        val bare = AvatarRenderer.bitmap(plain, registry, cache, 256)
        assertFalse(dotted.sameAs(bare))
        dotted.captureRoboImage("src/test/snapshots/widget/vector_dot.png")
    }

    @Test fun `viewport translation happens in character space before the output scale`() {
        val picture = VectorPicture(
            schemaVersion = 1,
            id = "framing_dot",
            contentVersion = 1,
            viewBox = 1024,
            parts = listOf(
                VectorPart("dot", 50, VectorFill(slot = "accessory.primary"), "M 200 200 L 800 200 L 800 700 L 200 700 Z"),
            ),
        )
        val renderer = CanvasVectorAssetRenderer()
        fun pixels(translateThenScale: Boolean): List<String> {
            val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val scale = 48f / Framing.BUST.size
            if (translateThenScale) {
                canvas.translate(-Framing.BUST.originX, -Framing.BUST.originY)
                canvas.scale(scale, scale)
            } else {
                canvas.scale(scale, scale)
                canvas.translate(-Framing.BUST.originX, -Framing.BUST.originY)
            }
            renderer.draw(
                canvas,
                picture,
                mapOf("accessory.primary" to 0xFFFF0000.toInt()),
                ItemTransform(),
                ItemTransform(),
                VectorPicture.VIEW_BOX.toFloat(),
                listOf(pathOf(picture.parts[0].commands, "nonzero")),
                emptyMap(),
                0,
            )
            val hits = mutableListOf<String>()
            for (y in 0 until 48) {
                for (x in 0 until 48) {
                    val pixel = bitmap.getPixel(x, y)
                    if ((pixel ushr 16) and 0xFF > 200 && (pixel and 0xFF) < 40) hits += "$x,$y"
                }
            }
            return hits
        }
        assertEquals(emptyList<String>(), pixels(true))
        assertTrue(pixels(false).contains("24,14"))
    }

    @Test fun `a vector scene keeps the frame shape`() {
        val scene = AssetDef(
            id = "scene_test_square",
            category = AssetCategory.SCENE,
            accessibilityLabel = "Square",
            render = AssetRender(type = "vector", file = "pictures/scene_test_square.json"),
            colorSlots = mapOf("accessory.secondary" to "#FF2E8792"),
        )
        val picture = """
            {"schemaVersion":1,"id":"scene_test_square","contentVersion":1,"viewBox":1024,
             "parts":[{"id":"bg","zBand":0,"fill":{"slot":"accessory.secondary"},"commands":"M 0 0 L 1024 0 L 1024 1024 L 0 1024 Z"}]}
        """.trimIndent()
        val registry = AssetRegistry(coreRegistry().manifests + AssetManifest(
            packId = "test_pictures",
            version = 1,
            assets = listOf(scene),
        ))
        val resolved = AvatarResolver(registry).resolve(
            request(config(scene = "scene_test_square"), target = RenderTarget.PROFILE, sizePx = 256),
        )
        assertTrue(resolved.layers.any { it.assetId == "scene_test_square" })
        val bitmap = AvatarRenderer.bitmap(resolved, registry, VectorPictureCache { picture }, 256)
        // Squircle corner: outside the frame stays transparent; the center is the scene color or the head.
        assertEquals(0, bitmap.getPixel(2, 2) ushr 24)
        assertEquals(0xFF, bitmap.getPixel(128, 20) ushr 24)
    }

    private fun dot() = AssetDef(
        id = "mark_test_dot",
        category = AssetCategory.HAIR,
        accessibilityLabel = "Dot",
        render = AssetRender(type = "vector", file = "pictures/mark_test_dot.json"),
        contentVersion = 1,
        colorSlots = mapOf("accessory.primary" to "#FFFF3B30"),
    )
}
