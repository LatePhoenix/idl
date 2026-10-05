package app.idl.avatar

import android.graphics.Bitmap
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.Mood
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.AvatarRenderRequest
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.VisiblePresence

/**
 * The fixed `emoji_core` recipes from the vector-slice spec. Other expressions on
 * `base_round_face` keep the procedural parts; only neutral and happy are overridden.
 */
object EmojiSlice {
    const val BASE = "base_round_face"
    val sizes = listOf(48, 128, 512)

    data class Recipe(
        val id: String,
        val label: String,
        val items: Map<String, List<String>> = emptyMap(),
        val scene: String? = null,
        val presence: VisiblePresence = VisiblePresence.NONE,
    )

    private val hair = mapOf("hair" to listOf("hair_round_bob"))
    private val beard = hair + ("facial_hair" to listOf("beard_round_full"))
    private val glasses = beard + ("face_accessory" to listOf("glasses_round_wire"))
    private val happy = VisiblePresence(mood = Mood.HAPPY)

    val recipes = listOf(
        Recipe("neutral", "Bare face, neutral"),
        Recipe("happy", "Happy", presence = happy),
        Recipe("hair", "Plus hair", items = hair, presence = happy),
        Recipe("beard", "Plus beard", items = beard, presence = happy),
        Recipe("glasses", "Plus glasses", items = glasses, presence = happy),
        Recipe("scene", "Soft scene", items = glasses, scene = "scene_round_soft", presence = happy),
        Recipe(
            "busy",
            "Busy and VR",
            items = glasses,
            scene = "scene_round_soft",
            presence = VisiblePresence(mood = Mood.HAPPY, availability = Availability.BUSY, activityType = ActivityType.VR),
        ),
    )

    val hairSwatches = listOf(
        "#5B3A29" to "Brown",
        "#1F1A17" to "Black",
        "#C4552A" to "Auburn",
        "#E6C36A" to "Blonde",
        "#8C8C8C" to "Gray",
    )

    fun configuration(recipe: Recipe, hairPrimary: String? = null, unlinkShadow: Boolean = false) = AvatarConfiguration(
        baseAssetId = BASE,
        paletteAssetId = "palette_sunny",
        eyeFamilyAssetId = "eyefam_round",
        mouthFamilyAssetId = "mouthfam_classic",
        defaultSceneAssetId = recipe.scene ?: "scene_plain",
        defaultFrameAssetId = "frame_squircle",
        restingExpressionId = "neutral",
        itemIds = recipe.items,
        colorOverrides = if (hairPrimary == null) emptyMap() else mapOf("hair.primary" to hairPrimary),
        unlinkedSlots = if (unlinkShadow) listOf("hair.shadow") else emptyList(),
    )

    fun request(recipe: Recipe, sizePx: Int, hairPrimary: String? = null, unlinkShadow: Boolean = false) = AvatarRenderRequest(
        configuration = configuration(recipe, hairPrimary, unlinkShadow),
        presence = recipe.presence,
        target = RenderTarget.PROFILE,
        sizePx = sizePx,
    )

    fun bitmap(
        registry: AssetRegistry,
        pictures: VectorPictureCache,
        recipe: Recipe,
        sizePx: Int,
        hairPrimary: String? = null,
        unlinkShadow: Boolean = false,
    ): Bitmap {
        val resolved = AvatarResolver(registry).resolve(request(recipe, sizePx, hairPrimary, unlinkShadow))
        return AvatarRenderer.bitmap(resolved, registry, pictures, sizePx)
    }
}
