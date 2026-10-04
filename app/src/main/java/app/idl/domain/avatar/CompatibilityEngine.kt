package app.idl.domain.avatar

/**
 * Identity-asset checks shared by save-time cleanup and [AvatarResolver].
 *
 * Conflict order is [LayerPriority] (highest first), then z-index, then id. Signature features
 * are [LayerPriority.BASE] and accessories are [LayerPriority.SIGNATURE], so ears beat a helmet
 * that only one side of the pair declares a conflict with. A fallback is kept in preference to
 * deleting the choice.
 */
class CompatibilityEngine(private val registry: AssetRegistry) {

    fun validate(config: AvatarConfiguration): List<ConfigIssue> {
        val issues = mutableListOf<ConfigIssue>()
        val base = registry.asset(config.baseAssetId)
        val baseId = if (base != null && base.category == AssetCategory.BASE) {
            base.id
        } else {
            issues += ConfigIssue(config.baseAssetId, "unknown")
            registry.defaults.base
        }

        val items = mutableListOf<Ranked>()
        fun check(raw: String?, category: AssetCategory, priority: LayerPriority) {
            if (raw == null) return
            val asset = registry.asset(raw)
            if (asset == null || asset.category != category) {
                issues += ConfigIssue(raw, "unknown")
                return
            }
            if (!registry.isCompatibleWithBase(asset, baseId)) {
                issues += ConfigIssue(asset.id, "incompatible with $baseId")
                return
            }
            items += Ranked(asset, priority)
        }

        check(config.paletteAssetId, AssetCategory.PALETTE, LayerPriority.BASE)
        check(config.eyeFamilyAssetId, AssetCategory.EYE_FAMILY, LayerPriority.FACE)
        check(config.mouthFamilyAssetId, AssetCategory.MOUTH_FAMILY, LayerPriority.FACE)
        config.signatureFeatureAssetIds.forEach { check(it, AssetCategory.SIGNATURE_FEATURE, LayerPriority.BASE) }
        check(config.signatureHeadAccessoryAssetId, AssetCategory.HEAD_ACCESSORY, LayerPriority.SIGNATURE)
        check(config.signatureFaceAccessoryAssetId, AssetCategory.FACE_ACCESSORY, LayerPriority.SIGNATURE)
        check(config.signatureBodyAccessoryAssetId, AssetCategory.BODY_ACCESSORY, LayerPriority.SIGNATURE)
        check(config.defaultPropAssetId, AssetCategory.FOREGROUND_PROP, LayerPriority.SIGNATURE)
        check(config.defaultSceneAssetId, AssetCategory.SCENE, LayerPriority.SCENE)
        check(config.defaultFrameAssetId, AssetCategory.FRAME, LayerPriority.BASE)
        if (registry.expression(config.restingExpressionId) == null) {
            issues += ConfigIssue(config.restingExpressionId, "unknown expression")
        }

        val occupied = mutableListOf<AssetDef>()
        for (item in items.sortedWith(RANK)) {
            val winner = occupied.firstOrNull { registry.conflicts(item.asset, it) }
            if (winner != null) issues += ConfigIssue(item.asset.id, "conflicts with ${winner.id}")
            else occupied += item.asset
        }
        return issues.distinct().sortedWith(compareBy({ it.assetId }, { it.problem }))
    }

    /**
     * A configuration [validate] accepts. Retired ids are rewritten to their live targets.
     * The issue list describes what was wrong with [config]; it is empty when nothing changed
     * for a reason other than retirement.
     */
    fun sanitize(config: AvatarConfiguration): Pair<AvatarConfiguration, List<ConfigIssue>> {
        val issues = mutableListOf<ConfigIssue>()
        val base = fitRequired(config.baseAssetId, AssetCategory.BASE, registry.defaults.base, issues)
        val baseId = base.id

        fun optional(raw: String?, category: AssetCategory): AssetDef? {
            if (raw == null) return null
            val asset = registry.asset(raw)
            if (asset == null || asset.category != category) {
                issues += ConfigIssue(raw, "unknown")
                return null
            }
            val fitted = firstFit(asset, baseId)
            if (fitted == null) {
                issues += ConfigIssue(asset.id, "incompatible with $baseId")
                return null
            }
            if (fitted.id != asset.id) issues += ConfigIssue(asset.id, "incompatible with $baseId")
            return fitted
        }

        val palette = fitRequired(config.paletteAssetId, AssetCategory.PALETTE, registry.defaults.palette, issues).id
        var eye = optional(config.eyeFamilyAssetId, AssetCategory.EYE_FAMILY)
        var mouth = optional(config.mouthFamilyAssetId, AssetCategory.MOUTH_FAMILY)
        val keptFeatures = config.signatureFeatureAssetIds
            .mapNotNull { optional(it, AssetCategory.SIGNATURE_FEATURE) }
            .map<AssetDef, AssetDef?> { it }
            .toMutableList()
        var head = optional(config.signatureHeadAccessoryAssetId, AssetCategory.HEAD_ACCESSORY)
        var face = optional(config.signatureFaceAccessoryAssetId, AssetCategory.FACE_ACCESSORY)
        var body = optional(config.signatureBodyAccessoryAssetId, AssetCategory.BODY_ACCESSORY)
        var prop = optional(config.defaultPropAssetId, AssetCategory.FOREGROUND_PROP)
        var scene = optional(config.defaultSceneAssetId, AssetCategory.SCENE)
        var frame = optional(config.defaultFrameAssetId, AssetCategory.FRAME)

        data class Slot(val asset: AssetDef, val priority: LayerPriority, val write: (AssetDef?) -> Unit)
        val slots = mutableListOf<Slot>()
        keptFeatures.forEachIndexed { index, asset ->
            if (asset != null) slots += Slot(asset, LayerPriority.BASE) { keptFeatures[index] = it }
        }
        fun track(asset: AssetDef?, priority: LayerPriority, write: (AssetDef?) -> Unit) {
            if (asset != null) slots += Slot(asset, priority, write)
        }
        track(eye, LayerPriority.FACE) { eye = it }
        track(mouth, LayerPriority.FACE) { mouth = it }
        track(head, LayerPriority.SIGNATURE) { head = it }
        track(face, LayerPriority.SIGNATURE) { face = it }
        track(body, LayerPriority.SIGNATURE) { body = it }
        track(prop, LayerPriority.SIGNATURE) { prop = it }
        track(scene, LayerPriority.SCENE) { scene = it }
        track(frame, LayerPriority.BASE) { frame = it }

        val occupied = mutableListOf<AssetDef>()
        for (slot in slots.sortedWith(compareBy<Slot>({ it.priority.ordinal }, { it.asset.z }, { it.asset.id }))) {
            val fitted = firstFit(slot.asset, baseId, occupied)
            if (fitted == null) {
                val winner = occupied.firstOrNull { registry.conflicts(slot.asset, it) }
                issues += ConfigIssue(
                    slot.asset.id,
                    if (winner != null) "conflicts with ${winner.id}" else "incompatible with $baseId",
                )
                slot.write(null)
            } else {
                if (fitted.id != slot.asset.id) {
                    val winner = occupied.first { registry.conflicts(slot.asset, it) }
                    issues += ConfigIssue(slot.asset.id, "conflicts with ${winner.id}")
                }
                slot.write(fitted)
                occupied += fitted
            }
        }

        val resting = if (registry.expression(config.restingExpressionId) != null) {
            config.restingExpressionId
        } else {
            issues += ConfigIssue(config.restingExpressionId, "unknown expression")
            "neutral"
        }

        val cleaned = config.copy(
            baseAssetId = baseId,
            paletteAssetId = palette,
            eyeFamilyAssetId = eye?.id,
            mouthFamilyAssetId = mouth?.id,
            signatureFeatureAssetIds = keptFeatures.mapNotNull { it?.id }.distinct(),
            signatureHeadAccessoryAssetId = head?.id,
            signatureFaceAccessoryAssetId = face?.id,
            signatureBodyAccessoryAssetId = body?.id,
            defaultPropAssetId = prop?.id,
            defaultSceneAssetId = scene?.id,
            defaultFrameAssetId = frame?.id,
            restingExpressionId = resting,
        )
        return cleaned to issues.distinct().sortedWith(compareBy({ it.assetId }, { it.problem }))
    }

    /**
     * First asset on [start]'s fallback chain, including itself, that fits [baseId] and conflicts
     * with none of [occupied]. The resolver and [sanitize] both go through here so a one-sided
     * `conflictsWith` cannot be missed by one of them.
     */
    internal fun firstFit(start: AssetDef, baseId: String, occupied: List<AssetDef> = emptyList()): AssetDef? {
        var current: AssetDef? = start
        val seen = HashSet<String>()
        while (current != null && seen.add(current.id)) {
            val blocked = occupied.any { registry.conflicts(current, it) }
            if (registry.isCompatibleWithBase(current, baseId) && !blocked) return current
            val next = current.fallback?.let { registry.asset(it) } ?: return null
            if (next.category != start.category) return null
            current = next
        }
        return null
    }

    private fun fitRequired(raw: String, category: AssetCategory, defaultId: String, issues: MutableList<ConfigIssue>): AssetDef {
        val asset = registry.asset(raw)
        if (asset == null || asset.category != category) {
            issues += ConfigIssue(raw, "unknown")
            return registry.asset(defaultId) ?: error("pack default $defaultId is missing")
        }
        return asset
    }

    private data class Ranked(val asset: AssetDef, val priority: LayerPriority)

    private companion object {
        val RANK = compareBy<Ranked>({ it.priority.ordinal }, { it.asset.z }, { it.asset.id })
    }
}

/** One reason [CompatibilityEngine.validate] rejected an id in a saved configuration. */
data class ConfigIssue(val assetId: String, val problem: String)
