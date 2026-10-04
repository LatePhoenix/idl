package app.idl.domain.avatar

import app.idl.domain.ActivityType
import app.idl.domain.AvatarComposer
import app.idl.domain.AvatarConfig
import app.idl.domain.BaseForm
import app.idl.domain.BodyAccessory
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceStyle
import app.idl.domain.FrameStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.PresenceView
import app.idl.domain.Prop
import app.idl.domain.ResolvedPresence
import app.idl.domain.Scene
import app.idl.domain.wire

/**
 * v1 enum avatars become v2 asset ids (decision D-29). [AvatarConfig.themeColor] is not copied:
 * the palette's accent replaces it, so a migrated avatar can change accent.
 *
 * [migrate] needs the pack it will be rendered with. The domain layer cannot open APK assets,
 * so the caller passes the registry Phase 2 will load via [AssetPacks].
 */
object LegacyAvatarMigration {

    fun migrate(v1: AvatarConfig, registry: AssetRegistry): AvatarConfiguration {
        val features = buildList {
            addAll(baseFeatures(v1.baseForm))
            when (v1.faceStyle) {
                FaceStyle.CLASSIC -> Unit
                FaceStyle.BLUSHY -> add("feature_blush")
                FaceStyle.FRECKLES -> add("feature_freckles")
            }
        }
        val mapped = AvatarConfiguration(
            baseAssetId = baseId(v1.baseForm),
            paletteAssetId = registry.ofCategory(AssetCategory.PALETTE)
                .firstOrNull { it.colors?.body == v1.bodyColor }?.id
                ?: registry.defaults.palette,
            eyeFamilyAssetId = if (v1.baseForm == BaseForm.PIXEL) "eyefam_pixel" else null,
            signatureFeatureAssetIds = features,
            signatureHeadAccessoryAssetId = headId(v1.headAccessory),
            signatureFaceAccessoryAssetId = faceId(v1.faceAccessory),
            signatureBodyAccessoryAssetId = bodyId(v1.bodyAccessory),
            defaultPropAssetId = propId(v1.handProp),
            defaultSceneAssetId = sceneId(v1.scene),
            defaultFrameAssetId = if (v1.baseForm == BaseForm.PIXEL) "frame_pixel" else frameId(v1.frameStyle),
            restingExpressionId = v1.expression.wire,
        )
        return CompatibilityEngine(registry).sanitize(mapped).first.migrateRecipe(registry.baseFamilies)
    }

    /** Copies only fields the viewer was sent. Expression is set only when mood is visible. */
    fun presence(view: PresenceView): VisiblePresence {
        val avatar = view.avatar
        val resting = view.restingAvatar
        return VisiblePresence(
            mood = view.mood,
            availability = view.availability,
            intent = view.intent,
            activityType = view.activityType?.takeIf { it != ActivityType.NONE },
            expressionId = if (view.mood != null) avatar.expression.wire else null,
            propAssetId = if (avatar.handProp != resting.handProp) propId(avatar.handProp) else null,
            headAccessoryAssetId = if (avatar.headAccessory != resting.headAccessory) headId(avatar.headAccessory) else null,
            bodyAccessoryAssetId = if (avatar.bodyAccessory != resting.bodyAccessory) bodyId(avatar.bodyAccessory) else null,
            sceneAssetId = if (avatar.scene != resting.scene) sceneId(avatar.scene) else null,
        )
    }

    /** Owner-side resolved state. A visual expression is kept only when mood itself is present. */
    fun presence(resolved: ResolvedPresence): VisiblePresence {
        val visual = resolved.visual
        return VisiblePresence(
            mood = resolved.mood,
            availability = resolved.availability,
            intent = resolved.intent,
            activityType = resolved.activity?.type?.takeIf { it != ActivityType.NONE },
            expressionId = resolved.mood?.let {
                (visual.expression ?: AvatarComposer.expressionFor(it, resolved.availability))?.wire
            },
            propAssetId = visual.props.firstOrNull()?.let { propId(it) },
            headAccessoryAssetId = visual.headAccessory?.let { headId(it) },
            bodyAccessoryAssetId = visual.bodyAccessory?.let { bodyId(it) },
            sceneAssetId = visual.scene?.let { sceneId(it) },
        )
    }

    private fun baseId(form: BaseForm): String = when (form) {
        BaseForm.HUMAN -> "base_orb"
        BaseForm.BLOB, BaseForm.ALIEN -> "base_blob"
        BaseForm.ROBOT, BaseForm.PIXEL -> "base_bot"
        BaseForm.GHOST -> "base_ghost"
        BaseForm.CAT, BaseForm.FOX, BaseForm.BEAR -> "base_critter"
    }

    private fun baseFeatures(form: BaseForm): List<String> = when (form) {
        BaseForm.CAT -> listOf("feature_ears_cat")
        BaseForm.FOX -> listOf("feature_ears_fox", "feature_muzzle")
        BaseForm.BEAR -> listOf("feature_ears_bear")
        BaseForm.ALIEN -> listOf("feature_antennae")
        else -> emptyList()
    }

    private fun headId(accessory: HeadAccessory): String? = when (accessory) {
        HeadAccessory.NONE -> null
        else -> "head_${accessory.wire}"
    }

    private fun faceId(accessory: FaceAccessory): String? = when (accessory) {
        FaceAccessory.NONE -> null
        FaceAccessory.GLASSES -> "face_glasses_round"
        FaceAccessory.SUNGLASSES -> "face_sunglasses"
    }

    private fun bodyId(accessory: BodyAccessory): String? = when (accessory) {
        BodyAccessory.NONE -> null
        else -> "body_${accessory.wire}"
    }

    private fun propId(prop: Prop): String? = when (prop) {
        Prop.NONE -> null
        Prop.PIZZA -> "prop_snack"
        else -> "prop_${prop.wire}"
    }

    private fun sceneId(scene: Scene): String = when (scene) {
        Scene.PLAIN_GRADIENT -> "scene_plain"
        Scene.DESK_SETUP -> "scene_desk"
        Scene.DUNGEON_TAVERN -> "scene_tavern"
        Scene.SPACE_STATION -> "scene_space_station"
        Scene.CLOUDSCAPE -> "scene_clouds"
        else -> "scene_${scene.wire}"
    }

    private fun frameId(style: FrameStyle): String = when (style) {
        FrameStyle.CIRCLE -> "frame_circle"
        FrameStyle.SQUIRCLE -> "frame_squircle"
    }
}
