package app.idl.data.push

import app.idl.IdlLog
import app.idl.avatar.RenderCache
import app.idl.data.local.AppSettings
import app.idl.data.local.IdlDao
import app.idl.data.repo.FriendsRepository
import app.idl.data.repo.purgeCachedUser
import app.idl.data.repo.PresenceRepository
import app.idl.data.repo.ReactionRepository
import app.idl.data.repo.WidgetRefresher
import app.idl.notify.Notifier

/**
 * Turns push events (FCM or mock) into cache refreshes, widget updates and notifications.
 * Payloads carry ids only, so every detail shown comes from a privacy-filtered fetch.
 */
class PushHandler(
    private val dao: IdlDao,
    private val presence: PresenceRepository,
    private val friends: FriendsRepository,
    private val reactions: ReactionRepository,
    private val widgets: WidgetRefresher,
    private val settings: AppSettings,
    private val notifier: Notifier,
    private val renders: RenderCache,
) {
    suspend fun handle(event: PushEvent) {
        IdlLog.i("push.received", "type" to event::class.simpleName)
        val prefs = settings.notificationsNow()
        when (event) {
            is PushEvent.PresenceChanged -> {
                presence.refreshFriend(event.userId)
                if (prefs.presence) {
                    val name = nameOf(event.userId) ?: return
                    notifier.post(Notifier.Channel.PRESENCE, event.userId.hashCode(), name, "$name updated their iDL", "idl://friend/${event.userId}")
                }
            }
            is PushEvent.ReactionReceived -> {
                reactions.refreshInbox()
                if (prefs.reactions) {
                    val name = nameOf(event.fromUserId) ?: "A friend"
                    val template = reactions.template(event.reactionId)
                    val text = template?.let { "${it.emoji} ${it.label}" } ?: "sent you a reaction"
                    notifier.post(Notifier.Channel.REACTIONS, event.reactionId.hashCode(), name, text, "idl://friend/${event.fromUserId}")
                }
            }
            is PushEvent.FriendRequest -> {
                friends.refresh()
                if (prefs.friendRequests) {
                    val name = nameOf(event.userId) ?: "Someone"
                    notifier.post(Notifier.Channel.FRIEND_REQUESTS, event.userId.hashCode(), "Friend request", "$name wants to add you", "idl://add")
                }
            }
            is PushEvent.FriendAccepted -> {
                friends.refresh()
                presence.refreshFriend(event.userId)
                if (prefs.friendRequests) {
                    val name = nameOf(event.userId) ?: "Your friend"
                    notifier.post(Notifier.Channel.FRIEND_REQUESTS, event.userId.hashCode(), "New friend", "$name accepted your invite", "idl://friend/${event.userId}")
                }
            }
            is PushEvent.FriendRemoved -> {
                purgeCachedUser(dao, renders, event.userId)
                widgets.friendsChanged(listOf(event.userId))
            }
        }
    }

    private suspend fun nameOf(userId: String) = dao.friendNow(userId)?.displayName
}
