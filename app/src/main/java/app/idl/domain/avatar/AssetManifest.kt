package app.idl.domain.avatar

import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.IdlJson
import app.idl.domain.Mood
import app.idl.domain.WireEnumSerializer
import kotlinx.serialization.Serializable

/*
 * Versioned asset packs (master plan §10). A pack is a JSON manifest. Assets are either
 * `procedural` (drawn by a named painter: today's placeholder art), `vector` (picture JSON),
 * or `raster` (WebP layers). All of them go through the same anchors, compatibility and fallback rules.
 */

@Serializable(with = AssetCategory.Serializer::class)
enum class AssetCategory(val defaultZ: Int, val multiple: Boolean = false, val drawn: Boolean = true) {
    BASE(20),
    PALETTE(0, drawn = false),
    EYE_FAMILY(0, drawn = false),
    MOUTH_FAMILY(0, drawn = false),
    SIGNATURE_FEATURE(30, multiple = true),
    FACE_EYE(50),
    FACE_BROW(60),
    FACE_MOUTH(70),
    EXPRESSION_OVERLAY(80, multiple = true),
    FACE_ACCESSORY(90),
    HEAD_ACCESSORY(100),
    BODY_ACCESSORY(110),
    FOREGROUND_PROP(120),
    SCENE(10),
    FRAME(130),
    AVAILABILITY_INDICATOR(140),
    ACTIVITY_BADGE(150),
    REACTION_OVERLAY(160, multiple = true),
    /** Identity hair. [defaultZ] is unused by the procedural painter; vector parts carry their own band. */
    HAIR(25),
    /** Identity facial hair. Same rule as [HAIR]. */
    FACIAL_HAIR(35);

    object Serializer : WireEnumSerializer<AssetCategory>("AssetCategory", entries, EXPRESSION_OVERLAY)
}

@Serializable(with = AssetTier.Serializer::class)
enum class AssetTier {
    FREE, PREMIUM;

    object Serializer : WireEnumSerializer<AssetTier>("AssetTier", entries, PREMIUM)
}

/** How much of the face an asset covers; covered face parts are not drawn. */
@Serializable(with = FaceOcclusion.Serializer::class)
enum class FaceOcclusion {
    NONE, EYES, FULL;

    object Serializer : WireEnumSerializer<FaceOcclusion>("FaceOcclusion", entries, NONE)
}

@Serializable
data class AssetRender(
    /** `procedural`, `vector`, or `raster`. */
    val type: String = "procedural",
    /** Painter key for procedural assets (see avatar.AvatarRenderer); defaults to the asset ID. */
    val painter: String? = null,
    /** Layer file for raster or vector assets, relative to the pack directory. */
    val file: String? = null,
)

/** Normalized (0..1) point within the avatar canvas. */
@Serializable
data class Anchor(val x: Float, val y: Float)

@Serializable
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

@Serializable
data class PaletteColors(val body: Int, val accent: Int, val outline: Int, val name: String)

@Serializable
data class AssetDef(
    val id: String,
    val category: AssetCategory,
    val accessibilityLabel: String,
    val render: AssetRender = AssetRender(),
    val collection: String = "core",
    val tier: AssetTier = AssetTier.FREE,
    /** Base asset IDs this works on; empty means every base. */
    val compatibleBases: List<String> = emptyList(),
    /** Treated symmetrically: if either side lists the other, they conflict. */
    val conflictsWith: List<String> = emptyList(),
    val requires: List<String> = emptyList(),
    /** Same-category substitute when this asset is incompatible, conflicting or retired. */
    val fallback: String? = null,
    val zIndex: Int? = null,
    val anchor: String? = null,
    val occlusion: FaceOcclusion = FaceOcclusion.NONE,
    val widgetSafe: Boolean = true,
    /** Dropped from renders smaller than this. */
    val minSizePx: Int = 0,
    val tags: List<String> = emptyList(),
    val license: String = "proprietary-idl",
    // Category-specific data.
    val anchors: Map<String, Anchor> = emptyMap(),
    val faceSafeZone: NormalizedRect? = null,
    val colors: PaletteColors? = null,
    /** Shape glyph for indicators and badges: availability must never be color-only. */
    val glyph: String? = null,
    /** Bumps when a vector picture's pixels change. Procedural rows stay at 1. */
    val contentVersion: Int = 1,
    /** Slot name to default `#RRGGBB` or `#AARRGGBB`. Empty on procedural rows. */
    val colorSlots: Map<String, String> = emptyMap(),
    /** Base family id. Only [AssetCategory.BASE] may set this. */
    val family: String? = null,
    /** Identity placement before a recipe [ItemTransform]. */
    val defaultTransform: ItemTransform? = null,
) {
    val z: Int get() = zIndex ?: category.defaultZ
    val painterKey: String get() = render.painter ?: id
}

@Serializable
data class ExpressionParts(val eyes: String? = null, val brows: String? = null, val mouth: String? = null)

@Serializable
data class ExpressionDef(
    val id: String,
    val label: String,
    val eyes: String,
    val brows: String? = null,
    val mouth: String,
    /** Essential cues that carry the meaning (e.g. sleep Zs); kept at small sizes when widget-safe. */
    val overlays: List<String> = emptyList(),
    /** Optional decoration (e.g. bandage); first to go on conflict or at small sizes. */
    val extras: List<String> = emptyList(),
    /** Per-base replacements, keyed by base asset ID. */
    val baseOverrides: Map<String, ExpressionParts> = emptyMap(),
) {
    fun partsFor(baseId: String): ExpressionParts {
        val o = baseOverrides[baseId]
        return ExpressionParts(o?.eyes ?: eyes, o?.brows ?: brows, o?.mouth ?: mouth)
    }
}

/** Pack defaults for a semantic state (`mood:sleepy`, `activity:vr`, ...). */
@Serializable
data class SemanticMapping(
    val expressionId: String? = null,
    val availabilityIndicator: String? = null,
    val activityBadge: String? = null,
    val propAssetId: String? = null,
    val headAccessoryAssetId: String? = null,
    val bodyAccessoryAssetId: String? = null,
    val sceneAssetId: String? = null,
    val overlayAssetIds: List<String> = emptyList(),
)

@Serializable
data class PackDefaults(
    val base: String,
    val palette: String,
    val scene: String,
    val frame: String,
    val eyeFamily: String,
    val mouthFamily: String,
)

@Serializable
data class AssetManifest(
    val packId: String,
    val version: Int,
    val minimumRendererVersion: Int = 1,
    val defaults: PackDefaults? = null,
    val assets: List<AssetDef> = emptyList(),
    val expressions: List<ExpressionDef> = emptyList(),
    val semantics: Map<String, SemanticMapping> = emptyMap(),
    /**
     * expression id → base id → parts. Merged into [ExpressionDef.baseOverrides] in manifest order.
     * Eye and mouth family variants do not apply to these part ids when the asset is vector.
     */
    val expressionOverrides: Map<String, Map<String, ExpressionParts>> = emptyMap(),
    /** Retired asset ID → replacement. Saved avatars must never break (§10.5). */
    val retired: Map<String, String> = emptyMap(),
) {
    companion object {
        fun parse(json: String): AssetManifest = IdlJson.decodeFromString(serializer(), json)
    }
}

/** Every pack the app ships, merged. Lookups follow retirement chains. */
class AssetRegistry(val manifests: List<AssetManifest>) {

    private val assets: Map<String, AssetDef> = manifests.flatMap { it.assets }.associateBy { it.id }
    private val expressions: Map<String, ExpressionDef> = buildMap {
        for (manifest in manifests) {
            for (expression in manifest.expressions) {
                val prior = this[expression.id]
                this[expression.id] = if (prior == null) {
                    expression
                } else {
                    expression.copy(baseOverrides = prior.baseOverrides + expression.baseOverrides)
                }
            }
        }
        for (manifest in manifests) {
            for ((expressionId, byBase) in manifest.expressionOverrides) {
                val existing = this[expressionId] ?: continue
                this[expressionId] = existing.copy(baseOverrides = existing.baseOverrides + byBase)
            }
        }
    }
    private val semantics: Map<String, SemanticMapping> = manifests.fold(emptyMap()) { acc, m -> acc + m.semantics }
    private val retired: Map<String, String> = manifests.fold(emptyMap()) { acc, m -> acc + m.retired }

    val defaults: PackDefaults = requireNotNull(manifests.firstNotNullOfOrNull { it.defaults }) { "no pack declares defaults" }

    /** `packId@version` list, sorted; part of the render key. */
    val packVersions: List<String> = manifests.map { "${it.packId}@${it.version}" }.sorted()

    val allAssets: Collection<AssetDef> get() = assets.values
    val allExpressions: Collection<ExpressionDef> get() = expressions.values

    /** Base asset id → family id, for bases that declare [AssetDef.family]. */
    val baseFamilies: Map<String, String> = assets.values
        .filter { it.category == AssetCategory.BASE && !it.family.isNullOrBlank() }
        .sortedBy { it.id }
        .associate { it.id to it.family!! }

    /** Resolves retired IDs to their replacement (bounded, so cycles can't hang). */
    fun canonicalId(id: String): String {
        var current = id
        repeat(16) {
            current = retired[current] ?: return current
        }
        return current
    }

    fun asset(id: String?): AssetDef? = id?.let { assets[canonicalId(it)] }
    fun expression(id: String?): ExpressionDef? = id?.let { expressions[it] }
    fun semantic(key: SemanticKey): SemanticMapping? = semantics[key.wire]
    fun ofCategory(category: AssetCategory): List<AssetDef> = assets.values.filter { it.category == category }.sortedBy { it.id }

    fun isCompatibleWithBase(asset: AssetDef, baseId: String): Boolean =
        asset.compatibleBases.isEmpty() || baseId in asset.compatibleBases

    fun conflicts(a: AssetDef, b: AssetDef): Boolean = b.id in a.conflictsWith || a.id in b.conflictsWith

    /** Pack integrity checks; an empty list means the pack is shippable. */
    fun validate(): List<String> {
        val issues = mutableListOf<String>()
        val allIds = manifests.flatMap { m -> m.assets.map { it.id } }
        allIds.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { issues += "duplicate asset id $it" }
        // A second pack adds per-base parts through expressionOverrides. It must not redefine an expression.
        manifests.flatMap { m -> m.expressions.map { it.id } }
            .groupBy { it }.filterValues { it.size > 1 }.keys.sorted()
            .forEach { issues += "expression $it is defined by more than one pack" }
        val bases = ofCategory(AssetCategory.BASE).map { it.id }.toSet()

        fun ref(owner: String, id: String?, vararg categories: AssetCategory) {
            if (id == null) return
            val a = asset(id)
            when {
                a == null -> issues += "$owner references unknown asset $id"
                categories.isNotEmpty() && a.category !in categories -> issues += "$owner references $id of category ${a.category}, expected ${categories.toList()}"
            }
        }

        for (a in assets.values) {
            if (a.accessibilityLabel.isBlank()) issues += "${a.id} has no accessibility label"
            a.compatibleBases.filterNot { it in bases }.forEach { issues += "${a.id} lists unknown base $it" }
            a.conflictsWith.forEach { ref(a.id, it) }
            a.requires.forEach { ref(a.id, it) }
            ref(a.id, a.fallback, a.category)
            if (a.render.type !in setOf("procedural", "raster", "vector")) issues += "${a.id} has unknown render type ${a.render.type}"
            if (a.render.type == "raster" && a.render.file.isNullOrBlank()) issues += "${a.id} has no file"
            if (a.render.type == "vector") {
                if (a.render.file.isNullOrBlank()) issues += "${a.id} has no file"
                if (a.colorSlots.isEmpty()) issues += "${a.id} has no color slots"
            }
            if (a.family != null && a.category != AssetCategory.BASE) issues += "${a.id} declares a family but is not a base"
            if (a.category == AssetCategory.PALETTE && a.colors == null) issues += "${a.id} palette has no colors"
            if (a.category == AssetCategory.BASE) {
                REQUIRED_BASE_ANCHORS.filterNot { it in a.anchors }.forEach { issues += "${a.id} is missing anchor $it" }
                if (a.faceSafeZone == null) issues += "${a.id} has no face-safe zone"
            }
            if (a.category in GLYPH_CATEGORIES) {
                if (a.glyph.isNullOrBlank()) issues += "${a.id} has no shape glyph"
                if (!a.widgetSafe) issues += "${a.id} must be widget-safe"
            }
            if (a.anchor != null && bases.any { b -> asset(b)?.anchors?.containsKey(a.anchor) == false && isCompatibleWithBase(a, b) }) {
                issues += "${a.id} uses anchor ${a.anchor} missing on a compatible base"
            }
            // Fallback chains must terminate.
            val seen = mutableSetOf(a.id)
            var next = a.fallback
            while (next != null) {
                if (!seen.add(next)) { issues += "${a.id} has a fallback cycle"; break }
                next = asset(next)?.fallback
            }
        }

        for (e in expressions.values) {
            for (base in bases) {
                val p = e.partsFor(base)
                ref("expression ${e.id}@$base", p.eyes, AssetCategory.FACE_EYE)
                ref("expression ${e.id}@$base", p.brows, AssetCategory.FACE_BROW)
                ref("expression ${e.id}@$base", p.mouth, AssetCategory.FACE_MOUTH)
                listOfNotNull(p.eyes, p.brows, p.mouth).mapNotNull(::asset).filterNot { isCompatibleWithBase(it, base) }
                    .forEach { issues += "expression ${e.id} uses ${it.id}, which is incompatible with $base" }
            }
            e.overlays.forEach { ref("expression ${e.id}", it, AssetCategory.EXPRESSION_OVERLAY) }
            e.extras.forEach { ref("expression ${e.id}", it, AssetCategory.EXPRESSION_OVERLAY) }
            e.baseOverrides.keys.filterNot { it in bases }.forEach { issues += "expression ${e.id} overrides unknown base $it" }
        }
        val overridePairs = mutableSetOf<Pair<String, String>>()
        fun claim(expressionId: String, baseId: String) {
            if (!overridePairs.add(expressionId to baseId)) {
                issues += "expression $expressionId overrides $baseId twice"
            }
        }
        for (manifest in manifests) {
            for (expression in manifest.expressions) {
                expression.baseOverrides.keys.forEach { claim(expression.id, it) }
            }
            for ((expressionId, byBase) in manifest.expressionOverrides) {
                if (expression(expressionId) == null) issues += "expressionOverrides names unknown expression $expressionId"
                for ((baseId, parts) in byBase) {
                    claim(expressionId, baseId)
                    listOfNotNull(parts.eyes, parts.brows, parts.mouth).forEach { partId ->
                        if (asset(partId) == null) issues += "expression $expressionId override names unknown part $partId"
                    }
                }
            }
        }
        val slotDefaults = mutableMapOf<String, MutableSet<String>>()
        for (asset in assets.values) {
            for ((slot, hex) in asset.colorSlots) {
                slotDefaults.getOrPut(slot) { mutableSetOf() }.add(hex.lowercase())
            }
        }
        slotDefaults.filterValues { it.size > 1 }.keys.sorted().forEach {
            issues += "color slot $it has conflicting defaults"
        }

        for ((key, s) in semantics) {
            if (s.expressionId != null && expression(s.expressionId) == null) issues += "semantic $key uses unknown expression ${s.expressionId}"
            ref("semantic $key", s.availabilityIndicator, AssetCategory.AVAILABILITY_INDICATOR)
            ref("semantic $key", s.activityBadge, AssetCategory.ACTIVITY_BADGE)
            ref("semantic $key", s.propAssetId, AssetCategory.FOREGROUND_PROP)
            ref("semantic $key", s.headAccessoryAssetId, AssetCategory.HEAD_ACCESSORY)
            ref("semantic $key", s.bodyAccessoryAssetId, AssetCategory.BODY_ACCESSORY)
            ref("semantic $key", s.sceneAssetId, AssetCategory.SCENE)
            s.overlayAssetIds.forEach { ref("semantic $key", it, AssetCategory.EXPRESSION_OVERLAY, AssetCategory.REACTION_OVERLAY) }
        }
        // The core vocabulary must always be expressible (§17.3: never paywalled or missing).
        Mood.entries.forEach { m ->
            if (semantic(SemanticKey.mood(m))?.expressionId == null) issues += "mood ${m.name} has no expression"
        }
        Availability.entries.forEach { a ->
            if (semantic(SemanticKey.availability(a))?.availabilityIndicator == null) issues += "availability ${a.name} has no indicator"
        }
        ActivityType.entries.filter { it != ActivityType.NONE }.forEach { t ->
            if (semantic(SemanticKey.activity(t))?.activityBadge == null) issues += "activity ${t.name} has no badge"
        }
        for ((old, new) in retired) {
            if (asset(new) == null) issues += "retired $old points to unknown $new"
        }
        manifests.mapNotNull { it.defaults }.forEach { d ->
            ref("defaults", d.base, AssetCategory.BASE)
            ref("defaults", d.palette, AssetCategory.PALETTE)
            ref("defaults", d.scene, AssetCategory.SCENE)
            ref("defaults", d.frame, AssetCategory.FRAME)
            ref("defaults", d.eyeFamily, AssetCategory.EYE_FAMILY)
            ref("defaults", d.mouthFamily, AssetCategory.MOUTH_FAMILY)
        }
        return issues.sorted()
    }

    companion object {
        val REQUIRED_BASE_ANCHORS = listOf("head_top", "face_center", "eyes", "mouth", "prop_hand", "body")
        private val GLYPH_CATEGORIES = setOf(AssetCategory.AVAILABILITY_INDICATOR, AssetCategory.ACTIVITY_BADGE)
    }
}
