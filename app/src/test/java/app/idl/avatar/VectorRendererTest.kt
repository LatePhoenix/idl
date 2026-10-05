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
import app.idl.domain.avatar.ItemTransform
import app.idl.domain.avatar.RenderTarget
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
        assertEquals(listOf("avatar.vector_missing asset=mark_test_dot"), lines.map { it.msg })
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

    private fun dot() = AssetDef(
        id = "mark_test_dot",
        category = AssetCategory.HAIR,
        accessibilityLabel = "Dot",
        render = AssetRender(type = "vector", file = "pictures/mark_test_dot.json"),
        contentVersion = 1,
        colorSlots = mapOf("accessory.primary" to "#FFFF3B30"),
    )
}
