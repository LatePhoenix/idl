package app.idl.domain.avatar

import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarComposer
import app.idl.domain.AvatarConfig
import app.idl.domain.FaceAccessory
import app.idl.domain.Mood
import app.idl.domain.PresenceResolver
import app.idl.domain.QuickState
import app.idl.domain.StatusIntent
import app.idl.domain.wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AvatarResolverTest {
    private val registry = coreRegistry()
    private val resolver = AvatarResolver(registry)

    @Test fun `every mood availability and activity resolves to its pack semantic`() {
        Mood.entries.forEach { mood ->
            val resolved = resolver.resolve(request(presence = VisiblePresence(mood = mood)))
            assertEquals(AvatarComposer.expressionFor(mood, null)!!.wire, resolved.expressionId)
        }
        Availability.entries.forEach { availability ->
            val resolved = resolver.resolve(request(presence = VisiblePresence(availability = availability)))
            assertTrue(resolved.has("avail_${availability.wire}"))
        }
        assertEquals("afk", resolver.resolve(request(presence = VisiblePresence(availability = Availability.AFK))).expressionId)
        assertEquals("dnd", resolver.resolve(request(presence = VisiblePresence(availability = Availability.DO_NOT_DISTURB))).expressionId)
        assertEquals(
            "sleepy",
            resolver.resolve(request(presence = VisiblePresence(mood = Mood.SLEEPY, availability = Availability.AFK))).expressionId,
        )
        ActivityType.entries.filter { it != ActivityType.NONE }.forEach { activity ->
            val resolved = resolver.resolve(request(presence = VisiblePresence(activityType = activity)))
            assertTrue("${activity.wire} badge", resolved.has("badge_${activity.wire}"))
        }
        assertTrue(resolveActivity(ActivityType.VR).has("head_vr_headset"))
        assertTrue(resolveActivity(ActivityType.GAMING).has("prop_controller"))
        assertTrue(resolveActivity(ActivityType.CODING).has("prop_keyboard"))
        assertTrue(resolveActivity(ActivityType.WORKING).has("prop_keyboard"))
        assertTrue(resolveActivity(ActivityType.READING).has("prop_book"))
        assertTrue(resolveActivity(ActivityType.LISTENING).has("head_headphones"))
    }

    @Test fun `every expression keeps its face parts on every base`() {
        val bases = registry.ofCategory(AssetCategory.BASE).map { it.id }
        for (base in bases) {
            for (expr in registry.allExpressions) {
                val resolved = resolver.resolve(request(config(base = base), VisiblePresence(expressionId = expr.id)))
                val parts = expr.partsFor(base)
                listOfNotNull(parts.eyes, parts.brows, parts.mouth).forEach { id ->
                    assertTrue("${expr.id} @$base missing $id", resolved.has(id))
                    assertTrue(resolved.dropped.none { it.assetId == id })
                }
            }
        }
    }

    @Test fun `focused uses the scan line only on the bot`() {
        val bot = resolver.resolve(request(config(base = "base_bot"), VisiblePresence(expressionId = "focused")))
        assertTrue(bot.has("eyes_scan"))
        assertFalse(bot.has("eyes_narrow"))
        listOf("base_blob", "base_ghost", "base_critter", "base_orb").forEach { base ->
            val resolved = resolver.resolve(request(config(base = base), VisiblePresence(expressionId = "focused")))
            assertTrue(resolved.has("eyes_narrow"))
            assertFalse(resolved.has("eyes_scan"))
        }
    }

    @Test fun `every expression is visually distinct on at least one base`() {
        val bases = registry.ofCategory(AssetCategory.BASE).map { it.id }
        val exprs = registry.allExpressions.map { it.id }
        fun signature(base: String, expr: String): List<String> {
            val resolved = resolver.resolve(request(config(base = base), VisiblePresence(expressionId = expr)))
            return resolved.layers
                .filter { it.category == AssetCategory.FACE_EYE || it.category == AssetCategory.FACE_BROW || it.category == AssetCategory.FACE_MOUTH || (it.category == AssetCategory.EXPRESSION_OVERLAY && it.priority == LayerPriority.EXPRESSION) }
                .map { it.assetId }
        }
        exprs.forEach { expr ->
            val distinct = bases.any { base ->
                val mine = signature(base, expr)
                exprs.filter { it != expr }.none { other -> signature(base, other) == mine }
            }
            assertTrue("$expr collides with another expression on every base", distinct)
        }
    }

    @Test fun `presence beats override beats semantic beats signature for each slot`() {
        val reg = precedenceRegistry()
        val resolve = AvatarResolver(reg)
        val full = precedenceConfig(reg)
        val presence = VisiblePresence(
            sceneAssetId = "scene_p",
            headAccessoryAssetId = "head_p",
            bodyAccessoryAssetId = "body_p",
            propAssetId = "prop_p",
            activityType = ActivityType.VR,
            availability = Availability.AVAILABLE,
        )
        val all = resolve.resolve(request(full, presence))
        assertEquals("scene_p", layerId(all, AssetCategory.SCENE))
        assertEquals(LayerPriority.SCENE, all.layers.first { it.category == AssetCategory.SCENE }.priority)
        assertEquals("head_p", layerId(all, AssetCategory.HEAD_ACCESSORY))
        assertEquals(LayerPriority.CONTEXT, all.layers.first { it.assetId == "head_p" }.priority)
        assertEquals("body_p", layerId(all, AssetCategory.BODY_ACCESSORY))
        assertEquals("prop_p", layerId(all, AssetCategory.FOREGROUND_PROP))
        assertEquals(LayerPriority.CONTEXT, all.layers.first { it.assetId == "prop_p" }.priority)
        assertEquals("face_sig", layerId(all, AssetCategory.FACE_ACCESSORY))
        assertEquals(LayerPriority.SIGNATURE, all.layers.first { it.assetId == "face_sig" }.priority)
        assertEquals("avail_o", layerId(all, AssetCategory.AVAILABILITY_INDICATOR))
        assertEquals("badge_vr", layerId(all, AssetCategory.ACTIVITY_BADGE))
        assertEquals(LayerPriority.ACTIVITY, all.layers.first { it.assetId == "badge_vr" }.priority)
        assertEquals("frame_d", layerId(all, AssetCategory.FRAME))
        assertEquals(LayerPriority.BASE, all.layers.first { it.assetId == "frame_d" }.priority)
        assertTrue(all.dropped.any { it.assetId == "head_sig" && it.reason == DropReason.DISPLACED })

        val noPresenceVisual = presence.copy(
            sceneAssetId = null, headAccessoryAssetId = null, bodyAccessoryAssetId = null, propAssetId = null,
        )
        val overridden = resolve.resolve(request(full, noPresenceVisual))
        assertEquals("scene_o", layerId(overridden, AssetCategory.SCENE))
        assertEquals("head_o", layerId(overridden, AssetCategory.HEAD_ACCESSORY))
        assertEquals(LayerPriority.ACTIVITY, overridden.layers.first { it.assetId == "head_o" }.priority)
        assertEquals("body_o", layerId(overridden, AssetCategory.BODY_ACCESSORY))
        assertEquals("prop_o", layerId(overridden, AssetCategory.FOREGROUND_PROP))
        assertEquals(LayerPriority.ACTIVITY, overridden.layers.first { it.assetId == "prop_o" }.priority)
        assertEquals("avail_o", layerId(overridden, AssetCategory.AVAILABILITY_INDICATOR))

        val semantic = resolve.resolve(request(full.copy(styleDna = StyleDna()), noPresenceVisual))
        assertEquals("scene_s", layerId(semantic, AssetCategory.SCENE))
        assertEquals("head_s", layerId(semantic, AssetCategory.HEAD_ACCESSORY))
        assertEquals("body_s", layerId(semantic, AssetCategory.BODY_ACCESSORY))
        assertEquals("prop_s", layerId(semantic, AssetCategory.FOREGROUND_PROP))
        assertEquals("avail_s", layerId(semantic, AssetCategory.AVAILABILITY_INDICATOR))
        assertTrue(semantic.has("badge_vr"))

        val identity = resolve.resolve(request(full.copy(styleDna = StyleDna()), VisiblePresence(availability = Availability.AVAILABLE)))
        assertEquals("scene_d", layerId(identity, AssetCategory.SCENE))
        assertEquals("head_sig", layerId(identity, AssetCategory.HEAD_ACCESSORY))
        assertEquals(LayerPriority.SIGNATURE, identity.layers.first { it.assetId == "head_sig" }.priority)
        assertEquals("body_sig", layerId(identity, AssetCategory.BODY_ACCESSORY))
        assertEquals("prop_def", layerId(identity, AssetCategory.FOREGROUND_PROP))
        assertEquals(LayerPriority.SIGNATURE, identity.layers.first { it.assetId == "prop_def" }.priority)
        assertEquals("avail_s", layerId(identity, AssetCategory.AVAILABILITY_INDICATOR))
        assertFalse(identity.has("badge_vr"))
        assertTrue(identity.dropped.none { it.reason == DropReason.DISPLACED })

        val bare = resolve.resolve(request(full.copy(
            styleDna = StyleDna(),
            signatureHeadAccessoryAssetId = null,
            signatureBodyAccessoryAssetId = null,
            signatureFaceAccessoryAssetId = null,
            defaultPropAssetId = null,
            defaultSceneAssetId = null,
            defaultFrameAssetId = null,
        )))
        assertEquals("scene_a", layerId(bare, AssetCategory.SCENE))
        assertEquals("frame_a", layerId(bare, AssetCategory.FRAME))
        assertNull(layerId(bare, AssetCategory.HEAD_ACCESSORY))
        assertNull(layerId(bare, AssetCategory.AVAILABILITY_INDICATOR))
    }

    @Test fun `an explicit vr headset takes activity priority over signature glasses`() {
        val saved = config(face = "face_glasses_round")
        val presence = VisiblePresence(
            mood = Mood.EXCITED,
            availability = Availability.TEXT_ONLY,
            activityType = ActivityType.VR,
            headAccessoryAssetId = "head_vr_headset",
        )
        val resolved = resolver.resolve(request(saved, presence, target = RenderTarget.STANDARD_WIDGET))
        assertTrue(resolved.has("head_vr_headset"))
        assertFalse(resolved.has("face_glasses_round"))
        assertEquals(LayerPriority.ACTIVITY, resolved.layers.first { it.assetId == "head_vr_headset" }.priority)
        val conflict = resolved.dropped.first { it.assetId == "face_glasses_round" }
        assertEquals(DropReason.CONFLICT, conflict.reason)
        assertEquals("head_vr_headset", conflict.detail)
        assertEquals(saved, request(saved, presence).configuration)
    }

    @Test fun `quick state vr with signature glasses keeps the headset until the status ends`() {
        val now = Instant.parse("2026-10-04T15:00:00Z")
        val saved = AvatarConfig(faceAccessory = FaceAccessory.GLASSES)
        val state = QuickState.ALL.first { it.id == "vr" }.toState(now)
        val identity = LegacyAvatarMigration.migrate(saved, registry)
        val presence = LegacyAvatarMigration.presence(PresenceResolver.resolve(listOf(state), now))
        assertEquals("head_vr_headset", presence.headAccessoryAssetId)
        assertEquals(ActivityType.VR, presence.activityType)
        assertEquals("face_glasses_round", identity.signatureFaceAccessoryAssetId)
        assertNull(identity.signatureHeadAccessoryAssetId)

        val resolved = resolver.resolve(request(identity, presence, target = RenderTarget.STANDARD_WIDGET))
        assertTrue(resolved.has("head_vr_headset"))
        assertFalse(resolved.has("face_glasses_round"))
        assertEquals(DropReason.CONFLICT, resolved.dropped.first { it.assetId == "face_glasses_round" }.reason)
        assertEquals(identity, request(identity, presence).configuration)

        val ended = resolver.resolve(request(identity))
        assertTrue(ended.has("face_glasses_round"))
        assertFalse(ended.has("head_vr_headset"))
    }

    @Test fun `signature glasses survive sick and return after vr`() {
        val saved = config(face = "face_glasses_round")
        val sick = resolver.resolve(request(saved, VisiblePresence(mood = Mood.SICK)))
        assertEquals("sick", sick.expressionId)
        assertTrue(sick.has("face_glasses_round"))
        assertTrue(sick.has("overlay_thermometer"))
        assertTrue(sick.dropped.none { it.assetId == "face_glasses_round" })
        assertEquals(saved, request(saved, VisiblePresence(mood = Mood.SICK)).configuration)

        val vr = resolver.resolve(request(saved, VisiblePresence(activityType = ActivityType.VR)))
        assertTrue(vr.has("head_vr_headset"))
        assertFalse(vr.has("face_glasses_round"))
        val conflict = vr.dropped.first { it.assetId == "face_glasses_round" }
        assertEquals(DropReason.CONFLICT, conflict.reason)
        assertEquals("head_vr_headset", conflict.detail)
        assertEquals(saved, request(saved, VisiblePresence(activityType = ActivityType.VR)).configuration)

        val ended = resolver.resolve(request(saved))
        assertTrue(ended.has("face_glasses_round"))
        assertFalse(ended.has("head_vr_headset"))
    }

    @Test fun `an activity headset displaces a signature beanie`() {
        val saved = config(head = "head_beanie")
        val resolved = resolver.resolve(request(saved, VisiblePresence(activityType = ActivityType.VR)))
        assertTrue(resolved.has("head_vr_headset"))
        assertFalse(resolved.has("head_beanie"))
        assertTrue(resolved.dropped.any { it.assetId == "head_beanie" && it.reason == DropReason.DISPLACED })
        assertEquals(saved, request(saved, VisiblePresence(activityType = ActivityType.VR)).configuration)
    }

    @Test fun `signature ears outrank a helmet that does not list the conflict`() {
        val resolved = resolver.resolve(request(config(
            base = "base_critter",
            features = listOf("feature_ears_cat"),
            head = "head_helmet",
        )))
        assertTrue(resolved.has("feature_ears_cat"))
        assertFalse(resolved.has("head_helmet"))
        val drop = resolved.dropped.first { it.assetId == "head_helmet" }
        assertEquals(DropReason.CONFLICT, drop.reason)
        assertEquals("feature_ears_cat", drop.detail)
    }

    @Test fun `visor eyes fall back to round eyes off the bot`() {
        val blob = resolver.resolve(request(config(base = "base_blob", eyes = "eyefam_visor")))
        assertEquals("eyefam_round", blob.layers.first { it.category == AssetCategory.FACE_EYE }.variant)
        val bot = resolver.resolve(request(config(base = "base_bot", eyes = "eyefam_visor")))
        assertEquals("eyefam_visor", bot.layers.first { it.category == AssetCategory.FACE_EYE }.variant)
    }

    @Test fun `a conflict declared on only one asset is still detected`() {
        val reg = sandbox(extra = listOf(
            piece("a_left", AssetCategory.SIGNATURE_FEATURE, conflicts = listOf("b_right")),
            piece("b_right", AssetCategory.SIGNATURE_FEATURE),
        ))
        val resolved = AvatarResolver(reg).resolve(request(config().copy(
            baseAssetId = "base_a",
            paletteAssetId = "pal_a",
            eyeFamilyAssetId = "eye_a",
            mouthFamilyAssetId = "mouth_a",
            defaultSceneAssetId = "scene_a",
            defaultFrameAssetId = "frame_a",
            signatureFeatureAssetIds = listOf("a_left", "b_right"),
        )))
        assertTrue(resolved.has("a_left"))
        assertFalse(resolved.has("b_right"))
        assertEquals("a_left", resolved.dropped.first { it.assetId == "b_right" }.detail)
        assertEquals(DropReason.CONFLICT, resolved.dropped.first { it.assetId == "b_right" }.reason)
    }

    @Test fun `a missing requirement drops dependents transitively`() {
        val reg = sandbox(extra = listOf(
            piece("pin", AssetCategory.SIGNATURE_FEATURE, requires = listOf("cape")),
            piece("cape", AssetCategory.SIGNATURE_FEATURE, requires = listOf("clasp")),
        ))
        val resolved = AvatarResolver(reg).resolve(request(config().copy(
            baseAssetId = "base_a",
            paletteAssetId = "pal_a",
            defaultSceneAssetId = "scene_a",
            defaultFrameAssetId = "frame_a",
            signatureFeatureAssetIds = listOf("pin", "cape"),
        )))
        assertFalse(resolved.has("pin"))
        assertFalse(resolved.has("cape"))
        assertEquals(
            setOf("pin", "cape"),
            resolved.dropped.filter { it.reason == DropReason.MISSING_REQUIREMENT }.map { it.assetId }.toSet(),
        )
    }

    @Test fun `a vr headset occludes eyes and brows but keeps the mouth`() {
        val resolved = resolver.resolve(request(presence = VisiblePresence(mood = Mood.HAPPY, activityType = ActivityType.VR)))
        assertTrue(resolved.has("head_vr_headset"))
        assertTrue(resolved.layers.none { it.category == AssetCategory.FACE_EYE || it.category == AssetCategory.FACE_BROW })
        assertTrue(resolved.layers.any { it.category == AssetCategory.FACE_MOUTH })
        assertTrue(resolved.dropped.any { it.reason == DropReason.OCCLUDED && it.assetId.startsWith("eyes_") })
        assertTrue(resolved.dropped.any { it.reason == DropReason.OCCLUDED && it.assetId.startsWith("brows_") })
        assertTrue(resolved.dropped.none { it.reason == DropReason.OCCLUDED && it.assetId.startsWith("mouth_") })
    }

    @Test fun `compact targets keep the badge over the prop and drop decoration`() {
        val rich = richConfig()
        listOf(RenderTarget.COMPACT_WIDGET, RenderTarget.CIRCLE_WIDGET, RenderTarget.FRIEND_TILE, RenderTarget.NOTIFICATION).forEach { target ->
            val resolved = resolver.resolve(request(rich, richPresence(), reactions = reactions(), target = target, sizePx = 128))
            assertTrue("$target badge", resolved.has("badge_gaming"))
            assertFalse("$target prop", resolved.has("prop_controller"))
            assertTrue(resolved.layers.none { it.category == AssetCategory.BODY_ACCESSORY || it.category == AssetCategory.REACTION_OVERLAY })
            assertFalse(resolved.has("overlay_sparkles"))
            assertTrue(resolved.has("overlay_confetti"))
            assertTrue(resolved.has("feature_freckles"))
            assertTrue(resolved.layers.any { it.category == AssetCategory.FACE_EYE })
        }
    }

    @Test fun `standard widget drops extras and keeps one reaction`() {
        val resolved = resolver.resolve(request(richConfig(), richPresence(), reactions(), RenderTarget.STANDARD_WIDGET, 128))
        assertFalse(resolved.has("overlay_sparkles"))
        assertTrue(resolved.has("overlay_confetti"))
        assertTrue(resolved.has("body_blanket"))
        assertTrue(resolved.has("prop_controller"))
        assertEquals(listOf("reaction_heart"), reactionIds(resolved))
    }

    @Test fun `large widget keeps extras and one reaction`() {
        val resolved = resolver.resolve(request(richConfig(), richPresence(), reactions(), RenderTarget.LARGE_WIDGET, 128))
        assertTrue(resolved.has("overlay_sparkles"))
        assertTrue(resolved.has("body_blanket"))
        assertEquals(listOf("reaction_heart"), reactionIds(resolved))
    }

    @Test fun `profile and share card keep at most three reactions`() {
        listOf(RenderTarget.PROFILE, RenderTarget.SHARE_CARD).forEach { target ->
            val resolved = resolver.resolve(request(richConfig(), richPresence(), reactions(), target, 256))
            assertEquals(setOf("reaction_heart", "reaction_coffee", "reaction_tea"), reactionIds(resolved).toSet())
            assertTrue(resolved.has("overlay_sparkles"))
            assertTrue(resolved.has("prop_controller"))
            assertTrue(resolved.has("badge_gaming"))
        }
    }

    @Test fun `minimum size drops assets that do not fit`() {
        val rich = richConfig()
        val presence = richPresence()
        val tiny = resolver.resolve(request(rich, presence, reactions(), RenderTarget.PROFILE, 48))
        assertFalse(tiny.has("feature_freckles"))
        assertFalse(tiny.has("overlay_sparkles"))
        assertFalse(tiny.has("overlay_confetti"))
        assertTrue(reactionIds(tiny).isEmpty())
        val at64 = resolver.resolve(request(rich, presence, reactions(), RenderTarget.PROFILE, 64))
        assertTrue(at64.has("feature_freckles"))
        assertTrue(at64.has("overlay_sparkles"))
        assertFalse(at64.has("overlay_confetti"))
        val at96 = resolver.resolve(request(rich, presence, reactions(), RenderTarget.PROFILE, 96))
        assertTrue(at96.has("overlay_confetti"))
        assertEquals(setOf("reaction_heart", "reaction_coffee", "reaction_tea"), reactionIds(at96).toSet())
    }

    @Test fun `assets that are not widget safe drop below 64px`() {
        val reg = sandbox(extra = listOf(piece("sticker", AssetCategory.SIGNATURE_FEATURE, widgetSafe = false, minSizePx = 0)))
        fun at(size: Int) = AvatarResolver(reg).resolve(request(
            config().copy(baseAssetId = "base_a", paletteAssetId = "pal_a", signatureFeatureAssetIds = listOf("sticker"), defaultSceneAssetId = "scene_a", defaultFrameAssetId = "frame_a"),
            target = RenderTarget.PROFILE,
            sizePx = size,
        ))
        assertFalse(at(63).has("sticker"))
        assertEquals(DropReason.TARGET_SIMPLIFIED, at(63).dropped.first { it.assetId == "sticker" }.reason)
        assertTrue(at(64).has("sticker"))
    }

    @Test fun `a hidden mood does not apply mood overrides or semantics`() {
        val dna = StyleDna(semanticVisualOverrides = mapOf(
            "mood:sleepy" to SemanticVisualOverride(expressionId = "excited", overlayAssetIds = listOf("overlay_sparkles")),
        ))
        val hidden = resolver.resolve(request(config(dna = dna)))
        assertEquals("neutral", hidden.expressionId)
        assertFalse(hidden.has("overlay_sparkles"))
        assertTrue(hidden.has("eyes_open"))
        val shown = resolver.resolve(request(config(dna = dna), VisiblePresence(mood = Mood.SLEEPY)))
        assertEquals("excited", shown.expressionId)
        assertTrue(shown.has("overlay_sparkles"))
    }

    @Test fun `a hidden activity adds no headset and no badge`() {
        val dna = StyleDna(semanticVisualOverrides = mapOf(
            "activity:vr" to SemanticVisualOverride(headAccessoryAssetId = "head_crown"),
        ))
        val hidden = resolver.resolve(request(config(dna = dna), VisiblePresence(mood = Mood.HAPPY)))
        assertFalse(hidden.has("head_vr_headset"))
        assertFalse(hidden.has("head_crown"))
        assertTrue(hidden.layers.none { it.category == AssetCategory.ACTIVITY_BADGE })
        val shown = resolver.resolve(request(config(dna = dna), VisiblePresence(activityType = ActivityType.VR)))
        assertTrue(shown.has("head_crown"))
        assertFalse(shown.has("head_vr_headset"))
        assertTrue(shown.has("badge_vr"))
    }

    @Test fun `a hidden intent adds no prop and no confetti`() {
        val dna = StyleDna(semanticVisualOverrides = mapOf(
            "intent:want_company" to SemanticVisualOverride(propAssetId = "prop_tea"),
            "intent:celebrating" to SemanticVisualOverride(overlayAssetIds = listOf("overlay_steam")),
        ))
        val hidden = resolver.resolve(request(config(dna = dna)))
        assertFalse(hidden.has("prop_tea"))
        assertFalse(hidden.has("prop_open_hand"))
        assertFalse(hidden.has("overlay_steam"))
        assertFalse(hidden.has("overlay_confetti"))
        val company = resolver.resolve(request(config(dna = dna), VisiblePresence(intent = StatusIntent.WANT_COMPANY)))
        assertTrue(company.has("prop_tea"))
        assertFalse(company.has("prop_open_hand"))
        val party = resolver.resolve(request(config(), VisiblePresence(intent = StatusIntent.CELEBRATING)))
        assertTrue(party.has("overlay_confetti"))
    }

    @Test fun `a hidden availability adds no indicator and keeps the resting expression`() {
        val dna = StyleDna(semanticVisualOverrides = mapOf(
            "availability:afk" to SemanticVisualOverride(expressionId = "excited"),
        ))
        val hidden = resolver.resolve(request(config(resting = "sleepy", dna = dna)))
        assertEquals("sleepy", hidden.expressionId)
        assertTrue(hidden.layers.none { it.category == AssetCategory.AVAILABILITY_INDICATOR })
        val shown = resolver.resolve(request(config(resting = "sleepy", dna = dna), VisiblePresence(availability = Availability.AFK)))
        assertEquals("excited", shown.expressionId)
        assertTrue(shown.has("avail_afk"))
    }

    @Test fun `equal requests resolve to equal avatars and keys`() {
        val req = request(config(head = "head_beanie"), VisiblePresence(mood = Mood.HAPPY, availability = Availability.TEXT_ONLY), listOf("reaction_heart"))
        assertEquals(resolver.resolve(req), resolver.resolve(req))
    }

    @Test fun `reordering the override map does not change the render key`() {
        val tea = SemanticVisualOverride(propAssetId = "prop_tea")
        val beanie = SemanticVisualOverride(headAccessoryAssetId = "head_beanie")
        val first = config(dna = StyleDna(semanticVisualOverrides = linkedMapOf("mood:sleepy" to tea, "activity:vr" to beanie)))
        val second = config(dna = StyleDna(semanticVisualOverrides = linkedMapOf("activity:vr" to beanie, "mood:sleepy" to tea)))
        val presence = VisiblePresence(mood = Mood.SLEEPY, activityType = ActivityType.VR)
        val a = resolver.resolve(request(first, presence))
        val b = resolver.resolve(request(second, presence))
        assertEquals(a, b)
    }

    @Test fun `changing any request field or the pack version changes the render key`() {
        val baseline = request(config(), VisiblePresence(mood = Mood.HAPPY), target = RenderTarget.PROFILE, sizePx = 256)
        val key = resolver.resolve(baseline).renderKey
        assertEquals(64, key.length)
        fun changes(updated: AvatarRenderRequest) = assertNotEquals(key, resolver.resolve(updated).renderKey)
        changes(baseline.copy(configuration = config(base = "base_ghost")))
        changes(baseline.copy(presence = VisiblePresence(mood = Mood.SAD)))
        changes(baseline.copy(reactionOverlayAssetIds = listOf("reaction_heart")))
        changes(baseline.copy(target = RenderTarget.STANDARD_WIDGET))
        changes(baseline.copy(sizePx = 255))
        changes(baseline.copy(wallpaperContrastMode = WallpaperContrastMode.DARK_WALLPAPER))
        changes(baseline.copy(accessibilityMode = AccessibilityRenderMode.HIGH_CONTRAST))
        changes(baseline.copy(rendererVersion = 9))

        val sameLayers = baseline.copy(wallpaperContrastMode = WallpaperContrastMode.LIGHT_WALLPAPER)
        assertEquals(resolver.resolve(baseline).layers, resolver.resolve(sameLayers).layers)
        assertNotEquals(key, resolver.resolve(sameLayers).renderKey)

        val compact = request(target = RenderTarget.COMPACT_WIDGET, sizePx = 48, presence = VisiblePresence(mood = Mood.HAPPY))
        val circle = compact.copy(target = RenderTarget.CIRCLE_WIDGET)
        assertEquals(resolver.resolve(compact).layers, resolver.resolve(circle).layers)
        assertNotEquals(resolver.resolve(compact).renderKey, resolver.resolve(circle).renderKey)

        val v1 = AvatarResolver(sandbox(version = 1))
        val v2 = AvatarResolver(sandbox(version = 2))
        val sand = AvatarRenderRequest(AvatarConfiguration(baseAssetId = "base_a", paletteAssetId = "pal_a"))
        assertEquals(v1.resolve(sand).layers, v2.resolve(sand).layers)
        assertNotEquals(v1.resolve(sand).renderKey, v2.resolve(sand).renderKey)
    }

    @Test fun `retired ids resolve to their replacement`() {
        val reg = sandbox(
            extra = listOf(piece("prop_new", AssetCategory.FOREGROUND_PROP)),
            retired = mapOf("prop_old" to "prop_new"),
        )
        val resolved = AvatarResolver(reg).resolve(request(config().copy(
            baseAssetId = "base_a",
            paletteAssetId = "pal_a",
            defaultPropAssetId = "prop_old",
            defaultSceneAssetId = "scene_a",
            defaultFrameAssetId = "frame_a",
        )))
        assertTrue(resolved.has("prop_new"))
        assertFalse(resolved.has("prop_old"))
        assertTrue(resolved.dropped.none { it.reason == DropReason.UNKNOWN_ASSET })
    }

    @Test fun `an unknown base falls back to the default and an unknown prop is dropped`() {
        val missingBase = resolver.resolve(request(config().copy(baseAssetId = "base_nope")))
        assertEquals("base_blob", missingBase.baseAssetId)
        assertTrue(missingBase.dropped.none { it.reason == DropReason.UNKNOWN_ASSET })

        val missingScene = resolver.resolve(request(
            config(scene = "scene_forest"),
            VisiblePresence(sceneAssetId = "scene_nope"),
        ))
        assertTrue(missingScene.has("scene_plain"))
        assertFalse(missingScene.has("scene_forest"))

        val missingProp = resolver.resolve(request(presence = VisiblePresence(propAssetId = "prop_nope")))
        assertFalse(missingProp.layers.any { it.category == AssetCategory.FOREGROUND_PROP })
        assertTrue(missingProp.dropped.any { it.assetId == "prop_nope" && it.reason == DropReason.UNKNOWN_ASSET })

        val fallbackProp = resolver.resolve(request(
            config(prop = "prop_coffee"),
            VisiblePresence(propAssetId = "prop_nope"),
        ))
        assertTrue(fallbackProp.has("prop_coffee"))
        assertTrue(fallbackProp.dropped.any { it.assetId == "prop_nope" && it.reason == DropReason.UNKNOWN_ASSET })
    }

    @Test fun `layers sort by z then id and the hoodie sits behind the base`() {
        val resolved = resolver.resolve(request(config(body = "body_hoodie", features = listOf("feature_blush"))))
        val ids = resolved.layers.map { it.assetId }
        assertEquals(ids.sortedWith(compareBy({ resolved.layers.first { layer -> layer.assetId == it }.z }, { it })), ids)
        assertTrue(ids.indexOf("body_hoodie") < ids.indexOf("base_blob"))
        val drops = resolved.dropped
        assertEquals(drops.sortedWith(compareBy({ it.assetId }, { it.reason.ordinal })), drops)
    }

    @Test fun `accessibility description names base expression availability and activity`() {
        val resolved = resolver.resolve(request(
            config(base = "base_critter"),
            VisiblePresence(mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY, activityType = ActivityType.VR),
        ))
        assertEquals("Critter avatar, sleepy, text only, in VR", resolved.accessibilityDescription)
    }

    @Test fun `scene detail follows size and the detail preference`() {
        val full = config(dna = StyleDna(sceneDetailPreference = SceneDetailPreference.FULL), scene = "scene_forest")
        assertTrue(resolver.resolve(request(full, sizePx = 96)).sceneDetail)
        assertFalse(resolver.resolve(request(full, sizePx = 95)).sceneDetail)
        val low = config(dna = StyleDna(sceneDetailPreference = SceneDetailPreference.LOW_DETAIL), scene = "scene_forest")
        assertTrue(resolver.resolve(request(low, sizePx = 256)).sceneDetail)
        assertFalse(resolver.resolve(request(low, sizePx = 255)).sceneDetail)
        val none = config(dna = StyleDna(sceneDetailPreference = SceneDetailPreference.NONE), scene = "scene_forest")
        val resolved = resolver.resolve(request(none, sizePx = 512))
        assertFalse(resolved.sceneDetail)
        assertTrue(resolved.has("scene_plain"))
        assertFalse(resolved.has("scene_forest"))
    }

    private fun resolveActivity(activity: ActivityType) =
        resolver.resolve(request(presence = VisiblePresence(activityType = activity)))

    private fun richConfig() = config(
        features = listOf("feature_freckles", "feature_ears_cat"),
        body = "body_blanket",
        base = "base_critter",
    )

    private fun richPresence() = VisiblePresence(
        mood = Mood.EXCITED,
        activityType = ActivityType.GAMING,
        intent = StatusIntent.CELEBRATING,
    )

    private fun reactions() = listOf("reaction_heart", "reaction_coffee", "reaction_tea", "reaction_snack", "reaction_hug")

    private fun reactionIds(resolved: ResolvedAvatar) =
        resolved.layers.filter { it.category == AssetCategory.REACTION_OVERLAY }.map { it.assetId }.sorted()

    private fun layerId(resolved: ResolvedAvatar, category: AssetCategory) =
        resolved.layers.firstOrNull { it.category == category }?.assetId

    private fun precedenceRegistry(): AssetRegistry {
        fun slot(id: String, category: AssetCategory) = piece(id, category)
        val extra = listOf(
            "scene_p", "scene_o", "scene_s", "scene_d",
        ).map { slot(it, AssetCategory.SCENE) } + listOf(
            "head_p", "head_o", "head_s", "head_sig",
        ).map { slot(it, AssetCategory.HEAD_ACCESSORY) } + listOf(
            "body_p", "body_o", "body_s", "body_sig",
        ).map { slot(it, AssetCategory.BODY_ACCESSORY) } + listOf(
            "prop_p", "prop_o", "prop_s", "prop_def",
        ).map { slot(it, AssetCategory.FOREGROUND_PROP) } + listOf(
            slot("face_sig", AssetCategory.FACE_ACCESSORY),
            slot("avail_o", AssetCategory.AVAILABILITY_INDICATOR),
            slot("avail_s", AssetCategory.AVAILABILITY_INDICATOR),
            piece("badge_vr", AssetCategory.ACTIVITY_BADGE).copy(glyph = "vr"),
            slot("frame_d", AssetCategory.FRAME),
        )
        return sandbox(
            extra = extra,
            semantics = mapOf(
                "activity:vr" to SemanticMapping(
                    sceneAssetId = "scene_s",
                    headAccessoryAssetId = "head_s",
                    bodyAccessoryAssetId = "body_s",
                    propAssetId = "prop_s",
                    activityBadge = "badge_vr",
                ),
                "availability:available" to SemanticMapping(availabilityIndicator = "avail_s"),
            ),
        )
    }

    private fun precedenceConfig(reg: AssetRegistry) = AvatarConfiguration(
        baseAssetId = "base_a",
        paletteAssetId = "pal_a",
        eyeFamilyAssetId = "eye_a",
        mouthFamilyAssetId = "mouth_a",
        signatureHeadAccessoryAssetId = "head_sig",
        signatureFaceAccessoryAssetId = "face_sig",
        signatureBodyAccessoryAssetId = "body_sig",
        defaultPropAssetId = "prop_def",
        defaultSceneAssetId = "scene_d",
        defaultFrameAssetId = "frame_d",
        styleDna = StyleDna(semanticVisualOverrides = mapOf(
            "activity:vr" to SemanticVisualOverride(
                sceneAssetId = "scene_o",
                headAccessoryAssetId = "head_o",
                bodyAccessoryAssetId = "body_o",
                propAssetId = "prop_o",
                availabilityIndicatorAssetId = "avail_o",
            ),
        )),
    ).also { assertEquals("base_a", reg.defaults.base) }
}
