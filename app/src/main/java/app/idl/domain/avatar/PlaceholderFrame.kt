package app.idl.domain.avatar

import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarPalette
import app.idl.domain.BaseForm
import app.idl.domain.BodyAccessory
import app.idl.domain.Expression
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceStyle
import app.idl.domain.FrameStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.Layer
import app.idl.domain.Prop
import app.idl.domain.Scene
import app.idl.domain.wire

/**
 * The procedural painter still draws v1 shapes. This maps a resolved v2 avatar onto those
 * shapes, and only includes a layer when the resolver kept it. An occluded face part is
 * therefore absent here too.
 */
data class PlaceholderFrame(
    val config: AvatarConfig,
    val layers: List<Layer>,
    val sceneDetail: Boolean,
    /** Manifest glyph for the availability indicator the resolver kept, if any. */
    val availabilityGlyph: String? = null,
    val availability: Availability? = null,
    /** Manifest glyph for the activity badge the resolver kept, if any. */
    val activityGlyph: String? = null,
)

object PlaceholderFrames {
    fun from(resolved: ResolvedAvatar, registry: AssetRegistry): PlaceholderFrame {
        val ids = resolved.layers.map { it.assetId }.toSet()
        val categories = resolved.layers.map { it.category }.toSet()
        val palette = registry.asset(resolved.paletteAssetId)?.colors
        val expression = Expression.entries.firstOrNull { it.wire == resolved.expressionId } ?: Expression.NEUTRAL
        val config = AvatarConfig(
            baseForm = baseForm(resolved.baseAssetId, ids),
            bodyColor = palette?.body ?: AvatarPalette.body[0],
            themeColor = palette?.accent ?: AvatarPalette.theme[0],
            expression = expression,
            faceStyle = when {
                "feature_freckles" in ids -> FaceStyle.FRECKLES
                "feature_blush" in ids -> FaceStyle.BLUSHY
                else -> FaceStyle.CLASSIC
            },
            headAccessory = head(resolved.layers.firstOrNull { it.category == AssetCategory.HEAD_ACCESSORY }?.assetId),
            faceAccessory = face(resolved.layers.firstOrNull { it.category == AssetCategory.FACE_ACCESSORY }?.assetId),
            bodyAccessory = body(resolved.layers.firstOrNull { it.category == AssetCategory.BODY_ACCESSORY }?.assetId),
            handProp = prop(resolved.layers.firstOrNull { it.category == AssetCategory.FOREGROUND_PROP }?.assetId),
            scene = scene(resolved.layers.firstOrNull { it.category == AssetCategory.SCENE }?.assetId),
            frameStyle = if ("frame_circle" in ids) FrameStyle.CIRCLE else FrameStyle.SQUIRCLE,
        )
        val availabilityLayer = resolved.layers.firstOrNull { it.category == AssetCategory.AVAILABILITY_INDICATOR }
        val activityLayer = resolved.layers.firstOrNull { it.category == AssetCategory.ACTIVITY_BADGE }
        val availabilityGlyph = availabilityLayer?.let { registry.asset(it.assetId)?.glyph }
        val activityGlyph = activityLayer?.let { registry.asset(it.assetId)?.glyph }
        val layers = buildList {
            if (AssetCategory.SCENE in categories) add(Layer.SCENE)
            if (AssetCategory.BODY_ACCESSORY in categories) add(Layer.BODY_ACCESSORY)
            if (AssetCategory.BASE in categories) add(Layer.HEAD_BASE)
            if (config.faceStyle != FaceStyle.CLASSIC) add(Layer.FACE_STYLE)
            if (AssetCategory.FACE_EYE in categories) add(Layer.EYES)
            if (AssetCategory.FACE_BROW in categories) add(Layer.BROWS)
            if (AssetCategory.FACE_MOUTH in categories) add(Layer.MOUTH)
            if (AssetCategory.FACE_ACCESSORY in categories) add(Layer.FACE_ACCESSORY)
            if (AssetCategory.HEAD_ACCESSORY in categories) add(Layer.HEAD_ACCESSORY)
            if (AssetCategory.EXPRESSION_OVERLAY in categories) add(Layer.FACE_EXTRA)
            if (AssetCategory.FOREGROUND_PROP in categories) add(Layer.PROP)
            if (availabilityLayer != null) add(Layer.AVAILABILITY_BADGE)
            if (activityLayer != null) add(Layer.ACTIVITY_BADGE)
        }
        return PlaceholderFrame(
            config,
            layers,
            resolved.sceneDetail,
            availabilityGlyph = availabilityGlyph,
            availability = availabilityLayer?.let { availabilityOf(it.assetId) },
            activityGlyph = activityGlyph,
        )
    }

    private fun availabilityOf(assetId: String): Availability? =
        Availability.entries.firstOrNull { "avail_${it.wire}" == assetId }

    private fun baseForm(baseId: String, ids: Set<String>): BaseForm {
        if ("frame_pixel" in ids) return BaseForm.PIXEL
        return when (baseId) {
            "base_orb" -> BaseForm.HUMAN
            "base_bot" -> BaseForm.ROBOT
            "base_ghost" -> BaseForm.GHOST
            "base_critter" -> when {
                "feature_ears_fox" in ids || "feature_muzzle" in ids -> BaseForm.FOX
                "feature_ears_bear" in ids -> BaseForm.BEAR
                "feature_ears_cat" in ids -> BaseForm.CAT
                else -> BaseForm.BLOB
            }
            "base_blob" -> if ("feature_antennae" in ids) BaseForm.ALIEN else BaseForm.BLOB
            else -> BaseForm.BLOB
        }
    }

    private fun head(id: String?): HeadAccessory = when (id) {
        null -> HeadAccessory.NONE
        else -> HeadAccessory.entries.firstOrNull { it != HeadAccessory.NONE && "head_${it.wire}" == id } ?: HeadAccessory.NONE
    }

    private fun face(id: String?): FaceAccessory = when (id) {
        "face_glasses_round" -> FaceAccessory.GLASSES
        "face_sunglasses" -> FaceAccessory.SUNGLASSES
        else -> FaceAccessory.NONE
    }

    private fun body(id: String?): BodyAccessory = when (id) {
        null -> BodyAccessory.NONE
        else -> BodyAccessory.entries.firstOrNull { it != BodyAccessory.NONE && "body_${it.wire}" == id } ?: BodyAccessory.NONE
    }

    private fun prop(id: String?): Prop = when (id) {
        null -> Prop.NONE
        "prop_snack" -> Prop.PIZZA
        else -> Prop.entries.firstOrNull { it != Prop.NONE && "prop_${it.wire}" == id } ?: Prop.NONE
    }

    private fun scene(id: String?): Scene = when (id) {
        null, "scene_plain" -> Scene.PLAIN_GRADIENT
        "scene_desk" -> Scene.DESK_SETUP
        "scene_tavern" -> Scene.DUNGEON_TAVERN
        "scene_space_station" -> Scene.SPACE_STATION
        "scene_clouds" -> Scene.CLOUDSCAPE
        else -> Scene.entries.firstOrNull { "scene_${it.wire}" == id } ?: Scene.PLAIN_GRADIENT
    }
}
