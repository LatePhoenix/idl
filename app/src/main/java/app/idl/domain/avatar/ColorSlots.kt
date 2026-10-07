package app.idl.domain.avatar

/**
 * Resolves semantic color slots for the vector assets in a draw list (AP-9 / §3.5).
 *
 * Order for each slot: recipe override → slot link (pack defaults) → OKLCH-derived
 * shadow/highlight from a recipe-overridden primary → asset default → neutral.
 * A slot in [AvatarConfiguration.unlinkedSlots] skips derivation and links.
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
        val links = registry.defaults.slotLinks
        val slots = (declared.keys + config.colorOverrides.keys + links.keys).toSortedSet()
        val memo = linkedMapOf<String, Int>()
        val visiting = mutableSetOf<String>()
        for (slot in slots) {
            resolveOne(slot, declared, config, links, memo, visiting)
        }
        return memo
    }

    private fun resolveOne(
        slot: String,
        declared: Map<String, String>,
        config: AvatarConfiguration,
        links: Map<String, String>,
        memo: MutableMap<String, Int>,
        visiting: MutableSet<String>,
    ): Int {
        memo[slot]?.let { return it }
        if (!visiting.add(slot)) return NEUTRAL
        try {
            val override = config.colorOverrides[slot]?.let(::parseHex)
            if (override != null) {
                memo[slot] = override
                return override
            }
            if (slot !in config.unlinkedSlots) {
                val source = links[slot]
                if (source != null) {
                    val linked = resolveOne(source, declared, config, links, memo, visiting)
                    memo[slot] = linked
                    return linked
                }
                val derived = derivedFromPrimary(slot, declared, config, links, memo, visiting)
                if (derived != null) {
                    memo[slot] = derived
                    return derived
                }
            }
            val fallback = parseHex(declared[slot] ?: "") ?: NEUTRAL
            memo[slot] = fallback
            return fallback
        } finally {
            visiting.remove(slot)
        }
    }

    /**
     * Shadow and highlight follow their primary only when the user set that primary
     * (same trigger as the old per-channel mix). Authored defaults stay until then.
     */
    private fun derivedFromPrimary(
        slot: String,
        declared: Map<String, String>,
        config: AvatarConfiguration,
        links: Map<String, String>,
        memo: MutableMap<String, Int>,
        visiting: MutableSet<String>,
    ): Int? {
        val primarySlot = when {
            slot.endsWith(".shadow") -> slot.removeSuffix(".shadow") + ".primary"
            slot.endsWith(".highlight") -> slot.removeSuffix(".highlight") + ".primary"
            else -> return null
        }
        if (config.colorOverrides[primarySlot]?.let(::parseHex) == null) return null
        val primary = resolveOne(primarySlot, declared, config, links, memo, visiting)
        return if (slot.endsWith(".shadow")) Oklch.deriveShadow(primary) else Oklch.deriveHighlight(primary)
    }

    /** `#RRGGBB` or `#AARRGGBB`. Anything else is ignored. */
    fun parseHex(raw: String): Int? {
        val hex = raw.removePrefix("#")
        if (hex.length != 6 && hex.length != 8) return null
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val value = hex.toLongOrNull(16) ?: return null
        return if (hex.length == 6) (0xFF000000 or value).toInt() else value.toInt()
    }

    /** Per-channel mix. Kept for procedural painters; vector derivation uses [Oklch]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - t) + ((b shr shift) and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
