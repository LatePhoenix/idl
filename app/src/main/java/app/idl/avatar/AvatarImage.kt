package app.idl.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.idl.container
import app.idl.domain.AvatarConfig
import app.idl.domain.avatar.AvatarRenderRequest
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.LegacyAvatarMigration
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.VisiblePresence

/** In-app avatar: paints with the same [AvatarRenderer] the widgets use. */
@Composable
fun AvatarImage(
    config: AvatarConfig,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    badges: AvatarBadges? = null,
) {
    val container = LocalContext.current.container
    Canvas(
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawIntoCanvas { canvas ->
            val registry = container.assetRegistry
            val sizePx = this.size.minDimension
            val configuration = LegacyAvatarMigration.migrate(config, registry)
            val target = when {
                sizePx >= 256f -> RenderTarget.PROFILE
                sizePx >= 96f -> RenderTarget.FRIEND_TILE
                else -> RenderTarget.COMPACT_WIDGET
            }
            val resolved = AvatarResolver(registry, container.expressionCatalog).resolve(
                AvatarRenderRequest(
                    configuration = configuration,
                    presence = VisiblePresence.NONE,
                    target = target,
                    sizePx = sizePx.toInt().coerceAtLeast(1),
                ),
            )
            AvatarRenderer.draw(
                canvas.nativeCanvas,
                resolved,
                registry,
                container.vectorPictures,
                sizePx,
                badges,
            )
        }
    }
}
