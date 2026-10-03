package app.idl.domain

import java.time.Duration
import java.time.Instant

object Expiry {
    val DEFAULT: Duration = Duration.ofHours(4)
    val MAX: Duration = Duration.ofDays(7)
    val MIN: Duration = Duration.ofMinutes(5)

    /** Choices offered in the Status Deck. */
    val PRESETS: List<Duration> = listOf(
        Duration.ofMinutes(30),
        Duration.ofHours(1),
        Duration.ofHours(2),
        Duration.ofHours(4),
        Duration.ofHours(8),
        Duration.ofHours(24),
    )

    /** Every status expires: clamps a requested duration into [MIN, MAX]. */
    fun expiresAt(start: Instant, requested: Duration?): Instant {
        val d = (requested ?: DEFAULT).coerceIn(MIN, MAX)
        return start.plus(d)
    }

    /** The next instant at which any of [instants] passes, or null if none is in the future. */
    fun nextExpiry(instants: Iterable<Instant?>, now: Instant): Instant? =
        instants.filterNotNull().filter { it.isAfter(now) }.minOrNull()

    fun label(d: Duration): String = when {
        d.toHours() >= 24 && d.toHours() % 24 == 0L -> "${d.toDays()}d"
        d.toMinutes() >= 60 && d.toMinutes() % 60 == 0L -> "${d.toHours()}h"
        else -> "${d.toMinutes()}m"
    }

    /** "3h left", "12m left", "<1m left"; null if already expired. */
    fun remaining(expiresAt: Instant?, now: Instant): String? {
        if (expiresAt == null || !expiresAt.isAfter(now)) return null
        val d = Duration.between(now, expiresAt)
        return when {
            d.toDays() >= 1 -> "${d.toDays()}d left"
            d.toHours() >= 1 -> "${d.toHours()}h left"
            d.toMinutes() >= 1 -> "${d.toMinutes()}m left"
            else -> "<1m left"
        }
    }

    /** "just now", "5m ago", "3h ago", "2d ago". */
    fun ago(then: Instant?, now: Instant): String? {
        if (then == null) return null
        val d = Duration.between(then, now).coerceAtLeast(Duration.ZERO)
        return when {
            d.toMinutes() < 1 -> "just now"
            d.toHours() < 1 -> "${d.toMinutes()}m ago"
            d.toDays() < 1 -> "${d.toHours()}h ago"
            else -> "${d.toDays()}d ago"
        }
    }
}

private fun Duration.coerceIn(min: Duration, max: Duration): Duration =
    if (this < min) min else if (this > max) max else this

private fun Duration.coerceAtLeast(min: Duration): Duration = if (this < min) min else this
