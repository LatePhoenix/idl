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

    private val lock = Any()
    private val slots = LinkedHashMap<String, Slot<V>>(16, 0.75f, true)
    private var used = 0

    fun get(key: String): V? = synchronized(lock) { slots[key]?.value }

    fun put(key: String, value: V) = synchronized(lock) {
        removeLocked(key)
        val bytes = sizeOf(value).coerceAtLeast(1)
        slots[key] = Slot(value, bytes)
        used += bytes
        evictLocked()
    }

    fun remove(key: String) = synchronized(lock) { removeLocked(key) }

    fun removePrefixed(prefix: String) = synchronized(lock) {
        slots.keys.filter { it.startsWith(prefix) }.toList().forEach { removeLocked(it) }
    }

    fun evictAll() = synchronized(lock) {
        slots.clear()
        used = 0
    }

    /** Bytes retained. Equal to the sum of the entry sizes. */
    fun byteCount(): Int = synchronized(lock) {
        val summed = slots.values.sumOf { it.bytes }
        check(summed == used) { "cache bytes $used != $summed" }
        used
    }

    private fun removeLocked(key: String) {
        val old = slots.remove(key) ?: return
        used -= old.bytes
    }

    private fun evictLocked() {
        val it = slots.entries.iterator()
        while (used > maxBytes && it.hasNext()) {
            used -= it.next().value.bytes
            it.remove()
        }
    }
}
