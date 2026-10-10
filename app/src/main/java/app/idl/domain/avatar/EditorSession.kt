package app.idl.domain.avatar

import app.idl.domain.wire
import kotlin.random.Random

/**
 * Editor state for one recipe (AP-11). No Android types. See `docs/avatar/EDITOR_NOTE.md`.
 * Undo keeps the last [UNDO_LIMIT] recipes. A new edit drops redo.
 */
class EditorSession(
    initial: AvatarConfiguration,
    private val registry: AssetRegistry,
) {
    var configuration: AvatarConfiguration = initial
        private set

    /** Recipe when the session opened. Before/after shows this without editing history. */
    val opened: AvatarConfiguration = initial

    var showingOpened: Boolean = false
        private set

    private val undoStack = ArrayDeque<AvatarConfiguration>()
    private val redoStack = ArrayDeque<AvatarConfiguration>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Items the grid may offer. Procedural wardrobe is omitted (F-37). */
    fun choices(category: AssetCategory): List<AssetDef> =
        registry.ofCategory(category).filter { asset ->
            asset.storeVisible &&
                registry.isCompatibleWithBase(asset, configuration.baseAssetId) &&
                (asset.render.type != "procedural" || category !in PROCEDURAL_HIDDEN)
        }

    fun wear(assetId: String): Boolean {
        val asset = registry.asset(assetId) ?: return false
        if (!registry.isCompatibleWithBase(asset, configuration.baseAssetId)) return false
        if (!asset.storeVisible) return false
        if (asset.category !in WEARABLE) return false
        if (wearing(asset.id)) return edit(without(configuration, asset))
        val cleared = worn(configuration)
            .filter { registry.conflicts(asset, it) }
            .fold(configuration) { acc, other -> without(acc, other) }
        return edit(place(cleared, asset))
    }

    fun clearCategory(category: AssetCategory): Boolean {
        if (category !in WEARABLE) return false
        return edit(withoutCategory(configuration, category))
    }

    fun setExpression(expressionId: String): Boolean {
        if (registry.expression(expressionId) == null) return false
        return edit(configuration.copy(restingExpressionId = expressionId))
    }

    /** [hex] is `#RRGGBB` or `#AARRGGBB`. Stored uppercase. */
    fun setColor(slot: String, hex: String): Boolean {
        val match = HEX.matchEntire(hex) ?: return false
        return edit(configuration.copy(colorOverrides = configuration.colorOverrides + (slot to match.value.uppercase())))
    }

    fun setUnlinked(slot: String, unlinked: Boolean): Boolean {
        if (slot.isBlank()) return false
        val next = if (unlinked) (configuration.unlinkedSlots + slot).distinct().sorted()
        else configuration.unlinkedSlots.filterNot { it == slot }
        return edit(configuration.copy(unlinkedSlots = next))
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(configuration)
        configuration = previous
        showingOpened = false
        return true
    }

    fun redo(): Boolean {
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(configuration)
        configuration = next
        showingOpened = false
        return true
    }

    /** Look back to the recipe this session opened. Does not push undo. */
    fun toggleBefore(): AvatarConfiguration {
        showingOpened = !showingOpened
        return if (showingOpened) opened else configuration
    }

    /**
     * Fresh first-run recipe. Clears undo, redo, and the before/after view.
     * [resetAll] stays the pack-default reset and keeps history.
     */
    fun startOver() {
        configuration = EditorDefaults.starter(registry)
        undoStack.clear()
        redoStack.clear()
        showingOpened = false
    }

    /** Pack defaults for base, palette, scene, and frame. Worn items and colors clear. */
    fun resetAll(): Boolean {
        val defaults = registry.defaults
        val expression = if (registry.expression("neutral") != null) "neutral" else configuration.restingExpressionId
        return edit(
            configuration.copy(
                baseAssetId = defaults.base,
                paletteAssetId = defaults.palette,
                eyeFamilyAssetId = defaults.eyeFamily,
                mouthFamilyAssetId = defaults.mouthFamily,
                defaultSceneAssetId = defaults.scene,
                defaultFrameAssetId = defaults.frame,
                signatureFeatureAssetIds = emptyList(),
                signatureHeadAccessoryAssetId = null,
                signatureFaceAccessoryAssetId = null,
                signatureBodyAccessoryAssetId = null,
                defaultPropAssetId = null,
                restingExpressionId = expression,
                itemIds = emptyMap(),
                colorOverrides = emptyMap(),
                unlinkedSlots = emptyList(),
                itemTransforms = emptyMap(),
                randomSeed = null,
            ),
        )
    }

    /**
     * Free, compatible, vector items only. Same [seed] and same starting recipe repeat.
     * A top is always chosen when the pack has a free vector one. Other wardrobe may be empty.
     */
    fun randomize(seed: Long): Boolean {
        val random = Random(seed)
        var next = configuration.copy(
            itemIds = emptyMap(),
            signatureFeatureAssetIds = emptyList(),
            signatureHeadAccessoryAssetId = null,
            signatureFaceAccessoryAssetId = null,
            signatureBodyAccessoryAssetId = null,
            defaultPropAssetId = null,
            randomSeed = seed,
        )
        val occupied = mutableListOf<AssetDef>()
        fun take(category: AssetCategory, max: Int, allowNone: Boolean) {
            val chosen = pick(category, random, max, allowNone, occupied)
            occupied += chosen
            chosen.forEach { asset -> next = place(next, asset) }
        }
        take(AssetCategory.HAIR, 1, allowNone = true)
        take(AssetCategory.FACIAL_HAIR, 2, allowNone = true)
        take(AssetCategory.TOP, 1, allowNone = false)
        take(AssetCategory.OUTERWEAR, 1, allowNone = true)
        take(AssetCategory.FACE_ACCESSORY, 1, allowNone = true)
        take(AssetCategory.HEAD_ACCESSORY, 1, allowNone = true)
        take(AssetCategory.JEWELRY, 2, allowNone = true)
        take(AssetCategory.SIGNATURE_FEATURE, 2, allowNone = true)
        val expressions = registry.allExpressions.map { it.id }.sorted()
        if (expressions.isNotEmpty()) {
            next = next.copy(restingExpressionId = expressions[random.nextInt(expressions.size)])
        }
        return edit(next)
    }

    fun wornIds(): List<String> = worn(configuration).map { it.id }.sorted()

    private fun wearing(id: String): Boolean = id in worn(configuration).map { it.id }

    private fun pick(
        category: AssetCategory,
        random: Random,
        max: Int,
        allowNone: Boolean,
        occupied: List<AssetDef>,
    ): List<AssetDef> {
        val options = choices(category)
            .filter { it.tier == AssetTier.FREE && it.render.type == "vector" }
            .filter { asset -> occupied.none { registry.conflicts(asset, it) } }
            .sortedBy { it.id }
            .toMutableList()
        if (options.isEmpty()) return emptyList()
        val cap = if (category.multiple) minOf(max, options.size) else 1
        val count = if (allowNone) random.nextInt(cap + 1) else cap
        val chosen = mutableListOf<AssetDef>()
        repeat(count) {
            if (options.isEmpty()) return@repeat
            val asset = options.removeAt(random.nextInt(options.size))
            options.removeAll { registry.conflicts(asset, it) }
            chosen += asset
        }
        return chosen
    }

    private fun edit(next: AvatarConfiguration): Boolean {
        if (next == configuration) return false
        undoStack.addLast(configuration)
        while (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        configuration = next
        showingOpened = false
        return true
    }

    private fun worn(config: AvatarConfiguration): List<AssetDef> = buildList {
        config.itemIds.values.flatten().forEach { registry.asset(it)?.let(::add) }
        config.signatureFeatureAssetIds.forEach { registry.asset(it)?.let(::add) }
        listOfNotNull(
            config.signatureHeadAccessoryAssetId,
            config.signatureFaceAccessoryAssetId,
            config.signatureBodyAccessoryAssetId,
            config.defaultPropAssetId,
            config.defaultSceneAssetId,
            config.defaultFrameAssetId,
        ).forEach { registry.asset(it)?.let(::add) }
    }

    private fun place(config: AvatarConfiguration, asset: AssetDef): AvatarConfiguration {
        val singleCleared = if (asset.category.multiple) config else withoutCategory(config, asset.category)
        return when (asset.category) {
            AssetCategory.HEAD_ACCESSORY -> singleCleared.copy(signatureHeadAccessoryAssetId = asset.id)
            AssetCategory.FACE_ACCESSORY -> singleCleared.copy(signatureFaceAccessoryAssetId = asset.id)
            AssetCategory.BODY_ACCESSORY -> singleCleared.copy(signatureBodyAccessoryAssetId = asset.id)
            AssetCategory.FOREGROUND_PROP -> singleCleared.copy(defaultPropAssetId = asset.id)
            AssetCategory.SCENE -> singleCleared.copy(defaultSceneAssetId = asset.id)
            AssetCategory.FRAME -> singleCleared.copy(defaultFrameAssetId = asset.id)
            AssetCategory.SIGNATURE_FEATURE -> singleCleared.copy(
                signatureFeatureAssetIds = (singleCleared.signatureFeatureAssetIds + asset.id).distinct().sorted(),
            )
            in ITEM_CATEGORIES -> {
                val key = asset.category.wire
                val existing = if (asset.category.multiple) singleCleared.itemIds[key].orEmpty() else emptyList()
                singleCleared.copy(itemIds = singleCleared.itemIds + (key to (existing + asset.id).distinct().sorted()))
            }
            else -> singleCleared
        }
    }

    private fun without(config: AvatarConfiguration, asset: AssetDef): AvatarConfiguration =
        when (asset.category) {
            AssetCategory.HEAD_ACCESSORY ->
                config.copy(signatureHeadAccessoryAssetId = config.signatureHeadAccessoryAssetId.takeUnless { it == asset.id })
            AssetCategory.FACE_ACCESSORY ->
                config.copy(signatureFaceAccessoryAssetId = config.signatureFaceAccessoryAssetId.takeUnless { it == asset.id })
            AssetCategory.BODY_ACCESSORY ->
                config.copy(signatureBodyAccessoryAssetId = config.signatureBodyAccessoryAssetId.takeUnless { it == asset.id })
            AssetCategory.FOREGROUND_PROP ->
                config.copy(defaultPropAssetId = config.defaultPropAssetId.takeUnless { it == asset.id })
            AssetCategory.SCENE ->
                config.copy(defaultSceneAssetId = config.defaultSceneAssetId.takeUnless { it == asset.id })
            AssetCategory.FRAME ->
                config.copy(defaultFrameAssetId = config.defaultFrameAssetId.takeUnless { it == asset.id })
            AssetCategory.SIGNATURE_FEATURE ->
                config.copy(signatureFeatureAssetIds = config.signatureFeatureAssetIds.filterNot { it == asset.id })
            in ITEM_CATEGORIES -> {
                val key = asset.category.wire
                val left = config.itemIds[key].orEmpty().filterNot { it == asset.id }
                val items = if (left.isEmpty()) config.itemIds - key else config.itemIds + (key to left)
                config.copy(itemIds = items)
            }
            else -> config
        }

    private fun withoutCategory(config: AvatarConfiguration, category: AssetCategory): AvatarConfiguration =
        worn(config).filter { it.category == category }.fold(config) { acc, asset -> without(acc, asset) }

    private companion object {
        const val UNDO_LIMIT = 50
        val HEX = Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")
        val ITEM_CATEGORIES = setOf(
            AssetCategory.HAIR,
            AssetCategory.FACIAL_HAIR,
            AssetCategory.JEWELRY,
            AssetCategory.TOP,
            AssetCategory.OUTERWEAR,
        )
        val WEARABLE = ITEM_CATEGORIES + setOf(
            AssetCategory.HEAD_ACCESSORY,
            AssetCategory.FACE_ACCESSORY,
            AssetCategory.BODY_ACCESSORY,
            AssetCategory.FOREGROUND_PROP,
            AssetCategory.SCENE,
            AssetCategory.FRAME,
            AssetCategory.SIGNATURE_FEATURE,
        )
        val PROCEDURAL_HIDDEN = ITEM_CATEGORIES + AssetCategory.SIGNATURE_FEATURE
    }
}
