package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Color
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.CompositeOrder
import app.idl.domain.avatar.DrawOp
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.repoRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class VectorSliceSnapshotTest {
    private val registry = coreRegistry()
    private val pictures = VectorPictureCache { file ->
        val prefixes = AssetPacks.SHIPPED.map { it.substringBeforeLast("manifest.json") }
        prefixes.firstNotNullOf { prefix ->
            val packed = File(repoRoot(), "app/src/main/assets/$prefix$file")
            if (packed.isFile) packed.readText() else null
        }
    }

    @Test fun `section 8 recipes snapshot at 48 128 and 512`() {
        val drawn = EmojiSlice.recipes.associate { recipe ->
            recipe.id to EmojiSlice.sizes.associate { size ->
                val bitmap = EmojiSlice.bitmap(registry, pictures, recipe, size)
                assertEquals(size, bitmap.width)
                assertEquals(size, bitmap.height)
                size to bitmap
            }
        }
        val neutral = drawn.getValue("neutral").getValue(48)
        val face = neutral.getPixel(24, 25)
        val eye = neutral.getPixel(19, 23)
        assertTrue("48 px face should stay readable", luma(face) - luma(eye) > 40)

        val hair = drawn.getValue("hair").getValue(48)
        assertNotEquals(neutral.getPixel(24, 8), hair.getPixel(24, 8))
        assertNotEquals(neutral.getPixel(24, 16), hair.getPixel(24, 16))

        val scene = drawn.getValue("scene").getValue(512)
        val busy = drawn.getValue("busy").getValue(48)
        assertEquals(0, Color.alpha(scene.getPixel(0, 0)))
        assertTrue(Color.alpha(scene.getPixel(256, 256)) > 200)
        val badge = badgeCenter(48)
        assertNotEquals(drawn.getValue("scene").getValue(48).getPixel(badge.first, badge.second), busy.getPixel(badge.first, badge.second))
        assertGlyphAfterBangs()

        drawn.forEach { (id, sizes) ->
            sizes.forEach { (size, bitmap) -> bitmap.captureRoboImage("src/test/snapshots/vector/${id}_$size.png") }
        }
    }

    private fun assertGlyphAfterBangs() {
        val resolved = AvatarResolver(registry).resolve(EmojiSlice.request(EmojiSlice.recipes.first { it.id == "busy" }, 48))
        val ops = CompositeOrder.ops(resolved, registry) { id ->
            registry.asset(id)?.let { pictures.get(it)?.picture }
        }
        val bangs = ops.indexOfFirst { it is DrawOp.VectorPart && it.assetId == "hair_round_bob" && it.partIndex == 1 }
        val glyph = ops.indexOfFirst { it is DrawOp.Procedural && it.category == app.idl.domain.avatar.AssetCategory.AVAILABILITY_INDICATOR }
        assertTrue(bangs >= 0 && glyph > bangs)
    }

    private fun badgeCenter(size: Int): Pair<Int, Int> {
        val s = size.toFloat()
        val br = s * 0.11f
        return (s - br * 1.35f).toInt() to (s - br * 1.35f).toInt()
    }

    private fun luma(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (r * 3 + g * 6 + b) / 10
    }
}
