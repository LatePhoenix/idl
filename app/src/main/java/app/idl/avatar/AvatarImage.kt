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

/** In-app avatar: paints with the same [AvatarRenderer] the widgets use. */
@Composable
fun AvatarImage(
    config: AvatarConfig,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    badges: AvatarBadges? = null,
) {
    val registry = LocalContext.current.container.assetRegistry
    Canvas(
        modifier
            .size(size)
            .semantics { this.contentDescription = contentDescription },
    ) {
        drawIntoCanvas { canvas ->
            AvatarRenderer.draw(canvas.nativeCanvas, config, this.size.minDimension, badges, registry = registry)
        }
    }
}
