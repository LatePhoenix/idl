package app.idl.domain.avatar

import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarComposer
import app.idl.domain.AvatarPalette
import app.idl.domain.Mood
import app.idl.domain.StatusGlyphs
import app.idl.domain.StatusIntent
import app.idl.domain.wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetPackTest {
    private val registry = coreRegistry()

    @Test fun `core proto validates with no issues`() {
        val issues = registry.validate()
        assertEquals(issues.joinToString("\n"), emptyList<String>(), issues)
        assertEquals(listOf("core_proto@1", "emoji_core@1"), registry.packVersions)
    }

    @Test fun `palettes use v1 body colors and a 35 percent darken for the outline`() {
        val ids = listOf(
            "palette_sunny", "palette_peach", "palette_tan", "palette_cocoa", "palette_bubblegum", "palette_sky",
            "palette_mint", "palette_lavender", "palette_moonlight", "palette_steel", "palette_ember", "palette_lime",
        )
        val accent = listOf(0, 2, 4, 5, 3, 1, 3, 0, 0, 1, 2, 3)
        ids.forEachIndexed { i, id ->
            val colors = registry.asset(id)!!.colors!!
            assertEquals(colors.name, AvatarPalette.body[i], colors.body)
            assertEquals(AvatarPalette.theme[accent[i]], colors.accent)
            assertEquals(darken(AvatarPalette.body[i], 0.35f), colors.outline)
        }
    }

    @Test fun `every base shares the placeholder anchors and face-safe zone`() {
        registry.ofCategory(AssetCategory.BASE).forEach { base ->
            assertEquals(0.5f, base.anchors.getValue("head_top").x, 0.001f)
            assertEquals(0.25f, base.anchors.getValue("head_top").y, 0.001f)
            assertEquals(0.5f, base.anchors.getValue("face_center").x, 0.001f)
            assertEquals(0.55f, base.anchors.getValue("face_center").y, 0.001f)
            assertEquals(0.53f, base.anchors.getValue("eyes").y, 0.001f)
            assertEquals(0.66f, base.anchors.getValue("mouth").y, 0.001f)
            assertEquals(0.2f, base.anchors.getValue("prop_hand").x, 0.001f)
            assertEquals(0.8f, base.anchors.getValue("prop_hand").y, 0.001f)
            assertEquals(0.9f, base.anchors.getValue("body").y, 0.001f)
            val zone = base.faceSafeZone!!
            assertEquals(0.28f, zone.left, 0.001f)
            assertEquals(0.38f, zone.top, 0.001f)
            assertEquals(0.72f, zone.right, 0.001f)
            assertEquals(0.75f, zone.bottom, 0.001f)
        }
    }

    @Test fun `mood availability and activity semantics cover the core vocabulary`() {
        Mood.entries.forEach { mood ->
            val expected = AvatarComposer.expressionFor(mood, null)!!.wire
            assertEquals(expected, registry.semantic(SemanticKey.mood(mood))!!.expressionId)
        }
        Availability.entries.forEach { availability ->
            val mapping = registry.semantic(SemanticKey.availability(availability))!!
            assertEquals("avail_${availability.wire}", mapping.availabilityIndicator)
            val glyph = registry.asset(mapping.availabilityIndicator)!!.glyph
            assertFalse(glyph.isNullOrBlank())
        }
        val glyphs = Availability.entries.map { registry.asset("avail_${it.wire}")!!.glyph }
        assertEquals(glyphs.size, glyphs.toSet().size)
        ActivityType.entries.filter { it != ActivityType.NONE }.forEach { activity ->
            val mapping = registry.semantic(SemanticKey.activity(activity))!!
            assertEquals("badge_${activity.wire}", mapping.activityBadge)
            assertEquals(activity.wire, registry.asset(mapping.activityBadge)!!.glyph)
        }
        assertEquals("head_vr_headset", registry.semantic(SemanticKey.activity(ActivityType.VR))!!.headAccessoryAssetId)
        assertEquals("prop_controller", registry.semantic(SemanticKey.activity(ActivityType.GAMING))!!.propAssetId)
        assertEquals("prop_keyboard", registry.semantic(SemanticKey.activity(ActivityType.CODING))!!.propAssetId)
        assertEquals("prop_keyboard", registry.semantic(SemanticKey.activity(ActivityType.WORKING))!!.propAssetId)
        assertEquals("prop_book", registry.semantic(SemanticKey.activity(ActivityType.READING))!!.propAssetId)
        assertEquals("head_headphones", registry.semantic(SemanticKey.activity(ActivityType.LISTENING))!!.headAccessoryAssetId)
        assertEquals("prop_open_hand", registry.semantic(SemanticKey.intent(StatusIntent.WANT_COMPANY))!!.propAssetId)
        assertEquals(listOf("overlay_confetti"), registry.semantic(SemanticKey.intent(StatusIntent.CELEBRATING))!!.overlayAssetIds)
    }

    @Test fun `expression table matches today's faces and focused scans on the bot`() {
        val focused = registry.expression("focused")!!
        assertEquals("eyes_narrow", focused.eyes)
        assertEquals("eyes_scan", focused.partsFor("base_bot").eyes)
        assertEquals("eyes_narrow", focused.partsFor("base_blob").eyes)
        val tuples = registry.allExpressions.map { expr ->
            listOf(expr.eyes, expr.brows, expr.mouth) + expr.overlays + expr.extras
        }
        assertEquals(registry.allExpressions.size, tuples.toSet().size)
        assertEquals("overlay_sleep_zs", registry.expression("sleepy")!!.overlays.single())
        assertEquals(listOf("overlay_thermometer", "overlay_bandage"), registry.expression("sick")!!.extras)
        assertEquals(listOf("overlay_blush"), registry.expression("happy")!!.extras)
        assertTrue(registry.expression("happy")!!.overlays.isEmpty())
    }

    @Test fun `availability and activity glyphs are widget safe and the hoodie sits behind the head`() {
        (registry.ofCategory(AssetCategory.AVAILABILITY_INDICATOR) + registry.ofCategory(AssetCategory.ACTIVITY_BADGE)).forEach {
            assertTrue(it.widgetSafe)
            assertFalse(it.glyph.isNullOrBlank())
        }
        Availability.entries.forEach { a ->
            assertEquals(StatusGlyphs.availability(a), registry.asset("avail_${a.wire}")!!.glyph)
        }
        ActivityType.entries.filter { it != ActivityType.NONE }.forEach { a ->
            assertEquals(StatusGlyphs.activity(a), registry.asset("badge_${a.wire}")!!.glyph)
        }
        assertEquals(15, registry.asset("body_hoodie")!!.z)
        assertEquals(FaceOcclusion.EYES, registry.asset("head_vr_headset")!!.occlusion)
        assertEquals(96, registry.asset("reaction_heart")!!.minSizePx)
        assertFalse(registry.asset("feature_freckles")!!.widgetSafe)
    }

    @Test fun `ear and glasses conflicts are declared from one side only`() {
        assertTrue(registry.asset("feature_ears_cat")!!.conflictsWith.contains("head_helmet"))
        assertFalse(registry.asset("head_helmet")!!.conflictsWith.contains("feature_ears_cat"))
        assertTrue(registry.asset("face_glasses_round")!!.conflictsWith.contains("head_vr_headset"))
        assertFalse(registry.asset("head_vr_headset")!!.conflictsWith.contains("face_glasses_round"))
        assertNotEquals(emptyList<String>(), registry.asset("feature_antennae")!!.conflictsWith)
    }

    @Test fun `every shipped vector picture validates and its file exists`() {
        val root = repoRoot()
        for (manifest in registry.manifests) {
            for (asset in manifest.assets) {
                if (asset.render.type != "vector") continue
                val file = asset.render.file
                assertFalse(asset.id, file.isNullOrBlank())
                val pictureFile = File(root, "app/src/main/assets/packs/${manifest.packId}/v${manifest.version}/$file")
                assertTrue(pictureFile.path, pictureFile.isFile)
                val picture = VectorPicture.parse(pictureFile.readText())
                val issues = VectorPictureValidator.validate(picture, asset)
                assertEquals(issues.joinToString("\n"), emptyList<String>(), issues)
            }
        }
    }

    @Test fun `domain sources do not import android`() {
        val hits = File(repoRoot(), "app/src/main/java/app/idl/domain").walk()
            .filter { it.extension == "kt" }
            .flatMap { file -> file.readLines().filter { it.contains("import android.") }.map { "${file.name}: $it" } }
            .toList()
        assertEquals(hits.joinToString(), emptyList<String>(), hits)
    }

    private fun darken(color: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val value = (color shr shift) and 0xFF
            return (value * (1 - t)).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
