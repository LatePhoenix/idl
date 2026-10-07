package app.idl.domain.avatar

import app.idl.domain.Expression
import app.idl.domain.Mood
import app.idl.domain.wire
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExpressionCatalogTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `the generator matches the checked-in catalog`() {
        val root = repoRoot()
        val os = System.getProperty("os.name").orEmpty()
        val python = if (os.lowercase().contains("windows")) "python" else "python3"
        val process = ProcessBuilder(python, "tools/gen_expression_catalog.py", "check")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
    }

    @Test fun `every mood has one priority-1 expression and every expression alias resolves`() {
        val root = json.parseToJsonElement(File(repoRoot(), "config/expression_catalog.json").readText()).jsonObject
        val expressions = root.getValue("expressions").jsonArray.map { it.jsonObject }
        val ids = expressions.map { it.getValue("expressionId").jsonPrimitive.content }.toSet()
        enumValues<Mood>().forEach { mood ->
            val matches = expressions.filter { row ->
                row.getValue("priority").jsonPrimitive.content == "1" && row.mood() == mood.wire
            }
            assertEquals(mood.wire, 1, matches.size)
        }
        val aliases = root.getValue("aliases").jsonObject
        enumValues<Expression>().forEach { expression ->
            val target = aliases.getValue(expression.wire).jsonPrimitive.content
            assertTrue("$target missing for ${expression.wire}", target in ids)
        }
        expressions.forEach { row ->
            val hand = row.getValue("subgroup").jsonPrimitive.content == "face-hand"
            assertEquals(row.getValue("expressionId").jsonPrimitive.content, hand, row.getValue("handOverlay").jsonPrimitive.content.toBoolean())
        }
    }

    private fun kotlinx.serialization.json.JsonObject.mood(): String? {
        val value = getValue("mood")
        return if (value is JsonNull) null else value.jsonPrimitive.content
    }
}
