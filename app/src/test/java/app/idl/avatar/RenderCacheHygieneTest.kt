package app.idl.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RenderCacheHygieneTest {
    @Test fun `the lru evicts the least recently used entry by bytes`() {
        val cache = ByteLruCache<Int>(maxBytes = 10) { it }
        cache.put("a", 6)
        cache.put("b", 4)
        assertEquals(6, cache.get("a"))
        cache.put("c", 4)
        assertNull(cache.get("b"))
        assertEquals(6, cache.get("a"))
        assertEquals(4, cache.get("c"))
    }

    @Test fun `purging a friend deletes only that friend's files`() {
        val root = Files.createTempDirectory("idl-renders").toFile()
        val cache = RenderCache(root)
        cache.publish("u_ari", "one") { it.write("ari".toByteArray()) }
        cache.publish("u_bo", "one") { it.write("bo".toByteArray()) }
        cache.deleteOwner("u_ari")
        assertFalse(File(root, "u_ari").exists())
        assertEquals("bo", File(root, "u_bo/one.png").readText())
        root.deleteRecursively()
    }

    @Test fun `clear removes every owner's files`() {
        val root = Files.createTempDirectory("idl-renders").toFile()
        val cache = RenderCache(root)
        cache.publish("u_ari", "one") { it.write("ari".toByteArray()) }
        cache.publish(RenderCache.OWNER_SELF, "one") { it.write("self".toByteArray()) }
        cache.clear()
        assertTrue(root.walkTopDown().none { it.isFile })
        root.deleteRecursively()
    }

    @Test fun `a partial write never leaves a readable corrupt file`() {
        val root = Files.createTempDirectory("idl-renders").toFile()
        val cache = RenderCache(root)
        cache.publish("u_ari", "one") { it.write("good".toByteArray()) }
        try {
            cache.publish("u_ari", "one") { out ->
                out.write("partial".toByteArray())
                throw java.io.IOException("disk full")
            }
        } catch (e: java.io.IOException) {
            assertEquals("disk full", e.message)
        }
        assertEquals("good", File(root, "u_ari/one.png").readText())
        assertTrue(root.walkTopDown().none { it.name.endsWith(".tmp") })
        root.deleteRecursively()
    }
}
