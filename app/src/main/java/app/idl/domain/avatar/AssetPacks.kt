package app.idl.domain.avatar

/**
 * Packs shipped inside the APK (decision D-26). This stays Android-free: the caller supplies
 * the file text, and the Android `AssetManager` adapter arrives with the renderer in Phase 2.
 */
object AssetPacks {
    /**
     * `core_proto` stays first so its defaults win. The pack version is part of the render key,
     * so moving `emoji_core` to v2 rebuilds caches once. Procedural pixels stay the same.
     */
    val SHIPPED = listOf(
        "packs/core_proto/v1/manifest.json",
        "packs/emoji_core/v2/manifest.json",
    )

    fun registry(read: (path: String) -> String): AssetRegistry {
        val loaded = SHIPPED.map { path ->
            path.substringBeforeLast("manifest.json") to AssetManifest.parse(read(path))
        }
        return AssetRegistry(loaded.map { it.second }, loaded.map { it.first })
    }
}
