package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import app.idl.domain.Mood
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
    private val pictures = VectorPictureCache(
        packDirectory = { asset -> registry.packDirectory(asset.id) },
    ) { path ->
        File(repoRoot(), "app/src/main/assets/$path").readText()
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
        // PROFILE is bust framing. Eye line y 420 maps to about y 15; the left eye is about x 19.
        val face = neutral.getPixel(24, 15)
        val eye = neutral.getPixel(19, 15)
        assertTrue("48 px face should stay readable", luma(face) - luma(eye) > 40)

        val hair = drawn.getValue("hair").getValue(48)
        // Bob side masses, mapped through bust framing at 48 px.
        assertNotEquals(neutral.getPixel(9, 14), hair.getPixel(9, 14))
        assertNotEquals(neutral.getPixel(38, 14), hair.getPixel(38, 14))

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

    @Test fun `the crew collar covers three rows under the chin and the head stays readable at 48`() {
        val bitmap = EmojiSlice.bitmap(
            registry,
            pictures,
            EmojiSlice.recipes.first { it.id == "neutral" },
            48,
            target = RenderTarget.COMPACT_WIDGET,
        )
        val shirtRows = (0 until 48).count { y ->
            y > 40 && closer(bitmap.getPixel(24, y), SHIRT, SKIN)
        }
        assertTrue("collar rows $shirtRows", shirtRows >= 3)

        var widest = 0
        for (y in 0 until 48) {
            var run = 0
            for (x in 0 until 48) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val face = r > 160 && g > 120 && b < 140
                val outline = r in 70..180 && g < 120 && b < 50
                if (face || outline) {
                    run++
                    if (run > widest) widest = run
                } else {
                    run = 0
                }
            }
        }
        val before = 760f / 1024f * 48f
        assertTrue("head width $widest is more than 8% under $before", widest >= before * 0.92f)
    }

    private fun closer(pixel: Int, a: Int, b: Int): Boolean = channelDistance(pixel, a) < channelDistance(pixel, b)

    private fun channelDistance(a: Int, b: Int): Int {
        fun ch(color: Int, shift: Int) = (color shr shift) and 0xFF
        val dr = ch(a, 16) - ch(b, 16)
        val dg = ch(a, 8) - ch(b, 8)
        val db = ch(a, 0) - ch(b, 0)
        return dr * dr + dg * dg + db * db
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

    companion object {
        private const val SHIRT = 0xFF2F6FBF.toInt()
        private const val SKIN = 0xFFFFC83D.toInt()
    }

    private fun luma(color: Int): Int {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (r * 3 + g * 6 + b) / 10
    }
}
