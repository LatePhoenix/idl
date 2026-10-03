package app.idl.data.remote.supabase

import app.idl.IdlClock
import app.idl.IdlLog
import app.idl.data.remote.IdlError
import app.idl.data.remote.IdlException
import app.idl.domain.IdlJson
import app.idl.domain.InstantSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant

data class SupabaseConfig(val url: String, val anonKey: String) {
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
    val base: String get() = url.trimEnd('/')
}

@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant,
    val userId: String,
)

/** Persists the session; the app uses DataStore, tests use memory. */
interface SessionStore {
    suspend fun load(): AuthSession?
    suspend fun save(session: AuthSession?)
}

class InMemorySessionStore(private var session: AuthSession? = null) : SessionStore {
    override suspend fun load() = session
    override suspend fun save(session: AuthSession?) { this.session = session }
}

/** What the REST client needs from auth. */
interface TokenProvider {
    /** A currently valid access token, refreshed if close to expiry; null when signed out. */
    suspend fun accessToken(): String?
    /** Called after a 401: force a refresh. Returns false if the user must sign in again. */
    suspend fun refreshAfterUnauthorized(): Boolean
}

/** Sign-in surface for the UI; the fake backend's implementation is always signed in. */
interface AuthGateway {
    val isRemote: Boolean
    suspend fun isSignedIn(): Boolean
    suspend fun requestEmailCode(email: String): Result<Unit>
    suspend fun verifyEmailCode(email: String, code: String): Result<Unit>
    suspend fun signOut()
}

object LocalAuthGateway : AuthGateway {
    override val isRemote = false
    override suspend fun isSignedIn() = true
    override suspend fun requestEmailCode(email: String) = Result.success(Unit)
    override suspend fun verifyEmailCode(email: String, code: String) = Result.success(Unit)
    override suspend fun signOut() = Unit
}

/**
 * Supabase Auth (GoTrue) over plain HTTP: passwordless email one-time codes, token refresh,
 * sign-out. The project's "Magic Link" email template must include `{{ .Token }}` so users
 * receive a code rather than a link (see supabase/README.md).
 */
class SupabaseAuth(
    private val http: OkHttpClient,
    private val config: SupabaseConfig,
    private val store: SessionStore,
    private val clock: IdlClock,
) : TokenProvider, AuthGateway {

    @Serializable
    private data class GoTrueSession(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        val user: User,
    ) {
        @Serializable data class User(val id: String)
    }

    private val mutex = Mutex()
    override val isRemote = true

    override suspend fun isSignedIn(): Boolean = store.load() != null

    override suspend fun requestEmailCode(email: String): Result<Unit> = runAuth {
        post("otp", buildJsonObject { put("email", email.trim()); put("create_user", true) }, bearer = null).close()
        IdlLog.i("auth.code_requested")
    }

    override suspend fun verifyEmailCode(email: String, code: String): Result<Unit> = runAuth {
        val body = buildJsonObject { put("type", "email"); put("email", email.trim()); put("token", code.trim()) }
        save(post("verify", body, bearer = null))
        IdlLog.i("auth.signed_in")
    }

    override suspend fun signOut() {
        val session = store.load()
        store.save(null)
        if (session != null) {
            runCatching { post("logout", JsonObject(emptyMap()), bearer = session.accessToken).close() }
        }
        IdlLog.i("auth.signed_out")
    }

    override suspend fun accessToken(): String? = mutex.withLock {
        val session = store.load() ?: return null
        if (session.expiresAt.isAfter(clock.now().plusSeconds(60))) return session.accessToken
        refreshLocked(session)?.accessToken
    }

    override suspend fun refreshAfterUnauthorized(): Boolean = mutex.withLock {
        val session = store.load() ?: return false
        refreshLocked(session) != null
    }

    private suspend fun refreshLocked(session: AuthSession): AuthSession? = try {
        save(post("token?grant_type=refresh_token", buildJsonObject { put("refresh_token", session.refreshToken) }, bearer = null))
    } catch (e: IdlException) {
        if (e.error == IdlError.Offline || e.error.retryable) {
            // Keep the session; the caller will surface Offline and retry later.
            throw e
        }
        IdlLog.w("auth.refresh_rejected", "error" to e.error)
        store.save(null)
        null
    }

    private suspend fun save(response: Response): AuthSession {
        val parsed = response.use { IdlJson.decodeFromString(GoTrueSession.serializer(), it.body!!.string()) }
        val session = AuthSession(
            accessToken = parsed.accessToken,
            refreshToken = parsed.refreshToken,
            expiresAt = clock.now().plusSeconds(parsed.expiresIn),
            userId = parsed.user.id,
        )
        store.save(session)
        return session
    }

    /** Executes a GoTrue call; non-2xx responses become [IdlException]. Caller closes the response. */
    private suspend fun post(path: String, body: JsonObject, bearer: String?): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${config.base}/auth/v1/$path")
            .header("apikey", config.anonKey)
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .post(body.toString().toRequestBody(JSON))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw IdlException(IdlError.Offline)
        }
        if (!response.isSuccessful) {
            val text = response.use { it.body?.string().orEmpty() }
            throw IdlException(authError(response.code, text))
        }
        response
    }

    private suspend fun runAuth(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (e: IdlException) {
        Result.failure(e)
    }

    companion object {
        private val JSON = "application/json".toMediaType()

        internal fun authError(status: Int, body: String): IdlError = when {
            status == 429 -> IdlError.RateLimited
            status >= 500 -> IdlError.Server(status)
            // GoTrue answers 400/401/403 for wrong or expired codes and bad refresh tokens.
            body.contains("expired", ignoreCase = true) || body.contains("invalid", ignoreCase = true) ->
                IdlError.Invalid("code", "That code is wrong or has expired")
            status == 401 || status == 403 -> IdlError.Unauthorized
            else -> IdlError.Invalid("email", "Couldn't send a code to that address")
        }
    }
}
