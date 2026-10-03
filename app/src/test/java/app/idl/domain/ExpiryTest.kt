package app.idl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

class ExpiryTest {

    @Test fun `default duration applies when none requested`() {
        assertEquals(T0.plus(Expiry.DEFAULT), Expiry.expiresAt(T0, null))
    }

    @Test fun `durations are clamped to min and max`() {
        assertEquals(T0.plus(Expiry.MAX), Expiry.expiresAt(T0, Duration.ofDays(30)))
        assertEquals(T0.plus(Expiry.MIN), Expiry.expiresAt(T0, Duration.ofSeconds(1)))
        assertEquals(T0.plus(Expiry.MIN), Expiry.expiresAt(T0, Duration.ofHours(-3)))
    }

    @Test fun `every status expires`() {
        Expiry.PRESETS.forEach { assertTrue(Expiry.expiresAt(T0, it).isAfter(T0)) }
        QuickState.ALL.forEach { assertTrue(it.toState(T0).expiresAt.isAfter(T0)) }
    }

    @Test fun `isLive is false at and after expiry`() {
        val s = state(expires = T0.plus(hours(1)))
        assertTrue(s.isLive(T0))
        assertFalse(s.isLive(T0.plus(hours(1))))
        assertFalse(s.isLive(T0.plus(hours(2))))
    }

    @Test fun `next expiry ignores past and null`() {
        val next = Expiry.nextExpiry(listOf(null, T0.minusSeconds(5), T0.plus(hours(3)), T0.plus(hours(1))), T0)
        assertEquals(T0.plus(hours(1)), next)
        assertNull(Expiry.nextExpiry(listOf(T0.minusSeconds(1)), T0))
    }

    @Test fun `remaining and ago labels`() {
        assertEquals("3h left", Expiry.remaining(T0.plus(hours(3)).plusSeconds(30), T0))
        assertEquals("12m left", Expiry.remaining(T0.plusSeconds(12 * 60 + 5), T0))
        assertEquals("<1m left", Expiry.remaining(T0.plusSeconds(20), T0))
        assertNull(Expiry.remaining(T0, T0))
        assertEquals("just now", Expiry.ago(T0, T0.plusSeconds(10)))
        assertEquals("2d ago", Expiry.ago(T0, T0.plus(Duration.ofDays(2))))
        assertEquals("8h", Expiry.label(Duration.ofHours(8)))
        assertEquals("30m", Expiry.label(Duration.ofMinutes(30)))
        assertEquals("1d", Expiry.label(Duration.ofHours(24)))
    }

    @Test fun `cached friend view reverts to resting avatar once expired`() {
        val resting = AvatarConfig(baseForm = BaseForm.CAT)
        val live = PresenceView(
            userId = "u", avatar = resting.copy(expression = Expression.SLEEPY, handProp = Prop.TEA), restingAvatar = resting,
            mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY, note = "nap", expiresAt = T0.plus(hours(1)),
        )
        assertEquals(live, live.expiredAt(T0))
        val expired = live.expiredAt(T0.plus(hours(1)))
        assertEquals(resting, expired.avatar)
        assertFalse(expired.hasStatus)
        assertNull(expired.note)
    }

    @Test fun `status draft converts to a clamped, trimmed manual state`() {
        val d = StatusDraft(
            mood = Mood.SLEEPY, intent = StatusIntent.NO_PREFERENCE, activity = ActivityType.NONE,
            note = "  " + "x".repeat(100), duration = Duration.ofDays(10), prop = Prop.TEA,
        )
        val s = d.toState(T0)
        assertEquals(PresenceSource.MANUAL, s.source)
        assertEquals(T0.plus(Expiry.MAX), s.expiresAt)
        assertEquals(StatusDraft.NOTE_MAX, s.note!!.length)
        assertNull(s.intent)
        assertNull(s.activity)
        assertEquals(listOf(Prop.TEA), s.visual?.props)
    }

    @Test fun `status draft round trips through a live state`() {
        val q = QuickState.ALL.first { it.id == "sleepy" }
        val s = StatusDraft.from(q).copy(note = "nap").toState(T0)
        val back = StatusDraft.from(s, T0.plus(Duration.ofMinutes(1)))
        assertEquals(Mood.SLEEPY, back.mood)
        assertEquals("nap", back.note)
        assertEquals(Duration.ofHours(8), back.duration)
        assertEquals(StatusDraft(), StatusDraft.from(s, s.expiresAt))
    }
}
