package app.idl

import android.util.Log
import java.time.Instant

fun interface IdlClock {
    fun now(): Instant

    companion object {
        val SYSTEM = IdlClock { Instant.now() }
    }
}

/**
 * Compile-time feature flags. Future integrations exist only as interfaces and placeholder
 * UI until their flag is turned on in a later milestone.
 */
object FeatureFlags {
    const val CIRCLES = false
    const val DEEP_LINKS = false
    const val INTEGRATION_DISCORD = false
    const val INTEGRATION_STEAM = false
    const val INTEGRATION_XBOX = false
    const val INTEGRATION_VRCQ = false
    const val INTEGRATION_DESKTOP_BRIDGE = false
    const val INTEGRATION_SDK = false
    /** Real backend adapter (Supabase). Off: the app runs on FakeIdlBackend. */
    const val REMOTE_BACKEND = false
    val MOCK_PUSH = BuildConfig.DEBUG
}

/**
 * Structured log wrapper. Event names are stable (`area.event`) so they can be forwarded to a
 * crash/analytics backend later. Never pass note text or friend names as fields.
 */
object IdlLog {
    private const val TAG = "iDL"

    fun i(event: String, vararg fields: Pair<String, Any?>) = log(Log.INFO, event, fields, null)
    fun w(event: String, vararg fields: Pair<String, Any?>, t: Throwable? = null) = log(Log.WARN, event, fields, t)
    fun e(event: String, t: Throwable? = null, vararg fields: Pair<String, Any?>) = log(Log.ERROR, event, fields, t)

    private fun log(level: Int, event: String, fields: Array<out Pair<String, Any?>>, t: Throwable?) {
        val msg = buildString {
            append(event)
            fields.forEach { (k, v) -> append(' ').append(k).append('=').append(v) }
            if (t != null) append(" err=").append(t.javaClass.simpleName).append(':').append(t.message)
        }
        runCatching { Log.println(level, TAG, msg) }
        if (level >= Log.ERROR) CrashReporter.current.record(event, t)
    }
}

/** Crash reporting seam; vendor is chosen with the backend (IDL_DECISIONS D-07). */
interface CrashReporter {
    fun record(event: String, t: Throwable?)

    companion object {
        @Volatile var current: CrashReporter = object : CrashReporter {
            override fun record(event: String, t: Throwable?) = Unit
        }
    }
}
