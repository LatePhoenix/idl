package app.idl.widget

import android.graphics.Bitmap
import app.idl.avatar.AvatarBadges
import app.idl.avatar.AvatarRenderer
import app.idl.avatar.RenderContrast
import app.idl.domain.Activity
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.BaseForm
import app.idl.domain.FaceAccessory
import app.idl.domain.HeadAccessory
import app.idl.domain.PresenceResolver
import app.idl.domain.PrivacyFilter
import app.idl.domain.PrivacyRules
import app.idl.domain.QuickState
import app.idl.domain.Relationship
import app.idl.domain.ResolvedPresence
import app.idl.domain.Scene
import app.idl.domain.VisualOverride
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.ResolvedAvatar
import app.idl.domain.avatar.WallpaperContrastMode
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.has
import app.idl.domain.wire
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.ByteBuffer
import java.time.Instant

/**
 * JVM snapshots of the widget bitmap path. The device test
 * [app.idl.WidgetRenderPathTest] draws the same way: inputs, resolver, then
 * [AvatarRenderer.bitmap] at 256 px.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class WidgetSnapshotTest {
    private val registry = coreRegistry()
    private val now = Instant.parse("2026-10-04T15:00:00Z")

    @Test fun `every availability differs at the default 2x2 size`() {
        val target = WidgetRenderInputs.targetFor(110f, 110f)
        assertEquals(RenderTarget.STANDARD_WIDGET, target)
        val rendered = Availability.entries.map { availability ->
            val (resolved, bitmap) = draw(self(availability), target)
            assertTrue(resolved.has("avail_${availability.wire}"))
            snap("avail_${availability.wire}", bitmap)
            availability to pixels(bitmap)
        }
        for (i in rendered.indices) {
            for (j in i + 1 until rendered.size) {
                assertFalse(
                    "${rendered[i].first} and ${rendered[j].first} rendered the same bitmap",
                    rendered[i].second.contentEquals(rendered[j].second),
                )
            }
        }
    }

    @Test fun `busy VR covers compact standard and large`() {
        val model = busyVr()
        listOf(
            70f to RenderTarget.COMPACT_WIDGET,
            110f to RenderTarget.STANDARD_WIDGET,
            180f to RenderTarget.LARGE_WIDGET,
        ).forEach { (dp, expected) ->
            val target = WidgetRenderInputs.targetFor(dp, dp)
            assertEquals(expected, target)
            val (resolved, bitmap) = draw(model, target)
            assertTrue(resolved.has("avail_busy"))
            assertTrue(resolved.has("head_vr_headset"))
            snap("busy_vr_${expected.name.lowercase()}", bitmap)
        }
    }

    @Test fun `non-close friend keeps the VR headset over signature glasses`() {
        val target = WidgetRenderInputs.targetFor(110f, 110f)
        assertEquals(RenderTarget.STANDARD_WIDGET, target)
        val saved = AvatarConfig(baseForm = BaseForm.ROBOT, faceAccessory = FaceAccessory.GLASSES)
        val vr = PresenceResolver.resolve(listOf(QuickState.ALL.first { it.id == "vr" }.toState(now)), now)
        val view = checkNotNull(
            PrivacyFilter.viewFor(
                "me", "u_mo", saved, vr, PrivacyRules.DEFAULT,
                Relationship(isFriend = true, isCloseFriend = false), false,
            ),
        )
        val (resolved, bitmap) = draw(
            WidgetModel(title = "Mo", friendView = view, deepLink = "idl://friend/u_mo"),
            target,
        )
        assertTrue(resolved.has("head_vr_headset"))
        assertFalse(resolved.has("face_glasses_round"))
        snap("vr_over_glasses_nonclose", bitmap)
    }

    @Test fun `sleepy at the default size keeps the blanket and tea`() {
        val target = WidgetRenderInputs.targetFor(110f, 110f)
        assertEquals(RenderTarget.STANDARD_WIDGET, target)
        val (resolved, bitmap) = draw(sleepyAri(), target)
        assertTrue(resolved.has("body_blanket"))
        assertTrue(resolved.has("prop_tea"))
        snap("sleepy_default", bitmap)
    }

    @Test fun `sleepy compact drops the blanket and keeps the tea`() {
        val compactTarget = WidgetRenderInputs.targetFor(70f, 70f)
        val standardTarget = WidgetRenderInputs.targetFor(110f, 110f)
        assertEquals(RenderTarget.COMPACT_WIDGET, compactTarget)
        assertEquals(RenderTarget.STANDARD_WIDGET, standardTarget)
        val (compactResolved, compact) = draw(sleepyAri(), compactTarget)
        val (standardResolved, standard) = draw(sleepyAri(), standardTarget)
        assertFalse(compactResolved.has("body_blanket"))
        assertTrue(compactResolved.has("prop_tea"))
        assertTrue(standardResolved.has("body_blanket"))
        assertTrue(standardResolved.has("prop_tea"))
        assertFalse(pixels(compact).contentEquals(pixels(standard)))
        snap("sleepy_compact", compact)
    }

    @Test fun `light and dark wallpaper contrast differ`() {
        val target = WidgetRenderInputs.targetFor(110f, 110f)
        val model = self(Availability.AVAILABLE)
        val (_, dark) = draw(model, target, WallpaperContrastMode.DARK_WALLPAPER)
        val (_, light) = draw(model, target, WallpaperContrastMode.LIGHT_WALLPAPER)
        snap("contrast_dark", dark)
        snap("contrast_light", light)
        assertFalse(pixels(dark).contentEquals(pixels(light)))
    }

    private fun draw(
        model: WidgetModel,
        target: RenderTarget,
        wallpaper: WallpaperContrastMode = WallpaperContrastMode.DARK_WALLPAPER,
    ): Pair<ResolvedAvatar, Bitmap> {
        val inputs = checkNotNull(WidgetRenderInputs.from(model, registry, target))
        val resolved = AvatarResolver(registry).resolve(inputs.request(wallpaper))
        val bitmap = AvatarRenderer.bitmap(
            resolved,
            registry,
            256,
            AvatarBadges(),
            RenderContrast(wallpaper = wallpaper),
        )
        return resolved to bitmap
    }

    private fun sleepyAri(): WidgetModel {
        val saved = AvatarConfig(baseForm = BaseForm.FOX)
        val sleepy = PresenceResolver.resolve(listOf(QuickState.ALL.first { it.id == "sleepy" }.toState(now)), now)
        val view = checkNotNull(
            PrivacyFilter.viewFor(
                "me", "u_ari", saved, sleepy, PrivacyRules.DEFAULT,
                Relationship(isFriend = true, isCloseFriend = true), false,
            ),
        )
        return WidgetModel(title = "Ari", friendView = view, deepLink = "idl://friend/u_ari")
    }

    private fun self(availability: Availability) = WidgetModel(
        title = "Ari",
        restingAvatar = AvatarConfig(),
        selfPresence = ResolvedPresence(availability = availability),
        deepLink = "idl://status",
    )

    private fun busyVr() = WidgetModel(
        title = "Ari",
        restingAvatar = AvatarConfig(),
        selfPresence = ResolvedPresence(
            availability = Availability.BUSY,
            activity = Activity(type = ActivityType.VR),
            visual = VisualOverride(headAccessory = HeadAccessory.VR_HEADSET, scene = Scene.NEON_CITY),
        ),
        deepLink = "idl://status",
    )

    private fun snap(name: String, bitmap: Bitmap) {
        bitmap.captureRoboImage("src/test/snapshots/widget/$name.png")
    }

    private fun pixels(bitmap: Bitmap): ByteArray {
        val buffer = ByteBuffer.allocate(bitmap.allocationByteCount)
        bitmap.copyPixelsToBuffer(buffer)
        return buffer.array()
    }
}
