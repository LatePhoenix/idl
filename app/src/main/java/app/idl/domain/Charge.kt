package app.idl.domain

/**
 * Passive Charge earned while mutual home-screen widgets are pinned.
 * Callers supply the clock. Nothing here reads wall time or starts a timer.
 */
data class UserEconomyState(
    val userId: String,
    val currentCharge: Long,
    val lastEvaluatedEpochMs: Long,
    val lifetimeChargeEarned: Long,
)

data class FriendTether(
    val friendUserId: String,
    val hasLocalWidgetInstalled: Boolean,
    val hasRemoteWidgetInstalled: Boolean,
    val tetherActivatedEpochMs: Long?,
) {
    val isMutuallyTethered: Boolean
        get() = hasLocalWidgetInstalled && hasRemoteWidgetInstalled
}

/** What the Status Deck shows. [currentCharge] is the last committed balance. */
data class ChargeState(
    val currentCharge: Long = 0,
    val hourlyRate: Long = 0,
    val activeTethers: Int = 0,
    val lastEvaluatedEpochMs: Long = 0,
    val lifetimeChargeEarned: Long = 0,
) {
    companion object {
        val EMPTY = ChargeState()
    }
}

data class ChargeEvaluation(
    val state: UserEconomyState,
    val earned: Long,
    val hourlyRate: Long,
)

/**
 * Deterministic Charge accrual.
 *
 * Rate is the sum of marginal rates for the current mutual-tether count:
 * 10, 10, 8, 6, then 2 per further tether. The whole gap uses that rate;
 * there is no per-tether history.
 *
 * Whole Charge only. Leftover milliseconds stay on [UserEconomyState.lastEvaluatedEpochMs]
 * so evaluating often does not drop a fraction. A zero rate, or a gap longer than
 * 24 hours, moves the timestamp to now: untethered time and time past the ceiling
 * are not paid later. The ceiling limits one evaluation's earnings, not the balance.
 */
object ChargeEngine {
    const val HOUR_MS = 3_600_000L
    const val CAP_HOURS = 24L
    const val CAP_MS = CAP_HOURS * HOUR_MS

    fun hourlyRate(activeTethers: Int): Long {
        if (activeTethers <= 0) return 0
        var total = 0L
        for (i in 1..activeTethers) {
            total += when (i) {
                1, 2 -> 10L
                3 -> 8L
                4 -> 6L
                else -> 2L
            }
        }
        return total
    }

    fun activeCount(tethers: List<FriendTether>): Int = tethers.count { it.isMutuallyTethered }

    fun evaluate(state: UserEconomyState, nowEpochMs: Long, activeTethers: Int): ChargeEvaluation {
        val rate = hourlyRate(activeTethers)
        if (nowEpochMs < state.lastEvaluatedEpochMs) {
            return ChargeEvaluation(state, earned = 0, hourlyRate = rate)
        }
        val elapsed = nowEpochMs - state.lastEvaluatedEpochMs
        if (rate == 0L) {
            return ChargeEvaluation(
                state.copy(lastEvaluatedEpochMs = nowEpochMs),
                earned = 0,
                hourlyRate = 0,
            )
        }
        if (elapsed > CAP_MS) {
            val earned = rate * CAP_HOURS
            return ChargeEvaluation(
                state.copy(
                    currentCharge = state.currentCharge + earned,
                    lastEvaluatedEpochMs = nowEpochMs,
                    lifetimeChargeEarned = state.lifetimeChargeEarned + earned,
                ),
                earned = earned,
                hourlyRate = rate,
            )
        }
        val earned = rate * elapsed / HOUR_MS
        val consumed = earned * HOUR_MS / rate
        return ChargeEvaluation(
            state.copy(
                currentCharge = state.currentCharge + earned,
                lastEvaluatedEpochMs = state.lastEvaluatedEpochMs + consumed,
                lifetimeChargeEarned = state.lifetimeChargeEarned + earned,
            ),
            earned = earned,
            hourlyRate = rate,
        )
    }
}
