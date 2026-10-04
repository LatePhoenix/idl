package app.idl.avatar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import app.idl.domain.AvatarConfig
import app.idl.domain.IdlJson
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Flattened avatar bitmaps (decision D-27). The key is a hash of the already-filtered
 * inputs, so a cached image never contains a field the viewer was not sent.
 */
class RenderCache(private val root: File, memoryEntries: Int = 32) {
    private val memory = LruCache<String, Bitmap>(memoryEntries)

    fun bitmap(key: String, render: () -> Bitmap): Bitmap {
        memory.get(key)?.let { return it }
        val file = File(root, "$key.png")
        if (file.isFile) {
            BitmapFactory.decodeFile(file.absolutePath)?.let { cached ->
                memory.put(key, cached)
                return cached
            }
        }
        val fresh = render()
        memory.put(key, fresh)
        root.mkdirs()
        file.outputStream().use { fresh.compress(Bitmap.CompressFormat.PNG, 100, it) }
        trimDisk()
        return fresh
    }

    private fun trimDisk() {
        val files = root.listFiles()?.filter { it.isFile } ?: return
        if (files.size <= 80) return
        files.sortedBy { it.lastModified() }.take(files.size - 64).forEach { it.delete() }
    }

    companion object {
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
    }
}

private fun Enum<*>.wireName(): String = name.lowercase(Locale.ROOT)
