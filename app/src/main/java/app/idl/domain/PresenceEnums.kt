package app.idl.domain

import kotlinx.serialization.Serializable

@Serializable(with = Mood.Serializer::class)
enum class Mood(val label: String, val emoji: String) {
    NEUTRAL("Neutral", "😐"),
    GOOD("Good", "🙂"),
    HAPPY("Happy", "😊"),
    EXCITED("Excited", "🤩"),
    SLEEPY("Sleepy", "😴"),
    TIRED("Tired", "🥱"),
    LOW_ENERGY("Low energy", "🪫"),
    STRESSED("Stressed", "😣"),
    ANXIOUS("Anxious", "😰"),
    SAD("Sad", "😢"),
    SICK("Sick", "🤒"),
    FOCUSED("Focused", "🧐"),
    CHAOTIC("Chaotic", "🌀"),
    SOCIAL("Social", "🥳"),
    OVERWHELMED("Overwhelmed", "😵‍💫");

    object Serializer : WireEnumSerializer<Mood>("Mood", entries, NEUTRAL)
}

@Serializable(with = Availability.Serializer::class)
enum class Availability(val label: String, val emoji: String) {
    AVAILABLE("Available", "🟢"),
    TEXT_ONLY("Text only", "💬"),
    CALL_OK("Call OK", "📞"),
    GAMING("Gaming", "🎮"),
    BUSY("Busy", "⏳"),
    DO_NOT_DISTURB("Do not disturb", "🌙"),
    AFK("AFK", "💤"),
    OFFLINE("Offline", "⚪");

    object Serializer : WireEnumSerializer<Availability>("Availability", entries, OFFLINE)
}

@Serializable(with = StatusIntent.Serializer::class)
enum class StatusIntent(val label: String, val emoji: String) {
    NO_PREFERENCE("No preference", ""),
    INVITE_ME("Invite me", "🙋"),
    WANT_COMPANY("Want company", "🫂"),
    NEED_MEMES("Need memes", "🐸"),
    ASK_LATER("Ask later", "⏰"),
    CHECK_IN("Check in on me", "💛"),
    CELEBRATING("Celebrating", "🎉");

    object Serializer : WireEnumSerializer<StatusIntent>("StatusIntent", entries, NO_PREFERENCE)
}

@Serializable(with = ActivityType.Serializer::class)
enum class ActivityType(val label: String, val emoji: String) {
    NONE("None", ""),
    WORKING("Working", "💼"),
    CODING("Coding", "💻"),
    GAMING("Gaming", "🎮"),
    VR("In VR", "🥽"),
    WATCHING("Watching", "📺"),
    LISTENING("Listening", "🎧"),
    READING("Reading", "📖"),
    TRAVELING("Traveling", "✈️"),
    EXERCISING("Exercising", "🏃"),
    SLEEPING("Sleeping", "🌙"),
    CUSTOM("Custom", "✨");

    object Serializer : WireEnumSerializer<ActivityType>("ActivityType", entries, NONE)
}

/** Where a presence state came from, with its default precedence (higher wins). */
@Serializable(with = PresenceSource.Serializer::class)
enum class PresenceSource(val priority: Int, val isAutomated: Boolean) {
    MANUAL(100, false),
    VRCQ(80, true),
    DESKTOP_BRIDGE(70, true),
    DISCORD(60, true),
    STEAM(60, true),
    XBOX(60, true),
    EXTERNAL_SDK(60, true),
    ANDROID_LOCAL(40, true),
    INFERRED(10, true);

    object Serializer : WireEnumSerializer<PresenceSource>("PresenceSource", entries, INFERRED)
}
