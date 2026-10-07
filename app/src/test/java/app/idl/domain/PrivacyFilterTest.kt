package app.idl.domain

import app.idl.domain.avatar.LegacyAvatarMigration
import app.idl.domain.avatar.coreRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyFilterTest {
    private val registry = coreRegistry()
    private val base = AvatarConfig(
        baseForm = BaseForm.FOX, expression = Expression.NEUTRAL,
        headAccessory = HeadAccessory.CROWN, faceAccessory = FaceAccessory.GLASSES,
    )
    private val identity = LegacyAvatarMigration.migrate(base, registry)
    private val presence = PresenceResolver.resolve(
        listOf(
            state(
                mood = Mood.SLEEPY, availability = Availability.TEXT_ONLY, intent = StatusIntent.ASK_LATER,
                activity = Activity(ActivityType.VR, "VRChat", joinable = true, joinUrl = "vrchat://launch"),
                note = "napping", visual = VisualOverride(props = listOf(Prop.TEA)),
            ),
        ),
        T0,
    )
    private val friend = Relationship(isFriend = true)
    private val close = Relationship(isFriend = true, isCloseFriend = true)

    private fun view(
        rel: Relationship,
        rules: PrivacyRules = PrivacyRules.DEFAULT,
        invisible: Boolean = false,
        p: ResolvedPresence = presence,
        viewer: String = "viewer",
    ) = PrivacyFilter.viewFor(viewer, "owner", identity, p, rules, rel, invisible)

    @Test fun `non-friends and blocked users get nothing at all`() {
        assertNull(view(Relationship(isFriend = false)))
        assertNull(view(Relationship(isFriend = true, blocked = true)))
        assertNull(view(Relationship(isFriend = true, isCloseFriend = true, blocked = true)))
    }

    @Test fun `default friend sees avatar and broad availability only`() {
        val v = view(friend)!!
        assertEquals(Availability.TEXT_ONLY, v.availability)
        assertNotNull(v.updatedAt)
        assertNull(v.mood)
        assertNull(v.intent)
        assertNull(v.activityType)
        assertNull(v.activityLabel)
        assertNull(v.joinable)
        assertNull(v.joinUrl)
        assertNull(v.note)
    }

    @Test fun `derived-leak rule - hidden mood never shows through the expression`() {
        val v = view(friend)!!
        assertEquals(identity.restingExpressionId, v.identity.restingExpressionId)
        assertNull(v.visual?.expressionId)
        // Non-mood visuals (props) are part of avatar appearance and still show.
        assertEquals("prop_tea", v.visual?.propAssetId)
    }

    @Test fun `default close friend sees mood, intent, activity category and note`() {
        val v = view(close)!!
        assertEquals(Mood.SLEEPY, v.mood)
        assertEquals("sleepy", v.visual?.expressionId)
        assertEquals(StatusIntent.ASK_LATER, v.intent)
        assertEquals(ActivityType.VR, v.activityType)
        assertEquals("napping", v.note)
        // Activity name, joinable and links default to only-me.
        assertNull(v.activityLabel)
        assertNull(v.joinable)
        assertNull(v.joinUrl)
    }

    @Test fun `only-me and nobody hide from everyone but the owner`() {
        val rules = PrivacyRules.DEFAULT
            .with(AudienceRule(VisibilityCategory.AVAILABILITY, Audience.ONLY_ME))
            .with(AudienceRule(VisibilityCategory.STATUS_NOTE, Audience.NOBODY))
        val v = view(close, rules)!!
        assertNull(v.availability)
        assertNull(v.note)
        val self = view(close, rules, viewer = "owner")!!
        assertEquals(Availability.TEXT_ONLY, self.availability)
        assertEquals("napping", self.note)
    }

    @Test fun `activity name requires activity category`() {
        val rules = PrivacyRules.DEFAULT
            .with(AudienceRule(VisibilityCategory.ACTIVITY_CATEGORY, Audience.NOBODY))
            .with(AudienceRule(VisibilityCategory.ACTIVITY_NAME, Audience.FRIENDS))
            .with(AudienceRule(VisibilityCategory.JOINABLE, Audience.FRIENDS))
        val v = view(close, rules)!!
        assertNull(v.activityType)
        assertNull(v.activityLabel)
        assertNull(v.joinUrl)
    }

    @Test fun `joinable can be shared to friends`() {
        val rules = PrivacyRules.DEFAULT
            .with(AudienceRule(VisibilityCategory.ACTIVITY_CATEGORY, Audience.FRIENDS))
            .with(AudienceRule(VisibilityCategory.ACTIVITY_NAME, Audience.FRIENDS))
            .with(AudienceRule(VisibilityCategory.JOINABLE, Audience.FRIENDS))
        val v = view(friend, rules)!!
        assertEquals("VRChat", v.activityLabel)
        assertEquals(true, v.joinable)
        assertEquals("vrchat://launch", v.joinUrl)
    }

    @Test fun `hidden avatar shows only the minimal base`() {
        val rules = PrivacyRules.DEFAULT.with(AudienceRule(VisibilityCategory.AVATAR, Audience.NOBODY))
        val v = view(close, rules)!!
        assertEquals("base_teardrop", v.identity.baseAssetId)
        assertEquals(identity.paletteAssetId, v.identity.paletteAssetId)
        assertNull(v.identity.signatureHeadAccessoryAssetId)
        assertNull(v.identity.defaultPropAssetId)
        assertNull(v.visual)
    }

    @Test fun `invisible looks identical to having no status`() {
        val invisible = view(close, invisible = true)
        val noStatus = view(close, p = ResolvedPresence.EMPTY)
        assertEquals(noStatus, invisible)
        assertFalse(invisible!!.hasStatus)
        assertNull(invisible.updatedAt)
        assertNull(invisible.expiresAt)
        assertEquals(identity, invisible.identity)
    }

    @Test fun `owner sees everything even while invisible`() {
        val v = view(friend, invisible = true, viewer = "owner")!!
        assertEquals(Mood.SLEEPY, v.mood)
        assertEquals("VRChat", v.activityLabel)
    }

    @Test fun `individuals audience allows only listed friends`() {
        val rules = PrivacyRules.DEFAULT.with(AudienceRule(VisibilityCategory.STATUS_NOTE, Audience.INDIVIDUALS, setOf("alice")))
        assertEquals("napping", view(friend, rules, viewer = "alice")!!.note)
        assertNull(view(close, rules, viewer = "bob")!!.note)
    }

    @Test fun `circles audience matches viewer membership`() {
        val rules = PrivacyRules.DEFAULT.with(AudienceRule(VisibilityCategory.MOOD, Audience.CIRCLES, setOf("vr_crew")))
        assertEquals(Mood.SLEEPY, view(Relationship(isFriend = true, circleIds = setOf("vr_crew")), rules)!!.mood)
        assertNull(view(Relationship(isFriend = true, circleIds = setOf("family")), rules)!!.mood)
    }

    @Test fun `expiry travels with any visible status so caches can expire offline`() {
        assertEquals(presence.expiresAt, view(friend)!!.expiresAt)
        val nothingVisible = PrivacyRules(VisibilityCategory.entries.associateWith { AudienceRule(it, Audience.NOBODY) })
        val v = view(close, nothingVisible)!!
        assertFalse(v.hasStatus)
        assertNull(v.expiresAt)
    }

    @Test fun `defaults are conservative`() {
        val friendVisible = VisibilityCategory.entries.filter { PrivacyFilter.allows(it, PrivacyRules.DEFAULT, friend) }
        assertEquals(
            setOf(VisibilityCategory.AVATAR, VisibilityCategory.AVAILABILITY, VisibilityCategory.LAST_UPDATED),
            friendVisible.toSet(),
        )
        assertTrue(VisibilityCategory.entries.none { PrivacyFilter.allows(it, PrivacyRules.DEFAULT, Relationship(isFriend = false)) })
    }
}
