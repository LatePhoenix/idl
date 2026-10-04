package app.idl.domain.avatar

import app.idl.domain.IdlJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarConfigurationSchemaTest {

    @Test fun `schema 3 configuration round-trips through json`() {
        val config = sample()
        val json = IdlJson.encodeToString(AvatarConfiguration.serializer(), config)
        val back = IdlJson.decodeFromString(AvatarConfiguration.serializer(), json)
        assertEquals(config, back)
    }

    @Test fun `schema 2 json keeps its ids and migrates without inventing a family`() {
        val json = """
            {"baseAssetId":"base_blob","paletteAssetId":"palette_sunny","restingExpressionId":"happy","schemaVersion":2}
        """.trimIndent()
        val decoded = IdlJson.decodeFromString(AvatarConfiguration.serializer(), json)
        assertEquals(2, decoded.schemaVersion)
        assertEquals("base_blob", decoded.baseAssetId)
        assertEquals("happy", decoded.restingExpressionId)
        assertEquals("", decoded.familyId)
        assertTrue(decoded.itemIds.isEmpty())
        assertNull(decoded.randomSeed)

        val migrated = decoded.migrateRecipe(mapOf("base_round" to "round_face"))
        assertEquals(3, migrated.schemaVersion)
        assertEquals("base_blob", migrated.baseAssetId)
        assertEquals("happy", migrated.restingExpressionId)
        assertEquals("", migrated.familyId)
        assertEquals(AvatarConfiguration.DEFAULT_PACK_ID, migrated.packId)
        assertEquals(1, migrated.packVersion)
    }

    @Test fun `an explicit family map is the only way a base gains a family`() {
        val decoded = AvatarConfiguration(
            baseAssetId = "base_round",
            paletteAssetId = "palette_sunny",
            schemaVersion = 2,
        )
        assertEquals("", decoded.migrateRecipe().familyId)
        assertEquals("round_face", decoded.migrateRecipe(mapOf("base_round" to "round_face")).familyId)
    }

    @Test fun `a newer schema version is not rewritten`() {
        val json = """
            {"baseAssetId":"base_blob","paletteAssetId":"palette_sunny","schemaVersion":4,"futureKnob":true}
        """.trimIndent()
        val decoded = IdlJson.decodeFromString(AvatarConfiguration.serializer(), json)
        val migrated = decoded.migrateRecipe(mapOf("base_blob" to "round_face"))
        assertEquals(4, migrated.schemaVersion)
        assertEquals("base_blob", migrated.baseAssetId)
        assertEquals("", migrated.familyId)
    }

    @Test fun `migrating twice keeps the saved recipe`() {
        val once = sample().copy(schemaVersion = 2, familyId = "").migrateRecipe(mapOf("base_round" to "round_face"))
        val twice = once.migrateRecipe(mapOf("base_round" to "round_face"))
        assertEquals(once, twice)
    }

    @Test fun `schema 2 json loaded through decode comes out as schema 3`() {
        val json = """
            {"baseAssetId":"base_blob","paletteAssetId":"palette_sunny","restingExpressionId":"happy","schemaVersion":2}
        """.trimIndent()
        val loaded = AvatarConfiguration.decode(json)
        assertEquals(3, loaded.schemaVersion)
        assertEquals("base_blob", loaded.baseAssetId)
        assertEquals("happy", loaded.restingExpressionId)
        assertEquals("", loaded.familyId)
        assertEquals(AvatarConfiguration.DEFAULT_PACK_ID, loaded.packId)
    }

    @Test fun `item ids in one category are an unordered set`() {
        val resolver = AvatarResolver(coreRegistry())
        val forward = config().copy(itemIds = mapOf("jewelry" to listOf("pin_a", "pin_b")))
        val reverse = config().copy(itemIds = mapOf("jewelry" to listOf("pin_b", "pin_a")))
        assertEquals(
            resolver.resolve(AvatarRenderRequest(forward)).renderKey,
            resolver.resolve(AvatarRenderRequest(reverse)).renderKey,
        )
    }

    @Test fun `a newer schema is not written back`() {
        val newer = sample().copy(schemaVersion = 4)
        val blocked = newer.prepareForWrite()
        assertTrue(blocked is AvatarWrite.NeedsAppUpdate)
        assertEquals(AvatarWrite.NeedsAppUpdate.message, (blocked as AvatarWrite.NeedsAppUpdate).message)
        val saved = sample().copy(schemaVersion = 2, familyId = "").prepareForWrite()
        assertTrue(saved is AvatarWrite.Ready)
        assertEquals(3, (saved as AvatarWrite.Ready).configuration.schemaVersion)
    }

    @Test fun `color override key order does not change the render key`() {
        val resolver = AvatarResolver(coreRegistry())
        val first = config().copy(
            colorOverrides = linkedMapOf("hair.primary" to "#111111", "face.primary" to "#222222"),
            itemIds = linkedMapOf("hair" to listOf("hair_bob"), "hat" to listOf("hat_beanie")),
        )
        val second = config().copy(
            colorOverrides = linkedMapOf("face.primary" to "#222222", "hair.primary" to "#111111"),
            itemIds = linkedMapOf("hat" to listOf("hat_beanie"), "hair" to listOf("hair_bob")),
        )
        assertEquals(
            resolver.resolve(AvatarRenderRequest(first)).renderKey,
            resolver.resolve(AvatarRenderRequest(second)).renderKey,
        )
    }

    private fun sample() = AvatarConfiguration(
        baseAssetId = "base_round",
        paletteAssetId = "palette_sunny",
        packId = "core_proto",
        packVersion = 1,
        familyId = "round_face",
        itemIds = mapOf("hair" to listOf("hair_bob")),
        colorOverrides = mapOf("hair.primary" to "#112233"),
        unlinkedSlots = listOf("hair.shadow"),
        itemTransforms = mapOf("hair_bob" to ItemTransform(translateX = 4f, scale = 1.5f, rotationDeg = 90f)),
        background = AvatarBackground(mode = BackgroundMode.SOLID, color = "#010203"),
        randomSeed = 42L,
        schemaVersion = 3,
    )
}
