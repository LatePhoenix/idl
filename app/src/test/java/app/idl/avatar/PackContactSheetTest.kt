package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import app.idl.domain.wire
import app.idl.domain.avatar.AssetCategory
import app.idl.domain.avatar.AssetDef
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.AvatarRenderRequest
import app.idl.domain.avatar.AvatarResolver
import app.idl.domain.avatar.ColorSlots
import app.idl.domain.avatar.LayerPriority
import app.idl.domain.avatar.RenderTarget
import app.idl.domain.avatar.ResolvedAvatar
import app.idl.domain.avatar.ResolvedLayer
import app.idl.domain.avatar.coreRegistry
import app.idl.domain.avatar.repoRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * One sheet per vector category. 48 and 96 use head framing. 512 uses bust framing.
 * Light and dark are the sheet ground; the avatar scene stays the pack default unless the
 * asset itself is a scene.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class PackContactSheetTest {
    private val registry = coreRegistry()
    private val pictures = VectorPictureCache(
        packDirectory = { asset -> registry.packDirectory(asset.id) },
    ) { path ->
        File(repoRoot(), "app/src/main/assets/$path").readText()
    }
    private val neutral = EmojiSlice.configuration(EmojiSlice.recipes.first { it.id == "neutral" })

    @Test fun `each vector category has a contact sheet at 48 96 and 512`() {
        val byPack = linkedMapOf<String, MutableMap<AssetCategory, MutableList<AssetDef>>>()
        AssetPacks.SHIPPED.forEach { path ->
            val manifest = app.idl.domain.avatar.AssetManifest.parse(
                File(repoRoot(), "app/src/main/assets/$path").readText(),
            )
            manifest.assets.filter { it.render.type == "vector" }.forEach { asset ->
                byPack.getOrPut(manifest.packId) { linkedMapOf() }
                    .getOrPut(asset.category) { mutableListOf() }
                    .add(asset)
            }
        }
        assertTrue(byPack.containsKey("emoji_core"))
        val specs = listOf(
            Sheet(48, RenderTarget.COMPACT_WIDGET, dark = false, "48"),
            Sheet(48, RenderTarget.COMPACT_WIDGET, dark = true, "48_dark"),
            Sheet(96, RenderTarget.STANDARD_WIDGET, dark = false, "96"),
            Sheet(96, RenderTarget.STANDARD_WIDGET, dark = true, "96_dark"),
            Sheet(512, RenderTarget.PROFILE, dark = false, "512"),
            Sheet(512, RenderTarget.PROFILE, dark = true, "512_dark"),
        )
        byPack.forEach { (packId, categories) ->
            categories.toSortedMap(compareBy { it.wire }).forEach { (category, assets) ->
                val ordered = assets.sortedBy { it.id }
                specs.forEach { spec ->
                    sheet(ordered, spec).captureRoboImage(
                        "src/test/snapshots/packs/$packId/${category.wire}_${spec.suffix}.png",
                    )
                }
            }
        }
    }

    private fun sheet(assets: List<AssetDef>, spec: Sheet): Bitmap {
        val scale = when (spec.size) {
            48 -> 4
            96 -> 2
            else -> 1
        }
        val cell = spec.size * scale
        val gap = 12
        val header = 28
        val columns = assets.size
        val width = gap + columns * cell + columns * gap
        val height = header + cell + gap
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (spec.dark) DARK_GROUND else Color.WHITE)
        val paint = Paint().apply {
            color = if (spec.dark) LIGHT_LABEL else Color.BLACK
            textSize = 18f
            isAntiAlias = true
        }
        assets.forEachIndexed { index, asset ->
            val left = gap + index * (cell + gap)
            canvas.drawText(asset.id, left.toFloat(), 20f, paint)
            val resolved = equipped(asset, spec)
            if (asset.category.drawn) {
                assertTrue("${asset.id} on ${spec.suffix}", resolved.layers.any { it.assetId == asset.id })
            }
            val face = AvatarRenderer.bitmap(resolved, registry, pictures, spec.size)
            canvas.drawBitmap(
                face,
                null,
                Rect(left, header, left + cell, header + cell),
                Paint().apply { isFilterBitmap = false },
            )
        }
        return bitmap
    }

    private fun equipped(asset: AssetDef, spec: Sheet): ResolvedAvatar {
        val config = equip(neutral, asset)
        val request = AvatarRenderRequest(config, target = spec.target, sizePx = spec.size)
        val resolved = AvatarResolver(registry).resolve(request)
        if (!asset.category.drawn || resolved.layers.any { it.assetId == asset.id }) return resolved
        val replaced = resolved.layers.any { it.category == asset.category && !asset.category.multiple }
        val layers = if (replaced) {
            resolved.layers.map { layer ->
                if (layer.category == asset.category) layer.copy(assetId = asset.id, variant = null) else layer
            }
        } else {
            resolved.layers + ResolvedLayer(asset.id, asset.category, asset.category.defaultZ, LayerPriority.SIGNATURE)
        }
        return resolved.copy(layers = layers, colorSlots = ColorSlots.resolve(layers, config, registry))
    }

    private fun equip(config: AvatarConfiguration, asset: AssetDef): AvatarConfiguration = when (asset.category) {
        AssetCategory.BASE -> config.copy(baseAssetId = asset.id)
        AssetCategory.PALETTE -> config.copy(paletteAssetId = asset.id)
        AssetCategory.EYE_FAMILY -> config.copy(eyeFamilyAssetId = asset.id)
        AssetCategory.MOUTH_FAMILY -> config.copy(mouthFamilyAssetId = asset.id)
        AssetCategory.SCENE -> config.copy(defaultSceneAssetId = asset.id)
        AssetCategory.FRAME -> config.copy(defaultFrameAssetId = asset.id)
        AssetCategory.SIGNATURE_FEATURE -> config.copy(signatureFeatureAssetIds = listOf(asset.id))
        AssetCategory.HEAD_ACCESSORY -> config.copy(signatureHeadAccessoryAssetId = asset.id)
        AssetCategory.BODY_ACCESSORY -> config.copy(signatureBodyAccessoryAssetId = asset.id)
        AssetCategory.FACE_ACCESSORY -> config.copy(signatureFaceAccessoryAssetId = asset.id)
        AssetCategory.FOREGROUND_PROP -> config.copy(defaultPropAssetId = asset.id)
        else -> config.copy(itemIds = config.itemIds + (asset.category.wire to listOf(asset.id)))
    }

    private data class Sheet(val size: Int, val target: RenderTarget, val dark: Boolean, val suffix: String)

    companion object {
        private const val DARK_GROUND = 0xFF17131B.toInt()
        private const val LIGHT_LABEL = 0xFFEAF1F8.toInt()
    }
}
