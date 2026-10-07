package app.idl.domain.avatar

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-48 free baseline. Fails the build when a change paywalls core vocabulary or drops the
 * minimum free inventory (AP-10 §3.8).
 */
class D48GuardTest {
    private val registry = coreRegistry()

    private fun free(category: AssetCategory) =
        registry.ofCategory(category).filter { it.tier == AssetTier.FREE }

    @Test fun `every expression stays free forever`() {
        // Expressions are catalog rows, not priced assets. Their face parts must stay free.
        registry.allExpressions.forEach { expr ->
            val parts = listOfNotNull(expr.eyes, expr.brows, expr.mouth) + expr.overlays + expr.extras
            parts.forEach { id ->
                val asset = registry.asset(id)
                assertTrue("expression part $id missing", asset != null)
                assertTrue("expression part $id must be FREE", asset!!.tier == AssetTier.FREE)
            }
        }
    }

    @Test fun `every palette and color swatch is free`() {
        free(AssetCategory.PALETTE).let { palettes ->
            assertTrue("expected free palettes, got ${palettes.size}", palettes.isNotEmpty())
            assertTrue(palettes.all { it.tier == AssetTier.FREE && it.price == null })
        }
        val swatches = registry.manifests.flatMap { it.defaults?.palettes?.values.orEmpty().flatten() }
        assertTrue("swatches must exist for D-48 colors", swatches.isNotEmpty())
        swatches.forEach { swatch ->
            assertTrue(swatch.id.isNotBlank())
            assertTrue(swatch.hex.startsWith("#"))
        }
        // No palette asset may be premium.
        registry.ofCategory(AssetCategory.PALETTE).forEach {
            assertTrue("${it.id} palette must be FREE", it.tier == AssetTier.FREE)
        }
    }

    @Test fun `at least twelve free hairstyles cover the D-48 textures`() {
        val hair = free(AssetCategory.HAIR)
        assertTrue("need ≥12 free hairstyles, have ${hair.size}", hair.size >= 12)
        val tags = hair.flatMap { it.tags }.toSet()
        val required = listOf("straight", "wavy", "curly", "coily", "locs", "braids", "short", "long", "bald")
        required.forEach { tag ->
            assertTrue("missing free hair texture tag '$tag' in $tags", tag in tags)
        }
    }

    @Test fun `each core category has at least one free item`() {
        assertTrue(free(AssetCategory.FACIAL_HAIR).isNotEmpty())
        assertTrue(free(AssetCategory.FACE_ACCESSORY).isNotEmpty()) // eyewear
        assertTrue(free(AssetCategory.TOP).isNotEmpty())
        assertTrue(free(AssetCategory.SCENE).isNotEmpty()) // backgrounds
        assertTrue(free(AssetCategory.FRAME).isNotEmpty())
    }

    @Test fun `core semantic props stay free`() {
        val required = listOf(
            "prop_coffee",
            "prop_controller",
            "head_vr_headset",
            "body_blanket",
            "prop_book",
            "prop_phone",
        )
        required.forEach { id ->
            val asset = registry.asset(id)
            assertTrue("$id missing", asset != null)
            assertTrue("$id must be FREE", asset!!.tier == AssetTier.FREE)
        }
    }

    @Test fun `sample premium items exist to exercise the store path`() {
        val premium = registry.manifests.flatMap { it.assets }.filter { it.tier == AssetTier.PREMIUM }
        assertTrue("expected sample PREMIUM items, got none", premium.isNotEmpty())
        premium.forEach {
            assertTrue("${it.id} premium needs a Charge price", (it.price ?: 0) > 0)
        }
    }
}
