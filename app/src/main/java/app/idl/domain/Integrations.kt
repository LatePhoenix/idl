package app.idl.domain

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * External presence sources. Interfaces and contracts only in v0.1 — nothing here talks to
 * any third-party service. All are behind [app.idl.FeatureFlags] and off by default.
 */
interface PresenceIntegration {
    val source: PresenceSource
    val displayName: String

    /** Whether the user has granted consent (IntegrationConsent) for this source. */
    suspend fun isAuthorized(): Boolean

    /** Revoking must delete this source's states server-side; see IDL_PRIVACY_MODEL §5. */
    suspend fun revoke()
}

/** Future VRCQ bridge payload (IDL_API_CONTRACT §6). */
@Serializable
data class VrcqBridgePayload(
    val source: String = "vrcq",
    val activity: Activity,
    val visualHints: VisualHints = VisualHints(),
    val privacy: BridgePrivacy = BridgePrivacy(),
) {
    @Serializable
    data class Activity(val type: ActivityType, val label: String? = null, val joinable: Boolean = false)

    @Serializable
    data class VisualHints(val headAccessory: HeadAccessory? = null, val badge: String? = null)

    @Serializable
    data class BridgePrivacy(val worldNameVisible: Boolean = false, val instanceVisible: Boolean = false)
}

/** Normalizes source-specific payloads into the shared Presence Envelope. */
object EnvelopeNormalizer {
    /** Bridge states are short-lived heartbeats; the bridge must refresh them. */
    val BRIDGE_TTL: Duration = Duration.ofMinutes(15)

    fun fromVrcq(payload: VrcqBridgePayload, now: Instant): PresenceState = PresenceState(
        source = PresenceSource.VRCQ,
        // Bridges never set mood/availability/intent/note; the resolver also enforces this.
        activity = Activity(
            type = payload.activity.type,
            label = payload.activity.label,
            source = PresenceSource.VRCQ,
            joinable = payload.activity.joinable,
            joinUrl = null, // instance info is never forwarded in v1 contract
        ),
        visual = payload.visualHints.headAccessory?.let { VisualOverride(headAccessory = it) },
        startedAt = now,
        expiresAt = now.plus(BRIDGE_TTL),
        updatedAt = now,
    )
}
