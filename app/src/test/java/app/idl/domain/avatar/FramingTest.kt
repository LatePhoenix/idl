package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramingTest {
    @Test fun `head and bust viewports match the spec`() {
        assertEquals(-40f, Framing.HEAD.originX, 0f)
        assertEquals(0f, Framing.HEAD.originY, 0f)
        assertEquals(1104f, Framing.HEAD.size, 0f)
        assertEquals(-128f, Framing.BUST.originX, 0f)
        assertEquals(32f, Framing.BUST.originY, 0f)
        assertEquals(1280f, Framing.BUST.size, 0f)
    }

    @Test fun `each render target uses the framing from the spec`() {
        val head = setOf(
            RenderTarget.COMPACT_WIDGET,
            RenderTarget.CIRCLE_WIDGET,
            RenderTarget.FRIEND_TILE,
            RenderTarget.NOTIFICATION,
            RenderTarget.STANDARD_WIDGET,
            RenderTarget.LARGE_WIDGET,
        )
        val bust = setOf(RenderTarget.PROFILE, RenderTarget.SHARE_CARD)
        assertEquals(RenderTarget.entries.toSet(), head + bust)
        head.forEach { assertEquals(it.name, Framing.HEAD, it.framing) }
        bust.forEach { assertEquals(it.name, Framing.BUST, it.framing) }
    }

    @Test fun `viewport origin maps to the output origin and the far corner maps to the far edge`() {
        for (framing in Framing.entries) {
            val origin = framing.toOutput(framing.originX, framing.originY, 48f)
            assertEquals(0f, origin.first, 0.001f)
            assertEquals(0f, origin.second, 0.001f)
            val far = framing.toOutput(framing.originX + framing.size, framing.originY + framing.size, 48f)
            assertEquals(48f, far.first, 0.001f)
            assertEquals(48f, far.second, 0.001f)
        }
    }

    @Test fun `the render key differs when the framing differs`() {
        val resolver = AvatarResolver(coreRegistry())
        val head = resolver.resolve(request(target = RenderTarget.COMPACT_WIDGET, sizePx = 48))
        val bust = resolver.resolve(request(target = RenderTarget.PROFILE, sizePx = 48))
        assertEquals(Framing.HEAD, head.framing)
        assertEquals(Framing.BUST, bust.framing)
        assertNotEquals(head.renderKey, bust.renderKey)
        assertEquals(64, head.renderKey.length)
    }

    @Test fun `body region geometry is accepted only on the allowed bands`() {
        val asset = AssetDef(
            id = "body_probe",
            category = AssetCategory.BASE,
            accessibilityLabel = "Probe",
            render = AssetRender(type = "vector", file = "pictures/body_probe.json"),
            contentVersion = 1,
            colorSlots = mapOf("face.primary" to "#FFCCAA77"),
        )
        val shoulder = "M 1200 1400 L 1200 1400"
        assertTrue(issues(asset, 34, shoulder).isEmpty())
        assertTrue(issues(asset, 36, shoulder).isEmpty())
        assertTrue(issues(asset, 38, shoulder).isEmpty())
        assertTrue(issues(asset, 0, shoulder).isEmpty())
        assertTrue(issues(asset, 20, shoulder).isEmpty())
        assertTrue(issues(asset, 40, shoulder).any { "outside" in it })
        assertTrue(issues(asset, 34, "M -300 100 L -300 100").any { "outside" in it })
        assertTrue(issues(asset, 34, "M 100 1600 L 100 1600").any { "outside" in it })
        val overflow = picture(40, shoulder).let { it.copy(parts = listOf(it.parts[0].copy(allowOverflow = true))) }
        assertTrue(VectorPictureValidator.validate(overflow, asset).isEmpty())
    }

    private fun issues(asset: AssetDef, band: Int, commands: String) =
        VectorPictureValidator.validate(picture(band, commands), asset)

    private fun picture(band: Int, commands: String) = VectorPicture(
        schemaVersion = 1,
        id = "body_probe",
        contentVersion = 1,
        viewBox = 1024,
        parts = listOf(VectorPart("shape", band, VectorFill(slot = "face.primary"), commands)),
    )
}
