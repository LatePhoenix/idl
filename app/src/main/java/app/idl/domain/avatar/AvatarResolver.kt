package app.idl.domain.avatar

import app.idl.domain.ActivityType
import app.idl.domain.IdlJson
import app.idl.domain.wire
import java.security.MessageDigest
import java.util.Locale

/**
 * Conflict order from master plan §11.3, highest first. Earlier constants are accepted before
 * later ones, so a silhouette beats a decoration when both cannot be shown.
 *
 * [STATUS] is for head and body accessories chosen by the current status. It sits above a
 * signature so the choice wins for every viewer, and below [ACTIVITY] so an activity mapping
 * that names the same asset can still raise the slot (decision D-31).
 */
enum class LayerPriority {
    BASE,
    FACE,
    AVAILABILITY,
    EXPRESSION,
    ACTIVITY,
    STATUS,
    SIGNATURE,
    CONTEXT,
    SCENE,
    DECORATION,
}

/** Why a requested asset is absent from [ResolvedAvatar.layers]. */
enum class DropReason {
    UNKNOWN_ASSET,
    INCOMPATIBLE_BASE,
    CONFLICT,
    MISSING_REQUIREMENT,
    OCCLUDED,
    TARGET_SIMPLIFIED,
    DISPLACED,
}

data class ResolvedLayer(
    val assetId: String,
    val category: AssetCategory,
    val z: Int,
    val priority: LayerPriority,
    val variant: String? = null,
)

data class DroppedAsset(
    val assetId: String,
    val reason: DropReason,
    val detail: String? = null,
)

/**
 * The draw list for one [AvatarRenderRequest]. [renderKey] is the cache key (decision D-27):
 * the same inputs always produce the same key, and any change to the request or pack version
 * changes it. [accessibilityDescription] is the non-visual summary a screen reader speaks.
 */
data class ResolvedAvatar(
    val baseAssetId: String,
    val paletteAssetId: String,
    val expressionId: String,
    val layers: List<ResolvedLayer>,
    val dropped: List<DroppedAsset>,
    val sceneDetail: Boolean,
    val renderKey: String,
    val accessibilityDescription: String,
    /** Resolved sRGB colors for vector slots, sorted by slot name. Empty when no vector layer is drawn. */
    val colorSlots: Map<String, Int> = emptyMap(),
    /** Recipe transforms for assets that survived into [layers]. */
    val itemTransforms: Map<String, ItemTransform> = emptyMap(),
    /** Viewport for this render. Part of [renderKey]. */
    val framing: Framing = Framing.HEAD,
)

/**
 * Master plan §11.2, without drawing. Presence fields that are null are not consulted, so a
 * hidden mood cannot change the expression or pull in a `mood:*` override (decision D-24).
 */
class AvatarResolver(private val registry: AssetRegistry) {
    private val engine = CompatibilityEngine(registry)

    fun resolve(request: AvatarRenderRequest): ResolvedAvatar {
        val request = request.copy(configuration = request.configuration.migrateRecipe(registry.baseFamilies))
        val config = request.configuration
        val presence = request.presence
        val dropped = mutableListOf<DroppedAsset>()
        val candidates = mutableListOf<Candidate>()

        fun drop(assetId: String, reason: DropReason, detail: String? = null) {
            if (dropped.none { it.assetId == assetId && it.reason == reason }) {
                dropped += DroppedAsset(assetId, reason, detail)
            }
        }

        fun add(asset: AssetDef, priority: LayerPriority, variant: String? = null, order: Int = 0) {
            val existing = candidates.indexOfFirst { it.asset.id == asset.id }
            if (existing >= 0) {
                if (priority.ordinal < candidates[existing].priority.ordinal) {
                    candidates[existing] = Candidate(asset, priority, variant, order)
                }
                return
            }
            candidates += Candidate(asset, priority, variant, order)
        }

        fun addId(raw: String, categories: Set<AssetCategory>, priority: LayerPriority, variant: String? = null, order: Int = 0) {
            val canon = registry.canonicalId(raw)
            val asset = registry.asset(canon)
            if (asset == null || asset.category !in categories) drop(canon, DropReason.UNKNOWN_ASSET)
            else add(asset, priority, variant, order)
        }

        // Step 1–2. Unknown ids from presence or a scene mapping are skipped. Only when no
        // candidate resolves does an identity slot use the pack default.
        val baseId = resolveFamily(listOf(config.baseAssetId), AssetCategory.BASE, registry.defaults.base)
        val paletteId = resolveFamily(listOf(config.paletteAssetId), AssetCategory.PALETTE, registry.defaults.palette)
        val eyeFamilyId = resolveFamily(
            listOf(config.eyeFamilyAssetId ?: registry.defaults.eyeFamily),
            AssetCategory.EYE_FAMILY,
            registry.defaults.eyeFamily,
        )
        val mouthFamilyId = resolveFamily(
            listOf(config.mouthFamilyAssetId ?: registry.defaults.mouthFamily),
            AssetCategory.MOUTH_FAMILY,
            registry.defaults.mouthFamily,
        )

        val keys = presence.semanticKeys()
        fun override(key: SemanticKey) = config.styleDna.semanticVisualOverrides[key.wire]
        /** Activity mappings outrank a signature; other visible keys are still a status choice. */
        fun accessoryPriority(key: SemanticKey) =
            if (key.kind == SemanticKey.Kind.ACTIVITY) LayerPriority.ACTIVITY else LayerPriority.STATUS

        /** Props stay contextual. They don't conflict with signatures in the shipped pack. */
        fun propPriority(key: SemanticKey) =
            if (key.kind == SemanticKey.Kind.ACTIVITY) LayerPriority.ACTIVITY else LayerPriority.CONTEXT

        val sceneId = resolveFamily(
            buildList {
                add(presence.sceneAssetId)
                keys.forEach { add(override(it)?.sceneAssetId) }
                keys.forEach { add(registry.semantic(it)?.sceneAssetId) }
                add(config.defaultSceneAssetId)
            },
            AssetCategory.SCENE,
            registry.defaults.scene,
        )
        val frameId = resolveFamily(
            listOf(config.defaultFrameAssetId),
            AssetCategory.FRAME,
            registry.defaults.frame,
        )

        val head = pick(
            buildList {
                add(sourced(presence.headAccessoryAssetId, LayerPriority.STATUS, signature = false))
                keys.forEach { add(sourced(override(it)?.headAccessoryAssetId, accessoryPriority(it), false)) }
                keys.forEach { add(sourced(registry.semantic(it)?.headAccessoryAssetId, accessoryPriority(it), false)) }
                add(sourced(config.signatureHeadAccessoryAssetId, LayerPriority.SIGNATURE, true))
            },
            AssetCategory.HEAD_ACCESSORY,
            dropped,
        )
        val body = pick(
            buildList {
                add(sourced(presence.bodyAccessoryAssetId, LayerPriority.STATUS, signature = false))
                keys.forEach { add(sourced(override(it)?.bodyAccessoryAssetId, accessoryPriority(it), false)) }
                keys.forEach { add(sourced(registry.semantic(it)?.bodyAccessoryAssetId, accessoryPriority(it), false)) }
                add(sourced(config.signatureBodyAccessoryAssetId, LayerPriority.SIGNATURE, true))
            },
            AssetCategory.BODY_ACCESSORY,
            dropped,
        )
        val face = pick(
            listOf(sourced(config.signatureFaceAccessoryAssetId, LayerPriority.SIGNATURE, true)),
            AssetCategory.FACE_ACCESSORY,
            dropped,
        )
        val prop = pick(
            buildList {
                add(sourced(presence.propAssetId, LayerPriority.CONTEXT, false))
                keys.forEach { add(sourced(override(it)?.propAssetId, propPriority(it), false)) }
                keys.forEach { add(sourced(registry.semantic(it)?.propAssetId, propPriority(it), false)) }
                add(sourced(config.defaultPropAssetId, LayerPriority.SIGNATURE, true))
            },
            AssetCategory.FOREGROUND_PROP,
            dropped,
        )
        displace(config.signatureHeadAccessoryAssetId, head, dropped)
        displace(config.signatureBodyAccessoryAssetId, body, dropped)

        val availability = pick(
            buildList {
                keys.forEach { add(sourced(override(it)?.availabilityIndicatorAssetId, LayerPriority.AVAILABILITY, false)) }
                val indicator = presence.availability?.let { registry.semantic(SemanticKey.availability(it))?.availabilityIndicator }
                add(sourced(indicator, LayerPriority.AVAILABILITY, false))
            },
            AssetCategory.AVAILABILITY_INDICATOR,
            dropped,
        )
        val badgeId = presence.activityType
            ?.takeIf { it != ActivityType.NONE }
            ?.let { registry.semantic(SemanticKey.activity(it))?.activityBadge }
        val badge = pick(
            listOf(sourced(badgeId, LayerPriority.ACTIVITY, false)),
            AssetCategory.ACTIVITY_BADGE,
            dropped,
        )

        // Step 3. Expression sources stop at the first id the pack actually defines.
        val expressionId = firstExpression(buildList {
            add(presence.expressionId)
            keys.forEach { add(override(it)?.expressionId) }
            if (presence.mood != null) add(registry.semantic(SemanticKey.mood(presence.mood))?.expressionId)
            if (presence.availability != null) add(registry.semantic(SemanticKey.availability(presence.availability))?.expressionId)
            add(config.restingExpressionId)
            add("neutral")
        }, dropped)
        val expression = registry.expression(expressionId)
        val parts = expression?.partsFor(baseId)
        val eyeVariant = variantFor(eyeFamilyId, baseId, "eyefam_round")
        val mouthVariant = variantFor(mouthFamilyId, baseId, registry.defaults.mouthFamily)
        val overrideEyes = keys.firstNotNullOfOrNull { override(it)?.eyesAssetId }
        val eyesId = when {
            overrideEyes == null -> parts?.eyes
            registry.asset(overrideEyes)?.category == AssetCategory.FACE_EYE -> registry.asset(overrideEyes)!!.id
            else -> {
                drop(registry.canonicalId(overrideEyes), DropReason.UNKNOWN_ASSET)
                parts?.eyes
            }
        }

        registry.asset(baseId)?.let { add(it, LayerPriority.BASE) }
        config.signatureFeatureAssetIds.forEach { raw ->
            addId(raw, setOf(AssetCategory.SIGNATURE_FEATURE), LayerPriority.BASE)
        }
        eyesId?.let { addId(it, setOf(AssetCategory.FACE_EYE), LayerPriority.FACE, variantForAsset(it, eyeVariant)) }
        parts?.brows?.let { addId(it, setOf(AssetCategory.FACE_BROW), LayerPriority.FACE) }
        parts?.mouth?.let { addId(it, setOf(AssetCategory.FACE_MOUTH), LayerPriority.FACE, variantForAsset(it, mouthVariant)) }
        expression?.overlays?.forEach { addId(it, OVERLAYS, LayerPriority.EXPRESSION) }
        expression?.extras?.forEach { addId(it, OVERLAYS, LayerPriority.DECORATION) }
        keys.forEach { key ->
            override(key)?.overlayAssetIds?.forEach { addId(it, OVERLAYS, LayerPriority.CONTEXT) }
            registry.semantic(key)?.overlayAssetIds?.forEach { addId(it, OVERLAYS, LayerPriority.CONTEXT) }
        }
        listOfNotNull(head, body, face, prop, availability, badge).forEach { chosen ->
            add(checkNotNull(chosen.asset), chosen.priority)
        }
        registry.asset(sceneId)?.let { add(it, LayerPriority.SCENE) }
        registry.asset(frameId)?.let { add(it, LayerPriority.BASE) }
        request.reactionOverlayAssetIds.forEachIndexed { index, raw ->
            addId(raw, setOf(AssetCategory.REACTION_OVERLAY), LayerPriority.DECORATION, order = index)
        }
        // Worn items. Hair and facial hair are identity, so they use SIGNATURE and lose to a status.
        // A pack default top is worn when the recipe doesn't choose one, so a vector avatar isn't shirtless.
        for (categoryWire in config.itemIds.keys.sorted()) {
            val category = AssetCategory.entries.firstOrNull { it.wire == categoryWire }
            val ids = config.itemIds.getValue(categoryWire)
            if (category == null) {
                ids.forEach { drop(registry.canonicalId(it), DropReason.UNKNOWN_ASSET) }
                continue
            }
            for (raw in ids.sorted()) {
                val canon = registry.canonicalId(raw)
                val asset = registry.asset(canon)
                when {
                    asset == null || asset.category != category -> drop(canon, DropReason.UNKNOWN_ASSET)
                    !category.multiple && candidates.any { it.asset.category == category } -> {
                        val winner = candidates.first { it.asset.category == category }
                        drop(asset.id, DropReason.CONFLICT, winner.asset.id)
                    }
                    else -> add(asset, LayerPriority.SIGNATURE)
                }
            }
        }
        if (candidates.none { it.asset.category == AssetCategory.TOP }) {
            registry.defaultTopId?.let { id -> registry.asset(id)?.let { add(it, LayerPriority.SIGNATURE) } }
        }

        // Steps 4–8.
        var accepted = fitBase(candidates, baseId, dropped)
        accepted = resolveConflicts(accepted, baseId, dropped)
        accepted = dropUnsatisfied(accepted, dropped)
        accepted = dropOccluded(accepted, dropped)
        accepted = simplify(accepted, request, dropped)
        if (config.styleDna.sceneDetailPreference == SceneDetailPreference.NONE) {
            registry.asset(registry.defaults.scene)?.let { plain ->
                accepted = accepted.map { if (it.asset.category == AssetCategory.SCENE) it.copy(asset = plain) else it }
            }
        }

        val layers = accepted
            .filter { it.asset.category.drawn }
            .sortedWith(compareBy({ it.asset.z }, { it.asset.id }))
            .map { ResolvedLayer(it.asset.id, it.asset.category, it.asset.z, it.priority, it.variant) }
        val sceneDetail = when (config.styleDna.sceneDetailPreference) {
            SceneDetailPreference.NONE -> false
            SceneDetailPreference.LOW_DETAIL -> request.sizePx >= 256
            SceneDetailPreference.FULL -> request.sizePx >= 96
        }
        return ResolvedAvatar(
            baseAssetId = baseId,
            paletteAssetId = paletteId,
            expressionId = expressionId,
            layers = layers,
            dropped = dropped.sortedWith(compareBy({ it.assetId }, { it.reason.ordinal })),
            sceneDetail = sceneDetail,
            renderKey = renderKey(request, layers),
            accessibilityDescription = describe(baseId, expressionId, presence),
            colorSlots = ColorSlots.resolve(layers, config, registry),
            itemTransforms = config.itemTransforms.filterKeys { key -> layers.any { it.assetId == key } }.toSortedMap(),
            framing = request.target.framing,
        )
    }

    /**
     * Walk [candidates] until one resolves. An unknown id is not a choice: presence and scene
     * mappings fall through to the next source. The pack [fallback] is used only when every
     * candidate is missing, which is how a single identity slot (base, palette, eyes, mouth,
     * saved scene, frame) still lands on the pack default.
     */
    private fun resolveFamily(candidates: List<String?>, category: AssetCategory, fallback: String): String {
        for (raw in candidates) {
            if (raw.isNullOrBlank()) continue
            val asset = registry.asset(raw)
            if (asset != null && asset.category == category) return asset.id
        }
        return registry.asset(fallback)?.id ?: fallback
    }

    /**
     * The first source that resolves chooses the asset. Later sources that name that same
     * asset can only raise its [LayerPriority] (decision D-31). An explicit headset is [LayerPriority.STATUS]
     * on its own, and [LayerPriority.ACTIVITY] when `activity:vr` names it too. Either outranks a
     * signature in another slot for the life of the status.
     */
    private fun pick(options: List<Sourced?>, category: AssetCategory, dropped: MutableList<DroppedAsset>): Sourced? {
        var chosen: Sourced? = null
        var chosenAt = -1
        for ((index, option) in options.withIndex()) {
            if (option == null) continue
            val canon = registry.canonicalId(option.id)
            val asset = registry.asset(canon)
            if (asset != null && asset.category == category) {
                chosen = option.copy(id = asset.id, asset = asset)
                chosenAt = index
                break
            }
            if (dropped.none { it.assetId == canon && it.reason == DropReason.UNKNOWN_ASSET }) {
                dropped += DroppedAsset(canon, DropReason.UNKNOWN_ASSET)
            }
        }
        val selected = chosen ?: return null
        var priority = selected.priority
        for (option in options.drop(chosenAt + 1)) {
            if (option == null) continue
            val asset = registry.asset(registry.canonicalId(option.id)) ?: continue
            if (asset.id == selected.asset?.id && option.priority.ordinal < priority.ordinal) {
                priority = option.priority
            }
        }
        return if (priority == selected.priority) selected else selected.copy(priority = priority)
    }

    private fun displace(signatureId: String?, chosen: Sourced?, dropped: MutableList<DroppedAsset>) {
        if (chosen == null || chosen.signature || signatureId == null) return
        val canon = registry.canonicalId(signatureId)
        val asset = registry.asset(canon)
        if (asset == null) {
            if (dropped.none { it.assetId == canon && it.reason == DropReason.UNKNOWN_ASSET }) {
                dropped += DroppedAsset(canon, DropReason.UNKNOWN_ASSET)
            }
        } else if (asset.id != chosen.asset?.id) {
            dropped += DroppedAsset(asset.id, DropReason.DISPLACED)
        }
    }

    private fun firstExpression(options: List<String?>, dropped: MutableList<DroppedAsset>): String {
        for (raw in options) {
            if (raw.isNullOrBlank()) continue
            if (registry.expression(raw) != null) return raw
            if (dropped.none { it.assetId == raw && it.reason == DropReason.UNKNOWN_ASSET }) {
                dropped += DroppedAsset(raw, DropReason.UNKNOWN_ASSET)
            }
        }
        return "neutral"
    }

    /** A vector asset id is the whole choice. Family suffixes stay on procedural painters. */
    private fun variantForAsset(assetId: String, familyVariant: String): String? {
        val asset = registry.asset(assetId)
        return if (asset?.render?.type == "vector") null else familyVariant
    }

    private fun variantFor(familyId: String, baseId: String, hardFallback: String): String {
        val asset = registry.asset(familyId) ?: return hardFallback
        return engine.firstFit(asset, baseId)?.id ?: hardFallback
    }

    private fun fitBase(candidates: List<Candidate>, baseId: String, dropped: MutableList<DroppedAsset>): List<Candidate> {
        val kept = mutableListOf<Candidate>()
        for (candidate in candidates) {
            val fit = engine.firstFit(candidate.asset, baseId)
            if (fit == null) dropped += DroppedAsset(candidate.asset.id, DropReason.INCOMPATIBLE_BASE)
            else kept += candidate.copy(asset = fit)
        }
        return kept
    }

    private fun resolveConflicts(candidates: List<Candidate>, baseId: String, dropped: MutableList<DroppedAsset>): List<Candidate> {
        val accepted = mutableListOf<Candidate>()
        val occupied = mutableListOf<AssetDef>()
        val sorted = candidates.sortedWith(compareBy({ it.priority.ordinal }, { it.asset.z }, { it.asset.id }))
        for (candidate in sorted) {
            val fit = engine.firstFit(candidate.asset, baseId, occupied)
            if (fit == null) {
                val winner = occupied.firstOrNull { registry.conflicts(candidate.asset, it) }
                dropped += DroppedAsset(
                    candidate.asset.id,
                    if (winner != null) DropReason.CONFLICT else DropReason.INCOMPATIBLE_BASE,
                    winner?.id,
                )
            } else {
                accepted += candidate.copy(asset = fit)
                occupied += fit
            }
        }
        return accepted
    }

    private fun dropUnsatisfied(accepted: List<Candidate>, dropped: MutableList<DroppedAsset>): List<Candidate> {
        val remaining = accepted.toMutableList()
        while (true) {
            val ids = remaining.map { it.asset.id }.toSet()
            val losers = remaining.filter { candidate ->
                candidate.asset.requires.any { registry.canonicalId(it) !in ids }
            }
            if (losers.isEmpty()) return remaining
            remaining.removeAll(losers)
            losers.forEach { dropped += DroppedAsset(it.asset.id, DropReason.MISSING_REQUIREMENT) }
        }
    }

    private fun dropOccluded(accepted: List<Candidate>, dropped: MutableList<DroppedAsset>): List<Candidate> {
        val cover = accepted.maxOfOrNull { it.asset.occlusion } ?: FaceOcclusion.NONE
        if (cover == FaceOcclusion.NONE) return accepted
        val hidden = mutableSetOf(AssetCategory.FACE_EYE, AssetCategory.FACE_BROW)
        if (cover == FaceOcclusion.FULL) hidden += AssetCategory.FACE_MOUTH
        val losers = accepted.filter { it.asset.category in hidden }
        losers.forEach { dropped += DroppedAsset(it.asset.id, DropReason.OCCLUDED) }
        return accepted.filter { it.asset.category !in hidden }
    }

    private fun simplify(
        accepted: List<Candidate>,
        request: AvatarRenderRequest,
        dropped: MutableList<DroppedAsset>,
    ): List<Candidate> {
        val remaining = accepted.toMutableList()
        fun mark(predicate: (Candidate) -> Boolean) {
            val losers = remaining.filter(predicate)
            if (losers.isEmpty()) return
            remaining.removeAll(losers)
            losers.forEach { dropped += DroppedAsset(it.asset.id, DropReason.TARGET_SIMPLIFIED) }
        }
        mark { it.asset.minSizePx > request.sizePx }
        mark { request.sizePx < 64 && !it.asset.widgetSafe }
        when (request.target) {
            RenderTarget.COMPACT_WIDGET, RenderTarget.CIRCLE_WIDGET,
            RenderTarget.FRIEND_TILE, RenderTarget.NOTIFICATION,
            -> {
                mark { it.asset.category == AssetCategory.BODY_ACCESSORY }
                mark { it.priority == LayerPriority.DECORATION && it.asset.category != AssetCategory.REACTION_OVERLAY }
                mark { it.asset.category == AssetCategory.REACTION_OVERLAY }
                if (remaining.any { it.asset.category == AssetCategory.ACTIVITY_BADGE }) {
                    mark { it.asset.category == AssetCategory.FOREGROUND_PROP }
                } else {
                    val props = remaining.filter { it.asset.category == AssetCategory.FOREGROUND_PROP }
                    if (props.size > 1) {
                        val keep = props.minWith(compareBy<Candidate>({ it.priority.ordinal }, { it.asset.z }, { it.asset.id })).asset.id
                        mark { it.asset.category == AssetCategory.FOREGROUND_PROP && it.asset.id != keep }
                    }
                }
            }
            RenderTarget.STANDARD_WIDGET -> {
                mark { it.priority == LayerPriority.DECORATION && it.asset.category != AssetCategory.REACTION_OVERLAY }
                capReactions(remaining, dropped, 1)
            }
            RenderTarget.LARGE_WIDGET -> capReactions(remaining, dropped, 1)
            RenderTarget.PROFILE, RenderTarget.SHARE_CARD -> capReactions(remaining, dropped, 3)
        }
        return remaining
    }

    private fun capReactions(remaining: MutableList<Candidate>, dropped: MutableList<DroppedAsset>, max: Int) {
        val reactions = remaining.filter { it.asset.category == AssetCategory.REACTION_OVERLAY }.sortedBy { it.order }
        val keep = reactions.take(max).map { it.asset.id }.toSet()
        val losers = reactions.filter { it.asset.id !in keep }
        if (losers.isEmpty()) return
        remaining.removeAll(losers)
        losers.forEach { dropped += DroppedAsset(it.asset.id, DropReason.TARGET_SIMPLIFIED) }
    }

    private fun describe(baseId: String, expressionId: String, presence: VisiblePresence): String {
        val parts = mutableListOf("${registry.asset(baseId)?.accessibilityLabel ?: "Avatar"} avatar")
        fun add(raw: String) {
            val text = raw.replaceFirstChar { c -> c.lowercase(Locale.ROOT) }
            if (parts.none { it.equals(text, ignoreCase = true) }) parts += text
        }
        registry.expression(expressionId)?.label?.let { add(it) }
        presence.availability?.let { add(it.label) }
        presence.activityType?.takeIf { it != ActivityType.NONE }?.let { add(it.label) }
        return parts.joinToString(", ")
    }

    /** SHA-256 of every input that can change pixels, with override keys sorted so map order cannot. */
    private fun renderKey(request: AvatarRenderRequest, layers: List<ResolvedLayer>): String {
        val payload = buildString {
            append(request.rendererVersion).append('|')
            append(registry.packVersions.joinToString(",")).append('|')
            append(request.target.wire).append('|')
            append(request.target.framing.name).append('|')
            append(request.sizePx).append('|')
            append(request.wallpaperContrastMode.wire).append('|')
            append(request.accessibilityMode.wire).append('|')
            append(IdlJson.encodeToString(AvatarConfiguration.serializer(), request.configuration.withSortedOverrides())).append('|')
            append(IdlJson.encodeToString(VisiblePresence.serializer(), request.presence)).append('|')
            append(request.reactionOverlayAssetIds.joinToString(","))
            val vectors = layers.mapNotNull { layer ->
                val asset = registry.asset(layer.assetId) ?: return@mapNotNull null
                if (asset.render.type != "vector") null else "${asset.id}@${asset.contentVersion}"
            }.sorted()
            if (vectors.isNotEmpty()) append('|').append(vectors.joinToString(","))
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        return buildString(digest.size * 2) {
            for (byte in digest) {
                val value = byte.toInt() and 0xFF
                append(digits[value ushr 4])
                append(digits[value and 0x0F])
            }
        }
    }

    private fun sourced(id: String?, priority: LayerPriority, signature: Boolean): Sourced? =
        if (id.isNullOrBlank()) null else Sourced(id, priority, signature, asset = null)

    private data class Sourced(
        val id: String,
        val priority: LayerPriority,
        val signature: Boolean,
        val asset: AssetDef?,
    )

    private data class Candidate(
        val asset: AssetDef,
        val priority: LayerPriority,
        val variant: String?,
        val order: Int,
    )

    private companion object {
        val OVERLAYS = setOf(AssetCategory.EXPRESSION_OVERLAY, AssetCategory.REACTION_OVERLAY)
    }
}

private fun AvatarConfiguration.withSortedOverrides(): AvatarConfiguration {
    val sorted = linkedMapOf<String, SemanticVisualOverride>()
    for (key in styleDna.semanticVisualOverrides.keys.sorted()) {
        sorted[key] = styleDna.semanticVisualOverrides.getValue(key)
    }
    return copy(
        styleDna = styleDna.copy(semanticVisualOverrides = sorted),
        // Lists are unordered sets, so id order must not change the key.
        itemIds = itemIds.keys.sorted().associateWith { key -> itemIds.getValue(key).sorted() },
        colorOverrides = colorOverrides.keys.sorted().associateWith { colorOverrides.getValue(it) },
        unlinkedSlots = unlinkedSlots.sorted(),
        itemTransforms = itemTransforms.keys.sorted().associateWith { itemTransforms.getValue(it) },
    )
}
