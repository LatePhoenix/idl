package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import app.idl.domain.Mood
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.CompositeOrder
import app.idl.domain.avatar.DrawOp
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.VisiblePresence
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.repoRoot
import app.idl.widget.WidgetRenderInputs
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val face = neutral.getPixel(24, 18)
        val eye = neutral.getPixel(18, 20)
        assertTrue("48 px face should stay readable", luma(face) - luma(eye) > 40)

        val hair = drawn.getValue("hair").getValue(48)
        assertNotEquals(neutral.getPixel(24, 3), hair.getPixel(24, 3))
        assertNotEquals(neutral.getPixel(18, 13), hair.getPixel(18, 13))

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

    @Test fun `compact and standard widget targets keep vector identity layers`() {
        val compact = WidgetRenderInputs.targetFor(40f, 40f)
        val standard = WidgetRenderInputs.targetFor(110f, 110f)
        assertEquals(RenderTarget.COMPACT_WIDGET, compact)
        assertEquals(RenderTarget.STANDARD_WIDGET, standard)
        val recipe = EmojiSlice.recipes.first { it.id == "glasses" }
        val compactIds = layerIds(recipe, compact)
        val standardIds = layerIds(recipe, standard)
        val profileIds = layerIds(recipe, RenderTarget.PROFILE)
        listOf(
            "base_teardrop", "hair_bob", "beard_full", "glasses_round_wire",
            "eyes_round_happy", "brows_round_relaxed", "mouth_round_smile",
        ).forEach { id ->
            assertTrue(id, id in compactIds)
            assertTrue(id, id in standardIds)
            assertTrue(id, id in profileIds)
        }
        assertFalse(compactIds.contains("overlay_blush"))
        assertFalse(standardIds.contains("overlay_blush"))
        assertTrue(profileIds.contains("overlay_blush"))

        listOf(compact, standard).forEach { target ->
            val name = if (target == RenderTarget.COMPACT_WIDGET) "compact" else "standard"
            EmojiSlice.recipes.forEach { row ->
                val bitmap = EmojiSlice.bitmap(registry, pictures, row, target.defaultSizePx, target = target)
                assertEquals(target.defaultSizePx, bitmap.width)
                bitmap.captureRoboImage("src/test/snapshots/vector/widget/${row.id}_$name.png")
            }
        }
    }

    @Test fun `hair and facial hair contact sheet at 48 and 512`() {
        val hairs = listOf(
            "hair_short_crop" to "Short",
            "hair_bob" to "Bob",
            "hair_long_straight" to "Long",
        )
        val facial = listOf(
            "beard_full" to "Beard",
            "stubble" to "Stubble",
            "mustache_classic" to "Mustache",
        )
        listOf(48, 512).forEach { size ->
            val scale = if (size == 48) 4 else 1
            val cell = size * scale
            val gap = 12
            val header = 28
            val gutter = 72
            val width = gutter + facial.size * cell + (facial.size + 1) * gap
            val height = header + hairs.size * cell + (hairs.size + 1) * gap
            val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
                isAntiAlias = true
            }
            facial.forEachIndexed { col, (_, label) ->
                canvas.drawText(label, (gutter + gap + col * (cell + gap)).toFloat(), 20f, paint)
            }
            hairs.forEachIndexed { row, (style, label) ->
                val top = header + gap + row * (cell + gap)
                canvas.drawText(label, 8f, top + 24f, paint)
                facial.forEachIndexed { col, (facialId, _) ->
                    val recipe = EmojiSlice.Recipe(
                        id = "contact",
                        label = label,
                        items = mapOf("hair" to listOf(style), "facial_hair" to listOf(facialId)),
                        presence = VisiblePresence(mood = Mood.HAPPY),
                    )
                    val face = EmojiSlice.bitmap(registry, pictures, recipe, size)
                    val dest = Rect(
                        gutter + gap + col * (cell + gap),
                        top,
                        gutter + gap + col * (cell + gap) + cell,
                        top + cell,
                    )
                    canvas.drawBitmap(face, null, dest, Paint().apply { isFilterBitmap = false })
                }
            }
            sheet.captureRoboImage("src/test/snapshots/vector/contact_$size.png")
        }
    }

    private fun layerIds(recipe: EmojiSlice.Recipe, target: RenderTarget): Set<String> {
        val resolved = AvatarResolver(registry).resolve(EmojiSlice.request(recipe, target.defaultSizePx, target = target))
        return resolved.layers.map { it.assetId }.toSet()
    }

    private fun assertGlyphAfterBangs() {
        val resolved = AvatarResolver(registry).resolve(EmojiSlice.request(EmojiSlice.recipes.first { it.id == "busy" }, 48))
        val ops = CompositeOrder.ops(resolved, registry) { id ->
            registry.asset(id)?.let { pictures.get(it)?.picture }
        }
        val bangs = ops.indexOfFirst { it is DrawOp.VectorPart && it.assetId == "hair_bob" && it.partIndex == 1 }
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
