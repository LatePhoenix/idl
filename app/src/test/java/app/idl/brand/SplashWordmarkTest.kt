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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class SplashWordmarkTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `launch window shows the plum wordmark on cream`() {
        val bitmap = launchWindow()
        assertNear(Color.parseColor("#FFF1E1"), bitmap.getPixel(4, 4))
        assertNear(Color.parseColor("#3A2247"), bitmap.at(D_STEM))
        assertNear(CORAL, bitmap.at(FOREHEAD))
        assertNear(Color.parseColor("#3A2247"), bitmap.at(OPEN_EYE))
        bitmap.captureRoboImage("src/test/snapshots/brand/splash_light.png")
    }

    @Config(qualifiers = "night")
    @Test fun `dark mode swaps to cream letters on plum`() {
        val bitmap = launchWindow()
        assertNear(Color.parseColor("#3A2247"), bitmap.getPixel(4, 4))
        assertNear(Color.parseColor("#FFE9D2"), bitmap.at(D_STEM))
        assertNear(CORAL, bitmap.at(FOREHEAD))
        assertNear(Color.parseColor("#2A1430"), bitmap.at(OPEN_EYE))
        bitmap.captureRoboImage("src/test/snapshots/brand/splash_dark.png")
    }

    @Test fun `android 12 splash uses the wordmark as its icon`() {
        val attrs = context.obtainStyledAttributes(
            R.style.Theme_Idl_Starting,
            intArrayOf(android.R.attr.windowSplashScreenAnimatedIcon, android.R.attr.windowSplashScreenBackground),
        )
        try {
            assertEquals(R.drawable.splash_wordmark, attrs.getResourceId(0, 0))
            assertEquals(context.getColor(R.color.idl_splash_bg), attrs.getColor(1, 0))
        } finally {
            attrs.recycle()
        }
    }

    /** The Android 8-11 launch window background at mdpi, on a small phone-shaped canvas. */
    private fun launchWindow(): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val background = checkNotNull(context.getDrawable(R.drawable.splash_background))
        background.setBounds(0, 0, WIDTH, HEIGHT)
        background.draw(Canvas(bitmap))
        return bitmap
    }

    /** Probe points are in the 288-unit splash canvas, centred in the window. */
    private fun Bitmap.at(point: Pair<Float, Float>): Int =
        getPixel((point.first + (WIDTH - 288) / 2).toInt(), (point.second + (HEIGHT - 288) / 2).toInt())

    private fun assertNear(expected: Int, actual: Int) {
        val close = abs(Color.red(expected) - Color.red(actual)) <= 8 &&
            abs(Color.green(expected) - Color.green(actual)) <= 8 &&
            abs(Color.blue(expected) - Color.blue(actual)) <= 8
        assertTrue("expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}", close)
    }

    private companion object {
        const val WIDTH = 360
        const val HEIGHT = 640
        val CORAL = Color.parseColor("#FF8E6E")

        // Printed by docs/art/brand/gen_brand.py.
        val D_STEM = 117.6f to 158.46f
        val FOREHEAD = 79.25f to 110.09f
        val OPEN_EYE = 88.55f to 117.95f
    }
}
