package app.idl.domain.avatar

import app.idl.domain.IdlJson
import kotlinx.serialization.Serializable

/** One filled shape inside a picture. [zBand] is a character band from ARCHITECTURE.md §5. */
@Serializable
data class VectorPart(
    val id: String,
    val zBand: Int,
    val fill: VectorFill,
    val commands: String,
    val fillRule: String = "nonzero",
    val opacity: Float = 1f,
    val clip: VectorClip? = null,
    val allowOverflow: Boolean = false,
)

@Serializable
data class VectorFill(
    val slot: String? = null,
    val linear: LinearGradient? = null,
    val radial: RadialGradient? = null,
)

@Serializable
data class LinearGradient(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val stops: List<GradientStop> = emptyList(),
)

@Serializable
data class RadialGradient(
    val cx: Float,
    val cy: Float,
    val r: Float,
    val stops: List<GradientStop> = emptyList(),
)

@Serializable
data class GradientStop(
    val offset: Float,
    val slot: String,
    val alpha: Float? = null,
)

@Serializable
data class VectorClip(val id: String, val mode: String)

@Serializable
data class ClipPath(val id: String, val commands: String)

/**
 * Normalized picture JSON. [schemaVersion] is 1. Runtime code does not parse SVG files;
 * [PathData] parses the `commands` strings.
 */
@Serializable
data class VectorPicture(
    val schemaVersion: Int,
    val id: String,
    val contentVersion: Int,
    val viewBox: Int,
    val parts: List<VectorPart> = emptyList(),
    val clipPaths: List<ClipPath> = emptyList(),
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val VIEW_BOX = 1024

        fun parse(json: String): VectorPicture {
            val picture = IdlJson.decodeFromString(serializer(), json)
            require(picture.schemaVersion == SCHEMA_VERSION) {
                "unsupported picture schemaVersion ${picture.schemaVersion}"
            }
            return picture
        }

        fun encode(picture: VectorPicture): String = IdlJson.encodeToString(serializer(), picture)
    }
}
