package app.idl.domain.avatar

/**
 * How the user wants widget outlines to sit on the wallpaper.
 * Stored locally. [AUTO] follows the system wallpaper; the others override it.
 */
enum class WallpaperContrastPreference {
    AUTO, LIGHT, DARK;

    companion object {
        fun fromStored(raw: String?): WallpaperContrastPreference =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUTO
    }
}

/**
 * [supportsDarkText] is true when the system wallpaper asks for dark content
 * (`HINT_SUPPORTS_DARK_TEXT`). False means colors were read and the hint is absent.
 * Null means there were no colors, or the device is API 26–30 and cannot report hints.
 * Either of those falls back to a dark wallpaper. Light and Dark ignore the hint.
 */
fun wallpaperContrastMode(
    preference: WallpaperContrastPreference,
    supportsDarkText: Boolean?,
): WallpaperContrastMode = when (preference) {
    WallpaperContrastPreference.LIGHT -> WallpaperContrastMode.LIGHT_WALLPAPER
    WallpaperContrastPreference.DARK -> WallpaperContrastMode.DARK_WALLPAPER
    WallpaperContrastPreference.AUTO ->
        if (supportsDarkText == true) WallpaperContrastMode.LIGHT_WALLPAPER
        else WallpaperContrastMode.DARK_WALLPAPER
}
