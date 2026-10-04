package app.idl.widget

import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarComposer
import app.idl.domain.AvatarConfig
import app.idl.domain.FaceAccessory
import app.idl.domain.PresenceResolver
import app.idl.domain.PresenceView
import app.idl.domain.QuickState
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.DropReason
import app.idl.domain.avatar.LegacyAvatarMigration
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.has
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WidgetRenderInputsTest {
    private val registry = coreRegistry()
    private val now = Instant.parse("2026-10-04T15:00:00Z")

    @Test fun `cell size selects compact standard and large targets`() {
        assertEquals(RenderTarget.COMPACT_WIDGET, WidgetRenderInputs.targetFor(110f, 110f))
        assertEquals(RenderTarget.STANDARD_WIDGET, WidgetRenderInputs.targetFor(250f, 110f))
        assertEquals(RenderTarget.LARGE_WIDGET, WidgetRenderInputs.targetFor(180f, 180f))
        assertEquals(RenderTarget.LARGE_WIDGET, WidgetRenderInputs.targetFor(250f, 250f))
    }

    @Test fun `a friend widget resolves the resting avatar and passes status as presence`() {
        val resting = AvatarConfig(faceAccessory = FaceAccessory.GLASSES)
        val resolvedPresence = PresenceResolver.resolve(listOf(QuickState.ALL.first { it.id == "vr" }.toState(now)), now)
        val composed = AvatarComposer.compose(resting, resolvedPresence)
        val view = PresenceView(
            userId = "ari",
            avatar = composed,
            restingAvatar = resting,
            mood = resolvedPresence.mood,
            availability = resolvedPresence.availability,
            activityType = resolvedPresence.activity?.type,
        )
        // Sanitizing the composed avatar is the bug: glasses and the headset are both "saved".
        assertNull(LegacyAvatarMigration.migrate(composed, registry).signatureHeadAccessoryAssetId)

        val model = WidgetModel(title = "Ari", friendView = view, deepLink = "idl://friend/ari")
        val inputs = WidgetRenderInputs.from(model, registry, RenderTarget.STANDARD_WIDGET)!!
        assertEquals("face_glasses_round", inputs.configuration.signatureFaceAccessoryAssetId)
        assertNull(inputs.configuration.signatureHeadAccessoryAssetId)
        assertEquals("head_vr_headset", inputs.presence.headAccessoryAssetId)
        assertEquals(ActivityType.VR, inputs.presence.activityType)
        assertEquals(RenderTarget.STANDARD_WIDGET.defaultSizePx, inputs.request().sizePx)

        val resolved = AvatarResolver(registry).resolve(inputs.request())
        assertTrue(resolved.has("head_vr_headset"))
        assertFalse(resolved.has("face_glasses_round"))
        assertEquals(DropReason.CONFLICT, resolved.dropped.first { it.assetId == "face_glasses_round" }.reason)
        val described = model.copy(avatarDescription = resolved.accessibilityDescription)
        assertTrue(described.contentDescription.startsWith("Ari, "))
        assertTrue(described.contentDescription.contains("in VR"))
    }

    @Test fun `a self widget keeps the saved avatar and feeds resolved presence`() {
        val presence = PresenceResolver.resolve(listOf(QuickState.ALL.first { it.id == "focus" }.toState(now)), now)
        val model = WidgetModel(
            title = "You",
            restingAvatar = AvatarConfig(),
            selfPresence = presence,
            deepLink = "idl://status",
        )
        val inputs = WidgetRenderInputs.from(model, registry, RenderTarget.COMPACT_WIDGET)!!
        assertEquals(Availability.BUSY, inputs.presence.availability)
        assertEquals(ActivityType.WORKING, inputs.presence.activityType)
        assertNull(inputs.configuration.signatureHeadAccessoryAssetId)
        val resolved = AvatarResolver(registry).resolve(inputs.request())
        assertTrue(resolved.has("avail_busy"))
        assertTrue(resolved.has("badge_working"))
        assertEquals("You, Orb avatar, focused, busy, working", model.copy(avatarDescription = resolved.accessibilityDescription).contentDescription)
    }

    @Test fun `a widget with no avatar has nothing to resolve`() {
        val model = WidgetModel(title = "Choose a friend", deepLink = "idl://widgets")
        assertNull(WidgetRenderInputs.from(model, registry, RenderTarget.COMPACT_WIDGET))
    }
}
