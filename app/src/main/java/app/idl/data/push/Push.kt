package app.idl.data.push

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Push payloads carry ids only; details are fetched (and privacy-filtered) afterwards. */
sealed interface PushEvent {
    data class PresenceChanged(val userId: String) : PushEvent
    data class ReactionReceived(val reactionId: String, val fromUserId: String) : PushEvent
    data class FriendRequest(val userId: String) : PushEvent
    data class FriendAccepted(val userId: String) : PushEvent
    data class FriendRemoved(val userId: String) : PushEvent
}

/** Abstraction over FCM. The alpha uses [MockPushSource]; FCM forwards into the same handler. */
interface PushEventSource {
    val events: Flow<PushEvent>
}

class MockPushSource : PushEventSource {
    private val flow = MutableSharedFlow<PushEvent>(extraBufferCapacity = 64)
    override val events: Flow<PushEvent> = flow.asSharedFlow()

    fun emit(event: PushEvent) {
        flow.tryEmit(event)
    }
}
