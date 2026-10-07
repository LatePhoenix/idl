package app.idl.domain.avatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacialHairMultipleTest {
    private val resolver = AvatarResolver(coreRegistry())

    @Test fun `beard and mustache can both be worn`() {
        val resolved = resolver.resolve(
            request(
                config(base = "base_teardrop").copy(
                    itemIds = mapOf("facial_hair" to listOf("beard_full", "mustache_classic")),
                ),
            ),
        )
        assertTrue(resolved.has("beard_full"))
        assertTrue(resolved.has("mustache_classic"))
    }

    @Test fun `stubble conflicts with a full beard`() {
        val resolved = resolver.resolve(
            request(
                config(base = "base_teardrop").copy(
                    itemIds = mapOf("facial_hair" to listOf("beard_full", "stubble")),
                ),
            ),
        )
        val kept = listOfNotNull(
            resolved.has("beard_full").takeIf { it }?.let { "beard_full" },
            resolved.has("stubble").takeIf { it }?.let { "stubble" },
        )
        assertTrue("exactly one of beard_full/stubble should remain: $kept", kept.size == 1)
        assertTrue(resolved.dropped.any { it.reason == DropReason.CONFLICT })
    }
}
