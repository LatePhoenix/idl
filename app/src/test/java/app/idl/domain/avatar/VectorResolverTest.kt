package app.idl.domain.avatar

import app.idl.domain.Layer
import app.idl.domain.Mood
import app.idl.domain.PresenceResolver
import app.idl.domain.QuickState
import app.idl.widget.WidgetRenderInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VectorResolverTest {
    @Test fun `item ids are accepted and the wrong category or base is dropped`() {
        val registry = sandbox(extra = listOf(
            hair("hair_a"),
            hair("hair_b"),
            piece("eye_open_2", AssetCategory.FACE_EYE),
            hair("hair_other").copy(compatibleBases = listOf("base_other")),
            hair("hair_tiny", minSize = 400),
        ))
        val resolver = AvatarResolver(registry)
        val worn = resolver.resolve(request(box(mapOf("hair" to listOf("hair_b", "hair_a")))))
        assertTrue(worn.has("hair_a"))
        assertFalse(worn.has("hair_b"))
        assertEquals(DropReason.CONFLICT, worn.dropped.first { it.assetId == "hair_b" }.reason)

        val unknown = resolver.resolve(request(box(mapOf("not_a_category" to listOf("hair_a")))))
        assertFalse(unknown.has("hair_a"))
        assertEquals(DropReason.UNKNOWN_ASSET, unknown.dropped.first { it.assetId == "hair_a" }.reason)

        val wrong = resolver.resolve(request(box(mapOf("hair" to listOf("eye_open")))))
        assertEquals(DropReason.UNKNOWN_ASSET, wrong.dropped.first { it.assetId == "eye_open" }.reason)

        val unfit = resolver.resolve(request(box(mapOf("hair" to listOf("hair_other")))))
        assertFalse(unfit.has("hair_other"))
        assertEquals(DropReason.INCOMPATIBLE_BASE, unfit.dropped.first { it.assetId == "hair_other" }.reason)

        val small = resolver.resolve(request(box(mapOf("hair" to listOf("hair_tiny"))), sizePx = 32))
        assertEquals(DropReason.TARGET_SIMPLIFIED, small.dropped.first { it.assetId == "hair_tiny" }.reason)
    }

    @Test fun `a STATUS headset beats glasses_round_wire for a non-close friend`() {
        val registry = sandbox(extra = listOf(
            piece("head_vr_headset", AssetCategory.HEAD_ACCESSORY),
            piece("glasses_round_wire", AssetCategory.FACE_ACCESSORY, conflicts = listOf("head_vr_headset")),
        ))
        val saved = box(mapOf("face_accessory" to listOf("glasses_round_wire")))
        val resolved = AvatarResolver(registry).resolve(
            request(saved, VisiblePresence(headAccessoryAssetId = "head_vr_headset")),
        )
        assertTrue(resolved.has("head_vr_headset"))
        assertEquals(LayerPriority.STATUS, resolved.layers.first { it.assetId == "head_vr_headset" }.priority)
        assertFalse(resolved.has("glasses_round_wire"))
        val dropped = resolved.dropped.first { it.assetId == "glasses_round_wire" }
        assertEquals(DropReason.CONFLICT, dropped.reason)
        assertEquals("head_vr_headset", dropped.detail)
    }

    @Test fun `a hidden mood produces no eyes_round_happy`() {
        val eyes = hair("eyes_round_happy").copy(
            category = AssetCategory.FACE_EYE,
            render = AssetRender(type = "vector", file = "pictures/eyes_round_happy.json"),
            colorSlots = mapOf("eye.iris" to "#FF224466"),
        )
        val registry = sandbox(
            extra = listOf(eyes),
            expressions = listOf(
                ExpressionDef("neutral", "Neutral", eyes = "eye_open", mouth = "mouth_line"),
                ExpressionDef("happy", "Happy", eyes = "eyes_round_happy", mouth = "mouth_line"),
            ),
            semantics = mapOf("mood:happy" to SemanticMapping(expressionId = "happy")),
        )
        val resolver = AvatarResolver(registry)
        val shown = resolver.resolve(request(box(), VisiblePresence(mood = Mood.HAPPY)))
        assertTrue(shown.has("eyes_round_happy"))
        assertNullVariant(shown, "eyes_round_happy")
        val hidden = resolver.resolve(request(box(), VisiblePresence()))
        assertFalse(hidden.has("eyes_round_happy"))
    }

    @Test fun `the same request resolves the same layers colors and render key`() {
        val registry = sandbox(extra = listOf(hair("hair_a")))
        val resolver = AvatarResolver(registry)
        val once = resolver.resolve(request(colored()))
        val twice = resolver.resolve(request(colored()))
        assertEquals(once.layers, twice.layers)
        assertEquals(once.colorSlots, twice.colorSlots)
        assertEquals(once.renderKey, twice.renderKey)
    }

    @Test fun `the render key changes when a vector contentVersion changes`() {
        val low = AvatarResolver(sandbox(extra = listOf(hair("hair_a", version = 1))))
        val high = AvatarResolver(sandbox(extra = listOf(hair("hair_a", version = 2))))
        val request = request(box(mapOf("hair" to listOf("hair_a"))))
        assertNotEquals(low.resolve(request).renderKey, high.resolve(request).renderKey)
    }

    @Test fun `color slots follow override then derived then default then neutral`() {
        val registry = sandbox(extra = listOf(hair("hair_a")))
        val resolver = AvatarResolver(registry)
        val primary = 0xFF336699.toInt()
        val overridden = resolver.resolve(request(colored(mapOf(
            "hair.primary" to "#336699",
            "hair.shadow" to "#010203",
        ))))
        assertEquals(primary, overridden.colorSlots.getValue("hair.primary"))
        assertEquals(0xFF010203.toInt(), overridden.colorSlots.getValue("hair.shadow"))

        val derived = resolver.resolve(request(colored(mapOf("hair.primary" to "#336699"))))
        assertEquals(ColorSlots.mix(primary, 0xFF000000.toInt(), 0.25f), derived.colorSlots.getValue("hair.shadow"))
        assertEquals(ColorSlots.mix(primary, 0xFFFFFFFF.toInt(), 0.30f), derived.colorSlots.getValue("hair.highlight"))

        val invalid = resolver.resolve(request(colored(mapOf("hair.primary" to "nope"))))
        assertEquals(0xFF112233.toInt(), invalid.colorSlots.getValue("hair.primary"))

        val unlinked = resolver.resolve(request(colored(
            overrides = mapOf("hair.primary" to "#336699"),
            unlinked = listOf("hair.shadow"),
        )))
        assertEquals(0xFF001122.toInt(), unlinked.colorSlots.getValue("hair.shadow"))

        val blank = sandbox(extra = listOf(hair("hair_a").copy(colorSlots = mapOf("gem.primary" to "nope"))))
        val neutral = AvatarResolver(blank).resolve(request(box(mapOf("hair" to listOf("hair_a")))))
        assertEquals(ColorSlots.NEUTRAL, neutral.colorSlots.getValue("gem.primary"))
    }

    @Test fun `item transforms are kept only for assets that are drawn`() {
        val registry = sandbox(extra = listOf(hair("hair_a"), piece("glasses_a", AssetCategory.FACE_ACCESSORY)))
        val shift = ItemTransform(translateY = 8f)
        val resolved = AvatarResolver(registry).resolve(request(box(
            mapOf("hair" to listOf("hair_a")),
        ).copy(
            signatureFaceAccessoryAssetId = "glasses_a",
            itemTransforms = mapOf(
                "hair_a" to ItemTransform(translateX = 4f),
                "glasses_a" to shift,
                "missing" to ItemTransform(scale = 2f),
            ),
        )))
        // Hair has no transform allowance; eyewear keeps the clamped vertical nudge.
        assertEquals(mapOf("glasses_a" to shift), resolved.itemTransforms)
    }

    private fun assertNullVariant(resolved: ResolvedAvatar, assetId: String) {
        assertEquals(null, resolved.layers.first { it.assetId == assetId }.variant)
    }

    private fun hair(id: String, version: Int = 1, minSize: Int = 0) = piece(id, AssetCategory.HAIR, minSizePx = minSize).copy(
        render = AssetRender(type = "vector", file = "pictures/$id.json"),
        contentVersion = version,
        colorSlots = mapOf(
            "hair.primary" to "#FF112233",
            "hair.shadow" to "#FF001122",
            "hair.highlight" to "#FFEEEEEE",
        ),
    )

    private fun box(
        items: Map<String, List<String>> = emptyMap(),
    ) = AvatarConfiguration(
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
