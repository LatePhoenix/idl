package app.idl

import android.graphics.Bitmap
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.avatar.AvatarBadges
import app.idl.avatar.AvatarRenderer
import app.idl.avatar.RenderContrast
import app.idl.data.local.FriendTetherEntity
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
import app.idl.domain.Activity
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.BaseForm
import app.idl.domain.Expression
import app.idl.domain.FaceAccessory
import app.idl.domain.HeadAccessory
import app.idl.domain.Mood
import app.idl.domain.PresenceResolver
import app.idl.domain.PrivacyFilter
import app.idl.domain.PrivacyRules
import app.idl.domain.QuickState
import app.idl.domain.Relationship
import app.idl.domain.ResolvedPresence
import app.idl.domain.Scene
import app.idl.domain.VisualOverride
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.WallpaperContrastMode
import app.idl.widget.WidgetData
import app.idl.widget.WidgetModel
import app.idl.widget.WidgetRenderInputs
import java.io.File
import java.io.FileOutputStream
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

    @Test fun pinningYouDoesNotChangeTheFriendWidget() = runTest {
        val db = IdlDatabase.inMemory(context)
        val dao = db.dao()
        val backend = FakeIdlBackend(InMemoryFakeWorldStore(), clock, MockPushSource(), backgroundScope, autoAcceptDelay = null)
        SessionRepository(db, dao, backend, LocalAuthGateway, noWidgets).register("Matt", "matt")
        val presence = PresenceRepository(dao, backend, clock, noWidgets, noScheduler)
        FriendsRepository(dao, backend, presence, noWidgets).refresh()
        presence.refreshAllFriends()
        dao.upsertWidgetSubscription(WidgetSubscriptionEntity(7, WidgetData.KIND_SOLO, "u_ari"))

        val unpinned = WidgetData.friend(dao, clock, 7)
        dao.upsertTether(FriendTetherEntity("u_ari", hasLocalWidgetInstalled = true, hasRemoteWidgetInstalled = true, tetherActivatedEpochMs = now.toEpochMilli()))
        val pinned = WidgetData.friend(dao, clock, 7)

        assertEquals(unpinned, pinned)
        listOf(unpinned, pinned).forEach { model ->
            val text = model.contentDescription.lowercase()
            listOf("resonat", "tether", "pinned").forEach { word ->
                assertFalse(text.contains(word))
            }
        }
        val registry = AssetPacks.registry { path ->
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
        val target = WidgetRenderInputs.targetFor(110f, 110f)
        fun render(model: WidgetModel): Bitmap {
            val inputs = checkNotNull(WidgetRenderInputs.from(model, registry, target))
            val resolved = AvatarResolver(registry).resolve(inputs.request(WallpaperContrastMode.DARK_WALLPAPER))
            return AvatarRenderer.bitmap(
                resolved,
                registry,
                256,
                AvatarBadges(),
                RenderContrast(wallpaper = WallpaperContrastMode.DARK_WALLPAPER),
            )
        }
        assertTrue(render(unpinned).sameAs(render(pinned)))
        db.close()
    }
}

/** Widget pixels: resolver layers, then the procedural painter. Not the v1 [AvatarSpec] plan. */
@RunWith(AndroidJUnit4::class)
class WidgetRenderPathTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val registry = AssetPacks.registry { path ->
        context.assets.open(path).bufferedReader().use { it.readText() }
    }

    @Test fun availabilityGlyphsDifferOnTheWidgetPath() {
        val resting = bitmap(null, null, RenderTarget.STANDARD_WIDGET)
        val renders = Availability.entries.associateWith { bitmap(it, null, RenderTarget.STANDARD_WIDGET) }
        renders.forEach { (availability, render) ->
            assertFalse("$availability matches a widget with no availability", render.sameAs(resting))
        }
        val values = Availability.entries
        for (i in values.indices) for (j in i + 1 until values.size) {
            assertFalse("${values[i]} vs ${values[j]}", renders.getValue(values[i]).sameAs(renders.getValue(values[j])))
        }
        val compactBusy = bitmap(Availability.BUSY, null, RenderTarget.COMPACT_WIDGET)
        val compactRest = bitmap(null, null, RenderTarget.COMPACT_WIDGET)
        assertFalse(compactBusy.sameAs(compactRest))
        val defaultBusy = bitmap(Availability.BUSY, null, WidgetRenderInputs.targetFor(110f, 110f))
        val largeBusy = bitmap(Availability.BUSY, ActivityType.VR, RenderTarget.LARGE_WIDGET)
        assertFalse(largeBusy.sameAs(bitmap(Availability.BUSY, null, RenderTarget.LARGE_WIDGET)))
        assertFalse(defaultBusy.sameAs(resting))
        save("widget-2x2-default-busy.png", defaultBusy)
        save("widget-2x2-busy-vr.png", largeBusy)
        save("widget-standard-resting.png", resting)
    }

    @Test fun vrHeadsetSurvivesSignatureGlassesOnTheWidgetPath() {
        val glasses = bitmap(null, null, RenderTarget.STANDARD_WIDGET, glasses = true)
        val vr = bitmap(Availability.TEXT_ONLY, ActivityType.VR, RenderTarget.STANDARD_WIDGET, glasses = true)
        assertFalse(glasses.sameAs(vr))
        save("widget-glasses.png", glasses)
        save("widget-vr-over-glasses.png", vr)
    }

    @Test fun nonCloseVrHeadsetAndSleepyBlanketAtTheDefaultWidgetSize() {
        val now = Instant.parse("2026-10-04T15:00:00Z")
        val target = WidgetRenderInputs.targetFor(110f, 110f)
        assertEquals(RenderTarget.STANDARD_WIDGET, target)

        val moSaved = AvatarConfig(baseForm = BaseForm.ROBOT, faceAccessory = FaceAccessory.GLASSES)
        val vr = PresenceResolver.resolve(listOf(QuickState.ALL.first { it.id == "vr" }.toState(now)), now)
        val moView = checkNotNull(
            PrivacyFilter.viewFor(
                "me", "u_mo", moSaved, vr, PrivacyRules.DEFAULT,
                Relationship(isFriend = true, isCloseFriend = false), false,
            ),
        )
        val mo = widgetBitmap(WidgetModel(title = "Mo", friendView = moView, deepLink = "idl://friend/u_mo"), target)
        val glassesOnly = widgetBitmap(WidgetModel(title = "Mo", restingAvatar = moSaved, deepLink = "idl://friend/u_mo"), target)
        assertFalse(mo.sameAs(glassesOnly))
        save("widget-vr-over-glasses-nonclose.png", mo)

        val ariSaved = AvatarConfig(baseForm = BaseForm.FOX)
        val sleepy = PresenceResolver.resolve(listOf(QuickState.ALL.first { it.id == "sleepy" }.toState(now)), now)
        val ariView = checkNotNull(
            PrivacyFilter.viewFor(
                "me", "u_ari", ariSaved, sleepy, PrivacyRules.DEFAULT,
                Relationship(isFriend = true, isCloseFriend = true), false,
            ),
        )
        val ari = widgetBitmap(WidgetModel(title = "Ari", friendView = ariView, deepLink = "idl://friend/u_ari"), target)
        val resting = widgetBitmap(WidgetModel(title = "Ari", restingAvatar = ariSaved, deepLink = "idl://friend/u_ari"), target)
        assertFalse(ari.sameAs(resting))
        save("widget-2x2-default-sleepy.png", ari)
    }

    private fun widgetBitmap(model: WidgetModel, target: RenderTarget): Bitmap {
        val inputs = checkNotNull(WidgetRenderInputs.from(model, registry, target))
        val resolved = AvatarResolver(registry).resolve(inputs.request(WallpaperContrastMode.DARK_WALLPAPER))
        return AvatarRenderer.bitmap(
            resolved,
            registry,
            256,
            AvatarBadges(),
            RenderContrast(wallpaper = WallpaperContrastMode.DARK_WALLPAPER),
        )
    }

    private fun bitmap(
        availability: Availability?,
        activity: ActivityType?,
        target: RenderTarget,
        glasses: Boolean = false,
    ): Bitmap {
        val model = WidgetModel(
            title = "Ari",
            restingAvatar = AvatarConfig(faceAccessory = if (glasses) FaceAccessory.GLASSES else FaceAccessory.NONE),
            selfPresence = ResolvedPresence(
                availability = availability,
                activity = activity?.let { Activity(type = it) },
                visual = if (activity == ActivityType.VR) {
                    VisualOverride(headAccessory = HeadAccessory.VR_HEADSET, scene = Scene.NEON_CITY)
                } else {
                    VisualOverride()
                },
            ),
            deepLink = "idl://status",
        )
        val inputs = checkNotNull(WidgetRenderInputs.from(model, registry, target))
        val resolved = AvatarResolver(registry).resolve(inputs.request(WallpaperContrastMode.DARK_WALLPAPER))
        return AvatarRenderer.bitmap(
            resolved,
            registry,
            256,
            AvatarBadges(),
            RenderContrast(wallpaper = WallpaperContrastMode.DARK_WALLPAPER),
        )
    }

    private fun save(name: String, bitmap: Bitmap) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(dir, name)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("idl.widget.shot", file.absolutePath)
    }
}
