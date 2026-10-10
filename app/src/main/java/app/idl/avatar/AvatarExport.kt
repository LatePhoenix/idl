package app.idl.avatar

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import app.idl.domain.IdlJson
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.AvatarRenderRequest
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.ExpressionCatalog
import app.idl.domain.avatar.RenderTarget
import java.io.File
import java.io.FileOutputStream

/**
 * Square exports rendered at the requested size (AP-11). The bitmap is never scaled up from a preview.
 */
object AvatarExport {
    val SIZES = intArrayOf(512, 1024, 2048)

    /** Light paper behind a solid export. Matches the in-app light background. */
    const val PAPER = 0xFFFFFBF7.toInt()

    fun render(
        configuration: AvatarConfiguration,
        registry: AssetRegistry,
        catalog: ExpressionCatalog,
        pictures: VectorPictureCache,
        sizePx: Int,
        backdrop: ExportBackdrop,
    ): Bitmap {
        check(sizePx in SIZES) { "export size must be 512, 1024, or 2048" }
        val resolved = AvatarResolver(registry, catalog).resolve(
            AvatarRenderRequest(
                configuration = configuration,
                target = RenderTarget.SHARE_CARD,
                sizePx = sizePx,
            ),
        )
        return AvatarRenderer.bitmap(resolved, registry, pictures, sizePx, backdrop = backdrop)
    }

    fun recipeJson(configuration: AvatarConfiguration): String =
        IdlJson.encodeToString(AvatarConfiguration.serializer(), configuration)

    fun sharePng(context: Context, bitmap: Bitmap, sizePx: Int) {
        val file = File(exportDir(context), "idl-avatar-$sizePx.png")
        FileOutputStream(file).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "png compress failed" }
        }
        share(context, file, "image/png", "Share avatar")
    }

    fun shareRecipe(context: Context, configuration: AvatarConfiguration) {
        val file = File(exportDir(context), "idl-avatar.json")
        file.writeText(recipeJson(configuration))
        share(context, file, "application/json", "Share recipe")
    }

    private fun exportDir(context: Context) = File(context.cacheDir, "export").apply { mkdirs() }

    private fun share(context: Context, file: File, type: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("", uri)
        }
        context.startActivity(Intent.createChooser(send, title))
    }
}
