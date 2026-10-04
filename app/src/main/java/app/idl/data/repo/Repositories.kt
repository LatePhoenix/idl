package app.idl.data.repo

import app.idl.IdlClock
import app.idl.IdlLog
import app.idl.data.local.AvatarEntity
import app.idl.data.local.FriendEntity
import app.idl.data.local.FriendPresenceEntity
import app.idl.data.local.IdlDao
import app.idl.data.local.IdlDatabase
import app.idl.data.remote.supabase.AuthGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.idl.data.local.OwnPresenceEntity
import app.idl.data.local.PrivacyRulesEntity
import app.idl.data.local.ReactionEntity
import app.idl.data.local.SessionEntity
import app.idl.data.local.SyncStateEntity
import app.idl.data.remote.IdlBackend
import app.idl.data.remote.IdlError
import app.idl.data.remote.IdlException
import app.idl.domain.AudienceRule
import app.idl.domain.AvatarConfig
import app.idl.domain.Friend
import app.idl.domain.FriendStatus
import app.idl.domain.IdlJson
import app.idl.domain.Invite
import app.idl.domain.Me
import app.idl.domain.PresenceSource
import app.idl.domain.PresenceState
import app.idl.domain.PresenceView
import app.idl.domain.PrivacyRules
import app.idl.domain.Reaction
import app.idl.domain.ReactionTemplate
import app.idl.domain.wire
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

/** Side effects the data layer triggers but does not own (widgets, WorkManager). */
interface WidgetRefresher {
    suspend fun selfChanged()
    suspend fun friendsChanged(userIds: Collection<String>)
    suspend fun all()
}

interface SyncScheduler {
    /** Retry pending writes / reconcile when network returns. */
    fun scheduleRetry()
    /** Wake up at [at] to re-render widgets because a status expires. */
    fun scheduleExpiry(at: Instant?)
}

/** Runs a backend call, mapping transport failures to [IdlError]. */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: IdlException) {
    Result.failure(e)
} catch (e: IOException) {
    Result.failure(IdlException(IdlError.Offline))
}

val Throwable.idlError: IdlError get() = (this as? IdlException)?.error ?: IdlError.Server(0)

// --- Mapping ----------------------------------------------------------------------------------

internal fun SessionEntity.toMe() = Me(userId, username, displayName, invisible)
internal fun FriendEntity.toFriend() = Friend(userId, username, displayName, FriendStatus.Serializer.fromWire(status), isCloseFriend)
internal fun Friend.toEntity() = FriendEntity(userId, username, displayName, status.wire, isCloseFriend)
internal fun Reaction.toEntity() = ReactionEntity(id, senderId, recipientId, template.wire, createdAt.toEpochMilli(), expiresAt.toEpochMilli(), dismissedAt?.toEpochMilli())
internal fun ReactionEntity.toReaction() = Reaction(
    id, senderId, recipientId, ReactionTemplate.Serializer.fromWire(template),
    Instant.ofEpochMilli(createdAtMs), Instant.ofEpochMilli(expiresAtMs), dismissedAtMs?.let(Instant::ofEpochMilli),
)
internal fun decodeAvatar(json: String) = IdlJson.decodeFromString(AvatarConfig.serializer(), json)
internal fun decodeState(json: String) = IdlJson.decodeFromString(PresenceState.serializer(), json)
internal fun decodeView(json: String) = IdlJson.decodeFromString(PresenceView.serializer(), json)

/** A friend as shown in the UI: relationship + last-known, server-filtered presence. */
data class FriendCard(
    val friend: Friend,
    val view: PresenceView?,
    val fetchedAt: Instant?,
)

// --- Repositories ---------------------------------------------------------------------------

class SessionRepository(
    private val db: IdlDatabase,
    private val dao: IdlDao,
    private val backend: IdlBackend,
    val auth: AuthGateway,
    private val widgets: WidgetRefresher,
) {
    val me: Flow<Me?> = dao.session().map { it?.toMe() }

    /**
     * After auth succeeds: load the server profile if one exists. Returns null when this account
     * still needs a username (first sign-in), in which case the UI calls [register].
     */
    suspend fun resumeProfile(): Result<Me?> = attempt {
        try {
            val me = backend.me()
            dao.upsertSession(SessionEntity(userId = me.userId, username = me.username, displayName = me.displayName, invisible = me.invisible))
            runCatching { backend.getAvatar() }.getOrNull()?.let {
                dao.upsertAvatar(AvatarEntity(me.userId, IdlJson.encodeToString(AvatarConfig.serializer(), it)))
            }
            me
        } catch (e: IdlException) {
            if (e.error == IdlError.NotFound) null else throw e
        }
    }

    /** Signs out and wipes every cached friend, presence and reaction from the device. */
    suspend fun signOut() {
        auth.signOut()
        withContext(Dispatchers.IO) { db.clearAllTables() }
        widgets.all()
        IdlLog.i("account.signed_out")
    }

    suspend fun register(displayName: String, username: String): Result<Me> = attempt {
        val me = backend.register(displayName, username)
        dao.upsertSession(SessionEntity(userId = me.userId, username = me.username, displayName = me.displayName, invisible = me.invisible))
        IdlLog.i("account.created")
        me
    }

    /** Invisible is applied server-side first; if that fails we do not pretend it worked. */
    suspend fun setInvisible(invisible: Boolean): Result<Unit> = attempt {
        backend.setInvisible(invisible)
        dao.sessionNow()?.let { dao.upsertSession(it.copy(invisible = invisible)) }
        IdlLog.i("presence.invisible", "on" to invisible)
        widgets.selfChanged()
    }
}

class AvatarRepository(
    private val dao: IdlDao,
    private val backend: IdlBackend,
    private val widgets: WidgetRefresher,
) {
    fun avatar(userId: String): Flow<AvatarConfig?> = dao.avatar(userId).map { it?.json?.let(::decodeAvatar) }

    suspend fun save(userId: String, config: AvatarConfig): Result<AvatarConfig> {
        // Avatar is cached first so the builder works offline; the server copy follows.
        dao.upsertAvatar(AvatarEntity(userId, IdlJson.encodeToString(AvatarConfig.serializer(), config)))
        widgets.selfChanged()
        return attempt { backend.putAvatar(config) }
    }
}

class PresenceRepository(
    private val dao: IdlDao,
    private val backend: IdlBackend,
    private val clock: IdlClock,
    private val widgets: WidgetRefresher,
    private val scheduler: SyncScheduler,
) {
    val ownStates: Flow<List<PresenceState>> =
        dao.ownPresence().map { rows -> rows.map { decodeState(it.json) } }

    val pendingSync: Flow<Boolean> = dao.ownPresence().map { rows -> rows.any { it.pendingSync } }

    val friendViews: Flow<Map<String, Pair<PresenceView, Instant>>> = dao.friendPresence().map { rows ->
        rows.associate { it.userId to (decodeView(it.json) to Instant.ofEpochMilli(it.fetchedAtMs)) }
    }

    /**
     * Saves locally first (so the widget and app update instantly and offline), then publishes.
     * If publishing fails with a retryable error the row stays `pendingSync` for the worker.
     */
    suspend fun setStatus(state: PresenceState): Result<Unit> {
        val json = IdlJson.encodeToString(PresenceState.serializer(), state)
        dao.upsertOwnPresence(OwnPresenceEntity(state.source.wire, json, pendingSync = true))
        widgets.selfChanged()
        scheduler.scheduleExpiry(state.expiresAt)
        IdlLog.i("presence.set", "source" to state.source.wire, "hasNote" to (state.note != null))
        return attempt { backend.putPresence(state) }
            .onSuccess { dao.upsertOwnPresence(OwnPresenceEntity(state.source.wire, json, pendingSync = false)) }
            .onFailure { if (it.idlError.retryable) scheduler.scheduleRetry() }
            .map { }
    }

    suspend fun clearStatus(): Result<Unit> {
        dao.deleteOwnPresence(PresenceSource.MANUAL.wire)
        widgets.selfChanged()
        return attempt { backend.clearPresence() }.onFailure { if (it.idlError.retryable) scheduler.scheduleRetry() }
    }

    /** Push pending own writes. Returns true when nothing is left pending. */
    suspend fun flushPending(): Boolean {
        val pending = dao.ownPresenceNow().filter { it.pendingSync }
        var ok = true
        for (row in pending) {
            val state = decodeState(row.json)
            if (!state.isLive(clock.now())) {
                dao.upsertOwnPresence(row.copy(pendingSync = false))
                continue
            }
            attempt { backend.putPresence(state) }
                .onSuccess { dao.upsertOwnPresence(row.copy(pendingSync = false)) }
                .onFailure { ok = false }
        }
        return ok
    }

    suspend fun refreshFriend(userId: String): Result<Unit> = attempt {
        try {
            val view = backend.friendPresence(userId)
            dao.upsertFriendPresence(listOf(view.toEntity(clock.now())))
            scheduler.scheduleExpiry(view.expiresAt)
        } catch (e: IdlException) {
            if (e.error == IdlError.Forbidden || e.error == IdlError.NotFound) {
                // Access revoked: purge immediately (IDL_PRIVACY_MODEL §5).
                dao.purgeUser(userId)
                IdlLog.i("friend.purged", "reason" to e.error)
            } else throw e
        }
        widgets.friendsChanged(listOf(userId))
    }

    suspend fun refreshAllFriends(): Result<Unit> = attempt {
        val views = backend.friendPresence()
        val now = clock.now()
        dao.upsertFriendPresence(views.map { it.toEntity(now) })
        scheduler.scheduleExpiry(views.mapNotNull { it.expiresAt }.filter { it.isAfter(now) }.minOrNull())
    }

    private fun PresenceView.toEntity(now: Instant) =
        FriendPresenceEntity(userId, IdlJson.encodeToString(PresenceView.serializer(), this), now.toEpochMilli())
}

class FriendsRepository(
    private val dao: IdlDao,
    private val backend: IdlBackend,
    private val presence: PresenceRepository,
    private val widgets: WidgetRefresher,
) {
    val friends: Flow<List<Friend>> = dao.friends().map { rows -> rows.map { it.toFriend() } }

    val cards: Flow<List<FriendCard>> = combine(friends, presence.friendViews) { fs, views ->
        fs.map { f -> FriendCard(f, views[f.userId]?.first, views[f.userId]?.second) }
    }

    fun card(userId: String): Flow<FriendCard?> = cards.map { list -> list.firstOrNull { it.friend.userId == userId } }

    suspend fun refresh(): Result<Unit> = attempt {
        val list = backend.friends()
        val previous = dao.friendsNow().map { it.userId }.toSet()
        dao.replaceFriends(list.map { it.toEntity() })
        // Anyone no longer returned has removed or blocked us: purge their cached data.
        val gone = previous - list.map { it.userId }.toSet()
        gone.forEach { dao.purgeUser(it) }
        if (gone.isNotEmpty()) widgets.friendsChanged(gone)
    }

    suspend fun createInvite(): Result<Invite> = attempt { backend.createInvite() }

    suspend fun redeem(code: String): Result<Friend> = attempt {
        val f = backend.redeemInvite(code)
        dao.upsertFriend(f.toEntity())
        IdlLog.i("friend.invite_redeemed", "status" to f.status.wire)
        if (f.status == FriendStatus.ACCEPTED) presence.refreshFriend(f.userId)
        f
    }

    suspend fun accept(userId: String): Result<Unit> = attempt {
        val f = backend.acceptRequest(userId)
        dao.upsertFriend(f.toEntity())
        presence.refreshFriend(userId)
        IdlLog.i("friend.request_accepted")
    }

    suspend fun decline(userId: String): Result<Unit> = attempt {
        backend.declineRequest(userId)
        dao.purgeUser(userId)
    }

    suspend fun remove(userId: String): Result<Unit> = attempt {
        backend.removeFriend(userId)
        dao.purgeUser(userId)
        widgets.friendsChanged(listOf(userId))
        IdlLog.i("friend.removed")
    }

    suspend fun block(userId: String): Result<Unit> = attempt {
        backend.block(userId)
        dao.purgeUser(userId)
        widgets.friendsChanged(listOf(userId))
        IdlLog.i("friend.blocked")
    }

    suspend fun unblock(userId: String): Result<Unit> = attempt { backend.unblock(userId) }

    suspend fun blocked(): Result<List<Friend>> = attempt { backend.blocked() }

    suspend fun setCloseFriend(userId: String, close: Boolean): Result<Unit> = attempt {
        backend.setCloseFriend(userId, close)
        dao.friendNow(userId)?.let { dao.upsertFriend(it.copy(isCloseFriend = close)) }
    }
}

class ReactionRepository(
    private val dao: IdlDao,
    private val backend: IdlBackend,
    private val clock: IdlClock,
) {
    val all: Flow<List<Reaction>> = dao.reactions().map { rows -> rows.map { it.toReaction() } }

    suspend fun send(recipientId: String, template: ReactionTemplate): Result<Reaction> = attempt {
        val r = backend.sendReaction(recipientId, template)
        dao.upsertReactions(listOf(r.toEntity()))
        IdlLog.i("reaction.sent", "template" to template.wire)
        r
    }

    suspend fun dismiss(id: String): Result<Unit> {
        dao.dismissReaction(id, clock.now().toEpochMilli())
        return attempt { backend.dismissReaction(id) }
    }

    suspend fun template(id: String): ReactionTemplate? = dao.reactionNow(id)?.toReaction()?.template

    suspend fun refreshInbox(): Result<Unit> = attempt {
        dao.purgeExpiredReactions(clock.now().toEpochMilli())
        dao.upsertReactions(backend.inbox().map { it.toEntity() })
    }
}

class PrivacyRepository(
    private val dao: IdlDao,
    private val backend: IdlBackend,
) {
    val rules: Flow<PrivacyRules> = dao.privacyRules().map { e ->
        e?.json?.let { IdlJson.decodeFromString(PrivacyRules.serializer(), it) } ?: PrivacyRules.DEFAULT
    }

    /** Server first: a privacy change that didn't reach the server must not look applied. */
    suspend fun setRule(current: PrivacyRules, rule: AudienceRule): Result<PrivacyRules> = attempt {
        val saved = backend.putPrivacyRules(current.with(rule))
        dao.upsertPrivacyRules(PrivacyRulesEntity(json = IdlJson.encodeToString(PrivacyRules.serializer(), saved)))
        IdlLog.i("privacy.rule_changed", "category" to rule.category.wire, "audience" to rule.audience.wire)
        saved
    }

    suspend fun refresh(): Result<Unit> = attempt {
        val r = backend.privacyRules()
        dao.upsertPrivacyRules(PrivacyRulesEntity(json = IdlJson.encodeToString(PrivacyRules.serializer(), r)))
    }
}

/** Full reconciliation, used on app start, by ReconcileWorker, and after push gaps. */
class SyncManager(
    private val dao: IdlDao,
    private val clock: IdlClock,
    private val presence: PresenceRepository,
    private val friends: FriendsRepository,
    private val reactions: ReactionRepository,
    private val privacy: PrivacyRepository,
    private val widgets: WidgetRefresher,
    private val economy: EconomyRepository,
    /** The server no longer accepts our session (revoked, or the refresh token expired). */
    private val onUnauthorized: suspend () -> Unit = {},
) {
    val lastSync: Flow<SyncStateEntity?> = dao.syncState()

    suspend fun reconcile(): Result<Unit> {
        if (dao.sessionNow() == null) return Result.success(Unit)
        val now = clock.now().toEpochMilli()
        val result = runCatching {
            presence.flushPending()
            friends.refresh().getOrThrow()
            presence.refreshAllFriends().getOrThrow()
            reactions.refreshInbox().getOrThrow()
            privacy.refresh().getOrThrow()
        }
        val prev = dao.syncStateNow()
        dao.upsertSyncState(
            SyncStateEntity(
                lastSuccessMs = if (result.isSuccess) now else prev?.lastSuccessMs,
                lastAttemptMs = now,
                lastError = result.exceptionOrNull()?.idlError?.toString(),
            ),
        )
        widgets.all()
        economy.evaluate()
        result.onSuccess { IdlLog.i("reconcile.ok") }
            .onFailure { IdlLog.w("reconcile.failed", "error" to it.idlError) }
        if (result.exceptionOrNull()?.idlError == IdlError.Unauthorized) onUnauthorized()
        return result
    }
}
