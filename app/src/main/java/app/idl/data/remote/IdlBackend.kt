package app.idl.data.remote

import app.idl.domain.AvatarConfig
import app.idl.domain.Friend
import app.idl.domain.Invite
import app.idl.domain.Me
import app.idl.domain.PresenceState
import app.idl.domain.PresenceView
import app.idl.domain.PrivacyRules
import app.idl.domain.Reaction
import app.idl.domain.ReactionTemplate

/** Explicit API error model (IDL_API_CONTRACT §1). */
sealed class IdlError(val retryable: Boolean) {
    data object Offline : IdlError(true)
    data object Unauthorized : IdlError(false)
    data object Forbidden : IdlError(false)
    data object NotFound : IdlError(false)
    data object Conflict : IdlError(true)
    data object RateLimited : IdlError(true)
    data class Invalid(val field: String, val reason: String) : IdlError(false)
    data class Server(val code: Int) : IdlError(true)

    val userMessage: String
        get() = when (this) {
            Offline -> "You're offline. Showing last-known state."
            Unauthorized -> "Please sign in again."
            Forbidden, NotFound -> "That person isn't available."
            Conflict -> "Something changed — try again."
            RateLimited -> "Slow down a little and try again."
            is Invalid -> reason
            is Server -> "iDL is having trouble. We'll retry."
        }
}

class IdlException(val error: IdlError) : Exception(error.toString())

/**
 * The backend contract (mirrors IDL_API_CONTRACT §2). The server is the only place privacy is
 * enforced: friend reads return already-filtered [PresenceView]s.
 */
interface IdlBackend {
    suspend fun register(displayName: String, username: String): Me
    suspend fun me(): Me

    suspend fun getAvatar(): AvatarConfig
    suspend fun putAvatar(config: AvatarConfig): AvatarConfig

    suspend fun putPresence(state: PresenceState): PresenceState
    suspend fun clearPresence()
    suspend fun setInvisible(invisible: Boolean)

    suspend fun friends(): List<Friend>
    suspend fun createInvite(): Invite
    suspend fun redeemInvite(code: String): Friend
    suspend fun acceptRequest(userId: String): Friend
    suspend fun declineRequest(userId: String)
    suspend fun removeFriend(userId: String)
    suspend fun setCloseFriend(userId: String, close: Boolean)
    suspend fun block(userId: String)
    suspend fun unblock(userId: String)
    suspend fun blocked(): List<Friend>

    suspend fun friendPresence(): List<PresenceView>
    /** Throws [IdlError.Forbidden] / [IdlError.NotFound] when access is revoked. */
    suspend fun friendPresence(userId: String): PresenceView

    suspend fun sendReaction(recipientId: String, template: ReactionTemplate): Reaction
    suspend fun inbox(): List<Reaction>
    suspend fun dismissReaction(id: String)

    suspend fun privacyRules(): PrivacyRules
    suspend fun putPrivacyRules(rules: PrivacyRules): PrivacyRules

    suspend fun registerDevice(pushToken: String): String
}
