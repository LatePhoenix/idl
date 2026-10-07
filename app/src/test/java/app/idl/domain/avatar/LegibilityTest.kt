package app.idl.domain.avatar

import android.graphics.Bitmap
import android.graphics.Color
import app.idl.avatar.AvatarRenderer
import app.idl.avatar.RenderContrast
import app.idl.avatar.VectorPictureCache
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Compact-widget (48 px) eye and mouth coverage against a neutral teardrop baseline (AP-8 / §4).
 * Thresholds are the neutral face's own non-skin pixel counts minus a 25% tolerance, so a new
 * accessory must not erase more than a quarter of the expression.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class LegibilityTest {
    private val registry = coreRegistry()
    private val pictures = VectorPictureCache { path ->
        File(repoRoot(), "app/src/main/assets/$path").readText()
    }
    private val resolver = AvatarResolver(registry)

    private fun pixels(configuration: AvatarConfiguration): IntArray {
        val resolved = resolver.resolve(
            AvatarRenderRequest(
                configuration = configuration,
                target = RenderTarget.COMPACT_WIDGET,
                sizePx = 48,
            ),
        )
        val bitmap = AvatarRenderer.bitmap(resolved, registry, pictures, 48, contrast = RenderContrast())
        val out = IntArray(48 * 48)
        bitmap.getPixels(out, 0, 48, 0, 0, 48, 48)
        return out
    }

    private fun nonSkinCount(pixels: IntArray): Int {
        // Skin/sunny body is near #FFD45C. Count darker ink that carries the face.
        return pixels.count { color ->
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            Color.alpha(color) > 200 && (r + g + b) < 600
        }
    }

    @Test fun `hair headwear eyewear and facial hair keep most of the neutral face at 48 px`() {
        val baseline = nonSkinCount(pixels(config(base = "base_teardrop", scene = null, frame = null)))
        val floor = (baseline * 0.75).toInt()
        assertTrue("neutral baseline should have visible ink, was $baseline", baseline > 20)

        val cases = listOf(
            "hair_bob" to config(base = "base_teardrop", scene = null, frame = null).copy(itemIds = mapOf("hair" to listOf("hair_bob"))),
            "hat_brim_cap" to config(base = "base_teardrop", head = "hat_brim_cap", scene = null, frame = null),
            "glasses" to config(base = "base_teardrop", face = "glasses_round_wire", scene = null, frame = null),
            "beard" to config(base = "base_teardrop", scene = null, frame = null).copy(itemIds = mapOf("facial_hair" to listOf("beard_full"))),
        )
        for ((name, configuration) in cases) {
            val count = nonSkinCount(pixels(configuration))
            assertTrue("$name erased too much of the face ($count < $floor; baseline $baseline)", count >= floor)
        }
    }
}
