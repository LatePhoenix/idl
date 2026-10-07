package app.idl.domain.avatar

import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarPalette
import app.idl.domain.BodyAccessory
import app.idl.domain.Expression
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceStyle
import app.idl.domain.FrameStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.IdlJson
import app.idl.domain.Prop
import app.idl.domain.Scene
import app.idl.domain.wire
import kotlinx.serialization.json.jsonObject

/**
 * Room and the cache store schema 3 [AvatarConfiguration] JSON. A v1 [AvatarConfig] blob
 * (it has `baseForm`) is migrated. A schema newer than this app is left untouched.
 */
object StoredAvatar {

    /** JSON to write back. A newer schema is returned unchanged so unknown fields survive. */
    fun rewrite(json: String, registry: AssetRegistry): String {
        val recipe = read(json, registry) ?: return json
        val write = recipe.prepareForWrite(registry.baseFamilies)
        check(write is AvatarWrite.Ready) { "a readable recipe must be writable" }
        return IdlJson.encodeToString(AvatarConfiguration.serializer(), write.configuration)
    }

    /**
     * The recipe this app can edit, or null when [json] is a newer schema.
     * Every legacy base becomes `base_teardrop`. Features that do not fit are dropped.
     */
    fun read(json: String, registry: AssetRegistry): AvatarConfiguration? {
        val obj = runCatching { IdlJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
        if ("baseForm" in obj) {
            val v1 = IdlJson.decodeFromString(AvatarConfig.serializer(), json)
            return LegacyAvatarMigration.migrate(v1, registry)
        }
        val decoded = AvatarConfiguration.decode(json, registry.baseFamilies)
        if (decoded.schemaVersion > AvatarConfiguration.SCHEMA_VERSION) return null
        return CompatibilityEngine(registry).sanitize(decoded.copy(baseAssetId = "base_teardrop")).first
    }

    /** Fields the old studio still edits. Hair, tops, and a pixel frame stay on the recipe. */
    fun toStudioConfig(recipe: AvatarConfiguration, registry: AssetRegistry): AvatarConfig {
        val palette = registry.asset(recipe.paletteAssetId)?.colors
        return AvatarConfig(
            bodyColor = palette?.body ?: AvatarPalette.body[0],
            themeColor = palette?.accent ?: AvatarPalette.theme[0],
            expression = Expression.Serializer.fromWire(recipe.restingExpressionId),
            faceStyle = when {
                "feature_blush" in recipe.signatureFeatureAssetIds -> FaceStyle.BLUSHY
                "feature_freckles" in recipe.signatureFeatureAssetIds -> FaceStyle.FRECKLES
                else -> FaceStyle.CLASSIC
            },
            headAccessory = enumFrom(recipe.signatureHeadAccessoryAssetId, "head_", HeadAccessory.entries, HeadAccessory.NONE),
            faceAccessory = when (recipe.signatureFaceAccessoryAssetId) {
                "face_glasses_round" -> FaceAccessory.GLASSES
                "face_sunglasses" -> FaceAccessory.SUNGLASSES
                else -> FaceAccessory.NONE
            },
            bodyAccessory = enumFrom(recipe.signatureBodyAccessoryAssetId, "body_", BodyAccessory.entries, BodyAccessory.NONE),
            handProp = when (recipe.defaultPropAssetId) {
                null -> Prop.NONE
                "prop_snack" -> Prop.PIZZA
                else -> Prop.entries.firstOrNull { "prop_${it.wire}" == recipe.defaultPropAssetId } ?: Prop.NONE
            },
            scene = sceneFrom(recipe.defaultSceneAssetId),
            frameStyle = if (recipe.defaultFrameAssetId == "frame_circle") FrameStyle.CIRCLE else FrameStyle.SQUIRCLE,
        )
    }

    /**
     * Studio edits land on the saved recipe. Unchanged fields stay, so a catalog expression,
     * hair, or `frame_pixel` is not replaced by the v1 projection.
     */
    fun applyStudio(existing: AvatarConfiguration, edited: AvatarConfig, registry: AssetRegistry): AvatarConfiguration {
        val projected = toStudioConfig(existing, registry)
        val mapped = LegacyAvatarMigration.migrate(edited, registry)
        val next = existing.copy(
            baseAssetId = "base_teardrop",
            paletteAssetId = if (edited.bodyColor != projected.bodyColor) mapped.paletteAssetId else existing.paletteAssetId,
            signatureFeatureAssetIds = mapped.signatureFeatureAssetIds,
            signatureHeadAccessoryAssetId = mapped.signatureHeadAccessoryAssetId,
            signatureFaceAccessoryAssetId = mapped.signatureFaceAccessoryAssetId,
            signatureBodyAccessoryAssetId = mapped.signatureBodyAccessoryAssetId,
            defaultPropAssetId = mapped.defaultPropAssetId,
            defaultSceneAssetId = if (edited.scene != projected.scene) mapped.defaultSceneAssetId else existing.defaultSceneAssetId,
            defaultFrameAssetId = if (edited.frameStyle != projected.frameStyle) mapped.defaultFrameAssetId else existing.defaultFrameAssetId,
            restingExpressionId = if (edited.expression != projected.expression) mapped.restingExpressionId else existing.restingExpressionId,
        )
        return CompatibilityEngine(registry).sanitize(next).first
    }

    private fun sceneFrom(id: String?): Scene {
        if (id == null) return Scene.PLAIN_GRADIENT
        val known = mapOf(
            "scene_plain" to Scene.PLAIN_GRADIENT,
            "scene_desk" to Scene.DESK_SETUP,
            "scene_tavern" to Scene.DUNGEON_TAVERN,
            "scene_space_station" to Scene.SPACE_STATION,
            "scene_clouds" to Scene.CLOUDSCAPE,
        )
        known[id]?.let { return it }
        val wire = id.removePrefix("scene_")
        return Scene.entries.firstOrNull { it.wire == wire } ?: Scene.PLAIN_GRADIENT
    }

    private fun <E : Enum<E>> enumFrom(id: String?, prefix: String, entries: List<E>, none: E): E {
        if (id == null) return none
        val wire = id.removePrefix(prefix)
        return entries.firstOrNull { it.wire == wire } ?: none
    }
}

/** What the studio loaded from a stored avatar blob. */
sealed class LoadedAvatar {
    data object Missing : LoadedAvatar()
    data class Editable(val config: AvatarConfig, val recipe: AvatarConfiguration) : LoadedAvatar()
    data object NeedsAppUpdate : LoadedAvatar()
}
