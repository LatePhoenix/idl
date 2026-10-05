package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorRegistryTest {
    @Test fun `expression overrides merge in manifest order`() {
        val happy = ExpressionDef("happy", "Happy", eyes = "eye_open", mouth = "mouth_line")
        val first = manifest(
            expressions = listOf(happy),
            overrides = mapOf("happy" to mapOf("base_a" to ExpressionParts(eyes = "eye_open"))),
        )
        val second = manifest(
            packId = "emoji_core",
            overrides = mapOf("happy" to mapOf("base_round_face" to ExpressionParts(eyes = "eyes_round_happy"))),
            extra = listOf(piece("eyes_round_happy", AssetCategory.FACE_EYE)),
        )
        val registry = AssetRegistry(listOf(first, second))
        val parts = registry.expression("happy")!!.partsFor("base_round_face")
        assertEquals("eyes_round_happy", parts.eyes)
        assertEquals("eye_open", registry.expression("happy")!!.partsFor("base_a").eyes)
    }

    @Test fun `duplicate and unknown expression overrides are registry errors`() {
        val happy = ExpressionDef(
            "happy", "Happy", eyes = "eye_open", mouth = "mouth_line",
            baseOverrides = mapOf("base_a" to ExpressionParts(eyes = "eye_open")),
        )
        val duplicated = AssetRegistry(listOf(manifest(
            expressions = listOf(happy),
            overrides = mapOf("happy" to mapOf("base_a" to ExpressionParts(eyes = "eye_open"))),
        )))
        assertTrue(duplicated.validate().any { "overrides base_a twice" in it })

        val unknown = AssetRegistry(listOf(manifest(
            overrides = mapOf("missing" to mapOf("base_a" to ExpressionParts(eyes = "no_such_eye"))),
        )))
        val issues = unknown.validate()
        assertTrue(issues.any { "unknown expression" in it })
        assertTrue(issues.any { "unknown part" in it })
    }

    @Test fun `a second pack may not redefine an expression`() {
        val happy = ExpressionDef("happy", "Happy", eyes = "eye_open", mouth = "mouth_line")
        val registry = AssetRegistry(listOf(
            manifest(expressions = listOf(happy)),
            manifest(packId = "emoji_core", expressions = listOf(happy.copy(eyes = "eye_open"))),
        ))
        assertTrue(registry.validate().any { "expression happy is defined by more than one pack" in it })
    }

    @Test fun `baseFamilies maps a base and migrateRecipe stores the family`() {
        val round = AssetDef(
            "base_round_face", AssetCategory.BASE, "Round face",
            family = "round_face",
            anchors = AssetRegistry.REQUIRED_BASE_ANCHORS.associateWith { Anchor(0.5f, 0.5f) },
            faceSafeZone = NormalizedRect(0f, 0f, 1f, 1f),
        )
        val registry = sandbox(extra = listOf(round))
        assertEquals("round_face", registry.baseFamilies["base_round_face"])
        val migrated = AvatarConfiguration(
            schemaVersion = 2,
            baseAssetId = "base_round_face",
            paletteAssetId = "pal_a",
        ).migrateRecipe(registry.baseFamilies)
        assertEquals(3, migrated.schemaVersion)
        assertEquals("round_face", migrated.familyId)
    }

    @Test fun `a family on a non-base and disagreeing slot defaults fail validation`() {
        val registry = sandbox(extra = listOf(
            piece("hair_a", AssetCategory.HAIR).copy(
                render = AssetRender("vector", file = "pictures/hair_a.json"),
                colorSlots = mapOf("hair.primary" to "#FF111111"),
                family = "round_face",
            ),
            piece("hair_b", AssetCategory.HAIR).copy(
                render = AssetRender("vector", file = "pictures/hair_b.json"),
                colorSlots = mapOf("hair.primary" to "#FF222222"),
            ),
        ))
        val issues = registry.validate()
        assertTrue(issues.any { "not a base" in it })
        assertTrue(issues.any { "conflicting defaults" in it })
    }

    private fun manifest(
        packId: String = "sandbox",
        expressions: List<ExpressionDef> = listOf(ExpressionDef("neutral", "Neutral", eyes = "eye_open", mouth = "mouth_line")),
        overrides: Map<String, Map<String, ExpressionParts>> = emptyMap(),
        extra: List<AssetDef> = emptyList(),
    ): AssetManifest = sandbox(extra = extra, expressions = expressions, expressionOverrides = overrides).manifests.single().let {
        if (packId == it.packId) it else it.copy(packId = packId)
    }
}
