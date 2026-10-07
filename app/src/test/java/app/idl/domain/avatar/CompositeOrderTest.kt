package app.idl.domain.avatar

import app.idl.domain.AvatarConfig
import app.idl.domain.BaseForm
import app.idl.domain.BodyAccessory
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.Layer
import app.idl.domain.PresenceResolver
import app.idl.domain.QuickState
import app.idl.widget.WidgetRenderInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CompositeOrderTest {
    @Test fun `procedural ops match today's layer order for every quick state`() {
        val registry = coreRegistry()
        val resolver = AvatarResolver(registry)
        val now = Instant.parse("2026-10-04T15:00:00Z")
        for (quick in QuickState.ALL) {
            val presence = LegacyAvatarMigration.presence(PresenceResolver.resolve(listOf(quick.toState(now)), now))
            val resolved = resolver.resolve(request(config(), presence, target = RenderTarget.STANDARD_WIDGET, sizePx = RenderTarget.STANDARD_WIDGET.defaultSizePx))
            val frame = PlaceholderFrames.from(resolved, registry)
            val stub = VectorPicture(
                schemaVersion = 1,
                id = "stub",
                contentVersion = 1,
                viewBox = 1024,
                parts = listOf(VectorPart("face", 40, VectorFill(slot = "face.primary"), "M 0 0")),
            )
            val ops = CompositeOrder.ops(resolved, registry) { id ->
                if (registry.asset(id)?.render?.type == "vector") stub.copy(id = id) else null
            }
            val vectorCategories = resolved.layers.mapNotNull { layer ->
                val asset = registry.asset(layer.assetId)
                if (asset?.render?.type == "vector") asset.category else null
            }.toSet()
            fun Layer.drawnAsVector() = when (this) {
                Layer.HEAD_BASE -> AssetCategory.BASE in vectorCategories
                Layer.EYES -> AssetCategory.FACE_EYE in vectorCategories
                Layer.BROWS -> AssetCategory.FACE_BROW in vectorCategories
                Layer.MOUTH -> AssetCategory.FACE_MOUTH in vectorCategories
                Layer.FACE_EXTRA -> AssetCategory.EXPRESSION_OVERLAY in vectorCategories
                else -> false
            }
            assertEquals(quick.id, frame.layers.filter { it != Layer.FACE_STYLE && !it.drawnAsVector() }, ops.mapNotNull { it.toLayer() })
            assertTrue(quick.id, ops.any { it is DrawOp.VectorPart && registry.asset(it.assetId)?.category == AssetCategory.BASE })
            assertTrue(quick.id, ops.dropWhile { !it.chrome }.all { it.chrome })
        }
    }

    @Test fun `procedural ops match today's layer order for worn items and face styles`() {
        val registry = coreRegistry()
        val resolver = AvatarResolver(registry)
        val saved = listOf(
            AvatarConfig(
                baseForm = BaseForm.CAT, faceStyle = FaceStyle.FRECKLES, headAccessory = HeadAccessory.BEANIE,
                faceAccessory = FaceAccessory.GLASSES, bodyAccessory = BodyAccessory.HOODIE,
            ),
            AvatarConfig(
                baseForm = BaseForm.FOX, faceStyle = FaceStyle.BLUSHY, headAccessory = HeadAccessory.CROWN,
                faceAccessory = FaceAccessory.SUNGLASSES, bodyAccessory = BodyAccessory.BLANKET,
            ),
            AvatarConfig(baseForm = BaseForm.ALIEN, headAccessory = HeadAccessory.HEADPHONES),
        )
        var sawFaceStyle = false
        for (v1 in saved) for (target in listOf(RenderTarget.STANDARD_WIDGET, RenderTarget.PROFILE)) {
            val label = "${v1.baseForm} at $target"
            val configuration = LegacyAvatarMigration.migrate(v1, registry)
            val resolved = resolver.resolve(request(configuration, target = target, sizePx = target.defaultSizePx))
            val frame = PlaceholderFrames.from(resolved, registry)
            val stub = VectorPicture(
                schemaVersion = 1,
                id = "stub",
                contentVersion = 1,
                viewBox = 1024,
                parts = listOf(VectorPart("face", 40, VectorFill(slot = "face.primary"), "M 0 0")),
            )
            val ops = CompositeOrder.ops(resolved, registry) { id ->
                if (registry.asset(id)?.render?.type == "vector") stub.copy(id = id) else null
            }
            val vectorFace = setOf(Layer.HEAD_BASE, Layer.EYES, Layer.BROWS, Layer.MOUTH)
            assertEquals(label, frame.layers.filter { it != Layer.FACE_STYLE && it !in vectorFace }, ops.mapNotNull { it.toLayer() })
            if (Layer.FACE_STYLE in frame.layers) {
                sawFaceStyle = true
                val featureAt = ops.indexOfFirst { it is DrawOp.Procedural && it.category == AssetCategory.SIGNATURE_FEATURE }
                val baseAt = ops.indexOfLast { it is DrawOp.VectorPart && registry.asset(it.assetId)?.category == AssetCategory.BASE }
                assertTrue(label, baseAt >= 0 && featureAt > baseAt)
            }
        }
        assertTrue("no case kept a face style", sawFaceStyle)
    }

    @Test fun `hair back is before the base and bangs are after the eyes`() {
        val registry = sandbox(extra = listOf(
            hair(),
            piece("avail_free", AssetCategory.AVAILABILITY_INDICATOR, glyph = true),
        ))
        val resolved = ResolvedAvatar(
            baseAssetId = "base_a",
            paletteAssetId = "pal_a",
            expressionId = "neutral",
            layers = listOf(
                ResolvedLayer("hair_round_bob", AssetCategory.HAIR, 25, LayerPriority.SIGNATURE),
                ResolvedLayer("base_a", AssetCategory.BASE, 20, LayerPriority.BASE),
                ResolvedLayer("eye_open", AssetCategory.FACE_EYE, 50, LayerPriority.FACE),
                ResolvedLayer("scene_a", AssetCategory.SCENE, 10, LayerPriority.SCENE),
                ResolvedLayer("avail_free", AssetCategory.AVAILABILITY_INDICATOR, 140, LayerPriority.AVAILABILITY),
            ),
            dropped = emptyList(),
            sceneDetail = false,
            renderKey = "test",
            accessibilityDescription = "",
        )
        val picture = VectorPicture(
            schemaVersion = 1,
            id = "hair_round_bob",
            contentVersion = 1,
            viewBox = 1024,
            parts = listOf(
                VectorPart("back", 20, VectorFill(slot = "hair.primary"), "M 0 0"),
                VectorPart("bangs", 70, VectorFill(slot = "hair.primary"), "M 0 0"),
            ),
        )
        val ops = CompositeOrder.ops(resolved, registry) { id -> if (id == "hair_round_bob") picture else null }
        val marks = ops.map { op ->
            when (op) {
                is DrawOp.Procedural -> op.category.name
                is DrawOp.VectorPart -> "${op.assetId}:${op.partIndex}"
            }
        }
        assertEquals(
            listOf("SCENE", "hair_round_bob:0", "BASE", "FACE_EYE", "hair_round_bob:1", "AVAILABILITY_INDICATOR"),
            marks,
        )
        assertTrue(ops.last().chrome)
        assertTrue(ops.dropLast(1).none { it.chrome })
        assertTrue(ops.none { it is DrawOp.Procedural && it.category == AssetCategory.HAIR })
    }

    private fun hair() = piece("hair_round_bob", AssetCategory.HAIR).copy(
        render = AssetRender(type = "vector", file = "pictures/hair_round_bob.json"),
        colorSlots = mapOf("hair.primary" to "#FF112233"),
    )

    private fun piece(id: String, category: AssetCategory, glyph: Boolean = false) =
        app.idl.domain.avatar.piece(id, category).copy(glyph = if (glyph) "dot" else null)

    private fun DrawOp.toLayer(): Layer? = when (this) {
        is DrawOp.Procedural -> when (category) {
            AssetCategory.SCENE -> Layer.SCENE
            AssetCategory.BODY_ACCESSORY -> Layer.BODY_ACCESSORY
            AssetCategory.BASE -> Layer.HEAD_BASE
            AssetCategory.FACE_EYE -> Layer.EYES
            AssetCategory.FACE_BROW -> Layer.BROWS
            AssetCategory.FACE_MOUTH -> Layer.MOUTH
            AssetCategory.FACE_ACCESSORY -> Layer.FACE_ACCESSORY
            AssetCategory.HEAD_ACCESSORY -> Layer.HEAD_ACCESSORY
            AssetCategory.EXPRESSION_OVERLAY -> Layer.FACE_EXTRA
            AssetCategory.FOREGROUND_PROP -> Layer.PROP
            AssetCategory.AVAILABILITY_INDICATOR -> Layer.AVAILABILITY_BADGE
            AssetCategory.ACTIVITY_BADGE -> Layer.ACTIVITY_BADGE
            else -> null
        }
        is DrawOp.VectorPart -> null
    }
}
