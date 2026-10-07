package app.idl.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteAndContrastTest {
    @Test fun `emoji_core palettes meet the AP-9 counts and every swatch is named`() {
        val palettes = coreRegistry().defaults.palettes
        assertTrue("skin natural+fantasy", (palettes["skin"]?.size ?: 0) >= 24)
        assertTrue("hair", (palettes["hair"]?.size ?: 0) >= 20)
        assertTrue("eye", (palettes["eye"]?.size ?: 0) >= 10)
        assertTrue("clothing", (palettes["clothing"]?.size ?: 0) >= 24)
        for ((family, swatches) in palettes) {
            for (swatch in swatches) {
                assertTrue("$family/${swatch.id} id", swatch.id.isNotBlank())
                assertTrue("$family/${swatch.id} name", swatch.name.isNotBlank())
                assertTrue("$family/${swatch.id} hex", ColorSlots.parseHex(swatch.hex) != null)
            }
        }
    }

    @Test fun `slot links are declared for brow and beard`() {
        val links = coreRegistry().defaults.slotLinks
        assertEquals("hair.primary", links["brow.primary"])
        assertEquals("hair.primary", links["beard.primary"])
    }

    @Test fun `every skin swatch contrasts with the outline or a light scene`() {
        // Mid and deep browns sit near the teardrop outline (#7A4E00); they still need to
        // read on a light scene. Light skins must clear the outline itself.
        val outline = ColorSlots.parseHex("#7A4E00")!!
        val lightScene = 0xFFF4F4F0.toInt()
        val skin = coreRegistry().defaults.palettes.getValue("skin")
        val weak = skin.filterNot { swatch ->
            val argb = ColorSlots.parseHex(swatch.hex)!!
            ContrastWarnings.contrastsWithOutline(swatch.hex, outline) ||
                ContrastWarnings.contrastRatio(argb, lightScene) >= ContrastWarnings.OUTLINE_RATIO
        }
        assertTrue("weak=${weak.map { it.id }}", weak.isEmpty())
        assertTrue(ContrastWarnings.contrastsWithOutline("#F6EDE4", outline))
        assertTrue(ContrastWarnings.contrastsWithOutline("#F3E0C8", outline))
    }

    @Test fun `contrast warnings fire for near-identical hair and skin`() {
        val warnings = ContrastWarnings.evaluate(mapOf(
            "hair.primary" to 0xFFC68642.toInt(),
            "face.primary" to 0xFFC68642.toInt(),
            "outline" to 0xFF7A4E00.toInt(),
        ))
        assertTrue(warnings.any { it.code == "hair_matches_skin" })
    }

    @Test fun `contrast warnings stay quiet for distinct hair and skin`() {
        val warnings = ContrastWarnings.evaluate(mapOf(
            "hair.primary" to 0xFF1A1210.toInt(),
            "face.primary" to 0xFFFFC83D.toInt(),
            "outline" to 0xFF7A4E00.toInt(),
            "top.primary" to 0xFF1E3A5F.toInt(),
            "bg.primary" to 0xFFF4F4F0.toInt(),
        ))
        assertFalse(warnings.any { it.code == "hair_matches_skin" })
        assertFalse(warnings.any { it.code == "top_matches_background" })
    }
}
