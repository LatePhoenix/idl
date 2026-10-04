package app.idl.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import app.idl.container
import app.idl.domain.avatar.WallpaperContrastMode
import app.idl.domain.avatar.wallpaperContrastMode

/**
 * Reads the system wallpaper once per widget render. [WallpaperColors.getColorHints] is API 31,
 * so API 26–30 and a null color result both report no hint and the domain maps that to dark.
 */
class SystemWallpaperContrast(private val context: Context) {
    suspend fun mode(): WallpaperContrastMode {
        val preference = context.container.settings.wallpaperContrastNow()
        return wallpaperContrastMode(preference, supportsDarkText())
    }

    fun supportsDarkText(): Boolean? {
        if (Build.VERSION.SDK_INT < 31) return null
        return supportsDarkTextApi31()
    }

    /** While this process is alive, wallpaper color changes refresh widgets. No polling. */
    fun listen(onSystemWallpaperChanged: () -> Unit) {
        if (Build.VERSION.SDK_INT < 27) return
        listenApi27(onSystemWallpaperChanged)
    }

    @RequiresApi(31)
    private fun supportsDarkTextApi31(): Boolean? {
        val colors = try {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        } catch (e: RuntimeException) {
            null
        } ?: return null
        return (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
    }

    @RequiresApi(27)
    private fun listenApi27(onSystemWallpaperChanged: () -> Unit) {
        val manager = WallpaperManager.getInstance(context)
        manager.addOnColorsChangedListener({ _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) onSystemWallpaperChanged()
        }, Handler(Looper.getMainLooper()))
    }
}
