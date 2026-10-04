package app.idl.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeEngineTest {
    private fun ledger(charge: Long = 0, last: Long = 0, lifetime: Long = 0) =
        UserEconomyState("me", charge, last, lifetime)

    @Test fun `marginal rates diminish after the second tether`() {
        assertEquals(0, ChargeEngine.hourlyRate(0))
        assertEquals(0, ChargeEngine.hourlyRate(-3))
        assertEquals(10, ChargeEngine.hourlyRate(1))
        assertEquals(20, ChargeEngine.hourlyRate(2))
        assertEquals(28, ChargeEngine.hourlyRate(3))
        assertEquals(34, ChargeEngine.hourlyRate(4))
        assertEquals(36, ChargeEngine.hourlyRate(5))
        assertEquals(38, ChargeEngine.hourlyRate(6))
        assertEquals(226, ChargeEngine.hourlyRate(100))
    }

    @Test fun `a mutual tether needs both widgets`() {
        val oneSided = FriendTether("a", hasLocalWidgetInstalled = true, hasRemoteWidgetInstalled = false, tetherActivatedEpochMs = null)
        val mutual = oneSided.copy(hasRemoteWidgetInstalled = true, tetherActivatedEpochMs = 5)
        assertFalse(oneSided.isMutuallyTethered)
        assertTrue(mutual.isMutuallyTethered)
        assertEquals(1, ChargeEngine.activeCount(listOf(oneSided, mutual)))
    }

    @Test fun `one hour at one tether earns ten and keeps the remainder clock`() {
        val start = 1_000_000L
        val result = ChargeEngine.evaluate(ledger(last = start), start + ChargeEngine.HOUR_MS, activeTethers = 1)
        assertEquals(10, result.earned)
        assertEquals(10, result.hourlyRate)
        assertEquals(10, result.state.currentCharge)
        assertEquals(10, result.state.lifetimeChargeEarned)
        assertEquals(start + ChargeEngine.HOUR_MS, result.state.lastEvaluatedEpochMs)
    }

    @Test fun `sub hour gaps bank time until a whole charge is earned`() {
        val start = 5_000L
        val threeMin = 180_000L
        val first = ChargeEngine.evaluate(ledger(last = start), start + threeMin, activeTethers = 1)
        assertEquals(0, first.earned)
        assertEquals(start, first.state.lastEvaluatedEpochMs)

        val second = ChargeEngine.evaluate(first.state, start + threeMin * 2, activeTethers = 1)
        assertEquals(1, second.earned)
        assertEquals(1, second.state.currentCharge)
        assertEquals(start + 360_000L, second.state.lastEvaluatedEpochMs)
    }

    @Test fun `two tethers earn the combined rate`() {
        val start = 0L
        val halfHour = ChargeEngine.HOUR_MS / 2
        val result = ChargeEngine.evaluate(ledger(charge = 50, last = start, lifetime = 7), start + halfHour, activeTethers = 2)
        assertEquals(20, result.hourlyRate)
        assertEquals(10, result.earned)
        assertEquals(60, result.state.currentCharge)
        assertEquals(17, result.state.lifetimeChargeEarned)
    }

    @Test fun `a day at the cap is paid and a longer gap is not`() {
        val start = 10_000L
        val day = ChargeEngine.CAP_MS
        val exact = ChargeEngine.evaluate(ledger(last = start), start + day, activeTethers = 1)
        assertEquals(240, exact.earned)
        assertEquals(start + day, exact.state.lastEvaluatedEpochMs)

        val now = start + day + ChargeEngine.HOUR_MS
        val over = ChargeEngine.evaluate(ledger(last = start), now, activeTethers = 1)
        assertEquals(240, over.earned)
        assertEquals(now, over.state.lastEvaluatedEpochMs)
        assertEquals(240, over.state.currentCharge)
    }

    @Test fun `twenty five hours at two tethers pays only the ceiling`() {
        val start = 0L
        val now = 25 * ChargeEngine.HOUR_MS
        val result = ChargeEngine.evaluate(ledger(last = start), now, activeTethers = 2)
        assertEquals(20 * 24, result.earned)
        assertEquals(now, result.state.lastEvaluatedEpochMs)
    }

    @Test fun `an untethered gap is discarded so a later tether cannot claim it`() {
        val start = 100L
        val later = start + 5 * ChargeEngine.HOUR_MS
        val idle = ChargeEngine.evaluate(ledger(charge = 4, last = start, lifetime = 4), later, activeTethers = 0)
        assertEquals(0, idle.earned)
        assertEquals(4, idle.state.currentCharge)
        assertEquals(later, idle.state.lastEvaluatedEpochMs)

        val next = ChargeEngine.evaluate(idle.state, later + ChargeEngine.HOUR_MS, activeTethers = 1)
        assertEquals(10, next.earned)
    }

    @Test fun `a clock that moves backwards does not pay or rewind the ledger`() {
        val state = ledger(charge = 3, last = 9_000, lifetime = 3)
        val backward = ChargeEngine.evaluate(state, nowEpochMs = 1_000, activeTethers = 4)
        assertEquals(0, backward.earned)
        assertEquals(state, backward.state)
        assertEquals(34, backward.hourlyRate)

        val idle = ChargeEngine.evaluate(state, nowEpochMs = 1_000, activeTethers = 0)
        assertEquals(state, idle.state)
    }

    @Test fun `five tethers over a simulated jump use the marginal schedule`() {
        val start = 50_000L
        val result = ChargeEngine.evaluate(ledger(last = start), start + 2 * ChargeEngine.HOUR_MS, activeTethers = 5)
        assertEquals(36, result.hourlyRate)
        assertEquals(72, result.earned)
        assertEquals(start + 2 * ChargeEngine.HOUR_MS, result.state.lastEvaluatedEpochMs)
    }
}
