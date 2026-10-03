package app.idl.domain

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class Activity(
    val type: ActivityType = ActivityType.NONE,
    val label: String? = null,
    val source: PresenceSource = PresenceSource.MANUAL,
    val joinable: Boolean = false,
    val joinUrl: String? = null,
)

/** Visual hints a state applies on top of the owner's base avatar. */
@Serializable
data class VisualOverride(
    val expression: Expression? = null,
    val props: List<Prop> = emptyList(),
    val scene: Scene? = null,
    val headAccessory: HeadAccessory? = null,
    val bodyAccessory: BodyAccessory? = null,
)

/**
 * One source's contribution to a user's presence: the normalized Presence Envelope.
 * Every source (manual, VRCQ, desktop bridge, SDK) produces this same shape.
 *
 * Invisible mode is deliberately *not* a field here: it is a user-level toggle that must not
 * end just because a status expired (see IDL_DECISIONS D-16).
 */
@Serializable
data class PresenceState(
    val source: PresenceSource = PresenceSource.MANUAL,
    val mood: Mood? = null,
    val availability: Availability? = null,
    val intent: StatusIntent? = null,
    val activity: Activity? = null,
    val note: String? = null,
    val visual: VisualOverride? = null,
    @Serializable(with = InstantSerializer::class) val startedAt: Instant,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant = startedAt,
    val priority: Int = source.priority,
) {
    fun isLive(now: Instant): Boolean = now.isBefore(expiresAt)
}

/** The single effective presence after precedence and expiry have been applied. */
data class ResolvedPresence(
    val mood: Mood? = null,
    val availability: Availability? = null,
    val intent: StatusIntent? = null,
    val activity: Activity? = null,
    val note: String? = null,
    val visual: VisualOverride = VisualOverride(),
    val updatedAt: Instant? = null,
    /** Earliest expiry among the states that contributed, i.e. when this view next changes. */
    val expiresAt: Instant? = null,
) {
    val isEmpty: Boolean
        get() = mood == null && availability == null && intent == null &&
            activity == null && note == null && visual == VisualOverride()

    companion object {
        val EMPTY = ResolvedPresence()
    }
}

/** Per-user configuration of automated presence sources. */
data class SourceSettings(
    val disabled: Set<PresenceSource> = emptySet(),
    /** If false (default), automated sources may not set mood, availability, intent or note. */
    val allowAutomatedCoreFields: Boolean = false,
    /** If false, activities from automated sources are not shown at all. */
    val showExternalActivity: Boolean = true,
)
