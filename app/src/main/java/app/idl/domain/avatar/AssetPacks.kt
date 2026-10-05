package app.idl.domain.avatar

/**
 * Packs shipped inside the APK (decision D-26). This stays Android-free: the caller supplies
 * the file text, and the Android `AssetManager` adapter arrives with the renderer in Phase 2.
 */
object AssetPacks {
    /** Phase 2 PR C adds `emoji_core` once that manifest exists. Pack version is inside the render key. */
    val SHIPPED = listOf("packs/core_proto/v1/manifest.json")

    fun registry(read: (path: String) -> String): AssetRegistry =
        AssetRegistry(SHIPPED.map { AssetManifest.parse(read(it)) })
}
