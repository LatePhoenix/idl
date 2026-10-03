package app.idl.data

import app.idl.IdlClock
import app.idl.data.push.MockPushSource
import app.idl.data.remote.FakeIdlBackend
import app.idl.data.remote.IdlError
import app.idl.data.remote.IdlException
import app.idl.data.remote.InMemoryFakeWorldStore
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.FriendStatus
import app.idl.domain.Mood
import app.idl.domain.QuickState
import app.idl.domain.ReactionTemplate
import app.idl.domain.T0
import app.idl.domain.state
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FakeIdlBackendTest {
    private var now: Instant = T0
    private val clock = IdlClock { now }

    private fun backend(scope: TestScope, store: InMemoryFakeWorldStore = InMemoryFakeWorldStore()) =
        FakeIdlBackend(store, clock, MockPushSource(), scope, autoAcceptDelay = null)

    private suspend fun expectError(expected: IdlError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: IdlException) {
            assertEquals(expected, e.error)
        }
    }

    @Test fun `register validates usernames`() = runTest {
        val b = backend(this)
        expectError(IdlError.Invalid("username", "3–20 letters, numbers or _")) { b.register("X", "a!") }
        expectError(IdlError.Invalid("username", "That username is taken")) { b.register("Ari 2", "ari") }
        assertEquals("matt", b.register("Matt", "Matt").username)
    }

    @Test fun `calls before registration are unauthorized`() = runTest {
        expectError(IdlError.Unauthorized) { backend(this).friends() }
    }

    @Test fun `friend presence is filtered by each friend's own rules`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        val views = b.friendPresence().associateBy { it.userId }

        // Ari marked me close: sees mood + note.
        assertEquals(Mood.SLEEPY, views.getValue("u_ari").mood)
        assertEquals("napping, text me", views.getValue("u_ari").note)
        // Juno shares activity names with all friends.
        assertEquals("VRChat", views.getValue("u_juno").activityLabel)
        // Mo did not mark me close: availability only.
        val mo = views.getValue("u_mo")
        assertEquals(Availability.BUSY, mo.availability)
        assertNull(mo.mood)
        assertNull(mo.note)
        assertNull(mo.activityType)
        // Sam's status already expired.
        assertFalse(views.getValue("u_sam").hasStatus)
        // Bea is invisible: indistinguishable from no status.
        val bea = views.getValue("u_bea")
        assertFalse(bea.hasStatus)
        assertNull(bea.updatedAt)
        // Pending/unconnected users are not readable.
        assertFalse("u_rin" in views)
        assertFalse("u_kit" in views)
    }

    @Test fun `invite redemption creates a pending request and accept connects`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        val f = b.redeemInvite("kit-2026")
        assertEquals(FriendStatus.OUTGOING, f.status)
        expectError(IdlError.Forbidden) { b.friendPresence("u_kit") }
        expectError(IdlError.Invalid("code", "That invite code isn't valid")) { b.redeemInvite("NOPE") }

        val rin = b.acceptRequest("u_rin")
        assertEquals(FriendStatus.ACCEPTED, rin.status)
        val view = b.friendPresence("u_rin")
        assertEquals(Availability.AVAILABLE, view.availability)
        assertNull(view.mood) // Rin hasn't marked us close yet
        assertNotNull(view.expiresAt)
    }

    @Test fun `blocking revokes access immediately and removes reactions`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        b.simulateIncomingReaction("u_ari", ReactionTemplate.HUG)
        assertEquals(1, b.inbox().size)
        b.block("u_ari")
        assertTrue(b.friends().none { it.userId == "u_ari" })
        assertTrue(b.inbox().isEmpty())
        expectError(IdlError.Forbidden) { b.friendPresence("u_ari") }
        expectError(IdlError.Forbidden) { b.sendReaction("u_ari", ReactionTemplate.HEART) }
        assertEquals(listOf("u_ari"), b.blocked().map { it.userId })
    }

    @Test fun `removing a friend revokes future access`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        b.removeFriend("u_juno")
        expectError(IdlError.Forbidden) { b.friendPresence("u_juno") }
    }

    @Test fun `reactions are sent, received, dismissed and rate limited`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        val r = b.sendReaction("u_ari", ReactionTemplate.COFFEE)
        assertEquals("u_ari", r.recipientId)
        assertEquals(T0.plus(Duration.ofHours(24)), r.expiresAt)
        repeat(9) { b.sendReaction("u_ari", ReactionTemplate.HEART) }
        expectError(IdlError.RateLimited) { b.sendReaction("u_ari", ReactionTemplate.HEART) }

        b.simulateIncomingReaction("u_juno", ReactionTemplate.WANT_COMPANY)
        val inbox = b.inbox()
        assertEquals(1, inbox.size)
        b.dismissReaction(inbox.single().id)
        assertTrue(b.inbox().isEmpty())
    }

    @Test fun `simulated presence change and expiry flow through filtering`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        b.simulatePresence("u_ari", QuickState.ALL.first { it.id == "gaming" })
        assertEquals(ActivityType.GAMING, b.friendPresence("u_ari").activityType)
        b.simulateExpiresSoon("u_ari", 60)
        now = now.plusSeconds(61)
        assertFalse(b.friendPresence("u_ari").hasStatus)
    }

    @Test fun `own presence validation`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        expectError(IdlError.Invalid("expiresAt", "Status must expire in the future")) {
            b.putPresence(state(mood = Mood.HAPPY, expires = T0))
        }
        expectError(IdlError.Invalid("note", "Keep notes under 80 characters")) {
            b.putPresence(state(mood = Mood.HAPPY, note = "x".repeat(81)))
        }
        b.putPresence(state(mood = Mood.HAPPY))
    }

    @Test fun `offline simulation fails every call with a retryable error`() = runTest {
        val b = backend(this).also { it.register("Matt", "matt") }
        b.simulateOffline = true
        expectError(IdlError.Offline) { b.friends() }
        assertTrue(IdlError.Offline.retryable)
    }

    @Test fun `world persists across instances`() = runTest {
        val store = InMemoryFakeWorldStore()
        backend(this, store).apply { register("Matt", "matt"); acceptRequest("u_rin") }
        val again = backend(this, store)
        assertEquals("matt", again.me().username)
        assertTrue(again.friends().any { it.userId == "u_rin" && it.status == FriendStatus.ACCEPTED })
    }
}
