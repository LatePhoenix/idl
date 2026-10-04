package app.idl.data.repo

import app.idl.BuildConfig
import app.idl.IdlClock
import app.idl.IdlLog
import app.idl.data.local.FriendTetherEntity
import app.idl.data.local.IdlDao
import app.idl.data.local.UserEconomyStateEntity
import app.idl.domain.ChargeEngine
import app.idl.domain.ChargeState
import app.idl.domain.FriendTether
import app.idl.domain.UserEconomyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Commits [ChargeEngine] results and tracks which friends are mutually widget-pinned.
 * Evaluation is on demand: app sync, widget refresh, and the Status Deck. No timer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EconomyRepository(
    private val dao: IdlDao,
    private val clock: IdlClock,
    private val scope: CoroutineScope,
    private val widgets: WidgetRefresher,
) {
    private val gate = Mutex()

    val state: StateFlow<ChargeState> = dao.session().flatMapLatest { session ->
        if (session == null) flowOf(ChargeState.EMPTY)
        else combine(dao.economy(session.userId), dao.tethers()) { economy, tethers ->
            val domain = tethers.map { it.toDomain() }
            val active = ChargeEngine.activeCount(domain)
            ChargeState(
                currentCharge = economy?.currentCharge ?: 0,
                hourlyRate = ChargeEngine.hourlyRate(active),
                activeTethers = active,
                lastEvaluatedEpochMs = economy?.lastEvaluatedEpochMs ?: 0,
                lifetimeChargeEarned = economy?.lifetimeChargeEarned ?: 0,
            )
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), ChargeState.EMPTY)

    suspend fun evaluate(): ChargeState {
        val session = dao.sessionNow() ?: return ChargeState.EMPTY
        return gate.withLock { evaluateLocked(session.userId) }
    }

    /** Recompute local pins from widget subscriptions, then let the new rate apply forward. */
    suspend fun syncLocalWidgets() {
        val session = dao.sessionNow() ?: return
        val changed = gate.withLock {
            evaluateLocked(session.userId)
            val installed = dao.widgetSubscriptionsNow().mapNotNull { it.friendUserId }.toSet()
            val existing = dao.tethersNow().associateBy { it.friendUserId }
            val now = clock.now().toEpochMilli()
            val ids = existing.keys + installed
            ids.forEach { id ->
                val prev = existing[id]
                dao.upsertTether(
                    tetherRow(
                        friendUserId = id,
                        local = id in installed,
                        remote = prev?.hasRemoteWidgetInstalled == true,
                        previousActivated = prev?.tetherActivatedEpochMs,
                        now = now,
                    ),
                )
            }
            ids.toList()
        }
        if (changed.isNotEmpty()) widgets.friendsChanged(changed)
    }

    suspend fun setRemoteWidgetInstalled(friendUserId: String, installed: Boolean) {
        val session = dao.sessionNow() ?: return
        gate.withLock {
            evaluateLocked(session.userId)
            val prev = dao.tetherNow(friendUserId)
            dao.upsertTether(
                tetherRow(
                    friendUserId = friendUserId,
                    local = prev?.hasLocalWidgetInstalled == true,
                    remote = installed,
                    previousActivated = prev?.tetherActivatedEpochMs,
                    now = clock.now().toEpochMilli(),
                ),
            )
        }
        widgets.friendsChanged(listOf(friendUserId))
    }

    /** Demo: mark this device as pinning [friendUserId] without a real app widget. */
    suspend fun setLocalWidgetInstalled(friendUserId: String, installed: Boolean): Boolean {
        val session = dao.sessionNow() ?: return false
        val stored = gate.withLock {
            evaluateLocked(session.userId)
            val fromWidget = dao.widgetSubscriptionsFor(friendUserId).isNotEmpty()
            val prev = dao.tetherNow(friendUserId)
            val local = installed || fromWidget
            dao.upsertTether(
                tetherRow(
                    friendUserId = friendUserId,
                    local = local,
                    remote = prev?.hasRemoteWidgetInstalled == true,
                    previousActivated = prev?.tetherActivatedEpochMs,
                    now = clock.now().toEpochMilli(),
                ),
            )
            local
        }
        widgets.friendsChanged(listOf(friendUserId))
        return stored
    }

    suspend fun tether(friendUserId: String): FriendTether? = dao.tetherNow(friendUserId)?.toDomain()

    /**
     * Debug only. Moves the ledger back by [hours] and evaluates, so a demo can
     * see a balance change without waiting. The 24-hour ceiling still applies.
     */
    suspend fun debugAccrueHours(hours: Long) {
        if (!BuildConfig.DEBUG) return
        val session = dao.sessionNow() ?: return
        gate.withLock {
            val now = clock.now().toEpochMilli()
            val row = dao.economyNow(session.userId)
                ?: UserEconomyStateEntity(session.userId, 0, now, 0)
            dao.upsertEconomy(row.copy(lastEvaluatedEpochMs = now - hours * ChargeEngine.HOUR_MS))
            evaluateLocked(session.userId)
        }
    }

    private suspend fun evaluateLocked(userId: String): ChargeState {
        val now = clock.now().toEpochMilli()
        val row = dao.economyNow(userId) ?: UserEconomyStateEntity(userId, 0, now, 0)
        val active = ChargeEngine.activeCount(dao.tethersNow().map { it.toDomain() })
        val result = ChargeEngine.evaluate(row.toDomain(), now, active)
        val next = UserEconomyStateEntity(
            userId = result.state.userId,
            currentCharge = result.state.currentCharge,
            lastEvaluatedEpochMs = result.state.lastEvaluatedEpochMs,
            lifetimeChargeEarned = result.state.lifetimeChargeEarned,
        )
        dao.upsertEconomy(next)
        if (result.earned > 0) {
            IdlLog.i("charge.accrued", "earned" to result.earned, "rate" to result.hourlyRate, "tethers" to active)
        }
        return ChargeState(
            currentCharge = next.currentCharge,
            hourlyRate = result.hourlyRate,
            activeTethers = active,
            lastEvaluatedEpochMs = next.lastEvaluatedEpochMs,
            lifetimeChargeEarned = next.lifetimeChargeEarned,
        )
    }

    private fun tetherRow(
        friendUserId: String,
        local: Boolean,
        remote: Boolean,
        previousActivated: Long?,
        now: Long,
    ): FriendTetherEntity {
        val mutual = local && remote
        return FriendTetherEntity(
            friendUserId = friendUserId,
            hasLocalWidgetInstalled = local,
            hasRemoteWidgetInstalled = remote,
            tetherActivatedEpochMs = if (mutual) previousActivated ?: now else null,
        )
    }
}

private fun UserEconomyStateEntity.toDomain() = UserEconomyState(
    userId = userId,
    currentCharge = currentCharge,
    lastEvaluatedEpochMs = lastEvaluatedEpochMs,
    lifetimeChargeEarned = lifetimeChargeEarned,
)

private fun FriendTetherEntity.toDomain() = FriendTether(
    friendUserId = friendUserId,
    hasLocalWidgetInstalled = hasLocalWidgetInstalled,
    hasRemoteWidgetInstalled = hasRemoteWidgetInstalled,
    tetherActivatedEpochMs = tetherActivatedEpochMs,
)
