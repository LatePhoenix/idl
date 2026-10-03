package app.idl.domain

import java.time.Duration
import java.time.Instant

val T0: Instant = Instant.parse("2026-10-03T14:00:00Z")

fun hours(h: Long): Duration = Duration.ofHours(h)

fun state(
    source: PresenceSource = PresenceSource.MANUAL,
    mood: Mood? = null,
    availability: Availability? = null,
    intent: StatusIntent? = null,
    activity: Activity? = null,
    note: String? = null,
    visual: VisualOverride? = null,
    start: Instant = T0,
    expires: Instant = T0.plus(hours(4)),
    updated: Instant = start,
) = PresenceState(source, mood, availability, intent, activity, note, visual, start, expires, updated)
