package app.idl.data.remote

import app.idl.IdlClock
import app.idl.IdlLog
import app.idl.data.push.MockPushSource
import app.idl.data.push.PushEvent
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.Friend
import app.idl.domain.FriendStatus
import app.idl.domain.IdlJson
import app.idl.domain.Invite
import app.idl.domain.Me
import app.idl.domain.PresenceResolver
import app.idl.domain.PresenceSource
import app.idl.domain.PresenceState
import app.idl.domain.PresenceView
import app.idl.domain.PrivacyFilter
import app.idl.domain.PrivacyRules
import app.idl.domain.QuickState
import app.idl.domain.Reaction
import app.idl.domain.ReactionRules
import app.idl.domain.ReactionTemplate
import app.idl.domain.Relationship
import app.idl.domain.Usernames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Duration

/** Everything the fake "server" knows. Persisted so demo friendships survive restarts. */
@Serializable
data class FakeWorld(
    val me: Me? = null,
    val myAvatar: AvatarConfiguration = AvatarConfiguration(
        baseAssetId = "base_teardrop",
        paletteAssetId = "palette_sunny",
    ),
    val myStates: List<PresenceState> = emptyList(),
    val myRules: PrivacyRules = PrivacyRules.DEFAULT,
    val users: Map<String, DemoUser> = emptyMap(),
    /** Relationship of each user to me; absent = not connected. */
    val relations: Map<String, FriendStatus> = emptyMap(),
    val myCloseFriends: Set<String> = emptySet(),
    val blockedByMe: Set<String> = emptySet(),
    val reactions: List<Reaction> = emptyList(),
    val seq: Long = 0,
)

interface FakeWorldStore {
    fun load(): FakeWorld?
    fun save(world: FakeWorld)
}

class FileFakeWorldStore(private val file: File) : FakeWorldStore {
    override fun load(): FakeWorld? = runCatching {
        if (file.exists()) IdlJson.decodeFromString(FakeWorld.serializer(), file.readText()) else null
    }.onFailure { IdlLog.w("fake.load_failed", t = it) }.getOrNull()

    override fun save(world: FakeWorld) {
        file.writeText(IdlJson.encodeToString(FakeWorld.serializer(), world))
    }
}

class InMemoryFakeWorldStore : FakeWorldStore {
    private var world: FakeWorld? = null
    override fun load() = world
    override fun save(world: FakeWorld) { this.world = world }
}

/**
 * In-process stand-in for the real backend. It enforces the same rules the server will
 * (friendship, blocks, privacy filtering, expiry), so UI and widget work is honest about
 * what a friend can see. Also hosts the debug "simulate" hooks used as mock push sources.
 */
class FakeIdlBackend(
    private val store: FakeWorldStore,
    private val clock: IdlClock,
    private val push: MockPushSource,
    private val scope: CoroutineScope,
    /** Delay before demo users auto-accept a redeemed invite. Null disables auto-accept. */
    private val autoAcceptDelay: Duration? = Duration.ofSeconds(2),
) : IdlBackend {

    private val mutex = Mutex()
    private var world: FakeWorld = store.load() ?: seed()

    /** Debug toggle: every call fails with [IdlError.Offline]. */
    @Volatile var simulateOffline = false

    private fun seed(): FakeWorld {
        val seeds = DemoData.seeds(clock.now())
        return FakeWorld(
            users = seeds.associate { it.user.id to it.user },
            relations = seeds.mapNotNull { s -> s.relation?.let { s.user.id to it } }.toMap(),
            myCloseFriends = setOf("u_ari", "u_juno"),
        )
    }

    private suspend fun <T> call(write: Boolean = false, block: (FakeWorld) -> Pair<FakeWorld, T>): T {
        if (simulateOffline) throw IdlException(IdlError.Offline)
        return mutex.withLock {
            val (next, result) = block(world)
            if (write) {
                world = next
                store.save(next)
            }
            result
        }
    }

    private suspend fun <T> read(block: (FakeWorld) -> T): T = call { it to block(it) }

    private fun FakeWorld.requireMe(): Me = me ?: throw IdlException(IdlError.Unauthorized)

    private fun FakeWorld.requireUser(id: String): DemoUser =
        users[id] ?: throw IdlException(IdlError.NotFound)

    private fun FakeWorld.toFriend(id: String, status: FriendStatus) = requireUser(id).let {
        Friend(it.id, it.username, it.displayName, status, isCloseFriend = id in myCloseFriends)
    }

    private fun FakeWorld.viewOf(id: String): PresenceView? {
        val me = requireMe()
        val u = users[id] ?: return null
        val rel = Relationship(
            isFriend = relations[id] == FriendStatus.ACCEPTED,
            isCloseFriend = u.closeFriendsMe,
            blocked = id in blockedByMe,
        )
        val now = clock.now()
        return PrivacyFilter.viewFor(
            viewerId = me.userId,
            ownerId = id,
            identity = u.recipe,
            presence = PresenceResolver.resolve(u.states, now),
            rules = u.rules,
            rel = rel,
            ownerInvisible = u.invisible,
        )
    }

    private fun FakeWorld.nextId(prefix: String): Pair<FakeWorld, String> =
        copy(seq = seq + 1) to "${prefix}_${seq + 1}"

    // --- Account -------------------------------------------------------------------------

    override suspend fun register(displayName: String, username: String): Me = call(write = true) { w ->
        val name = Usernames.normalize(username)
        if (!Usernames.isValid(name)) {
            throw IdlException(IdlError.Invalid("username", "3–20 letters, numbers or _"))
        }
        if (w.users.values.any { it.username == name }) {
            throw IdlException(IdlError.Invalid("username", "That username is taken"))
        }
        val me = Me(userId = "u_me_$name", username = name, displayName = displayName.trim().take(40))
        w.copy(me = me) to me
    }

    override suspend fun me(): Me = read { it.requireMe() }

    override suspend fun getAvatar(): AvatarConfiguration = read { it.myAvatar }

    override suspend fun putAvatar(config: AvatarConfiguration): AvatarConfiguration = call(write = true) { w ->
        w.requireMe()
        w.copy(myAvatar = config) to config
    }

    // --- Presence --------------------------------------------------------------------------

    override suspend fun putPresence(state: PresenceState): PresenceState = call(write = true) { w ->
        w.requireMe()
        if (!state.expiresAt.isAfter(state.startedAt)) {
            throw IdlException(IdlError.Invalid("expiresAt", "Status must expire in the future"))
        }
        if ((state.note?.length ?: 0) > 80) throw IdlException(IdlError.Invalid("note", "Keep notes under 80 characters"))
        val others = w.myStates.filter { it.source != state.source }
        w.copy(myStates = others + state) to state
    }

    override suspend fun clearPresence() = call(write = true) { w ->
        w.copy(myStates = w.myStates.filter { it.source != PresenceSource.MANUAL }) to Unit
    }

    override suspend fun setInvisible(invisible: Boolean) = call(write = true) { w ->
        val me = w.requireMe()
        w.copy(me = me.copy(invisible = invisible)) to Unit
    }

    // --- Friends ---------------------------------------------------------------------------

    override suspend fun friends(): List<Friend> = read { w ->
        w.requireMe()
        w.relations.filterKeys { it !in w.blockedByMe }.map { (id, status) -> w.toFriend(id, status) }
    }

    override suspend fun createInvite(): Invite = read { w ->
        val me = w.requireMe()
        val code = me.username.uppercase().take(6) + "-" + (me.userId.hashCode() and 0xFFFF).toString(16).uppercase()
        Invite(code = code, url = "idl://invite/$code", expiresAt = clock.now().plus(Duration.ofDays(7)))
    }

    override suspend fun redeemInvite(code: String): Friend {
        val friend = call(write = true) { w ->
            w.requireMe()
            val normalized = code.trim().uppercase().removePrefix("IDL://INVITE/")
            val user = w.users.values.firstOrNull { it.inviteCode == normalized }
                ?: throw IdlException(IdlError.Invalid("code", "That invite code isn't valid"))
            if (user.id in w.blockedByMe) throw IdlException(IdlError.Invalid("code", "That invite code isn't valid"))
            when (w.relations[user.id]) {
                FriendStatus.ACCEPTED -> w to w.toFriend(user.id, FriendStatus.ACCEPTED)
                FriendStatus.INCOMING -> {
                    val next = w.copy(relations = w.relations + (user.id to FriendStatus.ACCEPTED))
                    next to next.toFriend(user.id, FriendStatus.ACCEPTED)
                }
                else -> {
                    val next = w.copy(relations = w.relations + (user.id to FriendStatus.OUTGOING))
                    next to next.toFriend(user.id, FriendStatus.OUTGOING)
                }
            }
        }
        if (friend.status == FriendStatus.OUTGOING && autoAcceptDelay != null) {
            scope.launch {
                delay(autoAcceptDelay.toMillis())
                simulateAcceptOutgoing(friend.userId)
            }
        }
        return friend
    }

    override suspend fun acceptRequest(userId: String): Friend = call(write = true) { w ->
        if (w.relations[userId] != FriendStatus.INCOMING) throw IdlException(IdlError.NotFound)
        val next = w.copy(relations = w.relations + (userId to FriendStatus.ACCEPTED))
        next to next.toFriend(userId, FriendStatus.ACCEPTED)
    }

    override suspend fun declineRequest(userId: String) = call(write = true) { w ->
        w.copy(relations = w.relations - userId) to Unit
    }

    override suspend fun removeFriend(userId: String) = call(write = true) { w ->
        w.copy(relations = w.relations - userId, myCloseFriends = w.myCloseFriends - userId) to Unit
    }

    override suspend fun setCloseFriend(userId: String, close: Boolean) = call(write = true) { w ->
        if (w.relations[userId] != FriendStatus.ACCEPTED) throw IdlException(IdlError.Forbidden)
        w.copy(myCloseFriends = if (close) w.myCloseFriends + userId else w.myCloseFriends - userId) to Unit
    }

    override suspend fun block(userId: String) = call(write = true) { w ->
        w.requireUser(userId)
        w.copy(
            blockedByMe = w.blockedByMe + userId,
            relations = w.relations - userId,
            myCloseFriends = w.myCloseFriends - userId,
            reactions = w.reactions.filterNot { it.senderId == userId || it.recipientId == userId },
        ) to Unit
    }

    override suspend fun unblock(userId: String) = call(write = true) { w ->
        w.copy(blockedByMe = w.blockedByMe - userId) to Unit
    }

    override suspend fun blocked(): List<Friend> = read { w ->
        w.blockedByMe.mapNotNull { id ->
            w.users[id]?.let { Friend(it.id, it.username, it.displayName, FriendStatus.ACCEPTED) }
        }
    }

    // --- Friend presence ---------------------------------------------------------------------

    override suspend fun friendPresence(): List<PresenceView> = read { w ->
        w.relations.filterValues { it == FriendStatus.ACCEPTED }.keys.mapNotNull { w.viewOf(it) }
    }

    override suspend fun friendPresence(userId: String): PresenceView = read { w ->
        w.viewOf(userId) ?: throw IdlException(IdlError.Forbidden)
    }

    // --- Reactions -----------------------------------------------------------------------------

    override suspend fun sendReaction(recipientId: String, template: ReactionTemplate): Reaction = call(write = true) { w ->
        val me = w.requireMe()
        val now = clock.now()
        val rel = Relationship(isFriend = w.relations[recipientId] == FriendStatus.ACCEPTED, blocked = recipientId in w.blockedByMe)
        val recent = w.reactions.filter { it.senderId == me.userId && it.recipientId == recipientId }
        if (!rel.isFriend || rel.blocked) throw IdlException(IdlError.Forbidden)
        if (!ReactionRules.canSend(rel, recent, now)) throw IdlException(IdlError.RateLimited)
        val (w2, id) = w.nextId("r")
        val r = Reaction(id, me.userId, recipientId, template, now, now.plus(ReactionRules.TTL))
        w2.copy(reactions = w2.reactions + r) to r
    }

    override suspend fun inbox(): List<Reaction> = read { w ->
        ReactionRules.inbox(w.reactions, w.requireMe().userId, clock.now())
    }

    override suspend fun dismissReaction(id: String) = call(write = true) { w ->
        val me = w.requireMe()
        val now = clock.now()
        w.copy(reactions = w.reactions.map {
            if (it.id == id && it.recipientId == me.userId) ReactionRules.dismiss(it, now) else it
        }) to Unit
    }

    // --- Privacy & devices -----------------------------------------------------------------------

    override suspend fun privacyRules(): PrivacyRules = read { it.myRules }

    override suspend fun putPrivacyRules(rules: PrivacyRules): PrivacyRules = call(write = true) { w ->
        w.copy(myRules = rules) to rules
    }

    override suspend fun registerDevice(pushToken: String): String = "device_fake"

    // --- Debug simulation hooks (mock push sources) -----------------------------------------------

    fun demoFriendIds(): List<String> = world.relations.filterValues { it == FriendStatus.ACCEPTED }.keys.sorted()
    fun displayNameOf(id: String): String = world.users[id]?.displayName ?: id

    suspend fun simulatePresence(userId: String, quick: QuickState, note: String? = null) {
        mutate { w ->
            val u = w.requireUser(userId)
            w.copy(users = w.users + (userId to u.copy(states = listOf(quick.toState(clock.now(), note)))))
        }
        IdlLog.i("debug.simulate_presence", "quick" to quick.id)
        push.emit(PushEvent.PresenceChanged(userId))
    }

    suspend fun simulateExpiresSoon(userId: String, seconds: Long) {
        mutate { w ->
            val u = w.requireUser(userId)
            val until = clock.now().plusSeconds(seconds)
            w.copy(users = w.users + (userId to u.copy(states = u.states.map { it.copy(expiresAt = until) })))
        }
        push.emit(PushEvent.PresenceChanged(userId))
    }

    suspend fun simulateIncomingReaction(fromUserId: String, template: ReactionTemplate) {
        var created: Reaction? = null
        mutate { w ->
            val me = w.requireMe()
            val now = clock.now()
            val (w2, id) = w.nextId("r")
            val r = Reaction(id, fromUserId, me.userId, template, now, now.plus(ReactionRules.TTL))
            created = r
            w2.copy(reactions = w2.reactions + r)
        }
        created?.let { push.emit(PushEvent.ReactionReceived(it.id, fromUserId)) }
    }

    suspend fun simulateFriendRemovedMe(userId: String) {
        mutate { w -> w.copy(relations = w.relations - userId, myCloseFriends = w.myCloseFriends - userId) }
        push.emit(PushEvent.FriendRemoved(userId))
    }

    private suspend fun simulateAcceptOutgoing(userId: String) {
        var accepted = false
        mutate { w ->
            if (w.relations[userId] == FriendStatus.OUTGOING) {
                accepted = true
                w.copy(relations = w.relations + (userId to FriendStatus.ACCEPTED))
            } else w
        }
        if (accepted) push.emit(PushEvent.FriendAccepted(userId))
    }

    suspend fun resetDemo() {
        mutate { w -> seed().copy(me = w.me, myAvatar = w.myAvatar, myStates = w.myStates, myRules = w.myRules) }
        world.relations.keys.forEach { push.emit(PushEvent.PresenceChanged(it)) }
    }

    private suspend fun mutate(block: (FakeWorld) -> FakeWorld) = mutex.withLock {
        world = block(world)
        store.save(world)
    }
}
