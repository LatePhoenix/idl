package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorPictureTest {
    private val asset = AssetDef(
        id = "hair_round_bob",
        category = AssetCategory.HAIR,
        accessibilityLabel = "Bob",
        render = AssetRender(type = "vector", file = "pictures/hair_round_bob.json"),
        contentVersion = 3,
        colorSlots = mapOf("hair.primary" to "#FF332211", "hair.shadow" to "#FF110000"),
    )

    @Test fun `a picture round trips and ignores unknown keys`() {
        val picture = sample()
        val parsed = VectorPicture.parse(VectorPicture.encode(picture))
        assertEquals(picture, parsed)
        val withExtra = VectorPicture.encode(picture).replace("{", "{\"note\":\"later\",")
        assertEquals(picture.id, VectorPicture.parse(withExtra).id)
    }

    @Test fun `schemaVersion 2 loads and schemaVersion 3 is rejected`() {
        val v2 = VectorPicture.encode(sample().copy(schemaVersion = 2))
        assertEquals(2, VectorPicture.parse(v2).schemaVersion)
        val v3 = v2.replace("\"schemaVersion\": 2", "\"schemaVersion\": 3")
            .replace("\"schemaVersion\":2", "\"schemaVersion\":3")
        try {
            VectorPicture.parse(v3)
            throw AssertionError("schema 3 should be rejected")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message!!.contains("schemaVersion"))
        }
    }

    @Test fun `validator accepts a picture that matches the asset`() {
        assertEquals(emptyList<String>(), VectorPictureValidator.validate(sample(), asset))
    }

    @Test fun `validator reports each picture rule`() {
        val picture = sample()
        assertReported(picture.copy(schemaVersion = 3), "schemaVersion")
        assertReported(picture.copy(id = "other"), "does not match")
        assertReported(picture.copy(contentVersion = 0), "contentVersion")
        assertReported(picture.copy(contentVersion = 9), "does not match asset")
        assertReported(picture.copy(viewBox = 100), "viewBox")
        assertReported(picture.copy(parts = picture.parts + picture.parts[0]), "duplicate part")
        assertReported(
            picture.copy(clipPaths = picture.clipPaths + picture.clipPaths[0]),
            "duplicate clip",
        )
        assertReported(picture.copy(parts = listOf(picture.parts[0].copy(zBand = 200))), "character band")
        assertReported(picture.copy(parts = listOf(picture.parts[0].copy(zBand = 15))), "character band")
        assertReported(picture.copy(parts = listOf(picture.parts[0].copy(fillRule = "winding"))), "fillRule")
        assertReported(picture.copy(parts = listOf(picture.parts[0].copy(opacity = 2f))), "opacity")
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(fill = VectorFill()))),
            "exactly one fill",
        )
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(fill = VectorFill(slot = "missing")))),
            "not on the asset",
        )
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(clip = VectorClip("nope", "intersect")))),
            "missing path",
        )
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(clip = VectorClip("head", "xor")))),
            "clip mode",
        )
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(commands = "M 0 0 A 1 1"))),
            "unreadable",
        )
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(commands = "M -300 0"))),
            "outside",
        )
        assertEquals(
            emptyList<String>(),
            VectorPictureValidator.validate(
                picture.copy(parts = listOf(picture.parts[0].copy(commands = "M -300 0", allowOverflow = true))),
                asset,
            ),
        )
        val short = LinearGradient(0f, 0f, 1f, 1f, stops = listOf(GradientStop(0f, "hair.primary")))
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(fill = VectorFill(linear = short)))),
            "at least two stops",
        )
        val decreasing = LinearGradient(
            0f, 0f, 1f, 1f,
            stops = listOf(GradientStop(0.8f, "hair.primary"), GradientStop(0.2f, "hair.shadow")),
        )
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(fill = VectorFill(linear = decreasing)))),
            "offsets",
        )
        val alpha = RadialGradient(
            0f, 0f, 1f,
            stops = listOf(
                GradientStop(0f, "hair.primary", alpha = 2f),
                GradientStop(1f, "hair.shadow"),
            ),
        )
        assertReported(
            picture.copy(parts = listOf(picture.parts[0].copy(fill = VectorFill(radial = alpha)))),
            "alpha",
        )
    }

    @Test fun `version 2 accepts a stroke, tags and mask syntax`() {
        val picture = sample().copy(
            schemaVersion = 2,
            parts = listOf(
                sample().parts[0].copy(
                    stroke = VectorStroke(slot = "hair.shadow", width = 16f),
                    tags = listOf("hair_back"),
                    clipBy = listOf(ClipBy(mask = "occlude.hair_top", mode = "difference")),
                    publishMask = "occlude.side",
                ),
            ),
        )
        assertEquals(emptyList<String>(), VectorPictureValidator.validate(picture, asset))
    }

    @Test fun `version 2 rejects a bad stroke and version 1 rejects version 2 fields`() {
        val part = sample().parts[0]
        val wide = sample().copy(
            schemaVersion = 2,
            parts = listOf(part.copy(stroke = VectorStroke(slot = "hair.primary", width = 8f))),
        )
        assertReported(wide, "stroke width")
        val cap = sample().copy(
            schemaVersion = 2,
            parts = listOf(part.copy(stroke = VectorStroke(slot = "hair.primary", width = 16f, cap = "flat"))),
        )
        assertReported(cap, "stroke cap")
        val missing = sample().copy(
            schemaVersion = 2,
            parts = listOf(part.copy(stroke = VectorStroke(slot = "outline", width = 16f))),
        )
        assertReported(missing, "stroke slot")
        val mode = sample().copy(
            schemaVersion = 2,
            parts = listOf(part.copy(clipBy = listOf(ClipBy(mask = "occlude.hair_top", mode = "xor")))),
        )
        assertReported(mode, "clipBy mode")
        val repeated = sample().copy(
            schemaVersion = 2,
            parts = listOf(part.copy(tags = listOf("hair_back", "hair_back"))),
        )
        assertReported(repeated, "repeats tag")
        val onV1 = sample().copy(parts = listOf(part.copy(tags = listOf("hair_back"))))
        assertReported(onV1, "schemaVersion 1")
        val masks = sample().copy(
            schemaVersion = 2,
            parts = listOf(
                part.copy(id = "a", publishMask = "occlude.side"),
                part.copy(id = "b", publishMask = "occlude.side"),
            ),
        )
        assertReported(masks, "more than once")
    }

    private fun assertReported(picture: VectorPicture, fragment: String) {
        val issues = VectorPictureValidator.validate(picture, asset)
        assertTrue(issues.joinToString("\n"), issues.any { fragment in it })
    }

    private fun sample() = VectorPicture(
        schemaVersion = 1,
        id = "hair_round_bob",
        contentVersion = 3,
        viewBox = 1024,
        parts = listOf(
            VectorPart(
                id = "back",
                zBand = 20,
                fill = VectorFill(slot = "hair.primary"),
                commands = "M 0 0 L 10 0",
                clip = VectorClip("head", "intersect"),
            ),
        ),
        clipPaths = listOf(ClipPath("head", "M 0 0 Z")),
    )
}
