package app.idl.domain.avatar

import app.idl.avatar.RenderCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class WallpaperContrastTest {
    @Test fun `auto maps a dark-text hint to a light wallpaper`() {
        assertEquals(
            WallpaperContrastMode.LIGHT_WALLPAPER,
            wallpaperContrastMode(WallpaperContrastPreference.AUTO, supportsDarkText = true),
        )
    }

    @Test fun `auto maps a missing hint to a dark wallpaper`() {
        assertEquals(
            WallpaperContrastMode.DARK_WALLPAPER,
            wallpaperContrastMode(WallpaperContrastPreference.AUTO, supportsDarkText = false),
        )
    }

    @Test fun `auto maps null colors and api 26 through 30 to a dark wallpaper`() {
        assertEquals(
            WallpaperContrastMode.DARK_WALLPAPER,
            wallpaperContrastMode(WallpaperContrastPreference.AUTO, supportsDarkText = null),
        )
    }

    @Test fun `light and dark settings override the wallpaper hint`() {
        for (hint in listOf(true, false, null)) {
            assertEquals(
                WallpaperContrastMode.LIGHT_WALLPAPER,
                wallpaperContrastMode(WallpaperContrastPreference.LIGHT, hint),
            )
            assertEquals(
                WallpaperContrastMode.DARK_WALLPAPER,
                wallpaperContrastMode(WallpaperContrastPreference.DARK, hint),
            )
        }
    }

    @Test fun `an unknown stored setting falls back to auto`() {
        assertEquals(WallpaperContrastPreference.AUTO, WallpaperContrastPreference.fromStored(null))
        assertEquals(WallpaperContrastPreference.AUTO, WallpaperContrastPreference.fromStored("nope"))
        assertEquals(WallpaperContrastPreference.LIGHT, WallpaperContrastPreference.fromStored("light"))
    }

    @Test fun `light and dark wallpaper modes produce different render keys`() {
        val registry = coreRegistry()
        val resolver = AvatarResolver(registry)
        val light = resolver.resolve(request().copy(wallpaperContrastMode = WallpaperContrastMode.LIGHT_WALLPAPER))
        val dark = resolver.resolve(request().copy(wallpaperContrastMode = WallpaperContrastMode.DARK_WALLPAPER))
        assertEquals(light.layers, dark.layers)
        assertNotEquals(light.renderKey, dark.renderKey)
        assertNotEquals(
            RenderCache.keyOf("${light.renderKey}|256"),
            RenderCache.keyOf("${dark.renderKey}|256"),
        )
    }
}
