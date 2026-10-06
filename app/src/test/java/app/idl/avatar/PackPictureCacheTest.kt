package app.idl.avatar

import app.idl.domain.avatar.AssetCategory
import app.idl.domain.avatar.AssetDef
import app.idl.domain.avatar.AssetManifest
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.AssetRender
import app.idl.domain.avatar.PackDefaults
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PackPictureCacheTest {
    @Test fun `two packs that share a picture file each load their own`() {
        val shared = "pictures/shared.json"
        val left = mark("pack_a_mark")
        val right = mark("pack_b_mark")
        val registry = AssetRegistry(
            manifests = listOf(
                AssetManifest(
                    packId = "pack_a",
                    version = 1,
                    defaults = PackDefaults(
                        base = "base",
                        palette = "palette",
                        scene = "scene",
                        frame = "frame",
                        eyeFamily = "eyes",
                        mouthFamily = "mouth",
                    ),
                    assets = listOf(left),
                ),
                AssetManifest(packId = "pack_b", version = 1, assets = listOf(right)),
            ),
            packDirectories = listOf("packs/pack_a/v1/", "packs/pack_b/v1/"),
        )
        val reads = mutableListOf<String>()
        val cache = VectorPictureCache(
            packDirectory = { asset -> registry.packDirectory(asset.id) },
        ) { path ->
            reads += path
            val id = when (path) {
                "packs/pack_a/v1/$shared" -> "pack_a_mark"
                "packs/pack_b/v1/$shared" -> "pack_b_mark"
                else -> error("unexpected path $path")
            }
            picture(id)
        }

        assertEquals("pack_a_mark", cache.get(registry.asset("pack_a_mark")!!)!!.picture.id)
        assertEquals("pack_b_mark", cache.get(registry.asset("pack_b_mark")!!)!!.picture.id)
        assertEquals(listOf("packs/pack_a/v1/$shared", "packs/pack_b/v1/$shared"), reads)
    }

    private fun mark(id: String) = AssetDef(
        id = id,
        category = AssetCategory.HAIR,
        accessibilityLabel = id,
        render = AssetRender(type = "vector", file = "pictures/shared.json"),
        contentVersion = 1,
        colorSlots = mapOf("accessory.primary" to "#FF112233"),
    )

    private fun picture(id: String) = """
        {"schemaVersion":1,"id":"$id","contentVersion":1,"viewBox":1024,
         "parts":[{"id":"dot","zBand":80,"fill":{"slot":"accessory.primary"},"commands":"M 480 480 L 544 480 L 544 544 L 480 544 Z"}]}
    """.trimIndent()
}
