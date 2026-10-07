package app.idl.data.remote

import app.idl.domain.Activity
import app.idl.domain.ActivityType
import app.idl.domain.Audience
import app.idl.domain.AudienceRule
import app.idl.domain.Availability
import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarPalette
import app.idl.domain.BaseForm
import app.idl.domain.BodyAccessory
import app.idl.domain.FaceAccessory
import app.idl.domain.FaceStyle
import app.idl.domain.FriendStatus
import app.idl.domain.HeadAccessory
import app.idl.domain.Mood
import app.idl.domain.PresenceSource
import app.idl.domain.PresenceState
import app.idl.domain.PrivacyRules
import app.idl.domain.Prop
import app.idl.domain.Scene
import app.idl.domain.StatusIntent
import app.idl.domain.VisibilityCategory
import app.idl.domain.VisualOverride
import app.idl.domain.avatar.AvatarConfiguration
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/** A simulated other user living inside the fake backend. */
@Serializable
data class DemoUser(
    val id: String,
    val username: String,
    val displayName: String,
    val avatar: AvatarConfig,
    /** Schema 3 identity. Hair, color, and top stay distinct once every base is the teardrop. */
    val recipe: AvatarConfiguration = AvatarConfiguration(
        baseAssetId = "base_teardrop",
        paletteAssetId = "palette_sunny",
    ),
    val states: List<PresenceState> = emptyList(),
    val rules: PrivacyRules = PrivacyRules.DEFAULT,
    /** Whether this user has put *me* on their close-friends list. */
    val closeFriendsMe: Boolean = false,
    val invisible: Boolean = false,
    val inviteCode: String? = null,
)

/**
 * Deterministic demo world. Every instant is relative to the seed time, so the same seed
 * always produces the same world. Each friend exercises a different part of the privacy model.
 */
object DemoData {

    data class Seed(val user: DemoUser, val relation: FriendStatus?)

    fun seeds(now: Instant): List<Seed> {
        fun state(
            hoursLeft: Long,
            mood: Mood?,
            availability: Availability?,
            intent: StatusIntent? = null,
            activity: Activity? = null,
            note: String? = null,
            visual: VisualOverride? = null,
            startedHoursAgo: Long = 1,
        ) = PresenceState(
            source = PresenceSource.MANUAL,
            mood = mood, availability = availability, intent = intent, activity = activity,
            note = note, visual = visual,
            startedAt = now.minus(Duration.ofHours(startedHoursAgo)),
            expiresAt = now.plus(Duration.ofHours(hoursLeft)),
            updatedAt = now.minus(Duration.ofHours(startedHoursAgo)),
        )

        return listOf(
            // Close friend who shares nearly everything with me.
            Seed(
                DemoUser(
                    id = "u_ari", username = "ari", displayName = "Ari",
                    avatar = AvatarConfig(baseForm = BaseForm.FOX, bodyColor = AvatarPalette.body[10], themeColor = AvatarPalette.theme[2], faceStyle = FaceStyle.BLUSHY),
                    recipe = look("palette_peach", "hair_bob", features = listOf("feature_blush")),
                    states = listOf(
                        state(7, Mood.SLEEPY, Availability.TEXT_ONLY, StatusIntent.ASK_LATER, note = "napping, text me",
                            visual = VisualOverride(props = listOf(Prop.TEA), bodyAccessory = BodyAccessory.BLANKET, scene = Scene.COZY_BEDROOM)),
                    ),
                    closeFriendsMe = true,
                ),
                FriendStatus.ACCEPTED,
            ),
            // Close friend in VR who also shares activity names.
            Seed(
                DemoUser(
                    id = "u_juno", username = "juno", displayName = "Juno",
                    avatar = AvatarConfig(baseForm = BaseForm.GHOST, bodyColor = AvatarPalette.body[8], themeColor = AvatarPalette.theme[0]),
                    recipe = look("palette_moonlight", "hair_long_straight", top = "top_crew_tee"),
                    states = listOf(
                        state(3, Mood.EXCITED, Availability.TEXT_ONLY, StatusIntent.INVITE_ME,
                            activity = Activity(ActivityType.VR, label = "VRChat"),
                            visual = VisualOverride(headAccessory = HeadAccessory.VR_HEADSET, scene = Scene.NEON_CITY)),
                    ),
                    rules = PrivacyRules.DEFAULT.with(AudienceRule(VisibilityCategory.ACTIVITY_NAME, Audience.FRIENDS)),
                    closeFriendsMe = true,
                ),
                FriendStatus.ACCEPTED,
            ),
            // Friend, but I am NOT on Mo's close list: only avatar + availability are visible.
            Seed(
                DemoUser(
                    id = "u_mo", username = "mo", displayName = "Mo",
                    avatar = AvatarConfig(baseForm = BaseForm.ROBOT, bodyColor = AvatarPalette.body[9], themeColor = AvatarPalette.theme[1], faceAccessory = FaceAccessory.GLASSES),
                    recipe = look("palette_steel", "hair_short_crop", top = "top_crew_tee"),
                    states = listOf(
                        state(2, Mood.STRESSED, Availability.BUSY, StatusIntent.ASK_LATER,
                            activity = Activity(ActivityType.CODING), note = "deadline day",
                            visual = VisualOverride(props = listOf(Prop.KEYBOARD), scene = Scene.DESK_SETUP)),
                    ),
                    closeFriendsMe = false,
                ),
                FriendStatus.ACCEPTED,
            ),
            // Status already expired: must render as "no status".
            Seed(
                DemoUser(
                    id = "u_sam", username = "sam", displayName = "Sam",
                    avatar = AvatarConfig(baseForm = BaseForm.CAT, bodyColor = AvatarPalette.body[7], themeColor = AvatarPalette.theme[4], headAccessory = HeadAccessory.BEANIE),
                    recipe = look("palette_cocoa", "hair_bob", top = "top_crew_tee"),
                    states = listOf(
                        state(-1, Mood.SOCIAL, Availability.GAMING, StatusIntent.INVITE_ME,
                            activity = Activity(ActivityType.GAMING), startedHoursAgo = 4,
                            visual = VisualOverride(props = listOf(Prop.CONTROLLER))),
                    ),
                    closeFriendsMe = true,
                ),
                FriendStatus.ACCEPTED,
            ),
            // Invisible: must look identical to "no status".
            Seed(
                DemoUser(
                    id = "u_bea", username = "bea", displayName = "Bea",
                    avatar = AvatarConfig(baseForm = BaseForm.BLOB, bodyColor = AvatarPalette.body[4], themeColor = AvatarPalette.theme[3], headAccessory = HeadAccessory.CROWN),
                    recipe = look("palette_mint", "hair_long_straight"),
                    states = listOf(state(5, Mood.SAD, Availability.DO_NOT_DISTURB)),
                    closeFriendsMe = true,
                    invisible = true,
                ),
                FriendStatus.ACCEPTED,
            ),
            // Incoming friend request for the tester to approve.
            Seed(
                DemoUser(
                    id = "u_rin", username = "rin", displayName = "Rin",
                    avatar = AvatarConfig(baseForm = BaseForm.BEAR, bodyColor = AvatarPalette.body[3], themeColor = AvatarPalette.theme[5], headAccessory = HeadAccessory.HEADPHONES),
                    recipe = look("palette_lavender", "hair_short_crop"),
                    states = listOf(state(4, Mood.HAPPY, Availability.AVAILABLE, StatusIntent.WANT_COMPANY, visual = VisualOverride(props = listOf(Prop.COFFEE)))),
                ),
                FriendStatus.INCOMING,
            ),
            // Not connected yet; reachable with invite code KIT-2026.
            Seed(
                DemoUser(
                    id = "u_kit", username = "kit", displayName = "Kit",
                    avatar = AvatarConfig(baseForm = BaseForm.ALIEN, bodyColor = AvatarPalette.body[11], themeColor = AvatarPalette.theme[0], headAccessory = HeadAccessory.WIZARD_HAT),
                    recipe = look("palette_ember", "hair_bob"),
                    states = listOf(state(6, Mood.FOCUSED, Availability.CALL_OK, activity = Activity(ActivityType.READING), visual = VisualOverride(props = listOf(Prop.BOOK)))),
                    closeFriendsMe = true,
                    inviteCode = "KIT-2026",
                ),
                null,
            ),
            Seed(
                DemoUser(
                    id = "u_pix", username = "pix", displayName = "Pix",
                    avatar = AvatarConfig(baseForm = BaseForm.PIXEL, bodyColor = AvatarPalette.body[5], themeColor = AvatarPalette.theme[1]),
                    recipe = look("palette_lime", "hair_short_crop", top = "top_crew_tee"),
                    states = listOf(state(2, Mood.CHAOTIC, Availability.GAMING, StatusIntent.NEED_MEMES, activity = Activity(ActivityType.GAMING), visual = VisualOverride(props = listOf(Prop.GAMEPAD)))),
                    inviteCode = "PIX-2026",
                ),
                null,
            ),
        )
    }

    val INVITE_CODES = listOf("KIT-2026", "PIX-2026")

    private fun look(
        palette: String,
        hair: String,
        top: String? = null,
        features: List<String> = emptyList(),
    ) = AvatarConfiguration(
        baseAssetId = "base_teardrop",
        paletteAssetId = palette,
        signatureFeatureAssetIds = features,
        itemIds = buildMap {
            put("hair", listOf(hair))
            if (top != null) put("top", listOf(top))
        },
    )
}
