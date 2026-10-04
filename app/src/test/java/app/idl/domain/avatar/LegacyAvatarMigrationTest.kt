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

    @Test fun `each v1 base maps to the documented v2 identity`() {
        fun migrated(form: BaseForm, frame: FrameStyle = FrameStyle.SQUIRCLE) =
            LegacyAvatarMigration.migrate(AvatarConfig(baseForm = form, frameStyle = frame), registry)

        val human = migrated(BaseForm.HUMAN)
        assertEquals("base_orb", human.baseAssetId)
        assertTrue(human.signatureFeatureAssetIds.isEmpty())

        assertEquals("base_blob", migrated(BaseForm.BLOB).baseAssetId)
        assertEquals("base_bot", migrated(BaseForm.ROBOT).baseAssetId)
        assertEquals("base_ghost", migrated(BaseForm.GHOST).baseAssetId)

        val cat = migrated(BaseForm.CAT)
        assertEquals("base_critter", cat.baseAssetId)
        assertEquals(listOf("feature_ears_cat"), cat.signatureFeatureAssetIds)

        val fox = migrated(BaseForm.FOX)
        assertEquals("base_critter", fox.baseAssetId)
        assertEquals(listOf("feature_ears_fox", "feature_muzzle"), fox.signatureFeatureAssetIds)

        val bear = migrated(BaseForm.BEAR)
        assertEquals("base_critter", bear.baseAssetId)
        assertEquals(listOf("feature_ears_bear"), bear.signatureFeatureAssetIds)

        val alien = migrated(BaseForm.ALIEN)
        assertEquals("base_blob", alien.baseAssetId)
        assertEquals(listOf("feature_antennae"), alien.signatureFeatureAssetIds)

        val pixel = migrated(BaseForm.PIXEL, FrameStyle.CIRCLE)
        assertEquals("base_bot", pixel.baseAssetId)
        assertEquals("eyefam_pixel", pixel.eyeFamilyAssetId)
        assertEquals("frame_pixel", pixel.defaultFrameAssetId)
    }

    @Test fun `cat ears beat a migrated helmet and pixel frame wins over frame style`() {
        val cat = LegacyAvatarMigration.migrate(
            AvatarConfig(baseForm = BaseForm.CAT, headAccessory = HeadAccessory.HELMET, faceStyle = FaceStyle.BLUSHY),
            registry,
        )
        assertEquals(listOf("feature_ears_cat", "feature_blush"), cat.signatureFeatureAssetIds)
        assertNull(cat.signatureHeadAccessoryAssetId)

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
        val resting = AvatarConfig()
        val avatar = resting.copy(
            expression = Expression.SLEEPY,
            handProp = Prop.TEA,
            scene = Scene.FOREST,
            headAccessory = HeadAccessory.BEANIE,
            bodyAccessory = BodyAccessory.BLANKET,
        )
        val view = PresenceView(
            userId = "u",
            avatar = avatar,
            restingAvatar = resting,
            mood = Mood.SLEEPY,
            availability = Availability.TEXT_ONLY,
            activityType = ActivityType.VR,
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

        val same = LegacyAvatarMigration.presence(view.copy(avatar = resting, mood = null, availability = null, activityType = null))
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
