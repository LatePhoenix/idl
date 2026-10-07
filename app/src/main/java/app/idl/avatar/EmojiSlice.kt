package app.idl.avatar

import android.graphics.Bitmap
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.Mood
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.ExpressionCatalog
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.AvatarRenderRequest
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.VisiblePresence

/**
 * Fixed `emoji_core` recipes on the teardrop base. Pass [catalog] to resolve mood faces.
 * Without it, only the neutral and happy teardrop overrides apply.
 */
object EmojiSlice {
    const val BASE = "base_teardrop"
    val sizes = listOf(48, 128, 512)

    data class Recipe(
        val id: String,
        val label: String,
        val items: Map<String, List<String>> = emptyMap(),
        val scene: String? = null,
        val presence: VisiblePresence = VisiblePresence.NONE,
    )

    private val happy = VisiblePresence(mood = Mood.HAPPY)
    private fun hair(style: String) = mapOf("hair" to listOf(style))
    private val bob = hair("hair_bob")
    private val beard = bob + ("facial_hair" to listOf("beard_full"))
    private val glasses = beard + ("face_accessory" to listOf("glasses_round_wire"))

    val recipes = listOf(
        Recipe("neutral", "Bare face, neutral"),
        Recipe("happy", "Happy", presence = happy),
        Recipe("short", "Short crop", items = hair("hair_short_crop"), presence = happy),
        Recipe("hair", "Bob", items = bob, presence = happy),
        Recipe("long", "Long straight", items = hair("hair_long_straight"), presence = happy),
        Recipe("beard", "Full beard", items = beard, presence = happy),
        Recipe("stubble", "Stubble", items = hair("hair_short_crop") + ("facial_hair" to listOf("stubble")), presence = happy),
        Recipe("mustache", "Mustache", items = hair("hair_long_straight") + ("facial_hair" to listOf("mustache_classic")), presence = happy),
        Recipe("glasses", "Glasses", items = glasses, presence = happy),
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

    fun request(
        recipe: Recipe,
        sizePx: Int,
        hairPrimary: String? = null,
        unlinkShadow: Boolean = false,
        target: RenderTarget = RenderTarget.PROFILE,
    ) = AvatarRenderRequest(
        configuration = configuration(recipe, hairPrimary, unlinkShadow),
        presence = recipe.presence,
        target = target,
        sizePx = sizePx,
    )

    fun bitmap(
        registry: AssetRegistry,
        pictures: VectorPictureCache,
        recipe: Recipe,
        sizePx: Int,
        hairPrimary: String? = null,
        unlinkShadow: Boolean = false,
        target: RenderTarget = RenderTarget.PROFILE,
        catalog: ExpressionCatalog = ExpressionCatalog.EMPTY,
    ): Bitmap {
        val resolved = AvatarResolver(registry, catalog).resolve(request(recipe, sizePx, hairPrimary, unlinkShadow, target))
        return AvatarRenderer.bitmap(resolved, registry, pictures, sizePx)
    }
}
