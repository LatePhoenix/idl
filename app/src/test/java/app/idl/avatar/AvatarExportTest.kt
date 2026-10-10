package app.idl.avatar

import app.idl.domain.IdlJson
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AssetRegistry
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.EditorDefaults
import app.idl.domain.avatar.ExpressionCatalog
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.repoRoot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class AvatarExportTest {
    private val registry = AssetRegistry(
        coreRegistry().manifests,
        AssetPacks.SHIPPED.map { it.substringBeforeLast("manifest.json") },
    )
    private val pictures = VectorPictureCache(
        packDirectory = { asset -> registry.packDirectory(asset.id) },
    ) { path -> File(repoRoot(), "app/src/main/assets/$path").readText() }
    private val recipe = EditorDefaults.starter(registry)

    @Test fun `each export size is rendered at that size`() {
        AvatarExport.SIZES.forEach { size ->
            val bitmap = render(size, ExportBackdrop.TRANSPARENT)
            assertEquals(size, bitmap.width)
            assertEquals(size, bitmap.height)
        }
    }

    @Test fun `transparent solid and background fills differ and the recipe json round-trips`() {
        val transparent = render(512, ExportBackdrop.TRANSPARENT)
        val solid = render(512, ExportBackdrop.SOLID)
        val scene = render(512, ExportBackdrop.SCENE)
        var backgroundPixel = false
        for (y in 0 until 512 step 4) {
            for (x in 0 until 512 step 4) {
                val clear = transparent.getPixel(x, y)
                val paper = solid.getPixel(x, y)
                val painted = scene.getPixel(x, y)
                if (clear == 0 && paper == AvatarExport.PAPER && painted != 0 && painted != AvatarExport.PAPER) {
                    backgroundPixel = true
                }
            }
        }
        assertTrue(backgroundPixel)
        assertFalse(transparent.sameAs(solid))
        assertFalse(solid.sameAs(scene))
        val json = AvatarExport.recipeJson(recipe)
        val back = IdlJson.decodeFromString(AvatarConfiguration.serializer(), json)
        assertEquals(recipe, back)
        assertTrue(json.contains("base_teardrop"))
    }

    private fun render(size: Int, backdrop: ExportBackdrop) = AvatarExport.render(
        recipe,
        registry,
        ExpressionCatalog.EMPTY,
        pictures,
        size,
        backdrop,
    )
}
