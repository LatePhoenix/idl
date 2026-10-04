package app.idl

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.avatar.AvatarBadges
import app.idl.avatar.AvatarRenderer
import app.idl.data.local.IdlDatabase
import app.idl.data.local.WidgetSubscriptionEntity
import app.idl.data.push.MockPushSource
import app.idl.data.remote.FakeIdlBackend
import app.idl.data.remote.InMemoryFakeWorldStore
import app.idl.data.remote.supabase.LocalAuthGateway
import app.idl.data.repo.FriendsRepository
import app.idl.data.repo.PresenceRepository
import app.idl.data.repo.SessionRepository
import app.idl.data.repo.SyncScheduler
import app.idl.data.repo.WidgetRefresher
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.BaseForm
import app.idl.domain.Expression
import app.idl.domain.Mood
import app.idl.domain.QuickState
import app.idl.widget.WidgetData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AvatarRendererTest {

    @Test fun everyBaseAndExpressionRendersAtWidgetAndThumbnailSizes() {
        for (base in BaseForm.entries) for (expr in Expression.entries) for (size in listOf(48, 256)) {
            val bmp = AvatarRenderer.bitmap(
                AvatarConfig(baseForm = base, expression = expr),
                size,
                AvatarBadges(Availability.TEXT_ONLY, ActivityType.VR),
            )
            assertEquals(size, bmp.width)
        }
    }

    @Test fun availabilityGlyphsDifferAtCompactSize() {
        val renders = Availability.entries.map { availability ->
            AvatarRenderer.bitmap(AvatarConfig(), 48, AvatarBadges(availability, null))
        }
        for (i in renders.indices) for (j in i + 1 until renders.size) {
            assertFalse("${Availability.entries[i]} vs ${Availability.entries[j]}", renders[i].sameAs(renders[j]))
        }
    }

    @Test fun expressionsAreVisuallyDistinct() {
        val renders = Expression.entries.map { AvatarRenderer.bitmap(AvatarConfig(expression = it), 128) }
        for (i in renders.indices) for (j in i + 1 until renders.size) {
            assertFalse("${Expression.entries[i]} vs ${Expression.entries[j]}", renders[i].sameAs(renders[j]))
        }
    }

    @Test fun renderingIsDeterministic() {
        val cfg = AvatarConfig(baseForm = BaseForm.FOX, expression = Expression.SLEEPY)
        assertTrue(AvatarRenderer.bitmap(cfg, 200).sameAs(AvatarRenderer.bitmap(cfg, 200)))
    }
}

@RunWith(AndroidJUnit4::class)
class CacheAndWidgetDataTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var now: Instant = Instant.parse("2026-10-03T14:00:00Z")
    private val clock = IdlClock { now }
    private val noWidgets = object : WidgetRefresher {
        var friendUpdates = mutableListOf<String>()
        override suspend fun selfChanged() = Unit
        override suspend fun friendsChanged(userIds: Collection<String>) { friendUpdates += userIds }
        override suspend fun all() = Unit
    }
    private val noScheduler = object : SyncScheduler {
        var retries = 0
        override fun scheduleRetry() { retries++ }
        override fun scheduleExpiry(at: Instant?) = Unit
    }

    @Test fun offlineStatusIsSavedLocallyAndSyncedLater() = runTest {
        val db = IdlDatabase.inMemory(context)
        val backend = FakeIdlBackend(InMemoryFakeWorldStore(), clock, MockPushSource(), backgroundScope, autoAcceptDelay = null)
        SessionRepository(db, db.dao(), backend, LocalAuthGateway, noWidgets).register("Matt", "matt")
        val presence = PresenceRepository(db.dao(), backend, clock, noWidgets, noScheduler)

        backend.simulateOffline = true
        val state = QuickState.ALL.first { it.id == "sleepy" }.toState(now)
        assertTrue(presence.setStatus(state).isFailure)
        assertEquals(listOf(state), presence.ownStates.first())
        assertTrue(presence.pendingSync.first())
        assertEquals(1, noScheduler.retries)

        // Widget reads the local state while offline.
        val self = WidgetData.self(db.dao(), clock)
        assertEquals(Mood.SLEEPY, self.mood)

        backend.simulateOffline = false
        assertTrue(presence.flushPending())
        assertFalse(presence.pendingSync.first())
        db.close()
    }

    @Test fun friendWidgetShowsLastKnownThenRevertsOnExpiryAndPurgesOnRemoval() = runTest {
        val db = IdlDatabase.inMemory(context)
        val dao = db.dao()
        val backend = FakeIdlBackend(InMemoryFakeWorldStore(), clock, MockPushSource(), backgroundScope, autoAcceptDelay = null)
        SessionRepository(db, dao, backend, LocalAuthGateway, noWidgets).register("Matt", "matt")
        val presence = PresenceRepository(dao, backend, clock, noWidgets, noScheduler)
        val friends = FriendsRepository(dao, backend, presence, noWidgets)
        friends.refresh()
        presence.refreshAllFriends()
        dao.upsertWidgetSubscription(WidgetSubscriptionEntity(7, WidgetData.KIND_SOLO, "u_ari"))

        val fresh = WidgetData.friend(dao, clock, 7)
        assertEquals("Ari", fresh.title)
        assertEquals(Availability.TEXT_ONLY, fresh.availability)
        assertEquals("napping, text me", fresh.note)
        assertEquals("idl://friend/u_ari", fresh.deepLink)

        // Offline: no refresh possible, but expiry still applies to the cached state.
        backend.simulateOffline = true
        now = now.plusSeconds(8 * 3600)
        val expired = WidgetData.friend(dao, clock, 7)
        assertNull(expired.availability)
        assertNull(expired.note)
        assertEquals("No status", expired.subtitle)

        // Friend removes us: next fetch purges the cache and the widget says so.
        backend.simulateOffline = false
        backend.simulateFriendRemovedMe("u_ari")
        presence.refreshFriend("u_ari")
        assertNull(dao.friendPresenceNow("u_ari"))
        val gone = WidgetData.friend(dao, clock, 7)
        assertEquals("Not connected", gone.title)
        assertNotEquals(-1, noWidgets.friendUpdates.indexOf("u_ari"))
        db.close()
    }
}
