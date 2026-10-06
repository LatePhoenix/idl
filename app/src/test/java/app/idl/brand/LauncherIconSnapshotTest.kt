package app.idl.brand

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import app.idl.R
import app.idl.domain.avatar.repoRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class LauncherIconSnapshotTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test fun `foreground draws the coral face and plum features`() {
        val bitmap = layer(R.drawable.ic_launcher_foreground)
        assertEquals(0, Color.alpha(bitmap.at(CORNER)))
        assertNear(CORAL, bitmap.at(FOREHEAD))
        assertNear(PLUM, bitmap.at(OPEN_EYE))
        assertNear(PLUM, bitmap.at(STEM))
        bitmap.captureRoboImage("src/test/snapshots/brand/launcher_foreground_432.png")
    }

    @Test fun `monochrome layer cuts the features out of the face`() {
        val bitmap = layer(R.drawable.ic_launcher_monochrome)
        assertEquals(0, Color.alpha(bitmap.at(CORNER)))
        assertTrue("face should be solid", Color.alpha(bitmap.at(FOREHEAD)) > 200)
        assertTrue("eye should be a hole for themed icons", Color.alpha(bitmap.at(OPEN_EYE)) < 30)
        assertTrue("stem should be solid", Color.alpha(bitmap.at(STEM)) > 200)
        composite(192, R.drawable.ic_launcher_monochrome, THEMED_BG, tint = THEMED_INK)
            .captureRoboImage("src/test/snapshots/brand/launcher_themed_192.png")
    }

    @Test fun `launcher composite stays readable at 48 and 192 px`() {
        listOf(48, 192).forEach { size ->
            val bitmap = composite(size)
            assertNear(context.getColor(R.color.idl_launcher_bg), bitmap.getPixel(size / 10, size / 2))
            bitmap.captureRoboImage("src/test/snapshots/brand/launcher_$size.png")
        }
    }

    @Test fun `play store icon matches the committed 512 px asset`() {
        val bitmap = composite(PLAY_PX, masked = false)
        val background = context.getColor(R.color.idl_launcher_bg)
        listOf(0 to 0, PLAY_PX - 1 to 0, 0 to PLAY_PX - 1, PLAY_PX - 1 to PLAY_PX - 1).forEach { (x, y) ->
            assertEquals("Play applies its own mask, so corners stay opaque", background, bitmap.getPixel(x, y))
        }
        bitmap.captureRoboImage("src/test/snapshots/brand/launcher_playstore_512.png")

        val asset = File(repoRoot(), "docs/art/brand/playstore-icon-512.png")
        if (System.getProperty("idl.updateGolden") == "true") {
            asset.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        assertTrue("$asset is missing; rerun with -Pidl.updateGolden=true", asset.isFile)
        val committed = checkNotNull(BitmapFactory.decodeFile(asset.path))
        assertEquals(PLAY_PX, committed.width)
        assertEquals(PLAY_PX, committed.height)
        var differing = 0
        for (y in 0 until PLAY_PX) for (x in 0 until PLAY_PX) {
            if (!near(bitmap.getPixel(x, y), committed.getPixel(x, y))) differing++
        }
        assertTrue(
            "$asset is stale ($differing pixels differ); rerun with -Pidl.updateGolden=true",
            differing <= PLAY_PX * PLAY_PX / 1000,
        )
    }

    /** One layer on the full 108-unit adaptive canvas, 4 px per unit. */
    private fun layer(id: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(CANVAS_PX, CANVAS_PX, Bitmap.Config.ARGB_8888)
        val drawable = checkNotNull(context.getDrawable(id))
        drawable.setBounds(0, 0, CANVAS_PX, CANVAS_PX)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    /** What a launcher shows: the centre 72 units, background plus foreground, circle mask.
     *  With [tint], it previews an Android 13+ themed icon. Unmasked, it's the Play Store icon. */
    private fun composite(
        size: Int,
        foregroundId: Int = R.drawable.ic_launcher_foreground,
        background: Int = context.getColor(R.color.idl_launcher_bg),
        tint: Int? = null,
        masked: Boolean = true,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = size / 2f
        if (masked) canvas.clipPath(Path().apply { addCircle(half, half, half, Path.Direction.CW) })
        canvas.drawColor(background)
        val unit = size / 72f
        canvas.translate(-18 * unit, -18 * unit)
        val foreground = checkNotNull(context.getDrawable(foregroundId)).mutate()
        tint?.let(foreground::setTint)
        foreground.setBounds(0, 0, (108 * unit).toInt(), (108 * unit).toInt())
        foreground.draw(canvas)
        return bitmap
    }

    private fun Bitmap.at(point: Pair<Float, Float>): Int =
        getPixel((point.first * PX_PER_UNIT).toInt(), (point.second * PX_PER_UNIT).toInt())

    private fun near(expected: Int, actual: Int): Boolean =
        abs(Color.alpha(expected) - Color.alpha(actual)) <= 8 &&
            abs(Color.red(expected) - Color.red(actual)) <= 8 &&
            abs(Color.green(expected) - Color.green(actual)) <= 8 &&
            abs(Color.blue(expected) - Color.blue(actual)) <= 8

    private fun assertNear(expected: Int, actual: Int) {
        assertTrue("expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}", near(expected, actual))
    }

    private companion object {
        const val PX_PER_UNIT = 4
        const val CANVAS_PX = 108 * PX_PER_UNIT
        const val PLAY_PX = 512
        val CORAL = Color.parseColor("#FF8E6E")
        val PLUM = Color.parseColor("#3A2247")
        val THEMED_BG = Color.parseColor("#E6DDF3")
        val THEMED_INK = Color.parseColor("#3E2F5B")

        // Probe points in 108-unit canvas space, printed by docs/art/brand/gen_brand.py.
        val CORNER = 1f to 1f
        val FOREHEAD = 50.23f to 28.87f
        val OPEN_EYE = 58.11f to 35.53f
        val STEM = 58.46f to 75.52f
    }
}
