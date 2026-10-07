package app.idl.domain.avatar

import app.idl.domain.Mood
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandOverlayResolverTest {
    private val registry = coreRegistry()

    @Test fun `a hand-overlay expression suppresses the foreground prop for that render`() {
        val catalog = ExpressionCatalog(
            expressions = listOf(
                CatalogExpression(expressionId = "happy", mood = "happy", priority = 1, handOverlay = true),
            ),
        )
        val resolver = AvatarResolver(registry, catalog)
        val withHands = resolver.resolve(
            request(
                config(prop = "prop_tea", base = "base_teardrop"),
                presence = VisiblePresence(mood = Mood.HAPPY, expressionId = "happy"),
            ),
        )
        assertFalse(withHands.has("prop_tea"))
        assertTrue(withHands.dropped.any { it.assetId == "prop_tea" && it.reason == DropReason.OCCLUDED })

        val resting = AvatarResolver(registry, ExpressionCatalog.EMPTY).resolve(
            request(config(prop = "prop_tea", base = "base_teardrop")),
        )
        assertTrue(resting.has("prop_tea"))
    }
}
