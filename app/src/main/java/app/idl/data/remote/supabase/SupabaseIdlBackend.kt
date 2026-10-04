package app.idl.data.remote.supabase

import app.idl.data.remote.IdlBackend
import app.idl.data.remote.IdlError
import app.idl.data.remote.IdlException
import app.idl.domain.AvatarConfig
import app.idl.domain.Friend
import app.idl.domain.IdlJson
import app.idl.domain.Invite
import app.idl.domain.Me
import app.idl.domain.PresenceState
import app.idl.domain.PresenceView
import app.idl.domain.PrivacyRules
import app.idl.domain.Reaction
import app.idl.domain.ReactionTemplate
import app.idl.domain.wire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * [IdlBackend] over Supabase PostgREST. Every operation is one RPC defined in
 * supabase/migrations; the server derives the caller from the JWT and enforces all access
 * and privacy rules, so this class only marshals JSON and maps errors.
 */
class SupabaseIdlBackend(
    private val http: OkHttpClient,
    private val config: SupabaseConfig,
    private val tokens: TokenProvider,
) : IdlBackend {

    @Serializable
    private data class PostgrestError(
        val code: String? = null,
        val message: String? = null,
        val details: String? = null,
    )

    @Serializable
    private data class DeviceId(val id: String)

    // --- Transport ---------------------------------------------------------------------------

    private suspend fun rpcRaw(name: String, args: JsonObject): String {
        val first = execute(name, args, tokens.accessToken())
        if (first.first != 401) return unwrap(first)
        // Expired or revoked access token: refresh once, then retry.
        if (!tokens.refreshAfterUnauthorized()) throw IdlException(IdlError.Unauthorized)
        return unwrap(execute(name, args, tokens.accessToken()))
    }

    private suspend fun execute(name: String, args: JsonObject, token: String?): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            if (token == null) throw IdlException(IdlError.Unauthorized)
            val request = Request.Builder()
                .url("${config.base}/rest/v1/rpc/$name")
                .header("apikey", config.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .post(args.toString().toRequestBody(JSON))
                .build()
            try {
                http.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
            } catch (e: IOException) {
                throw IdlException(IdlError.Offline)
            }
        }

    private fun unwrap(response: Pair<Int, String>): String {
        val (status, body) = response
        if (status in 200..299) return body
        throw IdlException(errorFor(status, body))
    }

    private suspend fun <T> rpc(name: String, serializer: KSerializer<T>, args: JsonObject = EMPTY): T =
        IdlJson.decodeFromString(serializer, rpcRaw(name, args))

    private suspend fun rpcUnit(name: String, args: JsonObject = EMPTY) {
        rpcRaw(name, args)
    }

    private fun <T> json(value: T, serializer: KSerializer<T>): JsonElement =
        IdlJson.encodeToJsonElement(serializer, value)

    // --- Account -----------------------------------------------------------------------------

    override suspend fun register(displayName: String, username: String): Me =
        rpc("register_profile", Me.serializer(), buildJsonObject {
            put("p_username", username)
            put("p_display_name", displayName)
        })

    override suspend fun me(): Me = rpc("me", Me.serializer())

    override suspend fun getAvatar(): AvatarConfig = rpc("get_avatar", AvatarConfig.serializer())

    override suspend fun putAvatar(config: AvatarConfig): AvatarConfig =
        rpc("put_avatar", AvatarConfig.serializer(), buildJsonObject { put("p_config", json(config, AvatarConfig.serializer())) })

    // --- Presence ----------------------------------------------------------------------------

    override suspend fun putPresence(state: PresenceState): PresenceState =
        rpc("put_presence", PresenceState.serializer(), buildJsonObject { put("p_envelope", json(state, PresenceState.serializer())) })

    override suspend fun clearPresence() = rpcUnit("clear_presence")

    override suspend fun setInvisible(invisible: Boolean) =
        rpcUnit("set_invisible", buildJsonObject { put("p_invisible", invisible) })

    override suspend fun friendPresence(): List<PresenceView> =
        rpc("friend_presence", ListSerializer(PresenceView.serializer()))

    override suspend fun friendPresence(userId: String): PresenceView =
        rpc("friend_presence_one", PresenceView.serializer(), buildJsonObject { put("p_user_id", userId) })

    // --- Friends -----------------------------------------------------------------------------

    override suspend fun friends(): List<Friend> = rpc("friends", ListSerializer(Friend.serializer()))

    override suspend fun createInvite(): Invite = rpc("create_invite", Invite.serializer())

    override suspend fun redeemInvite(code: String): Friend =
        rpc("redeem_invite", Friend.serializer(), buildJsonObject { put("p_code", code) })

    override suspend fun acceptRequest(userId: String): Friend =
        rpc("accept_request", Friend.serializer(), userArg(userId))

    override suspend fun declineRequest(userId: String) = rpcUnit("decline_request", userArg(userId))

    override suspend fun removeFriend(userId: String) = rpcUnit("remove_friend", userArg(userId))

    override suspend fun setCloseFriend(userId: String, close: Boolean) =
        rpcUnit("set_close_friend", buildJsonObject { put("p_user_id", userId); put("p_close", close) })

    override suspend fun block(userId: String) = rpcUnit("block_user", userArg(userId))

    override suspend fun unblock(userId: String) = rpcUnit("unblock_user", userArg(userId))

    override suspend fun blocked(): List<Friend> = rpc("blocked_users", ListSerializer(Friend.serializer()))

    // --- Reactions ---------------------------------------------------------------------------

    override suspend fun sendReaction(recipientId: String, template: ReactionTemplate): Reaction =
        rpc("send_reaction", Reaction.serializer(), buildJsonObject {
            put("p_recipient_id", recipientId)
            put("p_template", template.wire)
        })

    override suspend fun inbox(): List<Reaction> = rpc("reaction_inbox", ListSerializer(Reaction.serializer()))

    override suspend fun dismissReaction(id: String) = rpcUnit("dismiss_reaction", buildJsonObject { put("p_id", id) })

    // --- Privacy & devices -------------------------------------------------------------------

    override suspend fun privacyRules(): PrivacyRules = rpc("get_privacy_rules", PrivacyRules.serializer())

    override suspend fun putPrivacyRules(rules: PrivacyRules): PrivacyRules =
        rpc("put_privacy_rules", PrivacyRules.serializer(), buildJsonObject { put("p_rules", json(rules, PrivacyRules.serializer())) })

    override suspend fun registerDevice(pushToken: String): String =
        rpc("register_device", DeviceId.serializer(), buildJsonObject { put("p_token", pushToken); put("p_platform", "android") }).id

    private fun userArg(userId: String) = buildJsonObject { put("p_user_id", userId) }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val EMPTY = JsonObject(emptyMap())

        /** Maps PostgREST responses (incl. PTxxx custom statuses raised by the RPCs) to [IdlError]. */
        internal fun errorFor(status: Int, body: String): IdlError {
            val err = runCatching { IdlJson.decodeFromString(PostgrestError.serializer(), body) }.getOrNull()
            return when (status) {
                400 -> IdlError.Invalid(err?.details.orEmpty(), err?.message ?: "Invalid request")
                401 -> IdlError.Unauthorized
                403 -> IdlError.Forbidden
                404 -> IdlError.NotFound
                409 -> IdlError.Conflict
                429 -> IdlError.RateLimited
                else -> IdlError.Server(status)
            }
        }
    }
}
