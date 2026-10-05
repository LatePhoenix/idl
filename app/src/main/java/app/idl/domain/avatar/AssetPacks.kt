package app.idl.domain.avatar

/**
 * Packs shipped inside the APK (decision D-26). This stays Android-free: the caller supplies
 * the file text, and the Android `AssetManager` adapter arrives with the renderer in Phase 2.
 */
object AssetPacks {
    /**
     * `core_proto` stays first so its defaults win. Adding `emoji_core` changes every render key
     * once, because the pack list is part of the key. Procedural pixels stay the same.
     */
    val SHIPPED = listOf(
        "packs/core_proto/v1/manifest.json",
        "packs/emoji_core/v1/manifest.json",
    )

    fun registry(read: (path: String) -> String): AssetRegistry =
        AssetRegistry(SHIPPED.map { AssetManifest.parse(read(it)) })
}
