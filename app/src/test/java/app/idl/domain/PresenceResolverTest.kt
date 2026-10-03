package app.idl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceResolverTest {

    @Test fun `no states resolves to empty`() {
        assertTrue(PresenceResolver.resolve(emptyList(), T0).isEmpty)
    }

    @Test fun `manual core fields win over automated sources`() {
        val manual = state(mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY)
        val vrcq = state(
            PresenceSource.VRCQ, mood = Mood.EXCITED, availability = Availability.GAMING,
            activity = Activity(ActivityType.VR, "VRChat", PresenceSource.VRCQ), updated = T0.plusSeconds(60),
        )
        val r = PresenceResolver.resolve(listOf(vrcq, manual), T0.plusSeconds(120))
        assertEquals(Mood.SLEEPY, r.mood)
        assertEquals(Availability.TEXT_ONLY, r.availability)
        assertEquals(ActivityType.VR, r.activity?.type) // automated may contribute activity
    }

    @Test fun `automated sources never set mood by default even when manual is absent`() {
        val desktop = state(PresenceSource.DESKTOP_BRIDGE, mood = Mood.FOCUSED, activity = Activity(ActivityType.CODING))
        val r = PresenceResolver.resolve(listOf(desktop), T0)
        assertNull(r.mood)
        assertEquals(ActivityType.CODING, r.activity?.type)
    }

    @Test fun `user can opt in to automated core fields`() {
        val desktop = state(PresenceSource.DESKTOP_BRIDGE, availability = Availability.BUSY)
        val r = PresenceResolver.resolve(listOf(desktop), T0, SourceSettings(allowAutomatedCoreFields = true))
        assertEquals(Availability.BUSY, r.availability)
    }

    @Test fun `higher priority automated source wins activity`() {
        val local = state(PresenceSource.ANDROID_LOCAL, activity = Activity(ActivityType.LISTENING))
        val vrcq = state(PresenceSource.VRCQ, activity = Activity(ActivityType.VR))
        assertEquals(ActivityType.VR, PresenceResolver.resolve(listOf(local, vrcq), T0).activity?.type)
    }

    @Test fun `manual activity beats automated activity`() {
        val manual = state(activity = Activity(ActivityType.READING))
        val vrcq = state(PresenceSource.VRCQ, activity = Activity(ActivityType.VR))
        assertEquals(ActivityType.READING, PresenceResolver.resolve(listOf(vrcq, manual), T0).activity?.type)
    }

    @Test fun `equal priority ties break by most recent update`() {
        val steam = state(PresenceSource.STEAM, activity = Activity(ActivityType.GAMING, "Old"), updated = T0)
        val discord = state(PresenceSource.DISCORD, activity = Activity(ActivityType.GAMING, "New"), updated = T0.plusSeconds(5))
        assertEquals("New", PresenceResolver.resolve(listOf(steam, discord), T0.plusSeconds(10)).activity?.label)
    }

    @Test fun `resolution is independent of input order`() {
        val a = state(PresenceSource.STEAM, activity = Activity(ActivityType.GAMING, "A"))
        val b = state(PresenceSource.XBOX, activity = Activity(ActivityType.GAMING, "B"))
        assertEquals(
            PresenceResolver.resolve(listOf(a, b), T0),
            PresenceResolver.resolve(listOf(b, a), T0),
        )
    }

    @Test fun `expired states are ignored and lower source shows through`() {
        val manual = state(mood = Mood.SAD, activity = Activity(ActivityType.WATCHING), expires = T0.plus(hours(1)))
        val local = state(PresenceSource.ANDROID_LOCAL, activity = Activity(ActivityType.LISTENING), expires = T0.plus(hours(5)))
        val r = PresenceResolver.resolve(listOf(manual, local), T0.plus(hours(2)))
        assertNull(r.mood)
        assertEquals(ActivityType.LISTENING, r.activity?.type)
    }

    @Test fun `state expiring exactly now is expired`() {
        val s = state(mood = Mood.HAPPY, expires = T0.plus(hours(1)))
        assertTrue(PresenceResolver.resolve(listOf(s), T0.plus(hours(1))).isEmpty)
    }

    @Test fun `disabled sources are ignored`() {
        val vrcq = state(PresenceSource.VRCQ, activity = Activity(ActivityType.VR))
        val r = PresenceResolver.resolve(listOf(vrcq), T0, SourceSettings(disabled = setOf(PresenceSource.VRCQ)))
        assertTrue(r.isEmpty)
    }

    @Test fun `external activity can be hidden while manual activity still shows`() {
        val vrcq = state(PresenceSource.VRCQ, activity = Activity(ActivityType.VR))
        val settings = SourceSettings(showExternalActivity = false)
        assertNull(PresenceResolver.resolve(listOf(vrcq), T0, settings).activity)
        val manual = state(activity = Activity(ActivityType.READING))
        assertEquals(ActivityType.READING, PresenceResolver.resolve(listOf(vrcq, manual), T0, settings).activity?.type)
    }

    @Test fun `expiresAt is the earliest contributing expiry`() {
        val manual = state(mood = Mood.HAPPY, expires = T0.plus(hours(8)))
        val vrcq = state(PresenceSource.VRCQ, activity = Activity(ActivityType.VR), expires = T0.plus(hours(1)))
        assertEquals(T0.plus(hours(1)), PresenceResolver.resolve(listOf(manual, vrcq), T0).expiresAt)
    }

    @Test fun `visual hints merge per field`() {
        val manual = state(mood = Mood.SLEEPY, visual = VisualOverride(props = listOf(Prop.TEA)))
        val vrcq = state(PresenceSource.VRCQ, visual = VisualOverride(headAccessory = HeadAccessory.VR_HEADSET))
        val v = PresenceResolver.resolve(listOf(manual, vrcq), T0).visual
        assertEquals(listOf(Prop.TEA), v.props)
        assertEquals(HeadAccessory.VR_HEADSET, v.headAccessory)
    }

    @Test fun `blank note is treated as no note`() {
        assertNull(PresenceResolver.resolve(listOf(state(note = "   ", mood = Mood.GOOD)), T0).note)
    }

    @Test fun `vrcq payload normalizes into activity and visual hints only`() {
        val payload = VrcqBridgePayload(
            activity = VrcqBridgePayload.Activity(ActivityType.VR, "VRChat"),
            visualHints = VrcqBridgePayload.VisualHints(headAccessory = HeadAccessory.VR_HEADSET),
        )
        val s = EnvelopeNormalizer.fromVrcq(payload, T0)
        assertEquals(PresenceSource.VRCQ, s.source)
        assertEquals(80, s.priority)
        assertNull(s.mood)
        assertNull(s.availability)
        assertNull(s.note)
        assertNull(s.activity?.joinUrl)
        assertEquals(T0.plus(EnvelopeNormalizer.BRIDGE_TTL), s.expiresAt)

        val manual = state(mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY)
        val r = PresenceResolver.resolve(listOf(manual, s), T0)
        assertEquals(Mood.SLEEPY, r.mood)
        assertEquals(ActivityType.VR, r.activity?.type)
        assertEquals(HeadAccessory.VR_HEADSET, r.visual.headAccessory)
    }

    @Test fun `composer derives expression from mood then availability`() {
        val base = AvatarConfig(expression = Expression.HAPPY)
        assertEquals(Expression.SLEEPY, AvatarComposer.compose(base, ResolvedPresence(mood = Mood.SLEEPY)).expression)
        assertEquals(Expression.DND, AvatarComposer.compose(base, ResolvedPresence(availability = Availability.DO_NOT_DISTURB)).expression)
        assertEquals(Expression.HAPPY, AvatarComposer.compose(base, ResolvedPresence.EMPTY).expression)
        val explicit = ResolvedPresence(mood = Mood.SAD, visual = VisualOverride(expression = Expression.MISCHIEVOUS))
        assertEquals(Expression.MISCHIEVOUS, AvatarComposer.compose(base, explicit).expression)
    }

    @Test fun `every mood maps to an expression`() {
        Mood.entries.forEach { assertNotNull(AvatarComposer.expressionFor(it, null)) }
    }
}
