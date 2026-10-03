package app.idl.domain

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant

/** Wire name of an enum constant: `TEXT_ONLY` -> `text_only`. */
val Enum<*>.wire: String get() = name.lowercase()

/**
 * Serializes enums as lowercase snake_case strings. Unknown values decode to [fallback] so an
 * older client never crashes when the server adds a new value.
 */
open class WireEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val values: List<E>,
    private val fallback: E,
) : KSerializer<E> {
    override val descriptor = PrimitiveSerialDescriptor("app.idl.$serialName", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(value.wire)
    override fun deserialize(decoder: Decoder): E = fromWire(decoder.decodeString())
    fun fromWire(value: String?): E = values.firstOrNull { it.wire == value?.lowercase() } ?: fallback
}

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("app.idl.Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/** Shared JSON configuration for wire payloads and cached blobs. */
val IdlJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
