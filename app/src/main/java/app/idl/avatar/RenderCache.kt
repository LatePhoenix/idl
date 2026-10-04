package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.idl.domain.AvatarConfig
import app.idl.domain.IdlJson
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Flattened avatar bitmaps (decision D-27). The key is a hash of the already-filtered
 * inputs, so a cached image never contains a field the viewer was not sent.
 * Files live under a directory named for the friend, so one person's renders can be deleted
 * without touching anyone else's. The memory budget is about 8 MB of bitmap bytes.
 */
class RenderCache(
    private val root: File,
    maxBytes: Int = MEMORY_BUDGET_BYTES,
) {
    private val memory = ByteLruCache<Bitmap>(maxBytes) { bitmap -> bitmap.byteCount }

    fun bitmap(key: String, ownerId: String, render: () -> Bitmap): Bitmap {
        val safeKey = fileName(key)
        val memKey = memoryKey(ownerId, safeKey)
        memory.get(memKey)?.let { return it }
        val dest = File(ownerDir(ownerId), "$safeKey.png")
        if (dest.isFile) {
            BitmapFactory.decodeFile(dest.absolutePath)?.let { cached ->
                memory.put(memKey, cached)
                return cached
            }
            dest.delete()
        }
        val fresh = render()
        publish(ownerId, safeKey) { out ->
            if (!fresh.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw IOException("png compress failed")
            }
        }
        memory.put(memKey, fresh)
        trimDisk()
        return fresh
    }

    /** Writes [ownerId]/[key].png by renaming a temp file in that same directory. */
    internal fun publish(ownerId: String, key: String, write: (OutputStream) -> Unit) {
        val dest = File(ownerDir(ownerId), "${fileName(key)}.png")
        writeAtomically(dest, write)
    }

    fun clear() {
        memory.evictAll()
        root.deleteRecursively()
    }

    /** Drops memory and disk entries for one friend. Other owners stay. */
    fun deleteOwner(ownerId: String) {
        val dir = ownerDir(ownerId)
        memory.removePrefixed(dir.name + SEPARATOR)
        dir.deleteRecursively()
    }

    private fun memoryKey(ownerId: String, key: String) = ownerDir(ownerId).name + SEPARATOR + key

    private fun ownerDir(ownerId: String): File = File(root, fileName(ownerId))

    private fun writeAtomically(dest: File, write: (OutputStream) -> Unit) {
        val dir = dest.parentFile ?: throw IOException("no cache directory")
        dir.mkdirs()
        val tmp = File(dir, dest.name + ".tmp")
        try {
            tmp.outputStream().use { out ->
                write(out)
                out.flush()
            }
            if (dest.exists() && !dest.delete()) throw IOException("could not replace ${dest.name}")
            if (!tmp.renameTo(dest)) throw IOException("could not publish ${dest.name}")
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private fun trimDisk() {
        val files = root.walkTopDown().filter { it.isFile && it.extension == "png" }.toList()
        if (files.size <= 80) return
        files.sortedBy { it.lastModified() }.take(files.size - 64).forEach { it.delete() }
    }

    companion object {
        /** D-27: about 8 MB of decoded bitmaps. */
        const val MEMORY_BUDGET_BYTES = 8 * 1024 * 1024

        /** Renders that are not a specific friend's widget. */
        const val OWNER_SELF = "_self"

        private const val SEPARATOR = "|"

        fun key(config: AvatarConfig, sizePx: Int, badges: AvatarBadges?, simplifyAtPx: Int): String {
            val raw = buildString {
                append(sizePx)
                append('|')
                append(simplifyAtPx)
                append('|')
                append(badges?.availability?.wireName())
                append('|')
                append(badges?.activity?.wireName())
                append('|')
                append(IdlJson.encodeToString(AvatarConfig.serializer(), config))
            }
            return keyOf(raw)
        }

        fun keyOf(raw: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            val hex = CharArray(digest.size * 2)
            val digits = "0123456789abcdef"
            digest.forEachIndexed { i, byte ->
                val v = byte.toInt() and 0xFF
                hex[i * 2] = digits[v ushr 4]
                hex[i * 2 + 1] = digits[v and 0x0F]
            }
            return String(hex)
        }

        private fun fileName(raw: String): String {
            val safe = raw.replace(Regex("[^A-Za-z0-9._-]"), "_")
            require(safe.isNotEmpty() && safe != "." && safe != "..") { "unsafe cache name" }
            return safe
        }
    }
}

private fun Enum<*>.wireName(): String = name.lowercase(Locale.ROOT)
