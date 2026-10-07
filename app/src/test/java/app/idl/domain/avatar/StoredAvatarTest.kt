package app.idl.domain.avatar

import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarPalette
import app.idl.domain.BaseForm
import app.idl.domain.Expression
import app.idl.domain.FaceStyle
import app.idl.domain.IdlJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredAvatarTest {
    private val registry = coreRegistry()

    @Test fun `each v1 base rewrites to a schema 3 teardrop`() {
        BaseForm.entries.forEach { form ->
            val json = IdlJson.encodeToString(AvatarConfig.serializer(), AvatarConfig(baseForm = form, faceStyle = FaceStyle.BLUSHY))
            val recipe = StoredAvatar.read(StoredAvatar.rewrite(json, registry), registry)!!
            assertEquals(form.name, "base_teardrop", recipe.baseAssetId)
            assertEquals(form.name, 3, recipe.schemaVersion)
            assertEquals(form.name, listOf("feature_blush"), recipe.signatureFeatureAssetIds)
            assertTrue(form.name, recipe.signatureFeatureAssetIds.none { it.startsWith("feature_ears_") || it == "feature_muzzle" || it == "feature_antennae" })
        }
    }

    @Test fun `a pixel avatar keeps the pixel eyes and frame`() {
        val json = IdlJson.encodeToString(AvatarConfig.serializer(), AvatarConfig(baseForm = BaseForm.PIXEL))
        val recipe = StoredAvatar.read(json, registry)!!
        assertEquals("base_teardrop", recipe.baseAssetId)
        assertEquals("eyefam_pixel", recipe.eyeFamilyAssetId)
        assertEquals("frame_pixel", recipe.defaultFrameAssetId)
    }

    @Test fun `schema 2 and 3 bases become the teardrop and hair stays`() {
        val schema2 = """{"baseAssetId":"base_orb","paletteAssetId":"palette_sunny","schemaVersion":2}"""
        assertEquals("base_teardrop", StoredAvatar.read(schema2, registry)!!.baseAssetId)

        val schema3 = AvatarConfiguration(
            baseAssetId = "base_critter",
            paletteAssetId = "palette_sunny",
            signatureFeatureAssetIds = listOf("feature_ears_cat", "feature_blush"),
            itemIds = mapOf("hair" to listOf("hair_bob")),
        )
        val json = IdlJson.encodeToString(AvatarConfiguration.serializer(), schema3)
        val recipe = StoredAvatar.read(StoredAvatar.rewrite(json, registry), registry)!!
        assertEquals("base_teardrop", recipe.baseAssetId)
        assertEquals(listOf("hair_bob"), recipe.itemIds["hair"])
        assertEquals(listOf("feature_blush"), recipe.signatureFeatureAssetIds)
    }

    @Test fun `a newer schema is not rewritten`() {
        val json = """{"baseAssetId":"base_blob","paletteAssetId":"palette_sunny","schemaVersion":4,"futureKnob":true}"""
        assertEquals(json, StoredAvatar.rewrite(json, registry))
        assertNull(StoredAvatar.read(json, registry))
        assertTrue(AvatarConfiguration.decode(json).prepareForWrite() is AvatarWrite.NeedsAppUpdate)
    }

    @Test fun `studio edits keep hair and a pixel frame`() {
        val saved = AvatarConfiguration(
            baseAssetId = "base_teardrop",
            paletteAssetId = "palette_sunny",
            eyeFamilyAssetId = "eyefam_pixel",
            defaultFrameAssetId = "frame_pixel",
            restingExpressionId = "neutral_face",
            itemIds = mapOf("hair" to listOf("hair_bob"), "top" to listOf("top_crew_tee")),
        )
        val studio = StoredAvatar.toStudioConfig(saved, registry)
        val edited = studio.copy(bodyColor = AvatarPalette.body[4], expression = Expression.HAPPY)
        val next = StoredAvatar.applyStudio(saved, edited, registry)
        assertEquals("base_teardrop", next.baseAssetId)
        assertEquals(listOf("hair_bob"), next.itemIds["hair"])
        assertEquals(listOf("top_crew_tee"), next.itemIds["top"])
        assertEquals("frame_pixel", next.defaultFrameAssetId)
        assertEquals("eyefam_pixel", next.eyeFamilyAssetId)
        assertEquals("happy", next.restingExpressionId)
        assertFalse(next.paletteAssetId == saved.paletteAssetId && edited.bodyColor == studio.bodyColor)
        assertEquals("palette_bubblegum", next.paletteAssetId)
    }
}
