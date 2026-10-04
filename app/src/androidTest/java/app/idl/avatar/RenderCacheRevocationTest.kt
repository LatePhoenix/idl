package app.idl.avatar

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.IdlClock
import app.idl.data.local.IdlDatabase
import app.idl.data.push.MockPushSource
import app.idl.data.remote.FakeIdlBackend
import app.idl.data.remote.InMemoryFakeWorldStore
import app.idl.data.remote.supabase.LocalAuthGateway
import app.idl.data.repo.FriendsRepository
import app.idl.data.repo.PresenceRepository
import app.idl.data.repo.SessionRepository
import app.idl.data.repo.SyncScheduler
import app.idl.data.repo.WidgetRefresher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RenderCacheRevocationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun signOutEmptiesTheCache() = runTest {
        val root = File(context.cacheDir, "signout-${System.nanoTime()}")
        val renders = RenderCache(root)
        renders.bitmap("abc", "u_ari") { tiny() }
        renders.bitmap("abc", RenderCache.OWNER_SELF) { tiny() }
        assertTrue(File(root, "u_ari/abc.png").isFile)
        val db = IdlDatabase.inMemory(context)
        val backend = FakeIdlBackend(InMemoryFakeWorldStore(), IdlClock { Instant.parse("2026-10-04T12:00:00Z") }, MockPushSource(), backgroundScope, autoAcceptDelay = null)
        SessionRepository(db, db.dao(), backend, LocalAuthGateway, noWidgets, renders).signOut()
        assertTrue(root.walkTopDown().none { it.isFile })
        db.close()
    }

    @Test fun removingAFriendDeletesOnlyThatFriendsRenders() = runTest {
        val root = File(context.cacheDir, "purge-${System.nanoTime()}")
        val renders = RenderCache(root)
        renders.bitmap("abc", "u_ari") { tiny() }
        renders.bitmap("abc", "u_bo") { tiny() }
        val db = IdlDatabase.inMemory(context)
        val dao = db.dao()
        val clock = IdlClock { Instant.parse("2026-10-04T12:00:00Z") }
        val backend = FakeIdlBackend(InMemoryFakeWorldStore(), clock, MockPushSource(), backgroundScope, autoAcceptDelay = null)
        SessionRepository(db, dao, backend, LocalAuthGateway, noWidgets, renders).register("Matt", "matt")
        val presence = PresenceRepository(dao, backend, clock, noWidgets, noScheduler, renders)
        val friends = FriendsRepository(dao, backend, presence, noWidgets, renders)
        assertTrue(friends.refresh().isSuccess)
        assertTrue(friends.remove("u_ari").isSuccess)
        assertFalse(File(root, "u_ari").exists())
        assertTrue(File(root, "u_bo/abc.png").isFile)
        db.close()
    }

    private fun tiny() = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    private val noWidgets = object : WidgetRefresher {
        override suspend fun selfChanged() = Unit
        override suspend fun friendsChanged(userIds: Collection<String>) = Unit
        override suspend fun all() = Unit
    }
    private val noScheduler = object : SyncScheduler {
        override fun scheduleRetry() = Unit
        override fun scheduleExpiry(at: Instant?) = Unit
    }
}
