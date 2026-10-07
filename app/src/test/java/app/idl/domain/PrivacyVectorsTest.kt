package app.idl.domain

import app.idl.domain.avatar.LegacyAvatarMigration
import app.idl.domain.avatar.coreRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * Golden privacy vectors shared with the server. The Kotlin PrivacyFilter is the reference;
 * this test (re)generates contract/privacy_vectors.json from it and fails if the committed file
 * drifts. supabase/tests/privacy_vectors.sql asserts that idl_private.presence_view() produces
 * exactly the same `expected` output for every case.
 *
 * Regenerate after an intentional behaviour change:
 *   ./gradlew testDebugUnitTest -Pidl.updateGolden=true
 */
class PrivacyVectorsTest {

    @Serializable
    data class Rel(
        val isFriend: Boolean,
        val isCloseFriend: Boolean = false,
        val blocked: Boolean = false,
        val circleIds: Set<String> = emptySet(),
    )

    @Serializable
    data class Case(
        val name: String,
        @Serializable(with = InstantSerializer::class) val now: Instant,
        val ownerId: String,
        val viewerId: String,
        val relationship: Rel,
        val ownerInvisible: Boolean = false,
        /** Null: the owner never saved an avatar (server falls back to the default). */
        val avatar: AvatarConfig? = null,
        val states: List<PresenceState> = emptyList(),
        val rules: PrivacyRules = PrivacyRules.DEFAULT,
        val expected: PresenceView? = null,
    )

    @Serializable
    data class Vectors(val note: String, val cases: List<Case>)

    private val owner = "00000000-0000-4000-8000-0000000000aa"
    private val viewer = "00000000-0000-4000-8000-0000000000bb"
    private val circleA = "00000000-0000-4000-8000-00000000c001"
    private val circleB = "00000000-0000-4000-8000-00000000c002"
    private val registry = coreRegistry()
    private val fox = AvatarConfig(
        baseForm = BaseForm.FOX, bodyColor = AvatarPalette.body[10], themeColor = AvatarPalette.theme[2],
        expression = Expression.HAPPY, headAccessory = HeadAccessory.CROWN, faceAccessory = FaceAccessory.GLASSES,
        faceStyle = FaceStyle.BLUSHY, frameStyle = FrameStyle.CIRCLE, renderVersion = 1,
    )
    private val friend = Rel(isFriend = true)
    private val close = Rel(isFriend = true, isCloseFriend = true)

    private val sleepy = state(
        mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY, intent = StatusIntent.ASK_LATER,
        activity = Activity(ActivityType.VR, "VRChat", joinable = true, joinUrl = "vrchat://launch"),
        note = "napping", visual = VisualOverride(props = listOf(Prop.TEA), scene = Scene.COZY_BEDROOM, bodyAccessory = BodyAccessory.BLANKET),
    )

    private fun rules(vararg pairs: Pair<VisibilityCategory, Audience>, ids: Set<String> = emptySet()) =
        pairs.fold(PrivacyRules.DEFAULT) { r, (c, a) -> r.with(AudienceRule(c, a, ids)) }

    private fun c(
        name: String,
        r: Rel = friend,
        states: List<PresenceState> = listOf(sleepy),
        rules: PrivacyRules = PrivacyRules.DEFAULT,
        invisible: Boolean = false,
        avatar: AvatarConfig? = fox,
        self: Boolean = false,
        now: Instant = T0.plus(Duration.ofMinutes(30)),
    ): Case {
        val viewerId = if (self) owner else viewer
        val identity = LegacyAvatarMigration.migrate(avatar ?: AvatarConfig(), registry)
        val relationship = Relationship(r.isFriend, r.isCloseFriend, r.blocked, r.circleIds)
        val expected = PrivacyFilter.viewFor(
            viewerId, owner, identity, PresenceResolver.resolve(states, now), rules, relationship, invisible,
        )
        return Case(name, now, owner, viewerId, r, invisible, avatar, states, rules, expected)
    }

    private fun cases(): List<Case> = listOf(
        c("not friends", r = Rel(isFriend = false)),
        c("blocked friend", r = Rel(isFriend = true, isCloseFriend = true, blocked = true)),
        c("default friend sees avatar and availability", r = friend),
        c("default close friend", r = close),
        c("owner sees everything", self = true),
        c("owner sees everything while invisible", self = true, invisible = true),
        c("invisible equals no status", r = close, invisible = true),
        c("no status", r = close, states = emptyList()),
        c("expired status", r = close, now = T0.plus(Duration.ofHours(5))),
        c("only me and nobody", r = close, rules = rules(VisibilityCategory.AVAILABILITY to Audience.ONLY_ME, VisibilityCategory.STATUS_NOTE to Audience.NOBODY)),
        c("activity name needs category", r = close, rules = rules(
            VisibilityCategory.ACTIVITY_CATEGORY to Audience.NOBODY, VisibilityCategory.ACTIVITY_NAME to Audience.FRIENDS, VisibilityCategory.JOINABLE to Audience.FRIENDS)),
        c("joinable shared with friends", r = friend, rules = rules(
            VisibilityCategory.ACTIVITY_CATEGORY to Audience.FRIENDS, VisibilityCategory.ACTIVITY_NAME to Audience.FRIENDS, VisibilityCategory.JOINABLE to Audience.FRIENDS)),
        c("hidden avatar is minimal", r = close, rules = rules(VisibilityCategory.AVATAR to Audience.NOBODY)),
        c("hidden avatar while invisible", r = close, invisible = true, rules = rules(VisibilityCategory.AVATAR to Audience.NOBODY)),
        c("nothing visible has no expiry", r = close, rules = PrivacyRules(VisibilityCategory.entries.associateWith { AudienceRule(it, Audience.NOBODY) })),
        c("avatar-only change carries expiry", r = friend, rules = PrivacyRules(VisibilityCategory.entries.associateWith {
            AudienceRule(it, if (it == VisibilityCategory.AVATAR) Audience.FRIENDS else Audience.NOBODY)
        })),
        c("individuals includes viewer", r = friend, rules = rules(VisibilityCategory.STATUS_NOTE to Audience.INDIVIDUALS, ids = setOf(viewer))),
        c("individuals excludes viewer", r = close, rules = rules(VisibilityCategory.STATUS_NOTE to Audience.INDIVIDUALS, ids = setOf(owner))),
        c("circle member sees mood", r = Rel(true, circleIds = setOf(circleA)), rules = rules(VisibilityCategory.MOOD to Audience.CIRCLES, ids = setOf(circleA))),
        c("other circle does not see mood", r = Rel(true, circleIds = setOf(circleB)), rules = rules(VisibilityCategory.MOOD to Audience.CIRCLES, ids = setOf(circleA))),
        c("no saved avatar uses default", r = close, avatar = null),
        c("dnd without mood derives expression", r = close, states = listOf(state(availability = Availability.DO_NOT_DISTURB))),
        c("explicit expression override", r = close, states = listOf(state(mood = Mood.SAD, visual = VisualOverride(expression = Expression.MISCHIEVOUS)))),
        c("blank note is no note", r = close, states = listOf(state(mood = Mood.GOOD, note = "   "))),
        c("manual beats vrcq core, vrcq adds activity", r = close, states = listOf(
            state(mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY, expires = T0.plus(Duration.ofHours(8))),
            state(PresenceSource.VRCQ, mood = Mood.EXCITED, availability = Availability.GAMING,
                activity = Activity(ActivityType.VR, "VRChat", PresenceSource.VRCQ),
                visual = VisualOverride(headAccessory = HeadAccessory.VR_HEADSET),
                updated = T0.plusSeconds(60), expires = T0.plus(Duration.ofHours(1))),
        )),
        c("automated mood alone is ignored", r = close, states = listOf(
            state(PresenceSource.DESKTOP_BRIDGE, mood = Mood.FOCUSED, activity = Activity(ActivityType.CODING)),
        )),
        c("equal priority newest wins", r = close, rules = rules(VisibilityCategory.ACTIVITY_NAME to Audience.FRIENDS), states = listOf(
            state(PresenceSource.STEAM, activity = Activity(ActivityType.GAMING, "Old", PresenceSource.STEAM), updated = T0),
            state(PresenceSource.DISCORD, activity = Activity(ActivityType.GAMING, "New", PresenceSource.DISCORD), updated = T0.plusSeconds(5)),
        )),
        c("equal priority and time breaks by source order", r = close, rules = rules(VisibilityCategory.ACTIVITY_NAME to Audience.FRIENDS), states = listOf(
            state(PresenceSource.XBOX, activity = Activity(ActivityType.GAMING, "Xbox", PresenceSource.XBOX)),
            state(PresenceSource.STEAM, activity = Activity(ActivityType.GAMING, "Steam", PresenceSource.STEAM)),
        )),
        c("expired manual lets lower source show", r = close, now = T0.plus(Duration.ofHours(2)), states = listOf(
            state(mood = Mood.SAD, activity = Activity(ActivityType.WATCHING), expires = T0.plus(Duration.ofHours(1))),
            state(PresenceSource.ANDROID_LOCAL, activity = Activity(ActivityType.LISTENING, source = PresenceSource.ANDROID_LOCAL), expires = T0.plus(Duration.ofHours(5))),
        )),
    )

    @Test fun `golden vectors are up to date`() {
        val json = Json(IdlJson) { prettyPrint = true }
        val generated = json.encodeToString(
            Vectors.serializer(),
            Vectors(
                note = "Generated by PrivacyVectorsTest from the Kotlin reference PrivacyFilter. Do not edit by hand.",
                cases = cases(),
            ),
        ) + "\n"
        val file = goldenFile()
        if (System.getProperty("idl.updateGolden") == "true" || !file.exists()) {
            file.parentFile.mkdirs()
            file.writeText(generated)
        }
        assertEquals(
            "contract/privacy_vectors.json is stale; rerun with -Pidl.updateGolden=true and review the diff",
            generated, file.readText().replace("\r\n", "\n"),
        )
    }

    private fun goldenFile(): File {
        // Unit tests run with the module dir as working directory.
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "repo root not found" }, "contract/privacy_vectors.json")
    }
}
