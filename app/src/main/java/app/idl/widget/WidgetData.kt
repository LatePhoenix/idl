package app.idl.widget

import app.idl.IdlClock
import app.idl.data.local.IdlDao
import app.idl.data.repo.decodeAvatar
import app.idl.data.repo.decodeState
import app.idl.data.repo.decodeView
import app.idl.domain.Availability
import app.idl.domain.AvatarComposer
import app.idl.domain.AvatarConfig
import app.idl.domain.ActivityType
import app.idl.domain.Expiry
import app.idl.domain.FriendStatus
import app.idl.domain.Mood
import app.idl.domain.PresenceResolver
import java.time.Duration
import java.time.Instant

/** Everything a widget needs, resolved at render time (expiry evaluated against now). */
data class WidgetModel(
    val title: String,
    val avatar: AvatarConfig?,
    val availability: Availability? = null,
    val activity: ActivityType? = null,
    val mood: Mood? = null,
    val note: String? = null,
    /** "3h left" / "no status" / "Not connected" etc. */
    val subtitle: String? = null,
    /** Honest staleness hint, e.g. "updated 3h ago". */
    val staleHint: String? = null,
    val deepLink: String,
    val invisible: Boolean = false,
) {
    val contentDescription: String
        get() = buildString {
            append(title)
            mood?.let { append(", feeling ").append(it.label.lowercase()) }
            availability?.let { append(", ").append(it.label.lowercase()) }
            activity?.takeIf { it != ActivityType.NONE }?.let { append(", ").append(it.label.lowercase()) }
            if (invisible) append(", invisible")
        }
}

object WidgetData {
    /** Cache older than this is flagged as stale *if* the last sync attempt failed. */
    val STALE_AFTER: Duration = Duration.ofHours(2)

    suspend fun self(dao: IdlDao, clock: IdlClock): WidgetModel {
        val session = dao.sessionNow() ?: return WidgetModel("iDL", null, subtitle = "Tap to set up", deepLink = "idl://home")
        val now = clock.now()
        val base = dao.avatarNow(session.userId)?.json?.let(::decodeAvatar) ?: AvatarConfig()
        val resolved = PresenceResolver.resolve(dao.ownPresenceNow().map { decodeState(it.json) }, now)
        return WidgetModel(
            title = "You",
            avatar = AvatarComposer.compose(base, resolved),
            availability = resolved.availability,
            activity = resolved.activity?.type,
            mood = resolved.mood,
            note = resolved.note,
            subtitle = when {
                session.invisible -> "Invisible"
                resolved.isEmpty -> "Tap to set your vibe"
                else -> Expiry.remaining(resolved.expiresAt, now)
            },
            deepLink = "idl://status",
            invisible = session.invisible,
        )
    }

    suspend fun friend(dao: IdlDao, clock: IdlClock, appWidgetId: Int): WidgetModel {
        val sub = dao.widgetSubscription(appWidgetId)
        val friendId = sub?.friendUserId
            ?: return WidgetModel("Choose a friend", null, subtitle = "Tap to pick", deepLink = "idl://widgets")
        val friend = dao.friendNow(friendId)
        if (friend == null || FriendStatus.Serializer.fromWire(friend.status) != FriendStatus.ACCEPTED) {
            return WidgetModel("Not connected", null, subtitle = "This friend is no longer shared", deepLink = "idl://home")
        }
        val row = dao.friendPresenceNow(friendId)
        val now = clock.now()
        val view = row?.json?.let(::decodeView)?.expiredAt(now)
        val fetchedAt = row?.let { Instant.ofEpochMilli(it.fetchedAtMs) }
        val sync = dao.syncStateNow()
        val lastFailed = sync?.lastError != null
        val stale = fetchedAt != null && lastFailed && Duration.between(fetchedAt, now) > STALE_AFTER
        return WidgetModel(
            title = friend.displayName,
            avatar = view?.avatar,
            availability = view?.availability,
            activity = view?.activityType,
            mood = view?.mood,
            note = view?.note,
            subtitle = when {
                view == null -> "Waiting for first sync"
                !view.hasStatus -> "No status"
                else -> listOfNotNull(view.availability?.label, view.mood?.label).joinToString(" · ").ifEmpty { "Status set" }
            },
            staleHint = if (stale) "updated ${Expiry.ago(fetchedAt, now)}" else null,
            deepLink = "idl://friend/$friendId",
        )
    }

    const val KIND_SOLO = "solo"
}
