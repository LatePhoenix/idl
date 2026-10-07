package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSessionTest {
    private val hat = vector("hat", AssetCategory.HEAD_ACCESSORY, conflicts = listOf("glasses"))
    private val glasses = vector("glasses", AssetCategory.FACE_ACCESSORY, conflicts = listOf("hat"))
    private val hairA = vector("hair_a", AssetCategory.HAIR)
    private val hairB = vector("hair_b", AssetCategory.HAIR)
    private val hairEmpty = piece("hair_empty", AssetCategory.HAIR)
    private val hairGold = vector("hair_gold", AssetCategory.HAIR, tier = AssetTier.PREMIUM)
    private val top = vector("top_tee", AssetCategory.TOP)
    private val beard = vector("beard", AssetCategory.FACIAL_HAIR)
    private val stubble = vector("stubble", AssetCategory.FACIAL_HAIR, conflicts = listOf("beard"))

    private val registry = sandbox(
        extra = listOf(hat, glasses, hairA, hairB, hairEmpty, hairGold, top, beard, stubble),
        expressions = listOf(
            ExpressionDef("neutral", "Neutral", eyes = "eye_open", mouth = "mouth_line"),
            ExpressionDef("happy", "Happy", eyes = "eye_open", mouth = "mouth_line"),
        ),
    )

    private fun session() = EditorSession(
        AvatarConfiguration(baseAssetId = "base_a", paletteAssetId = "pal_a"),
        registry,
    )

    @Test fun `undo then redo restores the recipe and a new edit clears redo`() {
        val session = session()
        assertFalse(session.undo())
        assertTrue(session.wear("hair_a"))
        assertTrue(session.wear("top_tee"))
        assertTrue(session.undo())
        assertEquals(listOf("hair_a"), session.wornIds())
        assertTrue(session.redo())
        assertEquals(listOf("hair_a", "top_tee"), session.wornIds())
        assertTrue(session.wear("hair_b"))
        assertFalse(session.canRedo)
        assertEquals(listOf("hair_b", "top_tee"), session.wornIds())
    }

    @Test fun `wearing the same item again removes it and a conflict loses to the new choice`() {
        val session = session()
        assertTrue(session.wear("glasses"))
        assertTrue(session.wear("hat"))
        assertEquals(listOf("hat"), session.wornIds())
        assertTrue(session.wear("hat"))
        assertEquals(emptyList<String>(), session.wornIds())
    }

    @Test fun `an incompatible item and a bad color do not change history`() {
        val odd = vector("hair_odd", AssetCategory.HAIR, bases = listOf("base_other"))
        val session = EditorSession(
            AvatarConfiguration(baseAssetId = "base_a", paletteAssetId = "pal_a"),
            sandbox(extra = listOf(odd)),
        )
        assertFalse(session.wear("hair_odd"))
        assertFalse(session.wear("missing"))
        assertFalse(session.setColor("hair.primary", "blue"))
        assertFalse(session.canUndo)
        assertTrue(session.setColor("hair.primary", "#3a6ea5"))
        assertEquals("#3A6EA5", session.configuration.colorOverrides["hair.primary"])
    }

    @Test fun `procedural wardrobe stays out of the grid and randomize`() {
        val session = session()
        assertFalse(session.choices(AssetCategory.HAIR).any { it.id == "hair_empty" })
        assertTrue(session.choices(AssetCategory.HAIR).any { it.id == "hair_gold" })
        repeat(1000) { seed ->
            val one = session()
            assertTrue(one.randomize(seed.toLong()))
            val ids = one.wornIds()
            assertFalse("hair_empty" in ids)
            assertFalse("hair_gold" in ids)
            val worn = ids.map { registry.asset(it)!! }
            worn.forEachIndexed { index, asset ->
                worn.drop(index + 1).forEach { other ->
                    assertFalse(registry.conflicts(asset, other))
                }
            }
            if (AssetCategory.HAIR.multiple.not()) {
                assertTrue(ids.count { registry.asset(it)!!.category == AssetCategory.HAIR } <= 1)
            }
            assertTrue(ids.any { it == "top_tee" })
        }
        val left = session()
        val right = session()
        left.randomize(42)
        right.randomize(42)
        assertEquals(left.configuration, right.configuration)
    }

    @Test fun `a premium try-on is refused on save until it is owned`() {
        val session = session()
        assertTrue(session.wear("hair_gold"))
        val locked = LocalEntitlements(registry)
        val refused = session.configuration.prepareForWrite(registry.baseFamilies, registry, locked)
        assertTrue(refused is AvatarWrite.NeedsEntitlement)
        locked.grant("hair_gold")
        val ready = session.configuration.prepareForWrite(registry.baseFamilies, registry, locked)
        assertTrue(ready is AvatarWrite.Ready)
    }

    @Test fun `reset category and reset all clear what the user wore`() {
        val session = session()
        session.wear("hair_a")
        session.wear("beard")
        session.wear("stubble")
        assertEquals(listOf("hair_a", "stubble"), session.wornIds())
        assertTrue(session.clearCategory(AssetCategory.HAIR))
        assertEquals(listOf("stubble"), session.wornIds())
        assertTrue(session.resetAll())
        assertEquals(emptyList<String>(), session.wornIds().filter { it != "scene_a" && it != "frame_a" })
        assertTrue(session.undo())
        assertEquals(listOf("stubble"), session.wornIds())
    }

    private fun vector(
        id: String,
        category: AssetCategory,
        conflicts: List<String> = emptyList(),
        tier: AssetTier = AssetTier.FREE,
        bases: List<String> = emptyList(),
    ) = piece(id, category, conflicts = conflicts).copy(
        render = AssetRender(type = "vector"),
        tier = tier,
        compatibleBases = bases,
    )
}
