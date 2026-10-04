package app.idl.domain

/**
 * Pure description of what an avatar looks like. The Android painter (`avatar.AvatarRenderer`)
 * draws exactly these layers in exactly this order, so rendering is deterministic and the
 * readability rules below are unit-testable without a device.
 */
enum class EyeShape { OPEN, HAPPY_ARC, SPARKLE, CLOSED_LINE, HALF_LID, TEARFUL, WIDE, ANGRY_SLANT, SPIRAL, NARROW, WINK, SIDE_GLANCE, DOT }
enum class MouthShape { FLAT, SMILE, BIG_GRIN, SMALL_O, FROWN, WAVY, SMIRK, ZIP, OPEN_O }
enum class FaceExtra { NONE, BLUSH, SPARKLES, ZZZ, TEAR, SWEAT, ANGER_MARK, THERMOMETER, STEAM, AFK_TAG, DND_BAR }

data class FaceSpec(val eyes: EyeShape, val mouth: MouthShape, val extra: FaceExtra)

enum class Layer {
    SCENE,
    BODY_ACCESSORY,
    HEAD_BASE,
    FACE_STYLE,
    EYES,
    BROWS,
    MOUTH,
    FACE_ACCESSORY,
    HEAD_ACCESSORY,
    FACE_EXTRA,
    PROP,
    AVAILABILITY_BADGE,
    ACTIVITY_BADGE,
}

data class RenderPlan(
    val layers: List<Layer>,
    /** Full scene illustration; below the threshold the scene is a plain tinted gradient. */
    val sceneDetail: Boolean,
)

object AvatarSpec {
    /** Below this size, decorative layers (props, body accessory) are dropped. */
    const val DECORATION_MIN_PX = 64
    const val SCENE_DETAIL_MIN_PX = 96
    const val ACTIVITY_BADGE_MIN_PX = 48

    fun face(expression: Expression): FaceSpec = when (expression) {
        Expression.NEUTRAL -> FaceSpec(EyeShape.OPEN, MouthShape.FLAT, FaceExtra.NONE)
        Expression.HAPPY -> FaceSpec(EyeShape.HAPPY_ARC, MouthShape.SMILE, FaceExtra.BLUSH)
        Expression.EXCITED -> FaceSpec(EyeShape.SPARKLE, MouthShape.BIG_GRIN, FaceExtra.SPARKLES)
        Expression.SLEEPY -> FaceSpec(EyeShape.CLOSED_LINE, MouthShape.SMALL_O, FaceExtra.ZZZ)
        Expression.TIRED -> FaceSpec(EyeShape.HALF_LID, MouthShape.FLAT, FaceExtra.NONE)
        Expression.SAD -> FaceSpec(EyeShape.TEARFUL, MouthShape.FROWN, FaceExtra.TEAR)
        Expression.ANXIOUS -> FaceSpec(EyeShape.WIDE, MouthShape.WAVY, FaceExtra.SWEAT)
        Expression.ANGRY -> FaceSpec(EyeShape.ANGRY_SLANT, MouthShape.FROWN, FaceExtra.ANGER_MARK)
        Expression.SICK -> FaceSpec(EyeShape.HALF_LID, MouthShape.WAVY, FaceExtra.THERMOMETER)
        Expression.FOCUSED -> FaceSpec(EyeShape.NARROW, MouthShape.FLAT, FaceExtra.NONE)
        Expression.OVERWHELMED -> FaceSpec(EyeShape.SPIRAL, MouthShape.OPEN_O, FaceExtra.STEAM)
        Expression.SOCIAL -> FaceSpec(EyeShape.WINK, MouthShape.BIG_GRIN, FaceExtra.NONE)
        Expression.MISCHIEVOUS -> FaceSpec(EyeShape.SIDE_GLANCE, MouthShape.SMIRK, FaceExtra.NONE)
        Expression.AFK -> FaceSpec(EyeShape.DOT, MouthShape.FLAT, FaceExtra.AFK_TAG)
        Expression.DND -> FaceSpec(EyeShape.CLOSED_LINE, MouthShape.ZIP, FaceExtra.DND_BAR)
    }

    fun plan(
        config: AvatarConfig,
        sizePx: Int,
        showAvailability: Boolean,
        showActivity: Boolean,
    ): RenderPlan {
        val decorate = sizePx >= DECORATION_MIN_PX
        val layers = buildList {
            add(Layer.SCENE)
            if (decorate && config.bodyAccessory != BodyAccessory.NONE) add(Layer.BODY_ACCESSORY)
            add(Layer.HEAD_BASE)
            if (decorate && config.faceStyle != FaceStyle.CLASSIC) add(Layer.FACE_STYLE)
            add(Layer.EYES)
            add(Layer.BROWS)
            add(Layer.MOUTH)
            if (config.faceAccessory != FaceAccessory.NONE) add(Layer.FACE_ACCESSORY)
            if (config.headAccessory != HeadAccessory.NONE) add(Layer.HEAD_ACCESSORY)
            if (face(config.expression).extra != FaceExtra.NONE) add(Layer.FACE_EXTRA)
            if (decorate && config.handProp != Prop.NONE) add(Layer.PROP)
            if (showAvailability) add(Layer.AVAILABILITY_BADGE)
            if (showActivity && sizePx >= ACTIVITY_BADGE_MIN_PX) add(Layer.ACTIVITY_BADGE)
        }
        return RenderPlan(layers, sceneDetail = sizePx >= SCENE_DETAIL_MIN_PX)
    }
}
