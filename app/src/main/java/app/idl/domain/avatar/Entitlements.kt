package app.idl.domain.avatar

/**
 * Who owns which catalog items (AP-10 / D-48). Rendering never consults this — friends see
 * whatever was saved. Saving and the editor's commit path do.
 *
 * The server-backed implementation waits for C.3. Until then [LocalEntitlements] covers demo
 * mode and the debug "Unlock all items" switch.
 */
interface Entitlements {
    fun owns(assetId: String): Boolean

    /** Explicitly owned premium ids (free items are owned implicitly). */
    val owned: Set<String>
}

/**
 * Local / demo entitlements. Free-tier assets are always owned. Premium assets need an entry in
 * [ownedPremium] unless [unlockAll] is true (debug builds only).
 */
class LocalEntitlements(
    private val registry: AssetRegistry,
    ownedPremium: Set<String> = emptySet(),
    unlockAll: Boolean = false,
) : Entitlements {
    private val ownedPremium = ownedPremium.toMutableSet()

    @Volatile
    var unlockAll: Boolean = unlockAll

    override val owned: Set<String>
        get() = ownedPremium.toSet()

    override fun owns(assetId: String): Boolean {
        if (unlockAll) return true
        val asset = registry.asset(assetId) ?: return true
        if (asset.tier == AssetTier.FREE) return true
        return assetId in ownedPremium
    }

    fun grant(assetId: String) {
        ownedPremium += assetId
    }

    fun revoke(assetId: String) {
        ownedPremium -= assetId
    }
}
