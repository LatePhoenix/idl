package app.idl.domain

import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.LegacyAvatarMigration
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable(with = Audience.Serializer::class)
enum class Audience(val label: String, val availableInAlpha: Boolean = true) {
    NOBODY("Nobody"),
    ONLY_ME("Only me"),
    FRIENDS("All friends"),
    CLOSE_FRIENDS("Close friends"),
    CIRCLES("Circles", availableInAlpha = false),
    INDIVIDUALS("Specific friends", availableInAlpha = false);

    object Serializer : WireEnumSerializer<Audience>("Audience", entries, ONLY_ME)
}

@Serializable(with = VisibilityCategory.Serializer::class)
enum class VisibilityCategory(val label: String, val defaultAudience: Audience) {
    AVATAR("Avatar appearance", Audience.FRIENDS),
    AVAILABILITY("Availability", Audience.FRIENDS),
    LAST_UPDATED("Last updated time", Audience.FRIENDS),
    MOOD("Mood & expression", Audience.CLOSE_FRIENDS),
    INTENT("Intent", Audience.CLOSE_FRIENDS),
    ACTIVITY_CATEGORY("Activity category", Audience.CLOSE_FRIENDS),
    STATUS_NOTE("Status note", Audience.CLOSE_FRIENDS),
    ACTIVITY_NAME("Activity name", Audience.ONLY_ME),
    JOINABLE("Joinable state", Audience.ONLY_ME),
    EXTERNAL_LINKS("External account links", Audience.ONLY_ME);

    object Serializer : WireEnumSerializer<VisibilityCategory>("VisibilityCategory", entries, EXTERNAL_LINKS)
}

@Serializable
data class AudienceRule(
    val category: VisibilityCategory,
    val audience: Audience,
    val ids: Set<String> = emptySet(),
)

@Serializable
data class PrivacyRules(val rules: Map<VisibilityCategory, AudienceRule> = emptyMap()) {
    fun ruleFor(category: VisibilityCategory): AudienceRule =
        rules[category] ?: AudienceRule(category, category.defaultAudience)

    fun with(rule: AudienceRule): PrivacyRules = copy(rules = rules + (rule.category to rule))

    companion object {
        val DEFAULT = PrivacyRules()
    }
}

/** The owner→viewer relationship, as known to the server. */
data class Relationship(
    val isFriend: Boolean,
    /** Owner has put the viewer on their close-friends list. */
    val isCloseFriend: Boolean = false,
    /** Either side has blocked the other. */
    val blocked: Boolean = false,
    /** Ids of the owner's circles that contain the viewer (v0.5). */
    val circleIds: Set<String> = emptySet(),
)

/**
 * Status visuals the viewer may draw on top of [PresenceView.identity].
 * [expressionId] is present only when mood is visible (F-19).
 */
@Serializable
data class PresenceVisual(
    val expressionId: String? = null,
    val propAssetId: String? = null,
    val headAccessoryAssetId: String? = null,
    val bodyAccessoryAssetId: String? = null,
    val sceneAssetId: String? = null,
) {
    val isEmpty: Boolean
        get() = expressionId == null && propAssetId == null && headAccessoryAssetId == null &&
            bodyAccessoryAssetId == null && sceneAssetId == null
}

/**
 * What one viewer is allowed to see of one owner. Absent fields are not visible.
 * The client composes the face from [identity] plus [visual] (D-24).
 */
@Serializable
data class PresenceView(
    val userId: String,
    val identity: AvatarConfiguration,
    val mood: Mood? = null,
    val availability: Availability? = null,
    val intent: StatusIntent? = null,
    val activityType: ActivityType? = null,
    val activityLabel: String? = null,
    val joinable: Boolean? = null,
    val joinUrl: String? = null,
    val note: String? = null,
    val visual: PresenceVisual? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
    /** Always present when any status field is, so caches can expire state offline. */
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
) {
    val hasStatus: Boolean
        get() = mood != null || availability != null || intent != null ||
            activityType != null || note != null

    fun isExpired(now: Instant): Boolean = expiresAt != null && !now.isBefore(expiresAt)

    /** The same view with any status that has expired by [now] removed. */
    fun expiredAt(now: Instant): PresenceView =
        if (isExpired(now)) PresenceView(userId, identity = identity) else this
}

/**
 * The reference privacy filter. The server must implement exactly this behaviour; the client
 * uses it only in the fake backend and for "preview as friend". Client UI never hides
 * fields on its own.
 */
object PrivacyFilter {

    fun allows(
        category: VisibilityCategory,
        rules: PrivacyRules,
        rel: Relationship,
        isSelf: Boolean = false,
    ): Boolean {
        if (isSelf) return true
        if (rel.blocked || !rel.isFriend) return false
        val rule = rules.ruleFor(category)
        return when (rule.audience) {
            Audience.NOBODY, Audience.ONLY_ME -> false
            Audience.FRIENDS -> true
            Audience.CLOSE_FRIENDS -> rel.isCloseFriend
            Audience.CIRCLES -> rule.ids.any { it in rel.circleIds }
            Audience.INDIVIDUALS -> false // resolved by viewerId overload
        }
    }

    private fun allows(
        category: VisibilityCategory,
        rules: PrivacyRules,
        rel: Relationship,
        viewerId: String,
    ): Boolean {
        val rule = rules.ruleFor(category)
        if (rule.audience == Audience.INDIVIDUALS) {
            return rel.isFriend && !rel.blocked && viewerId in rule.ids
        }
        return allows(category, rules, rel)
    }

    /**
     * Returns null when the viewer may see nothing at all (not friends, or blocked).
     */
    fun viewFor(
        viewerId: String,
        ownerId: String,
        identity: AvatarConfiguration,
        presence: ResolvedPresence,
        rules: PrivacyRules,
        rel: Relationship,
        ownerInvisible: Boolean,
    ): PresenceView? {
        if (viewerId == ownerId) {
            val visual = visualFor(presence, moodVisible = true)
            return PresenceView(
                userId = ownerId,
                identity = identity,
                mood = presence.mood,
                availability = presence.availability,
                intent = presence.intent,
                activityType = presence.activity?.type,
                activityLabel = presence.activity?.label,
                joinable = presence.activity?.joinable,
                joinUrl = presence.activity?.joinUrl,
                note = presence.note,
                visual = visual,
                updatedAt = presence.updatedAt,
                expiresAt = presence.expiresAt,
            )
        }
        if (!rel.isFriend || rel.blocked) return null

        fun can(c: VisibilityCategory) = allows(c, rules, rel, viewerId)
        val avatarVisible = can(VisibilityCategory.AVATAR)
        val shown = if (avatarVisible) identity else minimalIdentity(identity)

        // Invisible and "no status" must look identical to the viewer.
        if (ownerInvisible || presence.isEmpty) {
            return PresenceView(userId = ownerId, identity = shown)
        }

        val moodVisible = can(VisibilityCategory.MOOD)
        val activity = presence.activity
        val categoryVisible = activity != null && can(VisibilityCategory.ACTIVITY_CATEGORY)
        val joinVisible = categoryVisible && can(VisibilityCategory.JOINABLE)
        val visual = if (avatarVisible) visualFor(presence, moodVisible) else null

        val view = PresenceView(
            userId = ownerId,
            identity = shown,
            mood = presence.mood.takeIf { moodVisible },
            availability = presence.availability.takeIf { can(VisibilityCategory.AVAILABILITY) },
            intent = presence.intent.takeIf { can(VisibilityCategory.INTENT) },
            activityType = activity?.type.takeIf { categoryVisible },
            activityLabel = activity?.label.takeIf { categoryVisible && can(VisibilityCategory.ACTIVITY_NAME) },
            joinable = activity?.joinable.takeIf { joinVisible },
            joinUrl = activity?.joinUrl.takeIf { joinVisible },
            note = presence.note.takeIf { can(VisibilityCategory.STATUS_NOTE) },
            visual = visual,
            updatedAt = presence.updatedAt.takeIf { can(VisibilityCategory.LAST_UPDATED) },
        )
        return if (view.hasStatus || view.visual != null) view.copy(expiresAt = presence.expiresAt) else view
    }

    private fun minimalIdentity(identity: AvatarConfiguration) = AvatarConfiguration(
        baseAssetId = "base_teardrop",
        paletteAssetId = identity.paletteAssetId.ifBlank { "palette_sunny" },
        packId = identity.packId.ifBlank { AvatarConfiguration.DEFAULT_PACK_ID },
        packVersion = identity.packVersion,
        familyId = "teardrop_face",
        restingExpressionId = "neutral",
    )

    private fun visualFor(presence: ResolvedPresence, moodVisible: Boolean): PresenceVisual? {
        val visual = presence.visual
        val expressionId = if (moodVisible) {
            (visual.expression ?: AvatarComposer.expressionFor(presence.mood, presence.availability))?.wire
        } else {
            null
        }
        val mapped = PresenceVisual(
            expressionId = expressionId,
            propAssetId = visual.props.firstOrNull()?.let(LegacyAvatarMigration::propAssetId),
            headAccessoryAssetId = visual.headAccessory?.let(LegacyAvatarMigration::headAssetId),
            bodyAccessoryAssetId = visual.bodyAccessory?.let(LegacyAvatarMigration::bodyAssetId),
            sceneAssetId = visual.scene?.let(LegacyAvatarMigration::sceneAssetId),
        )
        return mapped.takeUnless { it.isEmpty }
    }
}
