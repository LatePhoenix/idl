package app.idl.data

import app.idl.IdlClock
import app.idl.data.remote.IdlError
import app.idl.data.remote.IdlException
import app.idl.data.remote.supabase.AuthSession
import app.idl.data.remote.supabase.InMemorySessionStore
import app.idl.data.remote.supabase.SupabaseAuth
import app.idl.data.remote.supabase.SupabaseConfig
import app.idl.data.remote.supabase.SupabaseIdlBackend
import app.idl.data.remote.supabase.TokenProvider
import app.idl.domain.Availability
import app.idl.domain.Mood
import app.idl.domain.ReactionTemplate
import app.idl.domain.T0
import app.idl.domain.state
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Instant

class SupabaseClientTest {
    private val server = MockWebServer()
    private val http = OkHttpClient()
    private lateinit var config: SupabaseConfig
    private var now: Instant = T0
    private val clock = IdlClock { now }

    @Before fun setUp() {
        server.start()
        config = SupabaseConfig(server.url("/").toString(), "anon-key")
    }

    @After fun tearDown() = server.shutdown()

    private class FakeTokens(var token: String? = "t1", val refreshTo: String? = "t2") : TokenProvider {
        var refreshes = 0
        override suspend fun accessToken() = token
        override suspend fun refreshAfterUnauthorized(): Boolean {
            refreshes++
            token = refreshTo
            return refreshTo != null
        }
    }

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private suspend fun expectError(expected: IdlError, block: suspend () -> Unit) {
        try {
            block()
            fail("expected $expected")
        } catch (e: IdlException) {
            assertEquals(expected, e.error)
        }
    }

    // --- REST / RPC ----------------------------------------------------------------------------

    @Test fun `rpc sends auth headers, named args and decodes the result`() = runTest {
        server.enqueue(json("""{"userId":"u1","username":"matt","displayName":"Matt","invisible":false}"""))
        val me = SupabaseIdlBackend(http, config, FakeTokens()).register("Matt", "matt")
        assertEquals("matt", me.username)

        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/rest/v1/rpc/register_profile", req.path)
        assertEquals("anon-key", req.getHeader("apikey"))
        assertEquals("Bearer t1", req.getHeader("Authorization"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("matt", body["p_username"]!!.jsonPrimitive.content)
        assertEquals("Matt", body["p_display_name"]!!.jsonPrimitive.content)
    }

    @Test fun `presence envelope is sent in the shared wire format`() = runTest {
        val s = state(mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY, note = "nap")
        server.enqueue(json("{}").setBody(app.idl.domain.IdlJson.encodeToString(app.idl.domain.PresenceState.serializer(), s)))
        assertEquals(s, SupabaseIdlBackend(http, config, FakeTokens()).putPresence(s))
        val envelope = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["p_envelope"]!!.jsonObject
        assertEquals("sleepy", envelope["mood"]!!.jsonPrimitive.content)
        assertEquals("text_only", envelope["availability"]!!.jsonPrimitive.content)
        assertEquals("2026-10-03T14:00:00Z", envelope["startedAt"]!!.jsonPrimitive.content)
    }

    @Test fun `void rpcs accept empty 204 responses`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        SupabaseIdlBackend(http, config, FakeTokens()).dismissReaction("r1")
        assertEquals("/rest/v1/rpc/dismiss_reaction", server.takeRequest().path)
    }

    @Test fun `server errors map to IdlError`() = runTest {
        val backend = SupabaseIdlBackend(http, config, FakeTokens())
        server.enqueue(json("""{"code":"PT400","message":"That username is taken","details":"username","hint":null}""", 400))
        expectError(IdlError.Invalid("username", "That username is taken")) { backend.register("A", "ari") }
        server.enqueue(json("""{"code":"PT403","message":"forbidden"}""", 403))
        expectError(IdlError.Forbidden) { backend.friendPresence("u2") }
        server.enqueue(json("""{"code":"PT404","message":"no_profile"}""", 404))
        expectError(IdlError.NotFound) { backend.me() }
        server.enqueue(json("""{"code":"PT429","message":"rate_limited"}""", 429))
        expectError(IdlError.RateLimited) { backend.sendReaction("u2", ReactionTemplate.HUG) }
        server.enqueue(MockResponse().setResponseCode(503).setBody("upstream"))
        expectError(IdlError.Server(503)) { backend.friends() }
    }

    @Test fun `401 refreshes once and retries with the new token`() = runTest {
        val tokens = FakeTokens()
        server.enqueue(json("""{"code":"PGRST301","message":"JWT expired"}""", 401))
        server.enqueue(json("[]"))
        assertEquals(emptyList<Any>(), SupabaseIdlBackend(http, config, tokens).friends())
        assertEquals(1, tokens.refreshes)
        assertEquals("Bearer t1", server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer t2", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `failed refresh surfaces Unauthorized`() = runTest {
        server.enqueue(json("""{"message":"JWT expired"}""", 401))
        expectError(IdlError.Unauthorized) { SupabaseIdlBackend(http, config, FakeTokens(refreshTo = null)).friends() }
    }

    @Test fun `signed out never hits the network`() = runTest {
        expectError(IdlError.Unauthorized) { SupabaseIdlBackend(http, config, FakeTokens(token = null)).friends() }
        assertEquals(0, server.requestCount)
    }

    @Test fun `unreachable server is Offline`() = runTest {
        server.shutdown()
        expectError(IdlError.Offline) { SupabaseIdlBackend(http, config, FakeTokens()).friends() }
    }

    // --- Auth -----------------------------------------------------------------------------------

    private fun session(access: String, refresh: String, expiresIn: Long = 3600) =
        json("""{"access_token":"$access","token_type":"bearer","expires_in":$expiresIn,"refresh_token":"$refresh","user":{"id":"u1","email":"m@example.com"}}""")

    @Test fun `email code sign-in stores the session`() = runTest {
        val store = InMemorySessionStore()
        val auth = SupabaseAuth(http, config, store, clock)
        server.enqueue(json("{}"))
        server.enqueue(session("a1", "r1"))

        assertTrue(auth.requestEmailCode(" m@example.com ").isSuccess)
        val otp = server.takeRequest()
        assertEquals("/auth/v1/otp", otp.path)
        assertTrue(otp.body.readUtf8().contains("\"create_user\":true"))

        assertTrue(auth.verifyEmailCode("m@example.com", "123456").isSuccess)
        val verify = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("email", verify["type"]!!.jsonPrimitive.content)
        assertEquals("123456", verify["token"]!!.jsonPrimitive.content)
        assertEquals(AuthSession("a1", "r1", T0.plusSeconds(3600), "u1"), store.load())
        assertEquals("a1", auth.accessToken())
    }

    @Test fun `wrong code is a friendly validation error`() = runTest {
        server.enqueue(json("""{"code":403,"error_code":"otp_expired","msg":"Token has expired or is invalid"}""", 403))
        val result = SupabaseAuth(http, config, InMemorySessionStore(), clock).verifyEmailCode("m@example.com", "000000")
        assertEquals(IdlError.Invalid("code", "That code is wrong or has expired"), (result.exceptionOrNull() as IdlException).error)
    }

    @Test fun `token is refreshed shortly before expiry`() = runTest {
        val store = InMemorySessionStore(AuthSession("a1", "r1", T0.plusSeconds(30), "u1"))
        server.enqueue(session("a2", "r2"))
        assertEquals("a2", SupabaseAuth(http, config, store, clock).accessToken())
        val req = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", req.path)
        assertTrue(req.body.readUtf8().contains("\"refresh_token\":\"r1\""))
        assertEquals("r2", store.load()!!.refreshToken)
    }

    @Test fun `rejected refresh signs out but offline refresh keeps the session`() = runTest {
        val store = InMemorySessionStore(AuthSession("a1", "r1", T0, "u1"))
        val auth = SupabaseAuth(http, config, store, clock)
        server.enqueue(json("""{"error":"invalid_grant","error_description":"Invalid Refresh Token"}""", 400))
        assertFalse(auth.refreshAfterUnauthorized())
        assertNull(store.load())

        store.save(AuthSession("a1", "r1", T0, "u1"))
        server.shutdown()
        try {
            auth.accessToken()
            fail("expected offline")
        } catch (e: IdlException) {
            assertEquals(IdlError.Offline, e.error)
        }
        assertEquals("r1", store.load()!!.refreshToken)
    }

    @Test fun `sign out clears the session even if the server is unreachable`() = runTest {
        val store = InMemorySessionStore(AuthSession("a1", "r1", T0.plusSeconds(3600), "u1"))
        server.shutdown()
        SupabaseAuth(http, config, store, clock).signOut()
        assertNull(store.load())
    }
}
