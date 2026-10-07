package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ColorSlotsAp9Test {
    @Test fun `shadow and highlight derive in OKLCH from an overridden primary`() {
        val registry = sandbox(extra = listOf(hair("hair_a")))
        val primary = 0xFF336699.toInt()
        val slots = AvatarResolver(registry).resolve(request(colored(mapOf("hair.primary" to "#336699")))).colorSlots
        assertEquals(primary, slots.getValue("hair.primary"))
        assertEquals(Oklch.deriveShadow(primary), slots.getValue("hair.shadow"))
        assertEquals(Oklch.deriveHighlight(primary), slots.getValue("hair.highlight"))
        assertNotEquals(ColorSlots.mix(primary, 0xFF000000.toInt(), 0.25f), slots.getValue("hair.shadow"))
    }

    @Test fun `unlinked shadow keeps the asset default`() {
        val slots = AvatarResolver(sandbox(extra = listOf(hair("hair_a")))).resolve(
            request(colored(mapOf("hair.primary" to "#336699"), unlinked = listOf("hair.shadow"))),
        ).colorSlots
        assertEquals(0xFF001122.toInt(), slots.getValue("hair.shadow"))
    }

    @Test fun `slot links copy hair primary onto beard unless overridden or unlinked`() {
        val registry = sandbox(
            extra = listOf(hair("hair_a"), beard("beard_a")),
            defaults = PackDefaults(
                "base_a", "pal_a", "scene_a", "frame_a", "eye_a", "mouth_a",
                slotLinks = mapOf("beard.primary" to "hair.primary"),
            ),
        )
        val linked = AvatarResolver(registry).resolve(request(box(
            mapOf("hair" to listOf("hair_a"), "facial_hair" to listOf("beard_a")),
        ).copy(colorOverrides = mapOf("hair.primary" to "#336699")))).colorSlots
        assertEquals(0xFF336699.toInt(), linked.getValue("beard.primary"))

        val overridden = AvatarResolver(registry).resolve(request(box(
            mapOf("hair" to listOf("hair_a"), "facial_hair" to listOf("beard_a")),
        ).copy(colorOverrides = mapOf(
            "hair.primary" to "#336699",
            "beard.primary" to "#AA5500",
        )))).colorSlots
        assertEquals(0xFFAA5500.toInt(), overridden.getValue("beard.primary"))

        val unlinked = AvatarResolver(registry).resolve(request(box(
            mapOf("hair" to listOf("hair_a"), "facial_hair" to listOf("beard_a")),
        ).copy(
            colorOverrides = mapOf("hair.primary" to "#336699"),
            unlinkedSlots = listOf("beard.primary"),
        ))).colorSlots
        assertEquals(0xFF5B3A29.toInt(), unlinked.getValue("beard.primary"))
    }

    private fun hair(id: String) = piece(id, AssetCategory.HAIR).copy(
        render = AssetRender(type = "vector", file = "pictures/$id.json"),
        colorSlots = mapOf(
            "hair.primary" to "#FF112233",
            "hair.shadow" to "#FF001122",
            "hair.highlight" to "#FFEEEEEE",
        ),
    )

    private fun beard(id: String) = piece(id, AssetCategory.FACIAL_HAIR).copy(
        render = AssetRender(type = "vector", file = "pictures/$id.json"),
        colorSlots = mapOf("beard.primary" to "#5B3A29"),
    )

    private fun box(items: Map<String, List<String>> = emptyMap()) = AvatarConfiguration(
        baseAssetId = "base_a",
        paletteAssetId = "pal_a",
        eyeFamilyAssetId = "eye_a",
        mouthFamilyAssetId = "mouth_a",
        defaultSceneAssetId = "scene_a",
        defaultFrameAssetId = "frame_a",
        itemIds = items,
    )

    private fun colored(
        overrides: Map<String, String> = emptyMap(),
        unlinked: List<String> = emptyList(),
    ) = box(mapOf("hair" to listOf("hair_a"))).copy(colorOverrides = overrides, unlinkedSlots = unlinked)
}
