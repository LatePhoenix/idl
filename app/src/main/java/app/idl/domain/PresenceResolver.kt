package app.idl.domain

import java.time.Instant

/**
 * Deterministic precedence: highest valid, non-expired state wins per field.
 *
 * Order: priority desc, then most recent updatedAt, then source declaration order.
 * Manual mood/availability/intent/note always win; automated sources only contribute
 * activity and visual hints unless [SourceSettings.allowAutomatedCoreFields] is set.
 */
object PresenceResolver {

    private val order = compareByDescending<PresenceState> { it.priority }
        .thenByDescending { it.updatedAt }
        .thenBy { it.source.ordinal }

    fun resolve(
        states: List<PresenceState>,
        now: Instant,
        settings: SourceSettings = SourceSettings(),
    ): ResolvedPresence {
        val live = states
            .filter { it.isLive(now) && it.source !in settings.disabled }
            .sortedWith(order)
        if (live.isEmpty()) return ResolvedPresence.EMPTY

        val core = live.filter { !it.source.isAutomated || settings.allowAutomatedCoreFields }
        val activityStates = live.filter {
            it.activity != null && it.activity.type != ActivityType.NONE &&
                (!it.source.isAutomated || settings.showExternalActivity)
        }

        val contributors = mutableSetOf<PresenceState>()
        fun <T> pick(from: List<PresenceState>, get: (PresenceState) -> T?): T? {
            for (s in from) {
                val v = get(s)
                if (v != null) {
                    contributors += s
                    return v
                }
            }
            return null
        }

        val mood = pick(core) { it.mood }
        val availability = pick(core) { it.availability }
        val intent = pick(core) { it.intent }
        val note = pick(core) { it.note?.takeIf { n -> n.isNotBlank() } }
        val activity = pick(activityStates) { it.activity }
        val visual = VisualOverride(
            expression = pick(live) { it.visual?.expression },
            props = pick(live) { it.visual?.props?.takeIf { p -> p.isNotEmpty() } } ?: emptyList(),
            scene = pick(live) { it.visual?.scene },
            headAccessory = pick(live) { it.visual?.headAccessory },
            bodyAccessory = pick(live) { it.visual?.bodyAccessory },
        )

        return ResolvedPresence(
            mood = mood,
            availability = availability,
            intent = intent,
            activity = activity,
            note = note,
            visual = visual,
            updatedAt = contributors.maxOfOrNull { it.updatedAt },
            expiresAt = contributors.minOfOrNull { it.expiresAt },
        )
    }
}

/** Applies a resolved presence's visual effects to a base avatar. */
object AvatarComposer {

    fun expressionFor(mood: Mood?, availability: Availability?): Expression? = when (mood) {
        Mood.NEUTRAL -> Expression.NEUTRAL
        Mood.GOOD, Mood.HAPPY -> Expression.HAPPY
        Mood.EXCITED -> Expression.EXCITED
        Mood.SLEEPY -> Expression.SLEEPY
        Mood.TIRED, Mood.LOW_ENERGY -> Expression.TIRED
        Mood.STRESSED, Mood.ANXIOUS -> Expression.ANXIOUS
        Mood.SAD -> Expression.SAD
        Mood.SICK -> Expression.SICK
        Mood.FOCUSED -> Expression.FOCUSED
        Mood.CHAOTIC -> Expression.MISCHIEVOUS
        Mood.SOCIAL -> Expression.SOCIAL
        Mood.OVERWHELMED -> Expression.OVERWHELMED
        null -> when (availability) {
            Availability.AFK -> Expression.AFK
            Availability.DO_NOT_DISTURB -> Expression.DND
            else -> null
        }
    }

    fun compose(base: AvatarConfig, presence: ResolvedPresence): AvatarConfig {
        val v = presence.visual
        return base.copy(
            expression = v.expression
                ?: expressionFor(presence.mood, presence.availability)
                ?: base.expression,
            handProp = v.props.firstOrNull() ?: base.handProp,
            scene = v.scene ?: base.scene,
            headAccessory = v.headAccessory ?: base.headAccessory,
            bodyAccessory = v.bodyAccessory ?: base.bodyAccessory,
        )
    }
}
