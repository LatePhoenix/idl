package app.idl.domain.avatar

import app.idl.domain.Expression
import app.idl.domain.IdlJson
import app.idl.domain.Mood
import app.idl.domain.wire
import kotlinx.serialization.Serializable

/**
 * The checked-in face list from AP-5. Packs still own the drawable parts.
 * A catalog id is used only when the pack defines an [ExpressionDef] with that id.
 */
@Serializable
data class ExpressionCatalog(
    val aliases: Map<String, String> = emptyMap(),
    val expressions: List<CatalogExpression> = emptyList(),
) {
    /** The automatic face for [mood], or null when the catalog has none. */
    fun priorityId(mood: Mood): String? =
        expressions.firstOrNull { it.priority == 1 && it.mood == mood.wire }?.expressionId

    /**
     * Catalog id for an [Expression] wire name or an id that is already canonical.
     * Unknown ids are returned unchanged.
     */
    fun canonical(id: String): String = aliases[id] ?: id

    fun expression(id: String): CatalogExpression? {
        val key = canonical(id)
        return expressions.firstOrNull { it.expressionId == key }
    }

    companion object {
        val EMPTY = ExpressionCatalog()

        fun parse(json: String): ExpressionCatalog = IdlJson.decodeFromString(serializer(), json)
    }
}

@Serializable
data class CatalogExpression(
    val expressionId: String,
    val subgroup: String = "",
    val mood: String? = null,
    val priority: Int = 2,
    val handOverlay: Boolean = false,
    val overlays: List<String> = emptyList(),
)

/** Every [Expression] wire name must be a catalog alias. Checked by [ExpressionCatalogTest]. */
fun Expression.catalogId(catalog: ExpressionCatalog): String = catalog.canonical(wire)
