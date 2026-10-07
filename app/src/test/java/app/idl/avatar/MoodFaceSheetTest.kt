package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import app.idl.domain.Mood
import app.idl.domain.wire
import app.idl.domain.avatar.AvatarRenderRequest
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.ExpressionCatalog
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.VisiblePresence
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.repoRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The priority-1 mood faces, one per [Mood], at the sizes the program asks for.
 * Light and dark are the sheet ground. The avatar scene stays the pack default.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class MoodFaceSheetTest {
    private val registry = coreRegistry()
    private val catalog = ExpressionCatalog.parse(
        File(repoRoot(), "config/expression_catalog.json").readText(),
    )
    private val pictures = VectorPictureCache(
        packDirectory = { asset -> registry.packDirectory(asset.id) },
    ) { path ->
        File(repoRoot(), "app/src/main/assets/$path").readText()
    }
    private val config = EmojiSlice.configuration(EmojiSlice.recipes.first { it.id == "neutral" })

    @Test fun `batch 1 faces have a contact sheet at 48 96 and 512`() {
        val specs = listOf(
            Sheet(48, RenderTarget.COMPACT_WIDGET, dark = false, "48"),
            Sheet(48, RenderTarget.COMPACT_WIDGET, dark = true, "48_dark"),
            Sheet(96, RenderTarget.STANDARD_WIDGET, dark = false, "96"),
            Sheet(96, RenderTarget.STANDARD_WIDGET, dark = true, "96_dark"),
            Sheet(512, RenderTarget.PROFILE, dark = false, "512"),
            Sheet(512, RenderTarget.PROFILE, dark = true, "512_dark"),
        )
        specs.forEach { spec ->
            sheet(spec).captureRoboImage("src/test/snapshots/vector/mood_faces_${spec.suffix}.png")
        }
    }

    @Test fun `no two batch 1 faces are pixel identical at 48`() {
        val seen = linkedMapOf<String, IntArray>()
        enumValues<Mood>().forEach { mood ->
            val face = render(mood, 48, RenderTarget.COMPACT_WIDGET)
            val pixels = IntArray(face.width * face.height)
            face.getPixels(pixels, 0, face.width, 0, 0, face.width, face.height)
            val match = seen.entries.firstOrNull { (_, prior) -> prior.contentEquals(pixels) }
            assertTrue("${mood.name} matches ${match?.key}", match == null)
            seen[mood.name] = pixels
        }
        assertEquals(enumValues<Mood>().size, seen.size)
    }

    private fun sheet(spec: Sheet): Bitmap {
        val scale = when (spec.size) {
            48 -> 4
            96 -> 2
            else -> 1
        }
        val cell = spec.size * scale
        val gap = 12
        val header = 28
        val moods = enumValues<Mood>()
        val width = gap + moods.size * (cell + gap)
        val height = header + cell + gap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (spec.dark) DARK_GROUND else Color.WHITE)
        val paint = Paint().apply {
            color = if (spec.dark) LIGHT_LABEL else Color.BLACK
            textSize = 18f
            isAntiAlias = true
        }
        moods.forEachIndexed { index, mood ->
            val left = gap + index * (cell + gap)
            canvas.drawText(mood.wire, left.toFloat(), 20f, paint)
            val face = render(mood, spec.size, spec.target)
            canvas.drawBitmap(
                face,
                null,
                Rect(left, header, left + cell, header + cell),
                Paint().apply { isFilterBitmap = false },
            )
        }
        return bitmap
    }

    private fun render(mood: Mood, size: Int, target: RenderTarget): Bitmap {
        val id = checkNotNull(catalog.priorityId(mood))
        val request = AvatarRenderRequest(
            configuration = config,
            presence = VisiblePresence(mood = mood),
            target = target,
            sizePx = size,
        )
        val resolved = AvatarResolver(registry, catalog).resolve(request)
        assertEquals(mood.name, id, resolved.expressionId)
        val eyes = registry.expression(id)?.partsFor("base_teardrop")?.eyes
        assertTrue("$id eyes", eyes != null && resolved.layers.any { it.assetId == eyes })
        return AvatarRenderer.bitmap(resolved, registry, pictures, size)
    }

    private data class Sheet(val size: Int, val target: RenderTarget, val dark: Boolean, val suffix: String)

    companion object {
        private const val DARK_GROUND = 0xFF17131B.toInt()
        private const val LIGHT_LABEL = 0xFFEAF1F8.toInt()
    }
}
