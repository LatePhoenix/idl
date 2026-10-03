package app.idl.domain

import java.time.Duration
import java.time.Instant

/** The Status Deck's editable state; converts to a manual [PresenceState]. */
data class StatusDraft(
    val mood: Mood? = null,
    val availability: Availability? = null,
    val intent: StatusIntent? = null,
    val activity: ActivityType? = null,
    val prop: Prop? = null,
    val note: String = "",
    val duration: Duration = Expiry.DEFAULT,
    /** Extra visuals carried over from a quick state (scene, accessories). */
    val visual: VisualOverride? = null,
) {
    val isEmpty: Boolean
        get() = mood == null && availability == null && intent == null &&
            (activity == null || activity == ActivityType.NONE) && prop == null && note.isBlank()

    fun toState(now: Instant): PresenceState {
        val base = visual ?: VisualOverride()
        val props = prop?.takeIf { it != Prop.NONE }?.let { listOf(it) } ?: base.props
        return PresenceState(
            source = PresenceSource.MANUAL,
            mood = mood,
            availability = availability,
            intent = intent?.takeIf { it != StatusIntent.NO_PREFERENCE },
            activity = activity?.takeIf { it != ActivityType.NONE }?.let { Activity(type = it) },
            note = note.trim().take(NOTE_MAX).ifEmpty { null },
            visual = base.copy(props = props).takeIf { it != VisualOverride() },
            startedAt = now,
            expiresAt = Expiry.expiresAt(now, duration),
            updatedAt = now,
        )
    }

    companion object {
        const val NOTE_MAX = 80

        fun from(state: PresenceState?, now: Instant): StatusDraft {
            if (state == null || !state.isLive(now)) return StatusDraft()
            val remaining = Duration.between(now, state.expiresAt)
            return StatusDraft(
                mood = state.mood,
                availability = state.availability,
                intent = state.intent,
                activity = state.activity?.type,
                prop = state.visual?.props?.firstOrNull(),
                note = state.note.orEmpty(),
                duration = Expiry.PRESETS.minByOrNull { (it - remaining).abs() } ?: Expiry.DEFAULT,
                visual = state.visual,
            )
        }

        fun from(quick: QuickState) = StatusDraft(
            mood = quick.mood,
            availability = quick.availability,
            intent = quick.intent,
            activity = quick.activity,
            prop = quick.visual?.props?.firstOrNull(),
            duration = quick.duration,
            visual = quick.visual,
        )
    }
}
