package app.idl.domain.avatar

/**
 * Cross-asset clip masks published by parts in the final draw list (AP-8 / §3.6).
 * Order is band → asset id → part index. The first publisher of a name wins; a subscription
 * whose name is missing is a no-op at draw time.
 */
object PublishedMasks {

    data class Source(
        val assetId: String,
        val partIndex: Int,
        val band: Int,
    )

    fun collect(
        ops: List<DrawOp>,
        pictureOf: (String) -> VectorPicture?,
    ): Map<String, Source> {
        data class Keyed(val band: Int, val assetId: String, val partIndex: Int, val name: String)
        val keyed = mutableListOf<Keyed>()
        for (op in ops) {
            if (op !is DrawOp.VectorPart) continue
            val part = pictureOf(op.assetId)?.parts?.getOrNull(op.partIndex) ?: continue
            val name = part.publishMask?.takeIf { it.isNotBlank() } ?: continue
            keyed += Keyed(part.zBand, op.assetId, op.partIndex, name)
        }
        keyed.sortWith(compareBy({ it.band }, { it.assetId }, { it.partIndex }))
        val out = linkedMapOf<String, Source>()
        for (entry in keyed) {
            out.putIfAbsent(entry.name, Source(entry.assetId, entry.partIndex, entry.band))
        }
        return out
    }
}
