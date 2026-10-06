package app.idl.domain.avatar

/**
 * Picture checks from the vector asset spec. An empty list means the picture matches [asset].
 * The pack test runs this for every shipped vector asset.
 */
object VectorPictureValidator {
    /** Character bands a part may use. Chrome bands (200+) are rejected on picture parts. */
    val CHARACTER_BANDS = setOf(0, 10, 20, 30, 34, 36, 38, 40, 50, 60, 70, 80, 90, 100, 110)

    /** Bands that may use the body region (x −256..1280, y −16..1536). */
    val BODY_REGION_BANDS = setOf(0, 20, 34, 36, 38)

    private const val MIN_COORD = -16f
    private const val MAX_COORD = 1040f
    private const val BODY_MIN_X = -256f
    private const val BODY_MAX_X = 1280f
    private const val BODY_MAX_Y = 1536f
    private val STROKE_CAPS = setOf("butt", "round", "square")
    private val STROKE_JOINS = setOf("miter", "round", "bevel")

    internal fun inside(zBand: Int, x: Float, y: Float): Boolean {
        val body = zBand in BODY_REGION_BANDS
        val minX = if (body) BODY_MIN_X else MIN_COORD
        val maxX = if (body) BODY_MAX_X else MAX_COORD
        val maxY = if (body) BODY_MAX_Y else MAX_COORD
        return x in minX..maxX && y in MIN_COORD..maxY
    }

    fun validate(picture: VectorPicture, asset: AssetDef): List<String> {
        val issues = mutableListOf<String>()
        if (picture.schemaVersion !in VectorPicture.SUPPORTED_SCHEMA_VERSIONS) {
            issues += "${picture.id} schemaVersion ${picture.schemaVersion} is not 1 or 2"
        }
        if (picture.id != asset.id) issues += "picture id ${picture.id} does not match asset ${asset.id}"
        if (picture.contentVersion < 1) issues += "${picture.id} contentVersion must be >= 1"
        if (picture.contentVersion != asset.contentVersion) {
            issues += "${picture.id} contentVersion ${picture.contentVersion} does not match asset ${asset.contentVersion}"
        }
        if (picture.viewBox != VectorPicture.VIEW_BOX) issues += "${picture.id} viewBox must be ${VectorPicture.VIEW_BOX}"

        picture.parts.mapNotNull { it.publishMask }.groupBy { it }.filterValues { it.size > 1 }.keys.sorted().forEach {
            issues += "${picture.id} publishes mask $it more than once"
        }
        val partIds = picture.parts.map { it.id }
        partIds.groupBy { it }.filterValues { it.size > 1 }.keys.sorted().forEach {
            issues += "${picture.id} has duplicate part id $it"
        }
        val clipIds = picture.clipPaths.map { it.id }
        clipIds.groupBy { it }.filterValues { it.size > 1 }.keys.sorted().forEach {
            issues += "${picture.id} has duplicate clip path id $it"
        }
        val clipSet = clipIds.toSet()

        for (clip in picture.clipPaths) {
            if (parseCommands(clip.commands) == null) issues += "${picture.id} clip ${clip.id} has unreadable commands"
        }
        for (part in picture.parts) {
            if (part.zBand !in CHARACTER_BANDS) issues += "${picture.id} part ${part.id} zBand ${part.zBand} is not a character band"
            if (part.fillRule != "nonzero" && part.fillRule != "evenodd") {
                issues += "${picture.id} part ${part.id} has unknown fillRule ${part.fillRule}"
            }
            if (part.opacity !in 0f..1f) issues += "${picture.id} part ${part.id} opacity is outside 0..1"
            val fills = listOfNotNull(
                part.fill.slot?.let { "slot" },
                part.fill.linear?.let { "linear" },
                part.fill.radial?.let { "radial" },
            )
            if (fills.size != 1) issues += "${picture.id} part ${part.id} must have exactly one fill"
            part.fill.slot?.let { slot ->
                if (slot !in asset.colorSlots) issues += "${picture.id} part ${part.id} fill slot $slot is not on the asset"
            }
            part.fill.linear?.let { issues += gradientIssues(picture.id, part.id, it.stops, asset) }
            part.fill.radial?.let { issues += gradientIssues(picture.id, part.id, it.stops, asset) }
            val clip = part.clip
            if (clip != null) {
                if (clip.mode != "intersect" && clip.mode != "difference") {
                    issues += "${picture.id} part ${part.id} has unknown clip mode ${clip.mode}"
                }
                if (clip.id !in clipSet) issues += "${picture.id} part ${part.id} clips to missing path ${clip.id}"
            }
            issues += version2Issues(picture, part, asset)
            val ops = parseCommands(part.commands)
            if (ops == null) {
                issues += "${picture.id} part ${part.id} has unreadable commands"
            } else if (!part.allowOverflow && ops.any { op -> coordinates(op).any { (x, y) -> !inside(part.zBand, x, y) } }) {
                val box = if (part.zBand in BODY_REGION_BANDS) "body region" else "-16..1040"
                issues += "${picture.id} part ${part.id} geometry is outside $box"
            }
        }
        return issues.sorted()
    }

    private fun version2Issues(picture: VectorPicture, part: VectorPart, asset: AssetDef): List<String> {
        val usesVersion2 = part.stroke != null || part.tags.isNotEmpty() || part.clipBy.isNotEmpty() || part.publishMask != null
        if (picture.schemaVersion == 1 && usesVersion2) {
            return listOf("${picture.id} part ${part.id} uses version 2 fields on schemaVersion 1")
        }
        if (picture.schemaVersion != 2) return emptyList()
        val issues = mutableListOf<String>()
        part.stroke?.let { stroke ->
            if (stroke.slot !in asset.colorSlots) {
                issues += "${picture.id} part ${part.id} stroke slot ${stroke.slot} is not on the asset"
            }
            if (stroke.width !in 16f..96f) {
                issues += "${picture.id} part ${part.id} stroke width ${stroke.width} is outside 16..96"
            }
            if (stroke.cap !in STROKE_CAPS) issues += "${picture.id} part ${part.id} has unknown stroke cap ${stroke.cap}"
            if (stroke.join !in STROKE_JOINS) issues += "${picture.id} part ${part.id} has unknown stroke join ${stroke.join}"
        }
        if (part.tags.any { it.isBlank() }) issues += "${picture.id} part ${part.id} has a blank tag"
        part.tags.groupBy { it }.filterValues { it.size > 1 }.keys.sorted().forEach {
            issues += "${picture.id} part ${part.id} repeats tag $it"
        }
        part.publishMask?.let { name ->
            if (name.isBlank()) issues += "${picture.id} part ${part.id} publishes a blank mask"
        }
        for (clip in part.clipBy) {
            if (clip.mask.isBlank()) issues += "${picture.id} part ${part.id} clipBy mask is blank"
            if (clip.mode != "intersect" && clip.mode != "difference") {
                issues += "${picture.id} part ${part.id} has unknown clipBy mode ${clip.mode}"
            }
        }
        return issues
    }

    private fun gradientIssues(pictureId: String, partId: String, stops: List<GradientStop>, asset: AssetDef): List<String> {
        val issues = mutableListOf<String>()
        if (stops.size < 2) issues += "$pictureId part $partId gradient needs at least two stops"
        val offsets = stops.map { it.offset }
        if (offsets.zipWithNext().any { (a, b) -> a >= b } || offsets.any { it !in 0f..1f }) {
            issues += "$pictureId part $partId gradient offsets must increase within 0..1"
        }
        for (stop in stops) {
            if (stop.slot !in asset.colorSlots) issues += "$pictureId part $partId fill slot ${stop.slot} is not on the asset"
            val alpha = stop.alpha
            if (alpha != null && alpha !in 0f..1f) issues += "$pictureId part $partId stop alpha is outside 0..1"
        }
        return issues
    }

    private fun parseCommands(commands: String): List<PathOp>? = try {
        PathData.parse(commands)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun coordinates(op: PathOp): List<Pair<Float, Float>> = when (op) {
        is PathOp.MoveTo -> listOf(op.x to op.y)
        is PathOp.LineTo -> listOf(op.x to op.y)
        is PathOp.QuadTo -> listOf(op.x1 to op.y1, op.x to op.y)
        is PathOp.CubicTo -> listOf(op.x1 to op.y1, op.x2 to op.y2, op.x to op.y)
        PathOp.Close -> emptyList()
    }
}
