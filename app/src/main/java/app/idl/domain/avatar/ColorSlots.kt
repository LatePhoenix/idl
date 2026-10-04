package app.idl.domain.avatar

/**
 * Resolves semantic color slots for the vector assets in a draw list.
 * Shadow is the primary mixed 25% toward black. Highlight is the primary mixed 30% toward white.
 * The mix is per channel, the same arithmetic the procedural painter uses.
 */
object ColorSlots {
    const val NEUTRAL: Int = 0xFF9E9E9E.toInt()

    fun resolve(layers: List<ResolvedLayer>, config: AvatarConfiguration, registry: AssetRegistry): Map<String, Int> {
        val declared = linkedMapOf<String, String>()
        for (layer in layers) {
            val asset = registry.asset(layer.assetId) ?: continue
            if (asset.render.type != "vector") continue
            for ((slot, hex) in asset.colorSlots) {
                declared.putIfAbsent(slot, hex)
            }
        }
        val resolved = linkedMapOf<String, Int>()
        for (slot in declared.keys.sorted()) {
            val override = config.colorOverrides[slot]?.let(::parseHex)
            val color = when {
                override != null -> override
                else -> derived(slot, config) ?: parseHex(declared.getValue(slot)) ?: NEUTRAL
            }
            resolved[slot] = color
        }
        return resolved
    }

    private fun derived(slot: String, config: AvatarConfiguration): Int? {
        val suffix = when {
            slot.endsWith(".shadow") -> ".shadow"
            slot.endsWith(".highlight") -> ".highlight"
            else -> return null
        }
        if (slot in config.unlinkedSlots) return null
        val primary = config.colorOverrides[slot.removeSuffix(suffix) + ".primary"]?.let(::parseHex) ?: return null
        val toward = if (suffix == ".shadow") 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        val amount = if (suffix == ".shadow") 0.25f else 0.30f
        return mix(primary, toward, amount)
    }

    /** `#RRGGBB` or `#AARRGGBB`. Anything else is ignored. */
    fun parseHex(raw: String): Int? {
        val hex = raw.removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (0xFF000000 or value).toInt() else value.toInt()
    }

    /** Per-channel mix. Alpha of the result is opaque, matching [app.idl.avatar.AvatarRenderer]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
