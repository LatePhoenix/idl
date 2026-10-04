package app.idl.widget

import app.idl.domain.AvatarConfig
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.AvatarRenderRequest
import app.idl.domain.avatar.LegacyAvatarMigration
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.VisiblePresence
import app.idl.domain.avatar.WallpaperContrastMode
import kotlin.math.max
import kotlin.math.min

/**
 * What a widget asks the resolver to draw. Identity is the resting avatar; status arrives as
 * [presence] and is never sanitized into the saved configuration.
 */
data class WidgetRenderInputs(
    val configuration: AvatarConfiguration,
    val presence: VisiblePresence,
    val target: RenderTarget,
) {
    fun request(wallpaper: WallpaperContrastMode = WallpaperContrastMode.NONE) = AvatarRenderRequest(
        configuration = configuration,
        presence = presence,
        target = target,
        sizePx = target.defaultSizePx,
        wallpaperContrastMode = wallpaper,
    )

    companion object {
        /**
         * Glance cell size in dp. A 1×1 cell simplifies as compact, a wide cell as standard,
         * and a square 2×2 as large.
         */
        fun targetFor(widthDp: Float, heightDp: Float): RenderTarget {
            val short = min(widthDp, heightDp)
            val long = max(widthDp, heightDp)
            return when {
                short >= 160f -> RenderTarget.LARGE_WIDGET
                long >= 200f -> RenderTarget.STANDARD_WIDGET
                else -> RenderTarget.COMPACT_WIDGET
            }
        }

        fun from(model: WidgetModel, registry: AssetRegistry, target: RenderTarget): WidgetRenderInputs? {
            val resting: AvatarConfig = model.friendView?.restingAvatar ?: model.restingAvatar ?: return null
            val presence = when {
                model.friendView != null -> LegacyAvatarMigration.presence(model.friendView)
                model.selfPresence != null -> LegacyAvatarMigration.presence(model.selfPresence)
                else -> VisiblePresence.NONE
            }
            return WidgetRenderInputs(
                configuration = LegacyAvatarMigration.migrate(resting, registry),
                presence = presence,
                target = target,
            )
        }
    }
}
