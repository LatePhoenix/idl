package app.idl.avatar

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.idl.domain.avatar.AssetPacks
import app.idl.domain.avatar.AvatarResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class VectorSliceDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun sliceRendersAt48And512WithExactDimensions() {
        val small = bitmap(EmojiSlice.recipes.first { it.id == "glasses" }, 48)
        val large = bitmap(EmojiSlice.recipes.first { it.id == "glasses" }, 512)
        assertEquals(48, small.width)
        assertEquals(48, small.height)
        assertEquals(512, large.width)
        assertEquals(512, large.height)
    }

    @Test fun theSameRecipeTwiceMatches() {
        val recipe = EmojiSlice.recipes.first { it.id == "scene" }
        assertTrue(bitmap(recipe, 128).sameAs(bitmap(recipe, 128)))
    }

    @Test fun aPictureLoadsOncePerContentVersion() {
        val reads = AtomicInteger()
        val cache = cache(reads)
        val registry = registry()
        val recipe = EmojiSlice.recipes.first { it.id == "glasses" }
        EmojiSlice.bitmap(registry, cache, recipe, 64)
        val once = reads.get()
        assertTrue(once > 0)
        EmojiSlice.bitmap(registry, cache, recipe, 64)
        assertEquals(once, reads.get())
        val asset = registry.asset("base_teardrop")!!
        assertNull(cache.get(asset.copy(contentVersion = 2)))
        assertEquals(once + 1, reads.get())
    }

    private fun bitmap(recipe: EmojiSlice.Recipe, size: Int): Bitmap =
        EmojiSlice.bitmap(registry(), cache(AtomicInteger()), recipe, size)

    private fun registry() = AssetPacks.registry { path ->
        context.assets.open(path).bufferedReader().use { it.readText() }
    }

    private fun cache(reads: AtomicInteger) = VectorPictureCache { file ->
        reads.incrementAndGet()
        val prefixes = AssetPacks.SHIPPED.map { it.substringBeforeLast("manifest.json") }
        prefixes.firstNotNullOfOrNull { prefix ->
            runCatching { context.assets.open(prefix + file).bufferedReader().use { it.readText() } }.getOrNull()
        } ?: throw FileNotFoundException(file)
    }
}
