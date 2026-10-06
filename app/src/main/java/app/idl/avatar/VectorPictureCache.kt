package app.idl.avatar

import android.graphics.Path
import app.idl.domain.avatar.AssetDef
import app.idl.domain.avatar.PathData
import app.idl.domain.avatar.PathOp
import app.idl.domain.avatar.VectorPicture
import app.idl.domain.avatar.VectorPictureValidator

/**
 * One parsed picture per `assetId@contentVersion`. The [Path] objects are built once and never
 * mutated; callers transform with [android.graphics.Canvas.concat].
 */
class VectorPictureCache(
    /** Owning pack directory for an asset, with a trailing slash. Null keeps [AssetRender.file] as the path. */
    private val packDirectory: (AssetDef) -> String? = { null },
    private val read: (String) -> String,
) {
    data class PicturePaths(
        val picture: VectorPicture,
        val parts: List<Path>,
        val clips: Map<String, Path>,
    )

    private val lock = Any()
    private val cache = HashMap<String, PicturePaths?>()

    fun get(asset: AssetDef): PicturePaths? {
        if (asset.render.type != "vector") return null
        val key = "${asset.id}@${asset.contentVersion}"
        synchronized(lock) {
            if (cache.containsKey(key)) return cache[key]
            val loaded = load(asset)
            cache[key] = loaded
            return loaded
        }
    }

    private fun load(asset: AssetDef): PicturePaths? {
        val file = asset.render.file ?: return null
        val path = packDirectory(asset)?.plus(file) ?: file
        val json = try {
            read(path)
        } catch (_: Exception) {
            return null
        }
        val picture = try {
            VectorPicture.parse(json)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (VectorPictureValidator.validate(picture, asset).isNotEmpty()) return null
        return try {
            PicturePaths(
                picture = picture,
                parts = picture.parts.map { pathOf(it.commands, it.fillRule) },
                clips = picture.clipPaths.associate { it.id to pathOf(it.commands, "nonzero") },
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

internal fun pathOf(commands: String, fillRule: String): Path {
    val path = Path()
    path.fillType = if (fillRule == "evenodd") Path.FillType.EVEN_ODD else Path.FillType.WINDING
    for (op in PathData.parse(commands)) {
        when (op) {
            is PathOp.MoveTo -> path.moveTo(op.x, op.y)
            is PathOp.LineTo -> path.lineTo(op.x, op.y)
            is PathOp.QuadTo -> path.quadTo(op.x1, op.y1, op.x, op.y)
            is PathOp.CubicTo -> path.cubicTo(op.x1, op.y1, op.x2, op.y2, op.x, op.y)
            PathOp.Close -> path.close()
        }
    }
    return path
}
