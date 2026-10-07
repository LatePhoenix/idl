package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublishedMasksTest {
    private fun part(id: String, band: Int, mask: String? = null, clipBy: List<ClipBy> = emptyList()) = VectorPart(
        id = id,
        zBand = band,
        fill = VectorFill(slot = "face.primary"),
        commands = "M 0 0 L 1 0 L 1 1 Z",
        publishMask = mask,
        clipBy = clipBy,
    )

    private fun picture(id: String, vararg parts: VectorPart) = VectorPicture(
        schemaVersion = 2,
        id = id,
        contentVersion = 1,
        viewBox = 1024,
        parts = parts.toList(),
    )

    @Test fun `masks collect in band then asset id then part index order and first wins`() {
        val hat = picture("hat_brim", part("brim", 90, mask = "occlude.hair_top"))
        val other = picture("aaa_hat", part("brim", 90, mask = "occlude.hair_top"))
        val hair = picture(
            "hair_bob",
            part("back", 20, clipBy = listOf(ClipBy("occlude.hair_top", "difference"))),
            part("top", 70),
        )
        val ops = listOf(
            DrawOp.VectorPart("hair_bob", 0, chrome = false),
            DrawOp.VectorPart("hair_bob", 1, chrome = false),
            DrawOp.VectorPart("hat_brim", 0, chrome = false),
            DrawOp.VectorPart("aaa_hat", 0, chrome = false),
        )
        val pictures = mapOf("hat_brim" to hat, "aaa_hat" to other, "hair_bob" to hair)
        val masks = PublishedMasks.collect(ops) { pictures[it] }
        assertEquals(setOf("occlude.hair_top"), masks.keys)
        // Same band: asset id aaa_hat sorts before hat_brim.
        assertEquals("aaa_hat", masks.getValue("occlude.hair_top").assetId)
    }

    @Test fun `a missing publisher is absent so clipBy stays a no-op`() {
        val hair = picture("hair_bob", part("top", 70, clipBy = listOf(ClipBy("occlude.hair_top", "difference"))))
        val ops = listOf(DrawOp.VectorPart("hair_bob", 0, chrome = false))
        assertTrue(PublishedMasks.collect(ops) { if (it == "hair_bob") hair else null }.isEmpty())
    }
}
