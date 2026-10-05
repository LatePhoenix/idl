package app.idl.domain.avatar

/**
 * Draw order for one resolved avatar. Procedural categories use the band table below.
 * Vector parts use their own [VectorPart.zBand]. A category that has a vector asset is not
 * also drawn procedurally. Chrome bands are 200 and above.
 */
object CompositeOrder {
    fun ops(
        resolved: ResolvedAvatar,
        registry: AssetRegistry,
        pictureOf: (String) -> VectorPicture? = { null },
    ): List<DrawOp> {
        val vectorCategories = resolved.layers.mapNotNull { layer ->
            val asset = registry.asset(layer.assetId) ?: return@mapNotNull null
            if (asset.render.type == "vector" && pictureOf(asset.id) != null) asset.category else null
        }.toSet()

        val keyed = mutableListOf<Keyed>()
        val proceduralSeen = mutableSetOf<AssetCategory>()
        for (layer in resolved.layers.sortedBy { it.assetId }) {
            val asset = registry.asset(layer.assetId) ?: continue
            if (asset.render.type == "vector") {
                val picture = pictureOf(asset.id) ?: continue
                picture.parts.forEachIndexed { index, part ->
                    keyed += Keyed(
                        band = part.zBand,
                        z = asset.category.defaultZ,
                        assetId = asset.id,
                        partIndex = index,
                        op = DrawOp.VectorPart(asset.id, index, chrome = part.zBand >= CHROME),
                    )
                }
            } else if (asset.category !in vectorCategories && asset.category.drawn && proceduralSeen.add(asset.category)) {
                val band = proceduralBand(asset.category) ?: continue
                keyed += Keyed(
                    band = band,
                    z = asset.category.defaultZ,
                    assetId = asset.id,
                    partIndex = 0,
                    op = DrawOp.Procedural(asset.category, chrome = band >= CHROME),
                )
            }
        }
        return keyed.sortedWith(compareBy({ it.band }, { it.z }, { it.assetId }, { it.partIndex })).map { it.op }
    }

    /** Procedural band for a category. Null means the category is not painted. */
    fun proceduralBand(category: AssetCategory): Int? = when (category) {
        AssetCategory.SCENE -> 0
        AssetCategory.BODY_ACCESSORY -> 10
        AssetCategory.BASE, AssetCategory.SIGNATURE_FEATURE -> 40
        AssetCategory.FACE_EYE, AssetCategory.FACE_BROW, AssetCategory.FACE_MOUTH -> 50
        AssetCategory.FACE_ACCESSORY -> 80
        AssetCategory.HEAD_ACCESSORY -> 90
        AssetCategory.EXPRESSION_OVERLAY -> 95
        AssetCategory.FOREGROUND_PROP -> 105
        AssetCategory.AVAILABILITY_INDICATOR -> 210
        AssetCategory.ACTIVITY_BADGE -> 220
        else -> null
    }

    private const val CHROME = 200

    private data class Keyed(
        val band: Int,
        val z: Int,
        val assetId: String,
        val partIndex: Int,
        val op: DrawOp,
    )
}

sealed interface DrawOp {
    val chrome: Boolean

    data class Procedural(val category: AssetCategory, override val chrome: Boolean) : DrawOp
    data class VectorPart(val assetId: String, val partIndex: Int, override val chrome: Boolean) : DrawOp
}
