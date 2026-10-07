package app.idl.domain.avatar

import app.idl.domain.Expression
import app.idl.domain.Mood
import app.idl.domain.wire
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExpressionCatalogTest {
    private val catalog = ExpressionCatalog.parse(
        File(repoRoot(), "config/expression_catalog.json").readText(),
    )

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
        val ids = catalog.expressions.map { it.expressionId }.toSet()
        enumValues<Mood>().forEach { mood ->
            val id = catalog.priorityId(mood)
            assertTrue(mood.name, id != null && id in ids)
            assertEquals(id, catalog.expressions.single { it.priority == 1 && it.mood == mood.wire }.expressionId)
        }
        enumValues<Expression>().forEach { expression ->
            val target = expression.catalogId(catalog)
            assertTrue("$target missing for ${expression.wire}", target in ids)
        }
        catalog.expressions.forEach { row ->
            assertEquals(row.expressionId, row.subgroup == "face-hand", row.handOverlay)
        }
    }

    @Test fun `a visible mood uses the catalog face and a hidden mood stays neutral`() {
        val happy = checkNotNull(catalog.priorityId(Mood.HAPPY))
        val registry = sandbox(
            extra = listOf(
                piece("eyes_smile", AssetCategory.FACE_EYE),
                piece("mouth_smile", AssetCategory.FACE_MOUTH),
            ),
            expressions = listOf(
                ExpressionDef("neutral", "Neutral", eyes = "eye_open", mouth = "mouth_line"),
                ExpressionDef(happy, "Happy", eyes = "eyes_smile", mouth = "mouth_smile", overlays = listOf("overlay_hearts")),
            ),
        )
        val resolver = AvatarResolver(registry, catalog)
        val config = AvatarConfiguration(baseAssetId = "base_a", paletteAssetId = "pal_a")
        val shown = resolver.resolve(request(config, VisiblePresence(mood = Mood.HAPPY)))
        assertEquals(happy, shown.expressionId)
        assertTrue(shown.has("eyes_smile"))
        assertTrue(shown.has("mouth_smile"))
        val hidden = resolver.resolve(request(config, VisiblePresence.NONE))
        assertEquals("neutral", hidden.expressionId)
        assertFalse(hidden.has("eyes_smile"))
        assertFalse(hidden.layers.any { it.assetId == "overlay_hearts" })
    }

    @Test fun `every mood resolves to its priority face when the pack has drawn it`() {
        val resolver = AvatarResolver(coreRegistry(), catalog)
        val teardrop = config(base = "base_teardrop")
        enumValues<Mood>().forEach { mood ->
            val resolved = resolver.resolve(request(teardrop, VisiblePresence(mood = mood)))
            assertEquals(mood.name, catalog.priorityId(mood), resolved.expressionId)
        }
    }

    @Test fun `a hidden mood on the teardrop keeps neutral eyes and mouth and drops overlays`() {
        val resolver = AvatarResolver(coreRegistry(), catalog)
        val hidden = resolver.resolve(request(config(base = "base_teardrop"), VisiblePresence.NONE))
        assertEquals("neutral_face", hidden.expressionId)
        assertTrue(hidden.has("eyes_round_neutral"))
        assertTrue(hidden.has("mouth_round_neutral"))
        assertFalse(hidden.layers.any { it.assetId.startsWith("overlay_") })
    }
}
