package app.idl.domain.avatar

import android.graphics.Color
import app.idl.avatar.AvatarRenderer
import app.idl.avatar.RenderContrast
import app.idl.avatar.VectorPictureCache
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Compact-widget (48 px) eye and mouth coverage (AP-8, F-33).
 *
 * Feature pixels are the non-skin pixels inside the eye and mouth zones from the Studio guides
 * (eye union [297, 356, 728, 484], mouth [372, 540, 652, 790], split at x = 512). A worn item
 * must keep at least 60% of those pixels, the same floor as `tools/studio/engine/legibility.py`.
 * Eyewear may pass on remaining contrast when a tint shifts the color but the eye is still readable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class LegibilityTest {
    private val registry = registryWithFixtures()
    private val pictures = VectorPictureCache(
        packDirectory = { asset -> registry.packDirectory(asset.id) },
    ) { path ->
        val imported = File(importedRoot, path)
        if (imported.isFile) imported.readText() else File(repoRoot(), "app/src/main/assets/$path").readText()
    }
    private val resolver = AvatarResolver(registry)

    @Test fun `a dark shape over the eyes fails the zone measure`() {
        val face = pixels(neutral())
        val bare = pixels(blank())
        val covered = pixels(neutral().copy(signatureFaceAccessoryAssetId = EYE_COVER))
        val eyes = minOf(
            score(face, covered, bare, LEFT_EYE, eyewear = true),
            score(face, covered, bare, RIGHT_EYE, eyewear = true),
        )
        assertTrue("an eye cover should hide the eyes, score was $eyes", eyes < PASS)
    }

    @Test fun `shipped items and importer fixtures keep the eyes and mouth at 48 px`() {
        val face = pixels(neutral())
        val bare = pixels(blank())
        val fixtures = setOf("glasses_round_thick", "hat_cuffed_beanie", "hair_short_tufted")
        val worn = registry.allAssets.filter { asset ->
            asset.render.type == "vector" && asset.category in WORN && asset.id != EYE_COVER
        }
        assertTrue(fixtures.all { id -> worn.any { it.id == id } })
        val failures = mutableListOf<String>()
        for (asset in worn.sortedBy { it.id }) {
            val pixels = pixels(wearing(asset))
            val eyewear = asset.category == AssetCategory.FACE_ACCESSORY
            val left = score(face, pixels, bare, LEFT_EYE, eyewear)
            val right = score(face, pixels, bare, RIGHT_EYE, eyewear)
            val mouth = score(face, pixels, bare, MOUTH, eyewear = false)
            if (left < PASS) failures.add("${asset.id} left eye $left")
            if (right < PASS) failures.add("${asset.id} right eye $right")
            // A beard is meant to cover the mouth. Every other worn item has to leave it readable.
            if (asset.category != AssetCategory.FACIAL_HAIR && mouth < PASS) {
                failures.add("${asset.id} mouth $mouth")
            }
        }
        assertTrue(failures.joinToString(), failures.isEmpty())
    }

    private fun neutral() = config(base = "base_teardrop", scene = null, frame = null)

    private fun blank() = neutral().copy(restingExpressionId = "blank_face")

    private fun wearing(asset: AssetDef): AvatarConfiguration {
        val base = neutral()
        return when (asset.category) {
            AssetCategory.HAIR -> base.copy(itemIds = mapOf("hair" to listOf(asset.id)))
            AssetCategory.FACIAL_HAIR -> base.copy(itemIds = mapOf("facial_hair" to listOf(asset.id)))
            AssetCategory.HEAD_ACCESSORY -> base.copy(signatureHeadAccessoryAssetId = asset.id)
            AssetCategory.FACE_ACCESSORY -> base.copy(signatureFaceAccessoryAssetId = asset.id)
            else -> base
        }
    }

    private fun pixels(configuration: AvatarConfiguration): IntArray {
        val resolved = resolver.resolve(
            AvatarRenderRequest(
                configuration = configuration,
                target = RenderTarget.COMPACT_WIDGET,
                sizePx = SIZE,
            ),
        )
        val bitmap = AvatarRenderer.bitmap(resolved, registry, pictures, SIZE, contrast = RenderContrast())
        val out = IntArray(SIZE * SIZE)
        bitmap.getPixels(out, 0, SIZE, 0, 0, SIZE, SIZE)
        return out
    }

    private data class Zone(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun score(face: IntArray, worn: IntArray, bare: IntArray, zone: Zone, eyewear: Boolean): Float {
        var feature = 0
        var kept = 0
        var featureLum = 0.0
        var wornFeatureLum = 0.0
        for (y in zone.top until zone.bottom) {
            for (x in zone.left until zone.right) {
                val i = index(x, y)
                if (close(face[i], bare[i])) continue
                feature++
                featureLum += lum(face[i])
                wornFeatureLum += lum(worn[i])
                if (close(face[i], worn[i])) kept++
            }
        }
        if (feature == 0) return 1f
        var result = kept.toFloat() / feature
        if (eyewear) {
            val pad = skinAround(face, worn, bare, zone)
            if (pad.count > 0) {
                val contrastBare = abs(featureLum / feature - pad.bareLum / pad.count)
                val contrastWorn = abs(wornFeatureLum / feature - pad.wornLum / pad.count)
                if (contrastBare > 1.0) {
                    val ratio = (contrastWorn / contrastBare).toFloat().coerceIn(0f, 1f)
                    if (ratio > result) result = ratio
                }
            }
        }
        return result
    }

    private data class SkinSample(val count: Int, val bareLum: Double, val wornLum: Double)

    /** Unchanged skin just outside the zone, so a tinted lens can be judged by contrast. */
    private fun skinAround(face: IntArray, worn: IntArray, bare: IntArray, zone: Zone): SkinSample {
        var count = 0
        var bareLum = 0.0
        var wornLum = 0.0
        val left = (zone.left - 3).coerceAtLeast(0)
        val top = (zone.top - 3).coerceAtLeast(0)
        val right = (zone.right + 3).coerceAtMost(SIZE)
        val bottom = (zone.bottom + 3).coerceAtMost(SIZE)
        for (y in top until bottom) {
            for (x in left until right) {
                if (x >= zone.left && x < zone.right && y >= zone.top && y < zone.bottom) continue
                val i = index(x, y)
                if (!close(face[i], bare[i]) || !close(face[i], worn[i])) continue
                count++
                bareLum += lum(face[i])
                wornLum += lum(worn[i])
            }
        }
        return SkinSample(count, bareLum, wornLum)
    }

    private fun close(a: Int, b: Int): Boolean {
        val delta = abs(Color.red(a) - Color.red(b)) +
            abs(Color.green(a) - Color.green(b)) +
            abs(Color.blue(a) - Color.blue(b))
        return delta <= TOLERANCE
    }

    private fun lum(color: Int) =
        Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114

    private fun index(x: Int, y: Int) = y * SIZE + x

    companion object {
        private const val SIZE = 48
        private const val PASS = 0.6f
        private const val TOLERANCE = 60
        private const val EYE_COVER = "glasses_test_eye_cover"
        private val WORN = setOf(
            AssetCategory.HAIR,
            AssetCategory.HEAD_ACCESSORY,
            AssetCategory.FACE_ACCESSORY,
            AssetCategory.FACIAL_HAIR,
        )
        private val LEFT_EYE = zone(297f, 356f, 512f, 484f)
        private val RIGHT_EYE = zone(512f, 356f, 728f, 484f)
        private val MOUTH = zone(372f, 540f, 652f, 790f)

        private fun zone(left: Float, top: Float, right: Float, bottom: Float): Zone {
            val (x0, y0) = Framing.HEAD.toOutput(left, top, SIZE.toFloat())
            val (x1, y1) = Framing.HEAD.toOutput(right, bottom, SIZE.toFloat())
            return Zone(
                x0.toInt().coerceIn(0, SIZE - 1),
                y0.toInt().coerceIn(0, SIZE - 1),
                ceil(x1.toDouble()).toInt().coerceIn(1, SIZE),
                ceil(y1.toDouble()).toInt().coerceIn(1, SIZE),
            )
        }

        private val importedRoot: File by lazy { importFixtures() }

        private fun registryWithFixtures(): AssetRegistry {
            val root = repoRoot()
            val readReal = { path: String -> File(root, "app/src/main/assets/$path").readText() }
            val core = AssetManifest.parse(readReal("packs/core_proto/v1/manifest.json"))
            val emoji = AssetManifest.parse(File(importedRoot, "packs/emoji_core/v2/manifest.json").readText())
            val cover = AssetManifest.parse(EYE_COVER_MANIFEST)
            return AssetRegistry(
                listOf(core, emoji, cover),
                listOf("packs/core_proto/v1/", "packs/emoji_core/v2/", "packs/legibility_fixture/v1/"),
            )
        }

        private fun importFixtures(): File {
            val root = repoRoot()
            val dest = File(root, "app/build/legibility-import").apply {
                deleteRecursively()
                mkdirs()
            }
            val script = File(dest, "import_fixtures.py")
            script.writeText(
                """
                import shutil, sys
                from pathlib import Path
                dest = Path(sys.argv[1])
                repo = Path(sys.argv[2])
                sys.path.insert(0, str(repo / "tools"))
                import import_art
                shutil.copytree(repo / "art" / "emoji_core", dest / "art" / "emoji_core")
                shutil.copytree(repo / "app" / "src" / "main" / "assets" / "packs" / "emoji_core", dest / "packs" / "emoji_core")
                shutil.copytree(repo / "app" / "src" / "main" / "assets" / "packs" / "core_proto", dest / "packs" / "core_proto")
                incoming = dest / "art" / "incoming"
                incoming.mkdir(parents=True)
                paths = import_art.RepoPaths(
                    art=dest / "art",
                    packs=dest / "packs",
                    catalog_json=dest / "catalog.json",
                    catalog_sql=dest / "catalog.sql",
                    incoming=incoming,
                )
                for name in ("glasses_round_thick", "hat_cuffed_beanie", "hair_short_tufted"):
                    drop = incoming / name
                    shutil.copytree(repo / "tools" / "testdata" / "art_incoming" / name, drop)
                    import_art.import_drop(drop, paths=paths)
                """.trimIndent(),
            )
            val output = StringBuilder()
            var ran = false
            for (command in listOf("python", "python3")) {
                try {
                    val process = ProcessBuilder(command, script.absolutePath, dest.absolutePath, root.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    output.append(process.inputStream.bufferedReader().readText())
                    val code = process.waitFor()
                    ran = true
                    if (code == 0) {
                        val coverDir = File(dest, "packs/legibility_fixture/v1/pictures")
                        coverDir.mkdirs()
                        File(coverDir, "glasses_test_eye_cover.json").writeText(EYE_COVER_PICTURE)
                        for (id in listOf("eyes_test_blank", "brows_test_blank", "mouth_test_blank")) {
                            File(coverDir, "$id.json").writeText(blankPicture(id))
                        }
                        return dest
                    }
                    error("importer exited $code\n$output")
                } catch (_: java.io.IOException) {
                    continue
                }
            }
            if (!ran) error("python was not on PATH")
            error(output.toString())
        }

        private fun blankPicture(id: String) = """
            {
              "schemaVersion": 1,
              "id": "$id",
              "contentVersion": 1,
              "viewBox": 1024,
              "parts": []
            }
        """.trimIndent()

        private val EYE_COVER_PICTURE = """
            {
              "schemaVersion": 1,
              "id": "glasses_test_eye_cover",
              "contentVersion": 1,
              "viewBox": 1024,
              "parts": [
                {
                  "id": "cover",
                  "zBand": 80,
                  "fill": { "slot": "glasses.frame" },
                  "commands": "M 180 240 L 860 240 L 860 600 L 180 600 Z"
                }
              ]
            }
        """.trimIndent()

        private val EYE_COVER_MANIFEST = """
            {
              "packId": "legibility_fixture",
              "version": 1,
              "assets": [
                {
                  "id": "glasses_test_eye_cover",
                  "category": "face_accessory",
                  "accessibilityLabel": "Eye cover",
                  "render": { "type": "vector", "file": "pictures/glasses_test_eye_cover.json" },
                  "collection": "legibility_fixture",
                  "license": "proprietary-idl",
                  "contentVersion": 1,
                  "colorSlots": { "glasses.frame": "#111111" },
                  "compatibleBases": ["base_teardrop"]
                },
                {
                  "id": "eyes_test_blank",
                  "category": "face_eye",
                  "accessibilityLabel": "Blank eyes",
                  "render": { "type": "vector", "file": "pictures/eyes_test_blank.json" },
                  "contentVersion": 1
                },
                {
                  "id": "brows_test_blank",
                  "category": "face_brow",
                  "accessibilityLabel": "Blank brows",
                  "render": { "type": "vector", "file": "pictures/brows_test_blank.json" },
                  "contentVersion": 1
                },
                {
                  "id": "mouth_test_blank",
                  "category": "face_mouth",
                  "accessibilityLabel": "Blank mouth",
                  "render": { "type": "vector", "file": "pictures/mouth_test_blank.json" },
                  "contentVersion": 1
                }
              ],
              "expressions": [
                {
                  "id": "blank_face",
                  "label": "Blank",
                  "eyes": "eyes_test_blank",
                  "brows": "brows_test_blank",
                  "mouth": "mouth_test_blank",
                  "baseOverrides": {
                    "base_teardrop": {
                      "eyes": "eyes_test_blank",
                      "brows": "brows_test_blank",
                      "mouth": "mouth_test_blank"
                    }
                  }
                }
              ]
            }
        """.trimIndent()
    }
}
