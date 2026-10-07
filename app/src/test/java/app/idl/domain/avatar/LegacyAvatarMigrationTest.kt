package app.idl.domain.avatar

import app.idl.domain.Activity
import app.idl.domain.ActivityType
import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarPalette
import app.idl.domain.Availability
import app.idl.domain.BaseForm
import app.idl.domain.BodyAccessory
import app.idl.domain.Expression
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceStyle
import app.idl.domain.FrameStyle
import app.idl.domain.HeadAccessory
import app.idl.domain.Mood
import app.idl.domain.PresenceView
import app.idl.domain.Prop
import app.idl.domain.ResolvedPresence
import app.idl.domain.Scene
import app.idl.domain.VisualOverride
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyAvatarMigrationTest {
    private val registry = coreRegistry()
    private val engine = CompatibilityEngine(registry)
    private val resolver = AvatarResolver(registry)

    @Test fun `every v1 combination migrates to a configuration that resolves`() {
        var index = 0
        for (base in BaseForm.entries) {
            for (head in HeadAccessory.entries) {
                for (face in FaceAccessory.entries) {
                    for (body in BodyAccessory.entries) {
                        for (style in FaceStyle.entries) {
                            val v1 = AvatarConfig(
                                baseForm = base,
                                headAccessory = head,
                                faceAccessory = face,
                                bodyAccessory = body,
                                faceStyle = style,
                                handProp = Prop.entries[index % Prop.entries.size],
                                scene = Scene.entries[index % Scene.entries.size],
                                expression = Expression.entries[index % Expression.entries.size],
                                bodyColor = AvatarPalette.body[index % AvatarPalette.body.size],
                                frameStyle = FrameStyle.entries[index % FrameStyle.entries.size],
                            )
                            index++
                            val v2 = LegacyAvatarMigration.migrate(v1, registry)
                            val issues = engine.validate(v2)
                            assertTrue("$v1 -> $issues", issues.isEmpty())
                            val unknown = resolver.resolve(AvatarRenderRequest(v2)).dropped
                                .filter { it.reason == DropReason.UNKNOWN_ASSET }
                            assertTrue("$v1 dropped $unknown", unknown.isEmpty())
                        }
                    }
                }
            }
        }
        assertEquals(9 * 9 * 3 * 3 * 3, index)
    }

    @Test fun `every v1 option migrates to free items so a user with no purchases can still save`() {
        // Regression (F-36): AP-10 made frame_pixel and scene_neon_city premium, which locked every
        // migrated pixel or neon-city avatar out of saving.
        val nothingOwned = LocalEntitlements(registry)
        val v1s = BaseForm.entries.map { AvatarConfig(baseForm = it) } +
            Scene.entries.map { AvatarConfig(scene = it) } +
            FrameStyle.entries.map { AvatarConfig(frameStyle = it) } +
            HeadAccessory.entries.map { AvatarConfig(headAccessory = it) } +
            FaceAccessory.entries.map { AvatarConfig(faceAccessory = it) } +
            BodyAccessory.entries.map { AvatarConfig(bodyAccessory = it) } +
            Prop.entries.map { AvatarConfig(handProp = it) } +
            FaceStyle.entries.map { AvatarConfig(faceStyle = it) }
        v1s.forEach { v1 ->
            val write = LegacyAvatarMigration.migrate(v1, registry)
                .prepareForWrite(registry.baseFamilies, registry, nothingOwned)
            assertTrue("$v1 -> $write", write is AvatarWrite.Ready)
        }
    }

    @Test fun `each v1 base maps to the teardrop and species marks are dropped`() {
        fun migrated(form: BaseForm, frame: FrameStyle = FrameStyle.SQUIRCLE) =
            LegacyAvatarMigration.migrate(AvatarConfig(baseForm = form, frameStyle = frame), registry)

        BaseForm.entries.forEach { form ->
            val config = migrated(form)
            assertEquals(form.name, "base_teardrop", config.baseAssetId)
            assertTrue(form.name, config.signatureFeatureAssetIds.none { it.startsWith("feature_ears_") || it == "feature_muzzle" || it == "feature_antennae" })
        }

        val pixel = migrated(BaseForm.PIXEL, FrameStyle.CIRCLE)
        assertEquals("eyefam_pixel", pixel.eyeFamilyAssetId)
        assertEquals("frame_pixel", pixel.defaultFrameAssetId)
    }

    @Test fun `a cat keeps blush and the helmet once the ears no longer fit`() {
        val cat = LegacyAvatarMigration.migrate(
            AvatarConfig(baseForm = BaseForm.CAT, headAccessory = HeadAccessory.HELMET, faceStyle = FaceStyle.BLUSHY),
            registry,
        )
        assertEquals("base_teardrop", cat.baseAssetId)
        assertEquals(listOf("feature_blush"), cat.signatureFeatureAssetIds)
        assertEquals("head_helmet", cat.signatureHeadAccessoryAssetId)

        val plain = LegacyAvatarMigration.migrate(AvatarConfig(frameStyle = FrameStyle.CIRCLE), registry)
        assertEquals("frame_circle", plain.defaultFrameAssetId)
        val squircle = LegacyAvatarMigration.migrate(AvatarConfig(frameStyle = FrameStyle.SQUIRCLE), registry)
        assertEquals("frame_squircle", squircle.defaultFrameAssetId)
    }

    @Test fun `body color selects the palette and theme color is dropped`() {
        val peach = LegacyAvatarMigration.migrate(
            AvatarConfig(bodyColor = AvatarPalette.body[1], themeColor = AvatarPalette.theme[0]),
            registry,
        )
        assertEquals("palette_peach", peach.paletteAssetId)
        val unknown = LegacyAvatarMigration.migrate(AvatarConfig(bodyColor = 0x123456), registry)
        assertEquals("palette_sunny", unknown.paletteAssetId)
    }

    @Test fun `v1 props scenes and accessories use the v2 ids`() {
        val migrated = LegacyAvatarMigration.migrate(AvatarConfig(
            handProp = Prop.PIZZA,
            scene = Scene.SPACE_STATION,
            headAccessory = HeadAccessory.VR_HEADSET,
            faceAccessory = FaceAccessory.SUNGLASSES,
            bodyAccessory = BodyAccessory.HOODIE,
            expression = Expression.SLEEPY,
        ), registry)
        assertEquals("prop_snack", migrated.defaultPropAssetId)
        assertEquals("scene_space_station", migrated.defaultSceneAssetId)
        assertEquals("sleepy", migrated.restingExpressionId)
        assertEquals("body_hoodie", migrated.signatureBodyAccessoryAssetId)
        assertEquals("face_sunglasses", migrated.signatureFaceAccessoryAssetId)
        assertNull(migrated.signatureHeadAccessoryAssetId)

        val headset = LegacyAvatarMigration.migrate(AvatarConfig(headAccessory = HeadAccessory.HEADPHONES), registry)
        assertEquals("head_headphones", headset.signatureHeadAccessoryAssetId)
        val scenes = mapOf(
            Scene.PLAIN_GRADIENT to "scene_plain",
            Scene.COZY_BEDROOM to "scene_cozy_bedroom",
            Scene.DESK_SETUP to "scene_desk",
            Scene.CAMPFIRE to "scene_campfire",
            Scene.DUNGEON_TAVERN to "scene_tavern",
            Scene.SPACE_STATION to "scene_space_station",
            Scene.RAINY_WINDOW to "scene_rainy_window",
            Scene.NEON_CITY to "scene_neon_city",
            Scene.FOREST to "scene_forest",
            Scene.CLOUDSCAPE to "scene_clouds",
        )
        scenes.forEach { (scene, id) ->
            assertEquals(id, LegacyAvatarMigration.migrate(AvatarConfig(scene = scene), registry).defaultSceneAssetId)
        }
    }

    @Test fun `a presence view copies only status visuals and hides expression without mood`() {
        val identity = AvatarConfiguration(baseAssetId = "base_teardrop", paletteAssetId = "palette_sunny")
        val visual = app.idl.domain.PresenceVisual(
            expressionId = "sleepy",
            propAssetId = "prop_tea",
            sceneAssetId = "scene_forest",
            headAccessoryAssetId = "head_beanie",
            bodyAccessoryAssetId = "body_blanket",
        )
        val view = PresenceView(
            userId = "u",
            identity = identity,
            mood = Mood.SLEEPY,
            availability = Availability.TEXT_ONLY,
            activityType = ActivityType.VR,
            visual = visual,
        )
        val visible = LegacyAvatarMigration.presence(view)
        assertEquals(Mood.SLEEPY, visible.mood)
        assertEquals(Availability.TEXT_ONLY, visible.availability)
        assertEquals(ActivityType.VR, visible.activityType)
        assertEquals("sleepy", visible.expressionId)
        assertEquals("prop_tea", visible.propAssetId)
        assertEquals("scene_forest", visible.sceneAssetId)
        assertEquals("head_beanie", visible.headAccessoryAssetId)
        assertEquals("body_blanket", visible.bodyAccessoryAssetId)

        val same = LegacyAvatarMigration.presence(
            view.copy(mood = null, availability = null, activityType = null, visual = null),
        )
        assertNull(same.expressionId)
        assertNull(same.propAssetId)
        assertNull(same.sceneAssetId)

        val hiddenMood = LegacyAvatarMigration.presence(view.copy(mood = null))
        assertNull(hiddenMood.expressionId)
        assertEquals("prop_tea", hiddenMood.propAssetId)
    }

    @Test fun `resolved presence maps v1 visuals to asset ids without leaking a hidden expression`() {
        val shown = LegacyAvatarMigration.presence(ResolvedPresence(
            mood = Mood.HAPPY,
            availability = Availability.TEXT_ONLY,
            activity = Activity(type = ActivityType.CODING),
            visual = VisualOverride(props = listOf(Prop.COFFEE), headAccessory = HeadAccessory.VR_HEADSET, scene = Scene.CAMPFIRE),
        ))
        assertEquals("happy", shown.expressionId)
        assertEquals("prop_coffee", shown.propAssetId)
        assertEquals("head_vr_headset", shown.headAccessoryAssetId)
        assertEquals("scene_campfire", shown.sceneAssetId)
        assertEquals(ActivityType.CODING, shown.activityType)

        val hidden = LegacyAvatarMigration.presence(ResolvedPresence(
            visual = VisualOverride(expression = Expression.ANGRY, props = listOf(Prop.TEA)),
        ))
        assertNull(hidden.mood)
        assertNull(hidden.expressionId)
        assertEquals("prop_tea", hidden.propAssetId)
    }
}
