package app.idl.data

import app.idl.data.remote.IdlError
import app.idl.data.remote.IdlException
import app.idl.data.remote.supabase.SupabaseConfig
import app.idl.data.remote.supabase.SupabaseIdlBackend
import app.idl.data.remote.supabase.TokenProvider
import app.idl.domain.Audience
import app.idl.domain.AudienceRule
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.BaseForm
import app.idl.domain.Expiry
import app.idl.domain.Expression
import app.idl.domain.FriendStatus
import app.idl.domain.Mood
import app.idl.domain.PrivacyRules
import app.idl.domain.QuickState
import app.idl.domain.ReactionTemplate
import app.idl.domain.VisibilityCategory
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end: the real Kotlin client against real PostgREST + the real migration.
 * Skipped unless PostgREST is running:
 *   supabase/tests/run.sh --rest
 *   ./gradlew testDebugUnitTest --tests '*SupabaseRestIT*' -Pidl.postgrestUrl=http://localhost:54330
 */
class SupabaseRestIT {
    private val url = System.getProperty("idl.postgrestUrl").orEmpty()
    private val secret = "idl-local-test-secret-at-least-32-chars"
    private val http = OkHttpClient()

    /** PostgREST is served at the root; the client expects Supabase's /rest/v1 prefix. */
    private val config get() = SupabaseConfig("$url/__supabase", "anon")
    private val prefixed = OkHttpClient.Builder().addInterceptor { chain ->
        val req = chain.request()
        val path = req.url.encodedPath.removePrefix("/__supabase/rest/v1")
        chain.proceed(req.newBuilder().url(req.url.newBuilder().encodedPath(path).build()).build())
    }.build()

    private fun jwt(sub: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        val header = enc.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val exp = Instant.now().epochSecond + 3600
        val payload = enc.encodeToString("""{"sub":"$sub","role":"authenticated","exp":$exp}""".toByteArray())
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        return "$header.$payload." + enc.encodeToString(mac.doFinal("$header.$payload".toByteArray()))
    }

    private fun user(): Pair<String, SupabaseIdlBackend> {
        val id = UUID.randomUUID().toString()
        // Stand-in for GoTrue creating auth.users (test-only RPC from 90_test_support.sql).
        val res = http.newCall(
            Request.Builder().url("$url/rpc/test_create_auth_user")
                .post("""{"p_id":"$id"}""".toRequestBody("application/json".toMediaType())).build(),
        ).execute()
        res.use { check(it.isSuccessful) { "create user failed: ${it.code} ${it.body?.string()}" } }
        val token = jwt(id)
        val tokens = object : TokenProvider {
            override suspend fun accessToken() = token
            override suspend fun refreshAfterUnauthorized() = false
        }
        return id to SupabaseIdlBackend(prefixed, config, tokens)
    }

    private suspend fun expectError(expected: IdlError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: IdlException) {
            assertEquals(expected, e.error)
        }
    }

    @Test fun fullLoopAgainstPostgrest() = runTest {
        assumeTrue("PostgREST not configured", url.isNotBlank())
        val suffix = UUID.randomUUID().toString().take(6).replace('-', '_')
        val (ariId, ari) = user()
        val (moId, mo) = user()

        expectError(IdlError.NotFound) { ari.me() } // signed in but no profile yet
        assertEquals("ari_$suffix", ari.register("Ari", "ari_$suffix").username)
        mo.register("Mo", "mo_$suffix")
        expectError(IdlError.Invalid("username", "That username is taken")) { user().second.register("X", "mo_$suffix") }

        // Avatar round trip in the shared wire format.
        val fox = AvatarConfig(baseForm = BaseForm.FOX, expression = Expression.HAPPY)
        assertEquals(fox, ari.putAvatar(fox))
        assertEquals(fox, ari.getAvatar())

        // Invite -> request -> accept.
        val invite = mo.createInvite()
        assertEquals(FriendStatus.OUTGOING, ari.redeemInvite(invite.url).status)
        assertEquals(FriendStatus.INCOMING, mo.friends().single().status)
        assertEquals(FriendStatus.ACCEPTED, mo.acceptRequest(ariId).status)
        mo.setCloseFriend(ariId, true)

        // Mo's status as Ari sees it (close friend: mood visible; note hidden by rule).
        mo.putPrivacyRules(PrivacyRules.DEFAULT.with(AudienceRule(VisibilityCategory.STATUS_NOTE, Audience.NOBODY)))
        val now = Instant.now()
        mo.putPresence(QuickState.ALL.first { it.id == "sleepy" }.toState(now, note = "secret nap"))
        val view = ari.friendPresence(moId)
        assertEquals(Mood.SLEEPY, view.mood)
        assertEquals(Availability.TEXT_ONLY, view.availability)
        assertEquals(Expression.SLEEPY, view.avatar.expression)
        assertNull(view.note)
        assertTrue(view.expiresAt!!.isAfter(now.plus(Expiry.DEFAULT)))
        assertEquals(listOf(view), ari.friendPresence())
        assertEquals(Audience.NOBODY, mo.privacyRules().ruleFor(VisibilityCategory.STATUS_NOTE).audience)

        // Invisible looks like no status.
        mo.setInvisible(true)
        assertFalse(ari.friendPresence(moId).hasStatus)
        assertTrue(mo.me().invisible)
        mo.setInvisible(false)

        // Reactions.
        val r = ari.sendReaction(moId, ReactionTemplate.COFFEE)
        assertEquals(listOf(r), mo.inbox())
        mo.dismissReaction(r.id)
        assertTrue(mo.inbox().isEmpty())

        // Block revokes immediately.
        mo.block(ariId)
        expectError(IdlError.Forbidden) { ari.friendPresence(moId) }
        expectError(IdlError.Forbidden) { ari.sendReaction(moId, ReactionTemplate.HEART) }
        assertTrue(ari.friends().isEmpty())
        assertEquals(ariId, mo.blocked().single().userId)
    }
}
