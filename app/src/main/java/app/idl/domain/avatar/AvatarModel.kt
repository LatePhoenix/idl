package app.idl.domain.avatar

import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.IdlJson
import app.idl.domain.Mood
import app.idl.domain.StatusIntent
import app.idl.domain.WireEnumSerializer
import app.idl.domain.wire
import kotlinx.serialization.Serializable

/*
 * Avatar model v2 (IDL_Avatar_Creator_Master_Plan §12). Everything visual is referenced by
 * asset ID from a versioned asset pack (AssetManifest), so art can change without changing
 * this schema. Storage metadata (owner, timestamps) lives on the server row, not in here.
 */

/** Uniform placement on top of an asset's default transform. Units are viewBox pixels. */
@Serializable
data class ItemTransform(
    val translateX: Float = 0f,
    val translateY: Float = 0f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val flipHorizontal: Boolean = false,
)

/** Background behind the character. [BackgroundMode.SCENE] uses [AvatarConfiguration.defaultSceneAssetId]. */
@Serializable
data class AvatarBackground(
    val mode: BackgroundMode = BackgroundMode.SCENE,
    val color: String? = null,
    val assetId: String? = null,
)

@Serializable(with = BackgroundMode.Serializer::class)
enum class BackgroundMode {
    SCENE, TRANSPARENT, SOLID;

    object Serializer : WireEnumSerializer<BackgroundMode>("BackgroundMode", entries, SCENE)
}

/** Persistent identity: rarely changes, and must stay recognizable through every status. */
@Serializable
data class AvatarConfiguration(
    val baseAssetId: String,
    val paletteAssetId: String,
    val eyeFamilyAssetId: String? = null,
    val mouthFamilyAssetId: String? = null,
    /** Silhouette features such as ears, antennae or a muzzle. */
    val signatureFeatureAssetIds: List<String> = emptyList(),
    val signatureHeadAccessoryAssetId: String? = null,
    val signatureFaceAccessoryAssetId: String? = null,
    val signatureBodyAccessoryAssetId: String? = null,
    val defaultPropAssetId: String? = null,
    val defaultSceneAssetId: String? = null,
    val defaultFrameAssetId: String? = null,
    /** Expression shown when no status sets one (an expression ID from the manifest). */
    val restingExpressionId: String = "neutral",
    val styleDna: StyleDna = StyleDna(),
    val renderVersion: Int = RENDER_VERSION,
    val schemaVersion: Int = SCHEMA_VERSION,
    /** Pack this recipe was authored against. Art swaps stay inside the pack. */
    val packId: String = DEFAULT_PACK_ID,
    val packVersion: Int = 1,
    /**
     * Head family (`round_face`, `robot_head`, …). Blank until a manifest maps [baseAssetId].
     * Never inferred from a display name.
     */
    val familyId: String = "",
    /**
     * Extra items keyed by category wire name. Hair and jewelry live here; expression does not.
     * Each list is an unordered set: drawing order is the asset z-index, not this list.
     */
    val itemIds: Map<String, List<String>> = emptyMap(),
    /** Semantic slot → `#RRGGBB` or `#AARRGGBB`. Absent slots use the asset default. */
    val colorOverrides: Map<String, String> = emptyMap(),
    /** Slots the user detached from derived highlight and shadow. */
    val unlinkedSlots: List<String> = emptyList(),
    val itemTransforms: Map<String, ItemTransform> = emptyMap(),
    val background: AvatarBackground = AvatarBackground(),
    /** Set only when the user randomized. The same seed and pack version repeat. */
    val randomSeed: Long? = null,
) {
    /**
     * Schema 2 payloads decode with the new defaults. This bumps them to schema 3.
     * A newer [schemaVersion] is returned unchanged so an older app does not rewrite it.
     * [baseFamilies] is asset id → family id from the pack. Only an explicit entry is applied.
     */
    fun migrateRecipe(baseFamilies: Map<String, String> = emptyMap()): AvatarConfiguration {
        if (schemaVersion > SCHEMA_VERSION) return this
        val family = familyId.ifBlank { baseFamilies[baseAssetId].orEmpty() }
        if (schemaVersion == SCHEMA_VERSION && family == familyId && packId.isNotBlank()) return this
        return copy(
            schemaVersion = SCHEMA_VERSION,
            packId = packId.ifBlank { DEFAULT_PACK_ID },
            familyId = family,
        )
    }

    /** Every asset id this recipe would persist (identity, items, scene, frame, …). */
    fun referencedAssetIds(): List<String> = buildList {
        add(baseAssetId)
        add(paletteAssetId)
        eyeFamilyAssetId?.let(::add)
        mouthFamilyAssetId?.let(::add)
        addAll(signatureFeatureAssetIds)
        signatureHeadAccessoryAssetId?.let(::add)
        signatureFaceAccessoryAssetId?.let(::add)
        signatureBodyAccessoryAssetId?.let(::add)
        defaultPropAssetId?.let(::add)
        defaultSceneAssetId?.let(::add)
        defaultFrameAssetId?.let(::add)
        itemIds.values.forEach { addAll(it) }
        background.assetId?.let(::add)
        styleDna.semanticVisualOverrides.values.forEach { override ->
            override.expressionId?.let(::add)
            override.eyesAssetId?.let(::add)
            override.propAssetId?.let(::add)
            override.headAccessoryAssetId?.let(::add)
            override.bodyAccessoryAssetId?.let(::add)
            override.sceneAssetId?.let(::add)
            override.availabilityIndicatorAssetId?.let(::add)
            addAll(override.overlayAssetIds)
        }
    }.distinct()

    /**
     * Payload safe to save or upload. A newer schema is not rewritten: unknown fields were
     * dropped on decode, so writing it back would destroy them.
     *
     * When [registry] and [entitlements] are both set, premium items the user does not own
     * yield [AvatarWrite.NeedsEntitlement] (AP-10). Cache rewrites omit both and skip the check.
     */
    fun prepareForWrite(
        baseFamilies: Map<String, String> = emptyMap(),
        registry: AssetRegistry? = null,
        entitlements: Entitlements? = null,
    ): AvatarWrite {
        if (schemaVersion > SCHEMA_VERSION) return AvatarWrite.NeedsAppUpdate
        val ready = migrateRecipe(baseFamilies)
        if (registry != null && entitlements != null) {
            val locked = ready.referencedAssetIds()
                .filter { id ->
                    val asset = registry.asset(id) ?: return@filter false
                    asset.tier == AssetTier.PREMIUM && !entitlements.owns(id)
                }
                .sorted()
            if (locked.isNotEmpty()) return AvatarWrite.NeedsEntitlement(locked)
        }
        return AvatarWrite.Ready(ready)
    }

    companion object {
        const val SCHEMA_VERSION = 3
        const val RENDER_VERSION = 3
        const val DEFAULT_PACK_ID = "core_proto"

        /** Decode a stored or received recipe and upgrade schema 2 to [SCHEMA_VERSION]. */
        fun decode(json: String, baseFamilies: Map<String, String> = emptyMap()): AvatarConfiguration =
            IdlJson.decodeFromString(serializer(), json).migrateRecipe(baseFamilies)
    }
}

/** Result of trying to save an [AvatarConfiguration]. */
sealed class AvatarWrite {
    data class Ready(val configuration: AvatarConfiguration) : AvatarWrite()
    data object NeedsAppUpdate : AvatarWrite() {
        const val message = "update the app to edit this avatar"
    }

    /** Recipe references premium items the user does not own (try-on is fine; save is not). */
    data class NeedsEntitlement(val assetIds: List<String>) : AvatarWrite() {
        val message: String
            get() = "unlock ${assetIds.joinToString(", ")} to save this avatar"
    }
}

/**
 * How a user's avatar behaves. Palette family and signature accessories live on
 * [AvatarConfiguration] (the plan duplicates them here; one source of truth is safer).
 */
@Serializable
data class StyleDna(
    val styleFamily: StyleFamily = StyleFamily.COZY,
    val expressionIntensity: ExpressionIntensity = ExpressionIntensity.MEDIUM,
    val sceneDetailPreference: SceneDetailPreference = SceneDetailPreference.LOW_DETAIL,
    val motionPreference: MotionPreference = MotionPreference.SUBTLE,
    /**
     * Per-user visual choices for a semantic state, keyed by [SemanticKey.wire]
     * (e.g. `mood:sleepy` → tea + blanket). Applied only when that state is visible to the viewer.
     */
    val semanticVisualOverrides: Map<String, SemanticVisualOverride> = emptyMap(),
)

@Serializable
data class SemanticVisualOverride(
    val expressionId: String? = null,
    val eyesAssetId: String? = null,
    val propAssetId: String? = null,
    val headAccessoryAssetId: String? = null,
    val bodyAccessoryAssetId: String? = null,
    val sceneAssetId: String? = null,
    val availabilityIndicatorAssetId: String? = null,
    val overlayAssetIds: List<String> = emptyList(),
)

/** A semantic presence state that visuals can be attached to. */
data class SemanticKey(val kind: Kind, val value: String) {
    enum class Kind { MOOD, AVAILABILITY, INTENT, ACTIVITY }

    val wire: String get() = "${kind.name.lowercase()}:$value"

    companion object {
        fun mood(m: Mood) = SemanticKey(Kind.MOOD, m.wire)
        fun availability(a: Availability) = SemanticKey(Kind.AVAILABILITY, a.wire)
        fun intent(i: StatusIntent) = SemanticKey(Kind.INTENT, i.wire)
        fun activity(a: ActivityType) = SemanticKey(Kind.ACTIVITY, a.wire)
    }
}

/** Future multi-identity support (§8.5); the MVP has exactly one active slot. */
@Serializable
data class IdentitySlot(
    val slotId: String,
    val name: String,
    val configuration: AvatarConfiguration,
    val active: Boolean,
)

@Serializable(with = StyleFamily.Serializer::class)
enum class StyleFamily(val label: String) {
    COZY("Cozy"), CHAOTIC("Chaotic"), TECHY("Techy"), CUTE("Cute"), COOL("Cool"),
    FANTASY("Fantasy"), SPOOKY("Spooky"), RETRO("Retro"), MINIMAL("Minimal"), DREAMY("Dreamy");

    object Serializer : WireEnumSerializer<StyleFamily>("StyleFamily", entries, COZY)
}

@Serializable(with = ExpressionIntensity.Serializer::class)
enum class ExpressionIntensity {
    LOW, MEDIUM, HIGH;

    object Serializer : WireEnumSerializer<ExpressionIntensity>("ExpressionIntensity", entries, MEDIUM)
}

@Serializable(with = SceneDetailPreference.Serializer::class)
enum class SceneDetailPreference {
    NONE, LOW_DETAIL, FULL;

    object Serializer : WireEnumSerializer<SceneDetailPreference>("SceneDetailPreference", entries, LOW_DETAIL)
}

@Serializable(with = MotionPreference.Serializer::class)
enum class MotionPreference {
    NONE, SUBTLE, LIVELY;

    object Serializer : WireEnumSerializer<MotionPreference>("MotionPreference", entries, SUBTLE)
}

/**
 * How much of the character the output square shows (D-47).
 * [originX], [originY] and [size] are in character space. The renderer maps that viewport onto
 * the output square.
 */
enum class Framing(val originX: Float, val originY: Float, val size: Float) {
    HEAD(-40f, 0f, 1104f),
    BUST(-128f, 32f, 1280f);

    /** Character-space point to output pixels. */
    fun toOutput(x: Float, y: Float, sizePx: Float): Pair<Float, Float> {
        val scale = sizePx / size
        return (x - originX) * scale to (y - originY) * scale
    }
}

/** Where a render is shown (§12.4); each target has its own simplification policy. */
@Serializable(with = RenderTarget.Serializer::class)
enum class RenderTarget(val defaultSizePx: Int, val framing: Framing) {
    COMPACT_WIDGET(48, Framing.HEAD),
    FRIEND_TILE(64, Framing.HEAD),
    STANDARD_WIDGET(96, Framing.HEAD),
    LARGE_WIDGET(128, Framing.HEAD),
    CIRCLE_WIDGET(48, Framing.HEAD),
    NOTIFICATION(64, Framing.HEAD),
    PROFILE(256, Framing.BUST),
    SHARE_CARD(512, Framing.BUST);

    object Serializer : WireEnumSerializer<RenderTarget>("RenderTarget", entries, PROFILE)
}

@Serializable(with = WallpaperContrastMode.Serializer::class)
enum class WallpaperContrastMode {
    /** In-app surfaces; no wallpaper behind the art. */
    NONE,
    LIGHT_WALLPAPER,
    DARK_WALLPAPER;

    object Serializer : WireEnumSerializer<WallpaperContrastMode>("WallpaperContrastMode", entries, NONE)
}

@Serializable(with = AccessibilityRenderMode.Serializer::class)
enum class AccessibilityRenderMode {
    STANDARD, HIGH_CONTRAST;

    object Serializer : WireEnumSerializer<AccessibilityRenderMode>("AccessibilityRenderMode", entries, STANDARD)
}

/**
 * Presence as the *viewer* is allowed to see it — the server has already filtered it
 * (decision D-24), so absent fields are hidden or unset. Visual fields are asset IDs.
 */
@Serializable
data class VisiblePresence(
    val mood: Mood? = null,
    val availability: Availability? = null,
    val intent: StatusIntent? = null,
    val activityType: ActivityType? = null,
    /** Explicit visuals the user chose for this status (e.g. from a Status Deck preset). */
    val expressionId: String? = null,
    val propAssetId: String? = null,
    val headAccessoryAssetId: String? = null,
    val bodyAccessoryAssetId: String? = null,
    val sceneAssetId: String? = null,
) {
    /** Semantic keys in the order their visuals take precedence: activity, intent, availability, mood. */
    fun semanticKeys(): List<SemanticKey> = listOfNotNull(
        activityType?.takeIf { it != ActivityType.NONE }?.let(SemanticKey::activity),
        intent?.takeIf { it != StatusIntent.NO_PREFERENCE }?.let(SemanticKey::intent),
        availability?.let(SemanticKey::availability),
        mood?.let(SemanticKey::mood),
    )

    companion object {
        val NONE = VisiblePresence()
    }
}

@Serializable
data class AvatarRenderRequest(
    val configuration: AvatarConfiguration,
    val presence: VisiblePresence = VisiblePresence.NONE,
    /** Reaction overlay asset IDs, newest first. */
    val reactionOverlayAssetIds: List<String> = emptyList(),
    val target: RenderTarget = RenderTarget.PROFILE,
    val sizePx: Int = target.defaultSizePx,
    val wallpaperContrastMode: WallpaperContrastMode = WallpaperContrastMode.NONE,
    val accessibilityMode: AccessibilityRenderMode = AccessibilityRenderMode.STANDARD,
    val rendererVersion: Int = AvatarConfiguration.RENDER_VERSION,
)
