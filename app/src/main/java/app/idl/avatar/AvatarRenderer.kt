package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.idl.IdlLog
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarSpec
import app.idl.domain.BaseForm
import app.idl.domain.BodyAccessory
import app.idl.domain.EyeShape
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceExtra
import app.idl.domain.FaceStyle
import app.idl.domain.FrameStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.Layer
import app.idl.domain.MouthShape
import app.idl.domain.Scene
import app.idl.domain.avatar.AccessibilityRenderMode
import app.idl.domain.avatar.AssetCategory
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.CompositeOrder
import app.idl.domain.avatar.DrawOp
import app.idl.domain.avatar.Framing
import app.idl.domain.avatar.ItemTransform
import app.idl.domain.avatar.VectorPicture
import app.idl.domain.avatar.PlaceholderFrame
import app.idl.domain.avatar.PlaceholderFrames
import app.idl.domain.avatar.ResolvedAvatar
import app.idl.domain.avatar.WallpaperContrastMode
import app.idl.domain.wire
import kotlin.math.cos
import kotlin.math.sin

/** Badges painted on top of the avatar (widgets and friend cards). */
data class AvatarBadges(
    val availability: Availability? = null,
    val activity: ActivityType? = null,
)

/** What fills the square behind the character. Export renders each of these at the requested size. */
enum class ExportBackdrop {
    TRANSPARENT,
    SOLID,
    SCENE,
}

/** Sticker outline and stroke weight for a render target's surroundings. */
data class RenderContrast(
    val wallpaper: WallpaperContrastMode = WallpaperContrastMode.NONE,
    val accessibility: AccessibilityRenderMode = AccessibilityRenderMode.STANDARD,
) {
    val outlineColor: Int?
        get() = when (wallpaper) {
            WallpaperContrastMode.LIGHT_WALLPAPER -> 0xFF16202B.toInt()
            WallpaperContrastMode.DARK_WALLPAPER -> 0xFFF4F7FA.toInt()
            WallpaperContrastMode.NONE -> null
        }

    val strokeScale: Float
        get() = if (accessibility == AccessibilityRenderMode.HIGH_CONTRAST) 1.8f else 1f
}

object AvailabilityColors {
    fun of(a: Availability?): Int = when (a) {
        Availability.AVAILABLE -> 0xFF3DBE6E.toInt()
        Availability.TEXT_ONLY -> 0xFF4C8DF6.toInt()
        Availability.CALL_OK -> 0xFF2BB3A3.toInt()
        Availability.GAMING -> 0xFF8E6CF0.toInt()
        Availability.BUSY -> 0xFFF0A23C.toInt()
        Availability.DO_NOT_DISTURB -> 0xFFC4577A.toInt() // soft rose, not alarm red
        Availability.AFK -> 0xFFB7A57A.toInt()
        Availability.OFFLINE, null -> 0xFF9AA0A6.toInt()
    }
}

/**
 * Deterministic Canvas painter for [AvatarConfig]. Layer order and inclusion come from the
 * pure [AvatarSpec.plan]; this class only knows how to draw each layer. The same code paints
 * in-app avatars (Compose) and widget bitmaps, so they always match.
 */
object AvatarRenderer {

    fun bitmap(
        config: AvatarConfig,
        sizePx: Int,
        badges: AvatarBadges? = null,
        simplifyAtPx: Int = sizePx,
        contrast: RenderContrast = RenderContrast(),
        registry: AssetRegistry,
    ): Bitmap = blank(sizePx).also { bmp ->
        draw(Canvas(bmp), config, sizePx.toFloat(), badges, simplifyAtPx, contrast, registry)
    }

    /**
     * Paints a resolved avatar. [pictures] is required. A vector layer whose picture is missing
     * is logged as `avatar.vector_missing` with the asset id only, then skipped so a procedural
     * asset of that category can still draw.
     */
    fun bitmap(
        resolved: ResolvedAvatar,
        registry: AssetRegistry,
        pictures: VectorPictureCache,
        sizePx: Int,
        badges: AvatarBadges? = null,
        contrast: RenderContrast = RenderContrast(),
        backdrop: ExportBackdrop = ExportBackdrop.SCENE,
    ): Bitmap = blank(sizePx).also { bmp ->
        val canvas = Canvas(bmp)
        if (backdrop == ExportBackdrop.SOLID) canvas.drawColor(AvatarExport.PAPER)
        draw(canvas, resolved, registry, pictures, sizePx.toFloat(), badges, contrast, backdrop)
    }

    private fun blank(sizePx: Int): Bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

    fun draw(
        canvas: Canvas,
        config: AvatarConfig,
        size: Float,
        badges: AvatarBadges? = null,
        simplifyAtPx: Int = size.toInt(),
        contrast: RenderContrast = RenderContrast(),
        registry: AssetRegistry,
    ) {
        val plan = AvatarSpec.plan(
            config, simplifyAtPx,
            showAvailability = badges?.availability != null,
            showActivity = badges?.activity != null && badges.activity != ActivityType.NONE,
        )
        val availability = badges?.availability
        val activity = badges?.activity?.takeIf { it != ActivityType.NONE }
        draw(
            canvas,
            PlaceholderFrame(
                config,
                plan.layers,
                plan.sceneDetail,
                availabilityGlyph = availability?.let { registry.asset("avail_${it.wire}")?.glyph },
                availability = availability,
                activityGlyph = activity?.let { registry.asset("badge_${it.wire}")?.glyph },
            ),
            size,
            badges,
            contrast,
        )
    }

    fun draw(
        canvas: Canvas,
        resolved: ResolvedAvatar,
        registry: AssetRegistry,
        pictures: VectorPictureCache,
        size: Float,
        badges: AvatarBadges? = null,
        contrast: RenderContrast = RenderContrast(),
        backdrop: ExportBackdrop = ExportBackdrop.SCENE,
    ) {
        val frame = PlaceholderFrames.from(resolved, registry)
        val loaded = HashMap<String, VectorPictureCache.PicturePaths?>()
        val seen = HashSet<String>()
        val proceduralFallback = mutableListOf<AssetCategory>()
        fun load(assetId: String): VectorPictureCache.PicturePaths? {
            if (assetId in seen) return loaded[assetId]
            seen += assetId
            val asset = registry.asset(assetId)
            if (asset == null || asset.render.type != "vector") return null
            val paths = pictures.get(asset)
            if (paths != null) {
                loaded[assetId] = paths
                return paths
            }
            IdlLog.w("avatar.vector_missing", "asset" to asset.id)
            val fallback = asset.fallback?.let { registry.asset(it) }
            val fallbackPaths = if (fallback?.render?.type == "vector") pictures.get(fallback) else null
            if (fallback != null && fallback.render.type != "vector" && CompositeOrder.proceduralBand(fallback.category) != null) {
                proceduralFallback += fallback.category
            }
            loaded[assetId] = fallbackPaths
            return fallbackPaths
        }
        val ops = CompositeOrder.ops(resolved, registry) { id -> load(id)?.picture }.toMutableList()
        for (category in proceduralFallback.distinct()) {
            if (ops.any { it is DrawOp.Procedural && it.category == category }) continue
            val band = CompositeOrder.proceduralBand(category) ?: continue
            val index = ops.indexOfFirst { op ->
                val (opBand, opZ) = rank(op, registry, ::load)
                opBand > band || (opBand == band && opZ > category.defaultZ)
            }
            val op = DrawOp.Procedural(category, chrome = band >= 200)
            if (index < 0) ops += op else ops.add(index, op)
        }
        val painter = Painter(canvas, frame.config, size, contrast)
        val vectors = CanvasVectorAssetRenderer()
        val publishedMasks = publishedMaskPaths(ops, registry, vectors, resolved, ::load)
        val (framed, chrome) = ops.partition { !it.chrome }
        // Framing is the vector character viewport. Procedural bases keep the full 1024 square
        // so a vector part still lines up with the placeholder head.
        val useFraming = registry.asset(resolved.baseAssetId)?.render?.type == "vector"
        val checkpoint = canvas.save()
        try {
            // The procedural scene clips to the frame. Without it (a vector scene, or none), clip here
            // so vector parts and the procedural layers above them keep the frame shape.
            if (framed.none { it is DrawOp.Procedural && it.category == AssetCategory.SCENE }) painter.frameClip()
            framed.forEach { op ->
                if (backdrop != ExportBackdrop.SCENE && isBackdrop(op, ::load)) return@forEach
                paintOp(op, painter, frame, badges, vectors, canvas, resolved, registry, size, useFraming, publishedMasks, ::load)
            }
        } finally {
            canvas.restoreToCount(checkpoint)
        }
        chrome.forEach { paintOp(it, painter, frame, badges, vectors, canvas, resolved, registry, size, useFraming = false, publishedMasks, ::load) }
    }

    private fun publishedMaskPaths(
        ops: List<DrawOp>,
        registry: AssetRegistry,
        vectors: CanvasVectorAssetRenderer,
        resolved: ResolvedAvatar,
        load: (String) -> VectorPictureCache.PicturePaths?,
    ): Map<String, android.graphics.Path> {
        val sources = app.idl.domain.avatar.PublishedMasks.collect(ops) { id -> load(id)?.picture }
        val out = linkedMapOf<String, android.graphics.Path>()
        for ((name, source) in sources) {
            val paths = load(source.assetId) ?: continue
            val partPath = paths.parts.getOrNull(source.partIndex) ?: continue
            val worn = registry.asset(source.assetId)
            out[name] = vectors.bakeTransform(
                partPath,
                worn?.defaultTransform ?: ItemTransform(),
                resolved.itemTransforms[source.assetId] ?: ItemTransform(),
            )
        }
        return out
    }

    fun draw(
        canvas: Canvas,
        frame: PlaceholderFrame,
        size: Float,
        badges: AvatarBadges? = null,
        contrast: RenderContrast = RenderContrast(),
    ) {
        val p = Painter(canvas, frame.config, size, contrast)
        val (framed, overlay) = frame.layers.partition { it != Layer.AVAILABILITY_BADGE && it != Layer.ACTIVITY_BADGE }
        val checkpoint = canvas.save()
        try {
            drawLayers(p, frame, framed, badges)
        } finally {
            canvas.restoreToCount(checkpoint)
        }
        drawLayers(p, frame, overlay, badges)
    }

    private fun isBackdrop(
        op: DrawOp,
        load: (String) -> VectorPictureCache.PicturePaths?,
    ): Boolean = when (op) {
        is DrawOp.Procedural -> op.category == AssetCategory.SCENE
        is DrawOp.VectorPart -> load(op.assetId)?.picture?.parts?.getOrNull(op.partIndex)?.zBand == 0
    }

    private fun paintOp(
        op: DrawOp,
        painter: Painter,
        frame: PlaceholderFrame,
        badges: AvatarBadges?,
        vectors: CanvasVectorAssetRenderer,
        canvas: Canvas,
        resolved: ResolvedAvatar,
        registry: AssetRegistry,
        size: Float,
        useFraming: Boolean,
        publishedMasks: Map<String, android.graphics.Path>,
        load: (String) -> VectorPictureCache.PicturePaths?,
    ) {
        when (op) {
            is DrawOp.Procedural -> when (op.category) {
                AssetCategory.SCENE -> painter.scene(frame.sceneDetail)
                AssetCategory.BODY_ACCESSORY -> painter.bodyAccessory()
                AssetCategory.BASE -> painter.head()
                AssetCategory.SIGNATURE_FEATURE -> if (frame.config.faceStyle != FaceStyle.CLASSIC) painter.faceStyle()
                AssetCategory.FACE_EYE -> painter.eyes()
                AssetCategory.FACE_BROW -> painter.brows()
                AssetCategory.FACE_MOUTH -> painter.mouth()
                AssetCategory.FACE_ACCESSORY -> painter.faceAccessory()
                AssetCategory.HEAD_ACCESSORY -> painter.headAccessory()
                AssetCategory.EXPRESSION_OVERLAY -> painter.extra()
                AssetCategory.FOREGROUND_PROP -> painter.prop()
                AssetCategory.AVAILABILITY_INDICATOR -> {
                    val availability = frame.availability ?: badges?.availability
                    val glyph = frame.availabilityGlyph
                    if (availability != null && glyph != null) painter.availabilityBadge(glyph, availability)
                }
                AssetCategory.ACTIVITY_BADGE -> frame.activityGlyph?.let { painter.activityBadge(it) }
                else -> Unit
            }
            is DrawOp.VectorPart -> {
                val paths = load(op.assetId) ?: return
                val worn = registry.asset(op.assetId) ?: return
                val part = paths.picture.parts.getOrNull(op.partIndex) ?: return
                val checkpoint = canvas.save()
                try {
                    // Backgrounds fill the output square. Other character parts use the framing
                    // viewport. Chrome stays in output pixels.
                    val character = useFraming && !op.chrome && part.zBand != 0
                    if (character) applyFraming(canvas, resolved.framing, size)
                    val colors = painter.contrast.outlineColor?.let { resolved.colorSlots + ("outline" to it) }
                        ?: resolved.colorSlots
                    vectors.draw(
                        canvas,
                        paths.picture,
                        colors,
                        worn.defaultTransform ?: ItemTransform(),
                        resolved.itemTransforms[op.assetId] ?: ItemTransform(),
                        if (character) VectorPicture.VIEW_BOX.toFloat() else size,
                        paths.parts,
                        paths.clips,
                        op.partIndex,
                        publishedMasks,
                    )
                } finally {
                    canvas.restoreToCount(checkpoint)
                }
            }
        }
    }

    /**
     * Maps the framing viewport onto the output square. Canvas pre-concatenates, so the later
     * call is applied to the point first. Translate in character space, then scale:
     * pixel = (character − origin) × (output / viewport).
     */
    private fun applyFraming(canvas: Canvas, framing: Framing, sizePx: Float) {
        val scale = sizePx / framing.size
        canvas.scale(scale, scale)
        canvas.translate(-framing.originX, -framing.originY)
    }

    private fun rank(
        op: DrawOp,
        registry: AssetRegistry,
        load: (String) -> VectorPictureCache.PicturePaths?,
    ): Pair<Int, Int> = when (op) {
        is DrawOp.Procedural -> (CompositeOrder.proceduralBand(op.category) ?: 0) to op.category.defaultZ
        is DrawOp.VectorPart -> {
            val band = load(op.assetId)?.picture?.parts?.getOrNull(op.partIndex)?.zBand ?: 0
            band to (registry.asset(op.assetId)?.category?.defaultZ ?: 0)
        }
    }

    private fun drawLayers(p: Painter, frame: PlaceholderFrame, layers: List<Layer>, badges: AvatarBadges?) {
        for (layer in layers) {
            when (layer) {
                Layer.SCENE -> p.scene(frame.sceneDetail)
                Layer.BODY_ACCESSORY -> p.bodyAccessory()
                Layer.HEAD_BASE -> p.head()
                Layer.FACE_STYLE -> p.faceStyle()
                Layer.EYES -> p.eyes()
                Layer.BROWS -> p.brows()
                Layer.MOUTH -> p.mouth()
                Layer.FACE_ACCESSORY -> p.faceAccessory()
                Layer.HEAD_ACCESSORY -> p.headAccessory()
                Layer.FACE_EXTRA -> p.extra()
                Layer.PROP -> p.prop()
                Layer.AVAILABILITY_BADGE -> {
                    val availability = frame.availability ?: badges?.availability
                    val glyph = frame.availabilityGlyph
                    if (availability != null && glyph != null) p.availabilityBadge(glyph, availability)
                }
                Layer.ACTIVITY_BADGE -> frame.activityGlyph?.let { p.activityBadge(it) }
            }
        }
    }

    private class Painter(val c: Canvas, val cfg: AvatarConfig, val s: Float, val contrast: RenderContrast = RenderContrast()) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val ink = 0xFF2B2235.toInt()
        val face = SpecFace(cfg)

        // Head geometry (unit = s). The head sits slightly low to leave room for hats.
        val cx = s * 0.5f
        val cy = s * 0.55f
        val r = s * 0.30f
        val eyeY = cy - r * 0.08f
        val eyeDx = r * 0.38f
        val mouthY = cy + r * 0.38f
        val lw = ((s * 0.028f).coerceAtLeast(1.5f)) * contrast.strokeScale

        /** Clips to the frame shape. [scene] does this first; a vector scene needs it on its own. */
        fun frameClip() {
            val bounds = RectF(0f, 0f, s, s)
            val clip = Path().apply {
                when (cfg.frameStyle) {
                    FrameStyle.CIRCLE -> addOval(bounds, Path.Direction.CW)
                    FrameStyle.SQUIRCLE -> addRoundRect(bounds, s * 0.26f, s * 0.26f, Path.Direction.CW)
                }
            }
            c.clipPath(clip)
        }

        fun scene(detail: Boolean) {
            val bounds = RectF(0f, 0f, s, s)
            frameClip()
            val (top, bottom) = sceneColors(cfg.scene)
            fill.shader = LinearGradient(0f, 0f, 0f, s, top, bottom, Shader.TileMode.CLAMP)
            c.drawRect(bounds, fill)
            fill.shader = null
            if (detail) sceneDetail(cfg.scene)
        }

        private fun sceneColors(scene: Scene): Pair<Int, Int> = when (scene) {
            Scene.PLAIN_GRADIENT -> lighten(cfg.themeColor, 0.55f) to lighten(cfg.themeColor, 0.15f)
            Scene.COZY_BEDROOM -> 0xFFF6D7B0.toInt() to 0xFFC98F6B.toInt()
            Scene.DESK_SETUP -> 0xFFDCE6F2.toInt() to 0xFF9FB3CC.toInt()
            Scene.CAMPFIRE -> 0xFF2D2A4A.toInt() to 0xFFE0874A.toInt()
            Scene.DUNGEON_TAVERN -> 0xFF5A3E2B.toInt() to 0xFF2E1F16.toInt()
            Scene.SPACE_STATION -> 0xFF101634.toInt() to 0xFF3A2C6E.toInt()
            Scene.RAINY_WINDOW -> 0xFF8DA2B5.toInt() to 0xFF4D6375.toInt()
            Scene.NEON_CITY -> 0xFF2A0F4F.toInt() to 0xFFE04E9B.toInt()
            Scene.FOREST -> 0xFFBFE3B4.toInt() to 0xFF3E7B4F.toInt()
            Scene.CLOUDSCAPE -> 0xFF9ED4F5.toInt() to 0xFFF3F8FF.toInt()
        }

        private fun sceneDetail(scene: Scene) {
            fill.color = 0x33FFFFFF
            when (scene) {
                Scene.PLAIN_GRADIENT -> Unit
                Scene.COZY_BEDROOM -> { // window + lamp glow
                    fill.color = 0x55FFF4D6; c.drawRoundRect(RectF(s * 0.68f, s * 0.08f, s * 0.92f, s * 0.32f), s * 0.03f, s * 0.03f, fill)
                    fill.color = 0x66FFE29A; c.drawCircle(s * 0.12f, s * 0.2f, s * 0.08f, fill)
                }
                Scene.DESK_SETUP -> { // monitor + desk edge
                    fill.color = 0x55324A66; c.drawRoundRect(RectF(s * 0.66f, s * 0.12f, s * 0.94f, s * 0.32f), s * 0.02f, s * 0.02f, fill)
                    fill.color = 0x44324A66; c.drawRect(0f, s * 0.9f, s, s, fill)
                }
                Scene.CAMPFIRE -> { // stars + glow
                    fill.color = 0xCCFFFFFF.toInt(); listOf(0.12f to 0.12f, 0.82f to 0.1f, 0.7f to 0.22f, 0.25f to 0.25f).forEach { (x, y) -> c.drawCircle(s * x, s * y, s * 0.012f, fill) }
                    fill.color = 0x55FFB347; c.drawCircle(s * 0.5f, s * 1.02f, s * 0.35f, fill)
                }
                Scene.DUNGEON_TAVERN -> { // brick lines + candle
                    stroke.color = 0x33000000; stroke.strokeWidth = lw * 0.6f
                    for (i in 1..4) c.drawLine(0f, s * i * 0.18f, s, s * i * 0.18f, stroke)
                    fill.color = 0x88FFC857.toInt(); c.drawCircle(s * 0.86f, s * 0.2f, s * 0.04f, fill)
                }
                Scene.SPACE_STATION -> {
                    fill.color = 0xDDFFFFFF.toInt(); listOf(0.1f to 0.15f, 0.88f to 0.12f, 0.8f to 0.3f, 0.2f to 0.32f, 0.5f to 0.08f).forEach { (x, y) -> c.drawCircle(s * x, s * y, s * 0.01f, fill) }
                    fill.color = 0x66F2A65A; c.drawCircle(s * 0.85f, s * 0.85f, s * 0.12f, fill)
                }
                Scene.RAINY_WINDOW -> {
                    stroke.color = 0x55FFFFFF; stroke.strokeWidth = lw * 0.5f
                    listOf(0.1f, 0.25f, 0.78f, 0.9f).forEachIndexed { i, x -> c.drawLine(s * x, s * (0.05f + i * 0.05f), s * (x - 0.03f), s * (0.18f + i * 0.05f), stroke) }
                }
                Scene.NEON_CITY -> {
                    fill.color = 0x661B0A33; listOf(0.0f to 0.6f, 0.15f to 0.7f, 0.78f to 0.55f, 0.9f to 0.68f).forEach { (x, y) -> c.drawRect(s * x, s * y, s * (x + 0.12f), s, fill) }
                    fill.color = 0xAA5CF0FF.toInt(); c.drawRect(s * 0.8f, s * 0.6f, s * 0.84f, s * 0.62f, fill)
                }
                Scene.FOREST -> {
                    fill.color = 0x55275C36; listOf(0.08f, 0.88f).forEach { x ->
                        c.drawPath(Path().apply { moveTo(s * x, s * 0.25f); lineTo(s * (x - 0.1f), s * 0.75f); lineTo(s * (x + 0.1f), s * 0.75f); close() }, fill)
                    }
                }
                Scene.CLOUDSCAPE -> {
                    fill.color = 0xCCFFFFFF.toInt(); c.drawCircle(s * 0.15f, s * 0.2f, s * 0.08f, fill); c.drawCircle(s * 0.25f, s * 0.2f, s * 0.06f, fill)
                    c.drawCircle(s * 0.82f, s * 0.15f, s * 0.07f, fill)
                }
            }
        }

        fun bodyAccessory() {
            when (cfg.bodyAccessory) {
                BodyAccessory.NONE -> Unit
                BodyAccessory.HOODIE -> {
                    fill.color = darken(cfg.themeColor, 0.1f)
                    c.drawCircle(cx, cy + r * 0.05f, r * 1.18f, fill)
                    c.drawRoundRect(RectF(cx - r * 1.3f, cy + r * 0.8f, cx + r * 1.3f, s * 1.1f), r * 0.6f, r * 0.6f, fill)
                    stroke.color = lighten(cfg.themeColor, 0.5f); stroke.strokeWidth = lw
                    c.drawLine(cx - r * 0.25f, cy + r * 1.05f, cx - r * 0.3f, cy + r * 1.4f, stroke)
                    c.drawLine(cx + r * 0.25f, cy + r * 1.05f, cx + r * 0.3f, cy + r * 1.4f, stroke)
                }
                BodyAccessory.BLANKET -> {
                    fill.color = 0xFF8FB8DE.toInt()
                    c.drawRoundRect(RectF(cx - r * 1.45f, cy + r * 0.7f, cx + r * 1.45f, s * 1.1f), r * 0.7f, r * 0.7f, fill)
                    stroke.color = 0x66FFFFFF; stroke.strokeWidth = lw * 0.8f
                    for (i in -2..2) c.drawLine(cx + i * r * 0.45f, cy + r * 0.8f, cx + i * r * 0.45f, s, stroke)
                }
            }
        }

        fun head() {
            fill.color = cfg.bodyColor
            stroke.color = darken(cfg.bodyColor, 0.35f)
            stroke.strokeWidth = lw
            val path = Path()
            when (cfg.baseForm) {
                BaseForm.HUMAN -> path.addCircle(cx, cy, r, Path.Direction.CW)
                BaseForm.BLOB -> {
                    val n = 8
                    for (i in 0..n) {
                        val a = (i * 2 * Math.PI / n).toFloat()
                        val rr = r * (if (i % 2 == 0) 1.04f else 0.94f)
                        val x = cx + rr * cos(a); val y = cy + rr * sin(a) * 0.95f
                        if (i == 0) path.moveTo(x, y) else {
                            val pa = ((i - 0.5) * 2 * Math.PI / n).toFloat()
                            path.quadTo(cx + r * 1.08f * cos(pa), cy + r * 1.02f * sin(pa), x, y)
                        }
                    }
                    path.close()
                }
                BaseForm.ROBOT -> {
                    path.addRoundRect(RectF(cx - r, cy - r * 0.9f, cx + r, cy + r * 0.95f), r * 0.28f, r * 0.28f, Path.Direction.CW)
                    c.drawLine(cx, cy - r * 0.9f, cx, cy - r * 1.25f, stroke)
                    fill.color = cfg.themeColor; c.drawCircle(cx, cy - r * 1.3f, r * 0.1f, fill); fill.color = cfg.bodyColor
                }
                BaseForm.GHOST -> {
                    path.moveTo(cx - r, cy + r)
                    path.lineTo(cx - r, cy)
                    path.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f)
                    path.lineTo(cx + r, cy + r)
                    val w = 2 * r / 4
                    for (i in 0 until 4) {
                        val x0 = cx + r - i * w
                        path.quadTo(x0 - w / 2, cy + r * (if (i % 2 == 0) 1.22f else 0.9f), x0 - w, cy + r)
                    }
                    path.close()
                }
                BaseForm.CAT, BaseForm.FOX -> {
                    val earH = if (cfg.baseForm == BaseForm.FOX) 0.75f else 0.55f
                    for (sign in listOf(-1f, 1f)) {
                        val ear = Path().apply {
                            moveTo(cx + sign * r * 0.85f, cy - r * 0.35f)
                            lineTo(cx + sign * r * 0.78f, cy - r * (0.95f + earH * 0.5f))
                            lineTo(cx + sign * r * 0.2f, cy - r * 0.9f)
                            close()
                        }
                        c.drawPath(ear, fill); c.drawPath(ear, stroke)
                    }
                    path.addCircle(cx, cy, r, Path.Direction.CW)
                }
                BaseForm.BEAR -> {
                    for (sign in listOf(-1f, 1f)) {
                        c.drawCircle(cx + sign * r * 0.72f, cy - r * 0.78f, r * 0.3f, fill)
                        c.drawCircle(cx + sign * r * 0.72f, cy - r * 0.78f, r * 0.3f, stroke)
                    }
                    path.addCircle(cx, cy, r, Path.Direction.CW)
                }
                BaseForm.ALIEN -> {
                    for (sign in listOf(-1f, 1f)) {
                        c.drawLine(cx + sign * r * 0.3f, cy - r * 0.9f, cx + sign * r * 0.55f, cy - r * 1.35f, stroke)
                        fill.color = cfg.themeColor; c.drawCircle(cx + sign * r * 0.55f, cy - r * 1.38f, r * 0.1f, fill); fill.color = cfg.bodyColor
                    }
                    path.addOval(RectF(cx - r * 0.95f, cy - r * 1.05f, cx + r * 0.95f, cy + r * 0.95f), Path.Direction.CW)
                }
                BaseForm.PIXEL -> {
                    // Stair-stepped square head; deliberately blocky.
                    val u = r / 4
                    path.moveTo(cx - 3 * u, cy - 4 * u)
                    path.lineTo(cx + 3 * u, cy - 4 * u); path.lineTo(cx + 3 * u, cy - 3 * u); path.lineTo(cx + 4 * u, cy - 3 * u)
                    path.lineTo(cx + 4 * u, cy + 3 * u); path.lineTo(cx + 3 * u, cy + 3 * u); path.lineTo(cx + 3 * u, cy + 4 * u)
                    path.lineTo(cx - 3 * u, cy + 4 * u); path.lineTo(cx - 3 * u, cy + 3 * u); path.lineTo(cx - 4 * u, cy + 3 * u)
                    path.lineTo(cx - 4 * u, cy - 3 * u); path.lineTo(cx - 3 * u, cy - 3 * u)
                    path.close()
                }
            }
            contrast.outlineColor?.let { color ->
                val width = stroke.strokeWidth
                val inkColor = stroke.color
                stroke.color = color
                stroke.strokeWidth = lw * 4.5f
                c.drawPath(path, stroke)
                stroke.strokeWidth = width
                stroke.color = inkColor
            }
            c.drawPath(path, fill)
            c.drawPath(path, stroke)
            if (cfg.baseForm == BaseForm.FOX) { // muzzle
                fill.color = 0xFFFFF4E8.toInt()
                c.drawOval(RectF(cx - r * 0.45f, cy + r * 0.1f, cx + r * 0.45f, cy + r * 0.75f), fill)
            }
            if (cfg.baseForm == BaseForm.ROBOT) { // visor
                fill.color = 0x22000000
                c.drawRoundRect(RectF(cx - r * 0.75f, eyeY - r * 0.28f, cx + r * 0.75f, eyeY + r * 0.28f), r * 0.2f, r * 0.2f, fill)
            }
        }

        fun faceStyle() {
            when (cfg.faceStyle) {
                FaceStyle.CLASSIC -> Unit
                FaceStyle.BLUSHY -> cheeks(0x55FF6F91)
                FaceStyle.FRECKLES -> {
                    fill.color = darken(cfg.bodyColor, 0.3f)
                    for (sign in listOf(-1f, 1f)) for (k in 0..2) {
                        c.drawCircle(cx + sign * (eyeDx + r * (k - 1) * 0.1f), eyeY + r * (0.32f + (k % 2) * 0.07f), r * 0.03f, fill)
                    }
                }
            }
        }

        private fun cheeks(color: Int) {
            fill.color = color
            for (sign in listOf(-1f, 1f)) c.drawOval(RectF(cx + sign * eyeDx - r * 0.17f, eyeY + r * 0.22f, cx + sign * eyeDx + r * 0.17f, eyeY + r * 0.38f), fill)
        }

        fun eyes() {
            stroke.color = ink; stroke.strokeWidth = lw * 1.2f
            fill.color = ink
            val er = r * 0.11f
            for (sign in listOf(-1f, 1f)) {
                val ex = cx + sign * eyeDx
                when (face.eyes) {
                    EyeShape.OPEN -> c.drawCircle(ex, eyeY, er, fill)
                    EyeShape.DOT -> c.drawCircle(ex, eyeY, er * 0.5f, fill)
                    EyeShape.HAPPY_ARC -> c.drawArc(RectF(ex - er * 1.2f, eyeY - er, ex + er * 1.2f, eyeY + er * 1.4f), 200f, 140f, false, stroke)
                    EyeShape.SPARKLE -> { star(ex, eyeY, er * 1.5f, ink); fill.color = ink }
                    EyeShape.CLOSED_LINE -> c.drawArc(RectF(ex - er * 1.2f, eyeY - er * 1.2f, ex + er * 1.2f, eyeY + er * 0.8f), 20f, 140f, false, stroke)
                    EyeShape.HALF_LID -> {
                        c.drawArc(RectF(ex - er, eyeY - er, ex + er, eyeY + er), 0f, 180f, true, fill)
                        c.drawLine(ex - er * 1.3f, eyeY, ex + er * 1.3f, eyeY, stroke)
                    }
                    EyeShape.TEARFUL -> {
                        c.drawCircle(ex, eyeY, er * 1.1f, fill)
                        fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(ex - er * 0.35f, eyeY - er * 0.35f, er * 0.4f, fill); fill.color = ink
                    }
                    EyeShape.WIDE -> {
                        fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(ex, eyeY, er * 1.5f, fill)
                        stroke.strokeWidth = lw * 0.8f; c.drawCircle(ex, eyeY, er * 1.5f, stroke); stroke.strokeWidth = lw * 1.2f
                        fill.color = ink; c.drawCircle(ex, eyeY, er * 0.55f, fill)
                    }
                    EyeShape.ANGRY_SLANT -> c.drawCircle(ex, eyeY + er * 0.3f, er * 0.9f, fill)
                    EyeShape.SPIRAL -> {
                        stroke.strokeWidth = lw * 0.8f
                        val sp = Path()
                        for (i in 0..24) {
                            val a = i * 0.55f; val rr = er * 0.12f * i / 2.2f
                            val x = ex + rr * cos(a); val y = eyeY + rr * sin(a)
                            if (i == 0) sp.moveTo(x, y) else sp.lineTo(x, y)
                        }
                        c.drawPath(sp, stroke); stroke.strokeWidth = lw * 1.2f
                    }
                    EyeShape.NARROW -> c.drawRoundRect(RectF(ex - er * 1.2f, eyeY - er * 0.35f, ex + er * 1.2f, eyeY + er * 0.35f), er * 0.3f, er * 0.3f, fill)
                    EyeShape.WINK -> if (sign < 0) c.drawCircle(ex, eyeY, er, fill)
                        else c.drawArc(RectF(ex - er * 1.2f, eyeY - er, ex + er * 1.2f, eyeY + er * 1.4f), 200f, 140f, false, stroke)
                    EyeShape.SIDE_GLANCE -> {
                        fill.color = 0xFFFFFFFF.toInt(); c.drawOval(RectF(ex - er * 1.4f, eyeY - er, ex + er * 1.4f, eyeY + er), fill)
                        fill.color = ink; c.drawCircle(ex + er * 0.7f, eyeY, er * 0.6f, fill)
                    }
                }
            }
        }

        fun brows() {
            stroke.color = ink
            stroke.strokeWidth = lw * 1.1f
            val by = eyeY - r * 0.32f
            for (sign in listOf(-1f, 1f)) {
                val x = cx + sign * eyeDx
                val half = r * 0.16f
                when (face.eyes) {
                    EyeShape.ANGRY_SLANT -> c.drawLine(x - sign * half, by - r * 0.06f, x + sign * half, by + r * 0.06f, stroke)
                    EyeShape.TEARFUL, EyeShape.WIDE -> c.drawLine(x - sign * half, by + r * 0.05f, x + sign * half, by - r * 0.06f, stroke)
                    EyeShape.HALF_LID, EyeShape.CLOSED_LINE, EyeShape.DOT -> c.drawLine(x - half, by + r * 0.04f, x + half, by + r * 0.04f, stroke)
                    EyeShape.NARROW -> c.drawLine(x - half, by, x + half, by + r * 0.02f, stroke)
                    EyeShape.HAPPY_ARC, EyeShape.SPARKLE, EyeShape.WINK ->
                        c.drawArc(RectF(x - half, by - r * 0.04f, x + half, by + r * 0.08f), 200f, 140f, false, stroke)
                    EyeShape.SIDE_GLANCE -> {
                        val lift = if (sign > 0) -r * 0.06f else 0f
                        c.drawLine(x - half, by + lift, x + half, by + lift * 0.3f, stroke)
                    }
                    else -> c.drawArc(RectF(x - half, by, x + half, by + r * 0.1f), 200f, 140f, false, stroke)
                }
            }
        }

        fun mouth() {
            stroke.color = ink; stroke.strokeWidth = lw * 1.2f
            fill.color = ink
            val mw = r * 0.32f
            when (face.mouth) {
                MouthShape.FLAT -> c.drawLine(cx - mw * 0.6f, mouthY, cx + mw * 0.6f, mouthY, stroke)
                MouthShape.SMILE -> c.drawArc(RectF(cx - mw, mouthY - mw * 0.9f, cx + mw, mouthY + mw * 0.4f), 20f, 140f, false, stroke)
                MouthShape.BIG_GRIN -> {
                    val p = Path().apply { moveTo(cx - mw, mouthY - mw * 0.2f); quadTo(cx, mouthY + mw * 1.3f, cx + mw, mouthY - mw * 0.2f); close() }
                    c.drawPath(p, fill)
                    fill.color = 0xFFFF7A8A.toInt(); c.drawCircle(cx, mouthY + mw * 0.35f, mw * 0.28f, fill)
                }
                MouthShape.SMALL_O -> c.drawOval(RectF(cx - mw * 0.2f, mouthY - mw * 0.1f, cx + mw * 0.2f, mouthY + mw * 0.35f), fill)
                MouthShape.OPEN_O -> c.drawOval(RectF(cx - mw * 0.4f, mouthY - mw * 0.3f, cx + mw * 0.4f, mouthY + mw * 0.6f), fill)
                MouthShape.FROWN -> c.drawArc(RectF(cx - mw * 0.8f, mouthY, cx + mw * 0.8f, mouthY + mw * 1.0f), 200f, 140f, false, stroke)
                MouthShape.WAVY -> {
                    val p = Path().apply {
                        moveTo(cx - mw, mouthY)
                        quadTo(cx - mw * 0.5f, mouthY - mw * 0.35f, cx, mouthY)
                        quadTo(cx + mw * 0.5f, mouthY + mw * 0.35f, cx + mw, mouthY)
                    }
                    c.drawPath(p, stroke)
                }
                MouthShape.SMIRK -> c.drawArc(RectF(cx - mw * 0.2f, mouthY - mw * 0.8f, cx + mw * 1.0f, mouthY + mw * 0.3f), 30f, 110f, false, stroke)
                MouthShape.ZIP -> {
                    c.drawLine(cx - mw, mouthY, cx + mw, mouthY, stroke)
                    stroke.strokeWidth = lw * 0.7f
                    for (i in -2..2) c.drawLine(cx + i * mw * 0.4f, mouthY - mw * 0.18f, cx + i * mw * 0.4f, mouthY + mw * 0.18f, stroke)
                }
            }
        }

        fun faceAccessory() {
            stroke.color = ink; stroke.strokeWidth = lw
            val gr = r * 0.22f
            when (cfg.faceAccessory) {
                FaceAccessory.NONE -> Unit
                FaceAccessory.GLASSES -> {
                    for (sign in listOf(-1f, 1f)) c.drawCircle(cx + sign * eyeDx, eyeY, gr, stroke)
                    c.drawLine(cx - eyeDx + gr, eyeY, cx + eyeDx - gr, eyeY, stroke)
                }
                FaceAccessory.SUNGLASSES -> {
                    fill.color = 0xEE1B1B24.toInt()
                    for (sign in listOf(-1f, 1f)) c.drawRoundRect(RectF(cx + sign * eyeDx - gr * 1.2f, eyeY - gr * 0.8f, cx + sign * eyeDx + gr * 1.2f, eyeY + gr * 0.9f), gr * 0.4f, gr * 0.4f, fill)
                    c.drawLine(cx - eyeDx + gr, eyeY - gr * 0.3f, cx + eyeDx - gr, eyeY - gr * 0.3f, stroke)
                }
            }
        }

        fun headAccessory() {
            val top = cy - r
            stroke.color = ink; stroke.strokeWidth = lw
            when (cfg.headAccessory) {
                HeadAccessory.NONE -> Unit
                HeadAccessory.HEADPHONES -> {
                    stroke.color = 0xFF3A3F55.toInt(); stroke.strokeWidth = lw * 2.2f
                    c.drawArc(RectF(cx - r * 1.08f, cy - r * 1.12f, cx + r * 1.08f, cy + r * 0.9f), 190f, 160f, false, stroke)
                    fill.color = cfg.themeColor
                    for (sign in listOf(-1f, 1f)) c.drawRoundRect(RectF(cx + sign * r * 1.05f - r * 0.18f, cy - r * 0.25f, cx + sign * r * 1.05f + r * 0.18f, cy + r * 0.3f), r * 0.1f, r * 0.1f, fill)
                }
                HeadAccessory.VR_HEADSET -> {
                    fill.color = 0xFF2E3142.toInt()
                    c.drawRoundRect(RectF(cx - r * 0.95f, eyeY - r * 0.32f, cx + r * 0.95f, eyeY + r * 0.3f), r * 0.18f, r * 0.18f, fill)
                    fill.color = cfg.themeColor
                    c.drawRoundRect(RectF(cx - r * 0.8f, eyeY - r * 0.18f, cx + r * 0.8f, eyeY + r * 0.16f), r * 0.12f, r * 0.12f, fill)
                    stroke.color = 0xFF2E3142.toInt(); stroke.strokeWidth = lw * 1.5f
                    c.drawLine(cx - r * 0.95f, eyeY, cx - r * 1.05f, eyeY - r * 0.1f, stroke)
                    c.drawLine(cx + r * 0.95f, eyeY, cx + r * 1.05f, eyeY - r * 0.1f, stroke)
                }
                HeadAccessory.BEANIE -> {
                    fill.color = cfg.themeColor
                    c.drawArc(RectF(cx - r * 0.95f, top - r * 0.25f, cx + r * 0.95f, top + r * 1.1f), 180f, 180f, true, fill)
                    fill.color = darken(cfg.themeColor, 0.2f)
                    c.drawRoundRect(RectF(cx - r * 1.0f, top + r * 0.35f, cx + r * 1.0f, top + r * 0.6f), r * 0.1f, r * 0.1f, fill)
                    fill.color = lighten(cfg.themeColor, 0.5f); c.drawCircle(cx, top - r * 0.25f, r * 0.15f, fill)
                }
                HeadAccessory.CROWN -> {
                    fill.color = 0xFFF4C542.toInt()
                    val p = Path().apply {
                        moveTo(cx - r * 0.6f, top + r * 0.15f); lineTo(cx - r * 0.6f, top - r * 0.35f); lineTo(cx - r * 0.3f, top - r * 0.1f)
                        lineTo(cx, top - r * 0.45f); lineTo(cx + r * 0.3f, top - r * 0.1f); lineTo(cx + r * 0.6f, top - r * 0.35f)
                        lineTo(cx + r * 0.6f, top + r * 0.15f); close()
                    }
                    c.drawPath(p, fill); stroke.color = 0xFFB8891B.toInt(); c.drawPath(p, stroke)
                }
                HeadAccessory.WIZARD_HAT -> {
                    fill.color = 0xFF4B3F9E.toInt()
                    val p = Path().apply { moveTo(cx - r * 0.75f, top + r * 0.25f); lineTo(cx + r * 0.15f, top - r * 0.95f); lineTo(cx + r * 0.75f, top + r * 0.25f); close() }
                    c.drawPath(p, fill)
                    c.drawRoundRect(RectF(cx - r * 1.0f, top + r * 0.15f, cx + r * 1.0f, top + r * 0.35f), r * 0.1f, r * 0.1f, fill)
                    star(cx, top - r * 0.25f, r * 0.12f, 0xFFF4C542.toInt())
                }
                HeadAccessory.CAT_EARS -> {
                    fill.color = cfg.themeColor
                    for (sign in listOf(-1f, 1f)) {
                        c.drawPath(Path().apply {
                            moveTo(cx + sign * r * 0.2f, top + r * 0.1f); lineTo(cx + sign * r * 0.55f, top - r * 0.5f); lineTo(cx + sign * r * 0.8f, top + r * 0.3f); close()
                        }, fill)
                    }
                }
                HeadAccessory.HELMET -> {
                    fill.color = 0xFFDDE3EA.toInt()
                    c.drawArc(RectF(cx - r * 1.08f, top - r * 0.12f, cx + r * 1.08f, cy + r * 0.6f), 180f, 180f, true, fill)
                    stroke.color = 0xFF8A96A3.toInt(); c.drawArc(RectF(cx - r * 1.08f, top - r * 0.12f, cx + r * 1.08f, cy + r * 0.6f), 180f, 180f, true, stroke)
                    fill.color = cfg.themeColor; c.drawRect(cx - r * 0.1f, top - r * 0.12f, cx + r * 0.1f, cy - r * 0.2f, fill)
                }
                HeadAccessory.SLEEP_CAP -> {
                    fill.color = 0xFF7A9BE0.toInt()
                    val p = Path().apply { moveTo(cx - r * 0.9f, top + r * 0.35f); quadTo(cx, top - r * 0.6f, cx + r * 1.1f, top + r * 0.05f); lineTo(cx + r * 0.9f, top + r * 0.35f); close() }
                    c.drawPath(p, fill)
                    fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(cx + r * 1.12f, top + r * 0.05f, r * 0.14f, fill)
                }
            }
        }

        fun extra() {
            when (face.extra) {
                FaceExtra.NONE -> Unit
                FaceExtra.BLUSH -> cheeks(0x66FF6F91)
                FaceExtra.SPARKLES -> { star(cx + r * 1.05f, cy - r * 0.95f, r * 0.16f, 0xFFF4C542.toInt()); star(cx - r * 1.1f, cy - r * 0.6f, r * 0.11f, 0xFFF4C542.toInt()) }
                FaceExtra.ZZZ -> text("z", cx + r * 0.95f, cy - r * 0.75f, r * 0.32f, 0xFF5B6BB5.toInt()).also { text("z", cx + r * 1.2f, cy - r * 1.05f, r * 0.24f, 0xFF5B6BB5.toInt()) }
                FaceExtra.TEAR -> { fill.color = 0xFF6EC1F5.toInt(); c.drawOval(RectF(cx + eyeDx - r * 0.06f, eyeY + r * 0.15f, cx + eyeDx + r * 0.08f, eyeY + r * 0.38f), fill) }
                FaceExtra.SWEAT -> { fill.color = 0xFF6EC1F5.toInt(); c.drawOval(RectF(cx + r * 0.7f, cy - r * 0.75f, cx + r * 0.88f, cy - r * 0.45f), fill) }
                FaceExtra.ANGER_MARK -> {
                    stroke.color = 0xFFE0584F.toInt(); stroke.strokeWidth = lw * 1.2f
                    val ax = cx + r * 0.75f; val ay = cy - r * 0.75f; val a = r * 0.12f
                    c.drawLine(ax - a, ay - a * 0.3f, ax + a, ay - a * 0.3f, stroke); c.drawLine(ax - a, ay + a * 0.3f, ax + a, ay + a * 0.3f, stroke)
                    c.drawLine(ax - a * 0.3f, ay - a, ax - a * 0.3f, ay + a, stroke); c.drawLine(ax + a * 0.3f, ay - a, ax + a * 0.3f, ay + a, stroke)
                }
                FaceExtra.THERMOMETER -> {
                    stroke.color = 0xFFE0584F.toInt(); stroke.strokeWidth = lw * 1.3f
                    c.drawLine(cx + r * 0.15f, mouthY, cx + r * 0.6f, mouthY - r * 0.15f, stroke)
                    fill.color = 0xFFE0584F.toInt(); c.drawCircle(cx + r * 0.62f, mouthY - r * 0.16f, r * 0.06f, fill)
                    fill.color = 0x3374C365; c.drawCircle(cx, cy, r, fill) // queasy tint
                }
                FaceExtra.STEAM -> {
                    stroke.color = 0x99FFFFFF.toInt(); stroke.strokeWidth = lw * 1.3f
                    for (sign in listOf(-1f, 1f)) {
                        val sx = cx + sign * r * 0.6f
                        c.drawPath(Path().apply { moveTo(sx, cy - r * 1.0f); quadTo(sx + r * 0.12f, cy - r * 1.15f, sx, cy - r * 1.3f); quadTo(sx - r * 0.12f, cy - r * 1.45f, sx, cy - r * 1.6f) }, stroke)
                    }
                }
                FaceExtra.AFK_TAG -> pill("AFK", cx + r * 0.7f, cy - r * 1.05f, 0xFFB7A57A.toInt())
                FaceExtra.DND_BAR -> {
                    fill.color = 0xFFC4577A.toInt(); c.drawCircle(cx + r * 0.85f, cy - r * 0.9f, r * 0.22f, fill)
                    stroke.color = 0xFFFFFFFF.toInt(); stroke.strokeWidth = lw * 1.3f
                    c.drawLine(cx + r * 0.73f, cy - r * 0.9f, cx + r * 0.97f, cy - r * 0.9f, stroke)
                }
            }
        }

        fun prop() {
            val emoji = cfg.handProp.emoji
            if (emoji.isEmpty()) return
            val px = cx - r * 1.0f; val py = cy + r * 0.85f
            fill.color = 0xDDFFFFFF.toInt(); c.drawCircle(px, py, r * 0.32f, fill)
            text(emoji, px, py + r * 0.12f, r * 0.36f, ink)
        }

        fun availabilityBadge(glyph: String, availability: Availability) {
            val br = s * 0.11f
            val bx = s - br * 1.35f
            val by = s - br * 1.35f
            fill.shader = null
            fill.color = 0xFFFFFFFF.toInt()
            c.drawCircle(bx, by, br * 1.22f, fill)
            fill.color = AvailabilityColors.of(availability)
            stroke.color = AvailabilityColors.of(availability)
            stroke.strokeWidth = (br * 0.22f).coerceAtLeast(1.5f)
            drawAvailabilityGlyph(glyph, bx, by, br * 0.72f)
        }

        fun activityBadge(glyph: String) {
            val br = s * 0.12f
            val bx = s - br * 1.2f
            val by = br * 1.2f
            fill.shader = null
            fill.color = 0xF2FFFFFF.toInt()
            c.drawCircle(bx, by, br, fill)
            fill.color = ink
            stroke.color = ink
            stroke.strokeWidth = (br * 0.16f).coerceAtLeast(1.5f)
            drawActivityGlyph(glyph, bx, by, br * 0.55f)
        }

        private fun drawAvailabilityGlyph(glyph: String, x: Float, y: Float, r: Float) {
            when (glyph) {
                "circle" -> c.drawCircle(x, y, r, fill)
                "hollow_ring" -> c.drawCircle(x, y, r, stroke)
                "speech_bubble" -> {
                    c.drawRoundRect(RectF(x - r, y - r * 0.75f, x + r, y + r * 0.45f), r * 0.35f, r * 0.35f, fill)
                    val tail = Path().apply {
                        moveTo(x - r * 0.15f, y + r * 0.35f)
                        lineTo(x - r * 0.55f, y + r)
                        lineTo(x + r * 0.3f, y + r * 0.35f)
                        close()
                    }
                    c.drawPath(tail, fill)
                }
                "handset" -> {
                    c.drawRoundRect(RectF(x - r * 0.42f, y - r, x + r * 0.42f, y + r), r * 0.2f, r * 0.2f, fill)
                    fill.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(x, y - r * 0.55f, r * 0.16f, fill)
                    c.drawCircle(x, y + r * 0.55f, r * 0.16f, fill)
                    fill.color = stroke.color
                }
                "controller" -> {
                    c.drawRoundRect(RectF(x - r, y - r * 0.45f, x + r, y + r * 0.45f), r * 0.4f, r * 0.4f, fill)
                    fill.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(x - r * 0.45f, y, r * 0.16f, fill)
                    c.drawCircle(x + r * 0.45f, y, r * 0.16f, fill)
                    fill.color = stroke.color
                }
                "hourglass" -> {
                    val glass = Path().apply {
                        moveTo(x - r, y - r)
                        lineTo(x + r, y - r)
                        lineTo(x, y)
                        lineTo(x + r, y + r)
                        lineTo(x - r, y + r)
                        close()
                    }
                    c.drawPath(glass, fill)
                }
                "crescent" -> c.drawArc(RectF(x - r, y - r, x + r, y + r), 50f, 260f, true, fill)
                "clock" -> {
                    c.drawCircle(x, y, r, stroke)
                    c.drawLine(x, y, x, y - r * 0.65f, stroke)
                    c.drawLine(x, y, x + r * 0.45f, y + r * 0.15f, stroke)
                }
                else -> c.drawCircle(x, y, r, stroke)
            }
        }

        private fun drawActivityGlyph(glyph: String, x: Float, y: Float, r: Float) {
            when (glyph) {
                "working" -> c.drawRoundRect(RectF(x - r, y - r * 0.7f, x + r, y + r * 0.7f), r * 0.15f, r * 0.15f, stroke)
                "coding" -> {
                    c.drawLine(x - r * 0.15f, y - r, x - r, y, stroke)
                    c.drawLine(x - r, y, x - r * 0.15f, y + r, stroke)
                    c.drawLine(x + r * 0.15f, y - r, x + r, y, stroke)
                    c.drawLine(x + r, y, x + r * 0.15f, y + r, stroke)
                }
                "gaming" -> c.drawRoundRect(RectF(x - r, y - r * 0.4f, x + r, y + r * 0.4f), r * 0.35f, r * 0.35f, stroke)
                "vr" -> {
                    c.drawRect(x - r, y - r * 0.4f, x - r * 0.1f, y + r * 0.4f, stroke)
                    c.drawRect(x + r * 0.1f, y - r * 0.4f, x + r, y + r * 0.4f, stroke)
                }
                "watching" -> c.drawRect(x - r, y - r * 0.7f, x + r, y + r * 0.7f, stroke)
                "listening" -> c.drawArc(RectF(x - r, y - r, x + r, y + r * 0.4f), 200f, 140f, false, stroke)
                "reading" -> {
                    c.drawLine(x, y - r, x, y + r, stroke)
                    c.drawLine(x - r, y - r * 0.7f, x, y - r, stroke)
                    c.drawLine(x, y - r, x + r, y - r * 0.7f, stroke)
                    c.drawLine(x - r, y + r * 0.7f, x, y + r, stroke)
                    c.drawLine(x, y + r, x + r, y + r * 0.7f, stroke)
                }
                "traveling" -> {
                    val arrow = Path().apply {
                        moveTo(x, y - r)
                        lineTo(x + r, y + r * 0.7f)
                        lineTo(x, y + r * 0.25f)
                        lineTo(x - r, y + r * 0.7f)
                        close()
                    }
                    c.drawPath(arrow, fill)
                }
                "exercising" -> {
                    c.drawLine(x - r, y + r * 0.6f, x - r * 0.2f, y - r * 0.2f, stroke)
                    c.drawLine(x - r * 0.2f, y - r * 0.2f, x + r * 0.3f, y + r * 0.2f, stroke)
                    c.drawLine(x + r * 0.3f, y + r * 0.2f, x + r, y - r, stroke)
                }
                "sleeping" -> c.drawArc(RectF(x - r, y - r, x + r, y + r), 50f, 260f, false, stroke)
                "custom" -> star(x, y, r, ink)
                else -> c.drawCircle(x, y, r * 0.35f, fill)
            }
        }

        private fun text(t: String, x: Float, y: Float, size: Float, color: Int) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
            }
            c.drawText(t, x, y, tp)
        }

        private fun pill(t: String, x: Float, y: Float, color: Int) {
            val h = r * 0.3f; val w = r * 0.7f
            fill.color = color; c.drawRoundRect(RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), h / 2, h / 2, fill)
            text(t, x, y + h * 0.28f, h * 0.7f, 0xFFFFFFFF.toInt())
        }

        private fun star(x: Float, y: Float, rr: Float, color: Int) {
            fill.color = color
            val p = Path()
            for (i in 0 until 8) {
                val a = (i * Math.PI / 4 - Math.PI / 2).toFloat()
                val rad = if (i % 2 == 0) rr else rr * 0.35f
                val px = x + rad * cos(a); val py = y + rad * sin(a)
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            p.close()
            c.drawPath(p, fill)
        }
    }

    /** Small helper so the painter reads the face spec once. */
    private class SpecFace(cfg: AvatarConfig) {
        private val f = AvatarSpec.face(cfg.expression)
        val eyes = f.eyes
        val mouth = f.mouth
        val extra = f.extra
    }

    private fun lighten(color: Int, t: Float) = mix(color, 0xFFFFFFFF.toInt(), t)
    private fun darken(color: Int, t: Float) = mix(color, 0xFF000000.toInt(), t)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
