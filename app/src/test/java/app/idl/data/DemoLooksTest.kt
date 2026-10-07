package app.idl.data

import app.idl.data.remote.DemoData
import app.idl.domain.avatar.AssetCategory
import app.idl.domain.avatar.coreRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DemoLooksTest {
    @Test fun `demo friends are distinct teardrops`() {
        val registry = coreRegistry()
        val recipes = DemoData.seeds(Instant.parse("2026-10-06T12:00:00Z")).map { it.user.recipe }
        assertEquals(8, recipes.size)
        recipes.forEach { recipe ->
            assertEquals("base_teardrop", recipe.baseAssetId)
            assertTrue(registry.asset(recipe.paletteAssetId)!!.category == AssetCategory.PALETTE)
            val hair = recipe.itemIds["hair"].orEmpty()
            assertEquals(1, hair.size)
            assertEquals(AssetCategory.HAIR, registry.asset(hair.single())!!.category)
            recipe.itemIds["top"].orEmpty().forEach { id ->
                assertEquals(AssetCategory.TOP, registry.asset(id)!!.category)
            }
        }
        val tuples = recipes.map { Triple(it.paletteAssetId, it.itemIds["hair"], it.itemIds["top"]) }
        assertEquals(tuples.size, tuples.toSet().size)
    }
}
