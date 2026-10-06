package app.idl.brand

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import app.idl.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The cropped W1 wordmark that the onboarding screen shows (`R.drawable.wordmark`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class InlineWordmarkTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `light wordmark has plum letters and a transparent background`() {
        val bitmap = render()
        assertEquals(0, Color.alpha(bitmap.getPixel(SCALE * 130, SCALE * 4)))
        assertNear(Color.parseColor("#3A2247"), bitmap.at(D_STEM))
        assertNear(CORAL, bitmap.at(FOREHEAD))
        assertNear(Color.parseColor("#3A2247"), bitmap.at(OPEN_EYE))
        bitmap.captureRoboImage("src/test/snapshots/brand/wordmark_light.png")
    }

    @Config(qualifiers = "night")
    @Test fun `dark wordmark has cream letters`() {
        val bitmap = render()
        assertNear(Color.parseColor("#FFE9D2"), bitmap.at(D_STEM))
        assertNear(CORAL, bitmap.at(FOREHEAD))
        assertNear(Color.parseColor("#2A1430"), bitmap.at(OPEN_EYE))
        bitmap.captureRoboImage("src/test/snapshots/brand/wordmark_dark.png")
    }

    @Test fun `drawable keeps the 136 by 71 aspect the screen relies on`() {
        val drawable = checkNotNull(context.getDrawable(R.drawable.wordmark))
        assertEquals(136f / 71f, drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight, 0.02f)
    }

    private fun render(): Bitmap {
        val bitmap = Bitmap.createBitmap(136 * SCALE, 71 * SCALE, Bitmap.Config.ARGB_8888)
        val drawable = checkNotNull(context.getDrawable(R.drawable.wordmark))
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun Bitmap.at(point: Pair<Float, Float>): Int =
        getPixel((point.first * SCALE).toInt(), (point.second * SCALE).toInt())

    private fun assertNear(expected: Int, actual: Int) {
        val close = Color.alpha(actual) > 200 &&
            abs(Color.red(expected) - Color.red(actual)) <= 8 &&
            abs(Color.green(expected) - Color.green(actual)) <= 8 &&
            abs(Color.blue(expected) - Color.blue(actual)) <= 8
        assertTrue("expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}", close)
    }

    private companion object {
        const val SCALE = 4
        val CORAL = Color.parseColor("#FF8E6E")

        // In the 136 x 71 viewport, from gen_brand.py's INLINE_PLACE.
        val D_STEM = 47f to 47f
        val FOREHEAD = 16.49f to 8.52f
        val OPEN_EYE = 23.89f to 14.77f
    }
}
