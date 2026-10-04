package app.idl.avatar

/**
 * Least-recently-used map sized by [sizeOf], not by entry count.
 * Iteration order is eldest first, so eviction is deterministic.
 */
internal class ByteLruCache<V>(
    private val maxBytes: Int,
    private val sizeOf: (V) -> Int,
) {
    private class Slot<V>(val value: V, val bytes: Int)

    private val slots = LinkedHashMap<String, Slot<V>>(16, 0.75f, true)
    private var used = 0

    fun get(key: String): V? = slots[key]?.value

    fun put(key: String, value: V) {
        remove(key)
        val bytes = sizeOf(value).coerceAtLeast(1)
        slots[key] = Slot(value, bytes)
        used += bytes
        evict()
    }

    fun remove(key: String) {
        val old = slots.remove(key) ?: return
        used -= old.bytes
    }

    fun removePrefixed(prefix: String) {
        slots.keys.filter { it.startsWith(prefix) }.toList().forEach { remove(it) }
    }

    fun evictAll() {
        slots.clear()
        used = 0
    }

    private fun evict() {
        val it = slots.entries.iterator()
        while (used > maxBytes && it.hasNext()) {
            used -= it.next().value.bytes
            it.remove()
        }
    }
}
