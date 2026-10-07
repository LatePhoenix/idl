package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityEngineTest {
    private val registry = coreRegistry()
    private val engine = CompatibilityEngine(registry)

    @Test fun `sanitize is idempotent once the configuration is clean`() {
        val dirty = config(
            base = "base_critter",
            eyes = "eyefam_visor",
            features = listOf("feature_ears_cat"),
            head = "head_helmet",
            prop = "not_a_prop",
            resting = "not_an_expression",
        )
        val (once, issues) = engine.sanitize(dirty)
        assertEquals("base_teardrop", once.baseAssetId)
        assertEquals("eyefam_round", once.eyeFamilyAssetId)
        assertEquals(emptyList<String>(), once.signatureFeatureAssetIds)
        assertEquals("head_helmet", once.signatureHeadAccessoryAssetId)
        assertNull(once.defaultPropAssetId)
        assertEquals("neutral", once.restingExpressionId)
        assertTrue(issues.any { it.assetId == "feature_ears_cat" && it.problem.startsWith("incompatible") })
        assertTrue(engine.validate(once).isEmpty())

        val twice = engine.sanitize(once)
        assertEquals(once, twice.first)
        assertTrue(twice.second.isEmpty())
        assertEquals(twice, engine.sanitize(twice.first))
    }

    @Test fun `a retired critter drops species marks and keeps the helmet`() {
        assertFalseListedOnlyOnTheEars()
        val (cleaned, issues) = engine.sanitize(config(
            base = "base_critter",
            features = listOf("feature_ears_fox", "feature_muzzle"),
            head = "head_helmet",
        ))
        assertEquals("base_teardrop", cleaned.baseAssetId)
        assertEquals(emptyList<String>(), cleaned.signatureFeatureAssetIds)
        assertEquals("head_helmet", cleaned.signatureHeadAccessoryAssetId)
        assertTrue(issues.any { it.assetId == "feature_ears_fox" && it.problem.startsWith("incompatible") })
    }

    @Test fun `an incompatible eye family is replaced by its fallback`() {
        val (cleaned, _) = engine.sanitize(config(base = "base_ghost", eyes = "eyefam_visor", mouth = "mouthfam_screen"))
        assertEquals("eyefam_round", cleaned.eyeFamilyAssetId)
        assertEquals("mouthfam_classic", cleaned.mouthFamilyAssetId)
        assertTrue(engine.validate(cleaned).isEmpty())
    }

    @Test fun `unknown base and palette fall back to pack defaults`() {
        val (cleaned, issues) = engine.sanitize(config().copy(baseAssetId = "base_missing", paletteAssetId = "palette_missing"))
        assertEquals(registry.canonicalId(registry.defaults.base), cleaned.baseAssetId)
        assertEquals(registry.defaults.palette, cleaned.paletteAssetId)
        assertTrue(issues.any { it.assetId == "base_missing" && it.problem == "unknown" })
        assertTrue(engine.validate(cleaned).isEmpty())
    }

    private fun assertFalseListedOnlyOnTheEars() {
        org.junit.Assert.assertFalse(registry.asset("head_helmet")!!.conflictsWith.contains("feature_ears_fox"))
        org.junit.Assert.assertTrue(registry.asset("feature_ears_fox")!!.conflictsWith.contains("head_helmet"))
    }
}
