package app.idl.avatar

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.withSave
import app.idl.domain.avatar.ColorSlots
import app.idl.domain.avatar.GradientStop
import app.idl.domain.avatar.ItemTransform
import app.idl.domain.avatar.VectorPicture

/**
 * Replays a picture onto a [Canvas]. Cached paths are drawn under the current matrix;
 * this class does not call [Path.transform] or [Path.offset].
 */
class CanvasVectorAssetRenderer : VectorAssetRenderer {
    override fun draw(
        canvas: Canvas,
        picture: VectorPicture,
        colors: Map<String, Int>,
        transform: ItemTransform,
        sizePx: Float,
    ) {
        draw(
            canvas,
            picture,
            colors,
            ItemTransform(),
            transform,
            sizePx,
            picture.parts.map { pathOf(it.commands, it.fillRule) },
            picture.clipPaths.associate { it.id to pathOf(it.commands, "nonzero") },
            partIndex = null,
        )
    }

    fun draw(
        canvas: Canvas,
        picture: VectorPicture,
        colors: Map<String, Int>,
        defaultTransform: ItemTransform,
        transform: ItemTransform,
        sizePx: Float,
        partPaths: List<Path>,
        clipPaths: Map<String, Path>,
        partIndex: Int?,
    ) {
        val indexes = if (partIndex == null) picture.parts.indices else listOf(partIndex)
        canvas.withSave {
            val scale = sizePx / VectorPicture.VIEW_BOX
            scale(scale, scale)
            // Canvas post-multiplies, so the recipe is concatenated before the default and
            // each transform's flip is concatenated before its translate.
            apply(this, transform)
            apply(this, defaultTransform)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            for (index in indexes) {
                val part = picture.parts.getOrNull(index) ?: continue
                val path = partPaths.getOrNull(index) ?: continue
                withSave {
                    part.clip?.let { clip ->
                        val clipPath = clipPaths[clip.id] ?: return@let
                        if (clip.mode == "difference") clipOutPath(clipPath) else clipPath(clipPath)
                    }
                    paint.shader = null
                    paint.alpha = 255
                    when {
                        part.fill.slot != null -> paint.color = withOpacity(colors[part.fill.slot] ?: ColorSlots.NEUTRAL, part.opacity)
                        part.fill.linear != null -> {
                            paint.shader = linear(part.fill.linear, colors)
                            paint.alpha = alphaOf(part.opacity)
                        }
                        part.fill.radial != null -> {
                            paint.shader = radial(part.fill.radial, colors)
                            paint.alpha = alphaOf(part.opacity)
                        }
                    }
                    drawPath(path, paint)
                    // clipBy is validated on the picture and applied across assets in AP-8.
                    part.stroke?.let { stroke ->
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = stroke.width
                        paint.strokeCap = capOf(stroke.cap)
                        paint.strokeJoin = joinOf(stroke.join)
                        paint.shader = null
                        paint.color = withOpacity(colors[stroke.slot] ?: ColorSlots.NEUTRAL, part.opacity)
                        drawPath(path, paint)
                        paint.style = Paint.Style.FILL
                    }
                }
            }
        }
    }

    private fun apply(canvas: Canvas, transform: ItemTransform) {
        if (transform.flipHorizontal) canvas.scale(-1f, 1f, 512f, 0f)
        canvas.scale(transform.scale, transform.scale, 512f, 512f)
        canvas.rotate(transform.rotationDeg, 512f, 512f)
        canvas.translate(transform.translateX, transform.translateY)
    }

    private fun linear(gradient: app.idl.domain.avatar.LinearGradient, colors: Map<String, Int>): LinearGradient {
        val stops = gradient.stops
        return LinearGradient(
            gradient.x1, gradient.y1, gradient.x2, gradient.y2,
            IntArray(stops.size) { stopColor(stops[it], colors) },
            FloatArray(stops.size) { stops[it].offset },
            Shader.TileMode.CLAMP,
        )
    }

    private fun radial(gradient: app.idl.domain.avatar.RadialGradient, colors: Map<String, Int>): RadialGradient {
        val stops = gradient.stops
        return RadialGradient(
            gradient.cx, gradient.cy, gradient.r,
            IntArray(stops.size) { stopColor(stops[it], colors) },
            FloatArray(stops.size) { stops[it].offset },
            Shader.TileMode.CLAMP,
        )
    }

    private fun stopColor(stop: GradientStop, colors: Map<String, Int>): Int {
        val base = colors[stop.slot] ?: ColorSlots.NEUTRAL
        val alpha = stop.alpha ?: return base
        return (base and 0x00FFFFFF) or (alphaOf(alpha) shl 24)
    }

    private fun withOpacity(color: Int, opacity: Float): Int {
        val a = ((color ushr 24) and 0xFF) / 255f * opacity
        return (color and 0x00FFFFFF) or (alphaOf(a) shl 24)
    }

    private fun alphaOf(opacity: Float): Int = (opacity.coerceIn(0f, 1f) * 255f).toInt()

    private fun capOf(name: String) = when (name) {
        "butt" -> Paint.Cap.BUTT
        "square" -> Paint.Cap.SQUARE
        else -> Paint.Cap.ROUND
    }

    private fun joinOf(name: String) = when (name) {
        "miter" -> Paint.Join.MITER
        "bevel" -> Paint.Join.BEVEL
        else -> Paint.Join.ROUND
    }
}

interface VectorAssetRenderer {
    fun draw(
        canvas: Canvas,
        picture: VectorPicture,
        colors: Map<String, Int>,
        transform: ItemTransform,
        sizePx: Float,
    )
}
