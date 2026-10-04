package app.idl.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections

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

    @Test fun `concurrent puts gets and owner deletes keep a consistent byte count`() {
        val lru = ByteLruCache<Int>(maxBytes = 4_000) { it.coerceAtLeast(1) }
        val root = Files.createTempDirectory("idl-renders").toFile()
        val files = RenderCache(root)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())
        val threads = (0 until 8).map { t ->
            Thread {
                try {
                    repeat(80) { i ->
                        val owner = "u_${t % 3}"
                        lru.put("$owner|$i", (i % 30) + 1)
                        lru.get("$owner|$i")
                        if (i % 5 == 0) lru.removePrefixed("$owner|")
                        val stamp = files.stamp(owner)
                        files.commit(owner, "k${i % 10}", stamp) { it.write(byteArrayOf((i and 0xFF).toByte())) }
                        if (i % 4 == 0) files.deleteOwner(owner)
                        lru.byteCount()
                        files.memoryBytes()
                    }
                } catch (t: Throwable) {
                    errors.add(t)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertTrue(errors.joinToString("\n"), errors.isEmpty())
        lru.byteCount()
        files.memoryBytes()
        root.deleteRecursively()
    }

    @Test fun `a render that finishes after its owner was purged leaves no files`() {
        val root = Files.createTempDirectory("idl-renders").toFile()
        val cache = RenderCache(root)
        val stamp = cache.stamp("u_ari")
        val published = cache.commit("u_ari", "one", stamp) {
            cache.deleteOwner("u_ari")
            it.write("late".toByteArray())
        }
        assertFalse(published)
        assertTrue(root.walkTopDown().none { it.isFile })
        assertEquals(0, cache.memoryBytes())
        root.deleteRecursively()
    }
}
