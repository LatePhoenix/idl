package app.idl.domain

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

@Serializable(with = ReactionTemplate.Serializer::class)
enum class ReactionTemplate(val label: String, val emoji: String) {
    HEART("Heart", "❤️"),
    COFFEE("Coffee", "☕"),
    TEA("Tea", "🍵"),
    SNACK("Snack", "🍪"),
    HUG("Hug", "🫂"),
    HIGH_FIVE("High five", "🙌"),
    SAME("Same", "🤝"),
    YOU_OKAY("You okay?", "💛"),
    WANT_COMPANY("Want company?", "🛋️"),
    JOIN("Join?", "🚪"),
    CALL_LATER("Call later?", "📞"),
    IM_AROUND("I'm around", "👋"),
    NICE("Nice", "✨"),
    GOOD_LUCK("Good luck", "🍀");

    object Serializer : WireEnumSerializer<ReactionTemplate>("ReactionTemplate", entries, HEART)
}

@Serializable
data class Reaction(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val template: ReactionTemplate,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant,
    @Serializable(with = InstantSerializer::class) val dismissedAt: Instant? = null,
) {
    fun isActive(now: Instant): Boolean = dismissedAt == null && now.isBefore(expiresAt)
}

object ReactionRules {
    val TTL: Duration = Duration.ofHours(24)

    /** Max reactions one sender may send one recipient per hour (spam guard). */
    const val PER_RECIPIENT_HOURLY_LIMIT = 10

    fun inbox(all: List<Reaction>, me: String, now: Instant): List<Reaction> =
        all.filter { it.recipientId == me && it.isActive(now) }.sortedByDescending { it.createdAt }

    fun dismiss(r: Reaction, now: Instant): Reaction = if (r.dismissedAt != null) r else r.copy(dismissedAt = now)

    fun canSend(rel: Relationship, recentToRecipient: List<Reaction>, now: Instant): Boolean {
        if (!rel.isFriend || rel.blocked) return false
        val hourAgo = now.minus(Duration.ofHours(1))
        return recentToRecipient.count { it.createdAt.isAfter(hourAgo) } < PER_RECIPIENT_HOURLY_LIMIT
    }
}

@Serializable(with = FriendStatus.Serializer::class)
enum class FriendStatus {
    ACCEPTED, INCOMING, OUTGOING;

    object Serializer : WireEnumSerializer<FriendStatus>("FriendStatus", entries, OUTGOING)
}

@Serializable
data class Friend(
    val userId: String,
    val username: String,
    val displayName: String,
    val status: FriendStatus,
    /** I have put this friend on my close-friends list. */
    val isCloseFriend: Boolean = false,
)

@Serializable
data class Me(
    val userId: String,
    val username: String,
    val displayName: String,
    val invisible: Boolean = false,
)

@Serializable
data class Invite(
    val code: String,
    val url: String,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant,
)

object Usernames {
    private val pattern = Regex("^[a-z0-9_]{3,20}$")
    fun isValid(username: String) = pattern.matches(username)
    fun normalize(raw: String) = raw.trim().lowercase().replace(' ', '_')
}

/** One- or two-tap status presets for the Status Deck. */
data class QuickState(
    val id: String,
    val label: String,
    val emoji: String,
    val mood: Mood?,
    val availability: Availability?,
    val intent: StatusIntent? = null,
    val activity: ActivityType? = null,
    val visual: VisualOverride? = null,
    val duration: Duration,
) {
    fun toState(now: Instant, note: String? = null) = PresenceState(
        source = PresenceSource.MANUAL,
        mood = mood,
        availability = availability,
        intent = intent,
        activity = activity?.let { Activity(type = it) },
        note = note,
        visual = visual,
        startedAt = now,
        expiresAt = Expiry.expiresAt(now, duration),
        updatedAt = now,
    )

    companion object {
        val ALL = listOf(
            QuickState("sleepy", "Sleepy", "😴", Mood.SLEEPY, Availability.TEXT_ONLY, StatusIntent.ASK_LATER,
                visual = VisualOverride(props = listOf(Prop.TEA), bodyAccessory = BodyAccessory.BLANKET, scene = Scene.COZY_BEDROOM),
                duration = Duration.ofHours(8)),
            QuickState("focus", "Heads down", "🧐", Mood.FOCUSED, Availability.BUSY, StatusIntent.ASK_LATER, ActivityType.WORKING,
                visual = VisualOverride(props = listOf(Prop.COFFEE), scene = Scene.DESK_SETUP),
                duration = Duration.ofHours(2)),
            QuickState("gaming", "Gaming", "🎮", Mood.SOCIAL, Availability.GAMING, StatusIntent.INVITE_ME, ActivityType.GAMING,
                visual = VisualOverride(props = listOf(Prop.CONTROLLER), headAccessory = HeadAccessory.HEADPHONES),
                duration = Duration.ofHours(3)),
            QuickState("vr", "In VR", "🥽", Mood.EXCITED, Availability.TEXT_ONLY, null, ActivityType.VR,
                visual = VisualOverride(headAccessory = HeadAccessory.VR_HEADSET, scene = Scene.NEON_CITY),
                duration = Duration.ofHours(2)),
            QuickState("company", "Want company", "🫂", Mood.LOW_ENERGY, Availability.AVAILABLE, StatusIntent.WANT_COMPANY,
                visual = VisualOverride(props = listOf(Prop.PIZZA)),
                duration = Duration.ofHours(4)),
            QuickState("around", "Around", "👋", Mood.GOOD, Availability.AVAILABLE, StatusIntent.NO_PREFERENCE,
                duration = Duration.ofHours(4)),
            QuickState("dnd", "Do not disturb", "🌙", null, Availability.DO_NOT_DISTURB, null,
                duration = Duration.ofHours(2)),
        )
    }
}
