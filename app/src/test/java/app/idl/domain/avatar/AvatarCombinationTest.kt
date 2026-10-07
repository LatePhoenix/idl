package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Seeded combination property test (AP-8 / §4). Two thousand recipes must resolve without
 * throwing, and the same seed must always produce the same render key.
 */
class AvatarCombinationTest {
    private val registry = coreRegistry()
    private val resolver = AvatarResolver(registry)

    @Test fun `two thousand seeded recipes resolve deterministically`() {
        val hairs = registry.ofCategory(AssetCategory.HAIR).map { it.id }
        val faces = registry.ofCategory(AssetCategory.FACE_ACCESSORY).map { it.id }
        val heads = registry.ofCategory(AssetCategory.HEAD_ACCESSORY).map { it.id }
        val facial = registry.ofCategory(AssetCategory.FACIAL_HAIR).map { it.id }
        val tops = registry.ofCategory(AssetCategory.TOP).map { it.id }
        require(hairs.isNotEmpty() && faces.isNotEmpty() && heads.isNotEmpty())

        val keys = ArrayList<String>(2000)
        val random = Random(seed = 8)
        repeat(2000) { index ->
            val seed = random.nextLong()
            val pick = Random(seed)
            val configuration = config(base = "base_teardrop").copy(
                signatureFaceAccessoryAssetId = faces[pick.nextInt(faces.size)],
                signatureHeadAccessoryAssetId = heads[pick.nextInt(heads.size)].takeIf { pick.nextBoolean() },
                itemIds = buildMap {
                    if (hairs.isNotEmpty() && pick.nextBoolean()) put("hair", listOf(hairs[pick.nextInt(hairs.size)]))
                    if (facial.isNotEmpty() && pick.nextBoolean()) {
                        put("facial_hair", listOf(facial[pick.nextInt(facial.size)]))
                    }
                    if (tops.isNotEmpty() && pick.nextBoolean()) put("top", listOf(tops[pick.nextInt(tops.size)]))
                },
                randomSeed = seed,
            )
            val first = resolver.resolve(request(configuration, target = RenderTarget.STANDARD_WIDGET))
            val second = resolver.resolve(request(configuration, target = RenderTarget.STANDARD_WIDGET))
            assertEquals("seed $seed at $index", first.renderKey, second.renderKey)
            keys += first.renderKey
        }
        assertTrue(keys.size == 2000)
        assertTrue(keys.toSet().size > 100)
    }
}
