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
         * Avatar image drawn inside a Glance cell, in dp. The default 2×2 draws 76, the wide
         * cell draws 92, and a square cell draws 120. Anything smaller stays at 48 for a future 1×1.
         */
        fun avatarDrawnDp(widthDp: Float, heightDp: Float): Float = when {
            min(widthDp, heightDp) >= 180f -> 120f
            max(widthDp, heightDp) >= 250f -> 92f
            min(widthDp, heightDp) >= 110f -> 76f
            else -> 48f
        }

        /**
         * Render target from the avatar's drawn size. 76 dp and 92 dp are [RenderTarget.STANDARD_WIDGET],
         * so the default 2×2 keeps body accessories. 120 dp is [RenderTarget.LARGE_WIDGET].
         * [RenderTarget.COMPACT_WIDGET] is reserved for a future 1×1.
         */
        fun targetFor(widthDp: Float, heightDp: Float): RenderTarget = when (avatarDrawnDp(widthDp, heightDp)) {
            in 96f..Float.MAX_VALUE -> RenderTarget.LARGE_WIDGET
            in 64f..96f -> RenderTarget.STANDARD_WIDGET
            else -> RenderTarget.COMPACT_WIDGET
        }

        /**
         * Builds inputs and draws. A widget with no avatar returns [model] and a null bitmap.
         * A registry, migration, or draw failure is reported via [onFailure] and also returns a
         * null bitmap, so the widget can show its fallback.
         */
        fun <T> renderCatching(
            model: WidgetModel,
            widthDp: Float,
            heightDp: Float,
            registry: () -> AssetRegistry,
            onFailure: (Throwable) -> Unit,
            draw: (AssetRegistry, WidgetRenderInputs) -> Pair<WidgetModel, T>,
        ): Pair<WidgetModel, T?> = try {
            val reg = registry()
            val inputs = from(model, reg, targetFor(widthDp, heightDp)) ?: return model to null
            draw(reg, inputs)
        } catch (failure: Throwable) {
            onFailure(failure)
            model to null
        }

        fun from(model: WidgetModel, registry: AssetRegistry, target: RenderTarget): WidgetRenderInputs? {
            val resting: AvatarConfig = model.friendView?.restingAvatar ?: model.restingAvatar ?: return null
            val presence = when {
                model.friendView != null -> LegacyAvatarMigration.presence(model.friendView)
                model.selfPresence != null -> LegacyAvatarMigration.presence(model.selfPresence)
                else -> VisiblePresence.NONE
            }
            return WidgetRenderInputs(
                configuration = LegacyAvatarMigration.migrate(resting, registry).migrateRecipe(registry.baseFamilies),
                presence = presence,
                target = target,
            )
        }
    }
}
