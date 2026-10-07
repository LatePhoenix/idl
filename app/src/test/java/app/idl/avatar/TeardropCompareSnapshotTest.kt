package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import app.idl.R
import app.idl.domain.avatar.GradientStop
import app.idl.domain.avatar.ItemTransform
import app.idl.domain.avatar.RadialGradient
import app.idl.domain.avatar.VectorFill
import app.idl.domain.avatar.VectorPart
import app.idl.domain.avatar.VectorPicture
import app.idl.domain.avatar.repoRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * AP-1 acceptance sheet: the pre-change base, the brand-matched base, and the launcher mark.
 * The 48 px row is the real 48 px render, scaled with nearest-neighbor so the pixels stay visible.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class TeardropCompareSnapshotTest {
    private val context = RuntimeEnvironment.getApplication()
    private val renderer = CanvasVectorAssetRenderer()
    private val colors = mapOf(
        "face.primary" to Color.parseColor("#FFC83D"),
        "face.shadow" to Color.parseColor("#E0A21A"),
        "face.highlight" to Color.parseColor("#FFE38A"),
        "outline" to Color.parseColor("#7A4E00"),
    )

    @Test fun `old base, new base, and the brand mark at 512 and 48`() {
        val newBase = VectorPicture.parse(
            File(repoRoot(), "app/src/main/assets/packs/emoji_core/v2/pictures/base_teardrop.json").readText(),
        )
        captureRow(512, 1, newBase, "src/test/snapshots/vector/ap1_compare_512.png")
        captureRow(48, 8, newBase, "src/test/snapshots/vector/ap1_compare_48.png")
    }

    private fun captureRow(size: Int, displayScale: Int, newBase: VectorPicture, path: String) {
        val cell = size * displayScale
        val gap = 16
        val header = 36
        val width = gap + 3 * (cell + gap)
        val height = header + cell + gap
        val sheet = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.parseColor("#FFF1E1"))
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3A2247")
            textSize = 22f
        }
        listOf("Old base", "Brand head", "Launcher").forEachIndexed { index, title ->
            val left = gap + index * (cell + gap)
            canvas.drawText(title, left.toFloat(), 26f, label)
            val rendered = when (index) {
                0 -> baseBitmap(oldBase(), size)
                1 -> baseBitmap(newBase, size)
                else -> launcher(size)
            }
            val shown = if (displayScale == 1) rendered else nearest(rendered, displayScale)
            canvas.drawBitmap(shown, null, Rect(left, header, left + cell, header + cell), Paint().apply { isFilterBitmap = false })
        }
        sheet.captureRoboImage(path)
    }

    private fun baseBitmap(picture: VectorPicture, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FFF1E1"))
        renderer.draw(canvas, picture, colors, ItemTransform(), size.toFloat())
        return bitmap
    }

    private fun launcher(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val half = size / 2f
        canvas.clipPath(Path().apply { addCircle(half, half, half, Path.Direction.CW) })
        canvas.drawColor(context.getColor(R.color.idl_launcher_bg))
        val unit = size / 72f
        canvas.translate(-18 * unit, -18 * unit)
        // mutate() copies the constant state. Without it, an earlier test's draw of this
        // drawable leaves a cached bitmap, and the 48 px row no longer matches on CI.
        val foreground = checkNotNull(context.getDrawable(R.drawable.ic_launcher_foreground)).mutate()
        foreground.setBounds(0, 0, (108 * unit).toInt(), (108 * unit).toInt())
        foreground.draw(canvas)
        return bitmap
    }

    private fun nearest(source: Bitmap, scale: Int): Bitmap {
        val out = Bitmap.createBitmap(source.width * scale, source.height * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply { isFilterBitmap = false }
        canvas.drawBitmap(
            source,
            null,
            Rect(0, 0, out.width, out.height),
            paint,
        )
        return out
    }

    private fun oldBase(): VectorPicture {
        val radial = VectorFill(
            radial = RadialGradient(
                cx = 390f,
                cy = 270f,
                r = 680f,
                stops = listOf(
                    GradientStop(0f, "face.highlight"),
                    GradientStop(1f, "face.primary"),
                ),
            ),
        )
        return VectorPicture(
            schemaVersion = 1,
            id = "base_teardrop_before_ap1",
            contentVersion = 1,
            viewBox = 1024,
            parts = listOf(
                VectorPart(
                    id = "face",
                    zBand = 40,
                    fill = radial,
                    commands = "M 512 96 C 700 96 892 250 892 430 C 892 650 760 860 642 930 L 642 1040 L 382 1040 L 382 930 C 264 860 132 650 132 430 C 132 250 324 96 512 96 Z",
                ),
                VectorPart(
                    id = "shade",
                    zBand = 40,
                    fill = VectorFill(slot = "face.shadow"),
                    commands = "M 260 760 C 340 820 430 850 512 858 C 594 850 684 820 764 760 C 790 820 700 940 628 1040 L 396 1040 C 324 940 234 820 260 760 Z",
                ),
                VectorPart(
                    id = "outline",
                    zBand = 40,
                    fill = VectorFill(slot = "outline"),
                    commands = "M 512 96 C 700 96 892 250 892 430 C 892 650 760 860 642 930 L 642 1040 L 382 1040 L 382 930 C 264 860 132 650 132 430 C 132 250 324 96 512 96 Z M 512 132 C 676 132 856 270 856 446 C 856 640 736 840 618 922 L 618 1040 L 406 1040 L 406 922 C 288 840 168 640 168 446 C 168 270 348 132 512 132 Z",
                    fillRule = "evenodd",
                ),
            ),
        )
    }
}
