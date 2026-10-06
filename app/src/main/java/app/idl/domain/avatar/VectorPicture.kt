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
    /** Picture format v2. Null on a version 1 part. */
    val stroke: VectorStroke? = null,
    /** Picture format v2. Empty on a version 1 part. */
    val tags: List<String> = emptyList(),
    /**
     * Picture format v2. Names masks published by other assets. Parsed and validated here;
     * applied when the picture is drawn in AP-8.
     */
    val clipBy: List<ClipBy> = emptyList(),
    /** Picture format v2. This part's path is published under the name for other assets to clip by. */
    val publishMask: String? = null,
)

/** Outline drawn in a slot color. Width is in viewBox units. Caps and joins default to round. */
@Serializable
data class VectorStroke(
    val slot: String,
    val width: Float,
    val cap: String = "round",
    val join: String = "round",
)

/** A subscription to a mask another asset publishes. */
@Serializable
data class ClipBy(
    val mask: String,
    val mode: String,
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
 * Normalized picture JSON. [schemaVersion] 1 and 2 both load. Version 2 adds strokes, tags
 * and mask syntax. Runtime code does not parse SVG files; [PathData] parses the `commands` strings.
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
        /** Version written for pictures that do not use version 2 fields. */
        const val SCHEMA_VERSION = 1
        val SUPPORTED_SCHEMA_VERSIONS = setOf(1, 2)
        const val VIEW_BOX = 1024

        fun parse(json: String): VectorPicture {
            val picture = IdlJson.decodeFromString(serializer(), json)
            require(picture.schemaVersion in SUPPORTED_SCHEMA_VERSIONS) {
                "unsupported picture schemaVersion ${picture.schemaVersion}"
            }
            return picture
        }

        fun encode(picture: VectorPicture): String = IdlJson.encodeToString(serializer(), picture)
    }
}
