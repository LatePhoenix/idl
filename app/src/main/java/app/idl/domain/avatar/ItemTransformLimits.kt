package app.idl.domain.avatar

/**
 * Per-category item-transform clamps for the editor and resolver (AP-8 / §3.6).
 * Categories without a limit keep only the identity transform.
 */
object ItemTransformLimits {

    fun clamp(category: AssetCategory, transform: ItemTransform): ItemTransform = when (category) {
        AssetCategory.FACE_ACCESSORY -> transform.copy(
            translateX = 0f,
            translateY = transform.translateY.coerceIn(-24f, 24f),
            scale = 1f,
            rotationDeg = 0f,
            flipHorizontal = false,
        )
        AssetCategory.HEAD_ACCESSORY -> transform.copy(
            translateX = 0f,
            translateY = 0f,
            scale = transform.scale.coerceIn(0.9f, 1.1f),
            rotationDeg = 0f,
            flipHorizontal = false,
        )
        else -> ItemTransform()
    }

    fun clampAll(
        transforms: Map<String, ItemTransform>,
        categoryOf: (String) -> AssetCategory?,
    ): Map<String, ItemTransform> =
        transforms.mapValues { (assetId, transform) ->
            val category = categoryOf(assetId) ?: return@mapValues ItemTransform()
            clamp(category, transform)
        }.filterValues { it != ItemTransform() }.toSortedMap()
}
