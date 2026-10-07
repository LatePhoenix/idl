package app.idl.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.idl.AppContainer
import app.idl.avatar.AvatarBadges
import app.idl.avatar.AvatarImage
import app.idl.data.repo.FriendCard
import app.idl.domain.AvatarComposer
import app.idl.domain.AvatarConfig
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.Expiry
import app.idl.domain.FriendStatus
import app.idl.domain.Me
import app.idl.domain.PresenceResolver
import app.idl.domain.PresenceState
import app.idl.domain.Reaction
import app.idl.ui.components.EmptyState
import app.idl.ui.components.OfflineBanner
import app.idl.ui.components.SectionTitle
import app.idl.ui.components.rememberNow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class HomeData(
    val me: Me? = null,
    val avatar: AvatarConfig = AvatarConfig(),
    val ownStates: List<PresenceState> = emptyList(),
    val pendingSync: Boolean = false,
    val cards: List<FriendCard> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val lastSyncError: String? = null,
    val loaded: Boolean = false,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val avatar = c.session.me.flatMapLatest { me -> me?.let { c.avatars.avatar(it.userId) } ?: flowOf(null) }

    val data = combine(
        combine(c.session.me, avatar, c.presence.ownStates, c.presence.pendingSync) { me, a, s, p -> listOf(me, a, s, p) },
        c.friends.cards,
        c.reactions.all,
        c.sync.lastSync,
    ) { head, cards, reactions, sync ->
        @Suppress("UNCHECKED_CAST")
        HomeData(
            me = head[0] as Me?,
            avatar = head[1] as AvatarConfig? ?: AvatarConfig(),
            ownStates = head[2] as List<PresenceState>,
            pendingSync = head[3] as Boolean,
            cards = cards,
            reactions = reactions,
            lastSyncError = sync?.lastError,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeData())

    fun refresh() = viewModelScope.launch { c.sync.reconcile() }
    fun dismiss(id: String) = viewModelScope.launch { c.reactions.dismiss(id) }
}

@Composable
fun HomeScreen(
    c: AppContainer,
    onOpenStatus: () -> Unit,
    onOpenFriend: (String) -> Unit,
    onAddFriend: () -> Unit,
    onPrivacy: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
    vm: HomeViewModel = viewModel { HomeViewModel(c) },
) {
    val d by vm.data.collectAsState()
    val now = rememberNow(c.clock)
    val resolved = PresenceResolver.resolve(d.ownStates, now)
    val myAvatar = AvatarComposer.compose(d.avatar, resolved)
    val myId = d.me?.userId
    val accepted = d.cards.filter { it.friend.status == FriendStatus.ACCEPTED }
    val incoming = d.cards.filter { it.friend.status == FriendStatus.INCOMING }
    val outgoing = d.cards.filter { it.friend.status == FriendStatus.OUTGOING }
    val inbox = d.reactions.filter { it.recipientId == myId && it.isActive(now) }
    val names = d.cards.associate { it.friend.userId to it.friend.displayName }

    Scaffold(
        topBar = {
            app.idl.ui.components.IdlTopBar("iDL") {
                IconButton(onClick = onWidgets) { Icon(Icons.Outlined.Widgets, contentDescription = "Widgets") }
                IconButton(onClick = onPrivacy) { Icon(Icons.Outlined.Shield, contentDescription = "Privacy center") }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddFriend, icon = { Icon(Icons.Outlined.PersonAdd, null) }, text = { Text("Add friend") })
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding(), bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OfflineBanner(
                        when {
                            d.lastSyncError?.contains("Offline") == true -> "Offline — showing last-known state."
                            d.pendingSync -> "Your status will sync when you're back online."
                            else -> null
                        },
                    )
                    MyCard(
                        name = d.me?.displayName ?: "You",
                        avatar = myAvatar,
                        badges = AvatarBadges(resolved.availability, resolved.activity?.type),
                        summary = if (resolved.isEmpty) "No status — tap to set your vibe" else listOfNotNull(
                            resolved.mood?.let { "${it.emoji} ${it.label}" },
                            resolved.availability?.label,
                            resolved.intent?.takeIf { it.emoji.isNotEmpty() }?.label,
                        ).joinToString(" · "),
                        note = resolved.note,
                        expiry = Expiry.remaining(resolved.expiresAt, now),
                        invisible = d.me?.invisible == true,
                        onClick = onOpenStatus,
                    )
                }
            }

            if (inbox.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("For you") }
                items(inbox, key = { "r_" + it.id }, span = { GridItemSpan(maxLineSpan) }) { r ->
                    ReactionRow(r, names[r.senderId] ?: "A friend", now, onOpen = { onOpenFriend(r.senderId) }, onDismiss = { vm.dismiss(r.id) })
                }
            }

            if (incoming.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(Modifier.fillMaxWidth().clickable(onClick = onAddFriend)) {
                        Text(
                            "${incoming.size} friend request${if (incoming.size > 1) "s" else ""} waiting",
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Your people") }
            if (!d.loaded) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("Loading…", Modifier.padding(16.dp)) }
            } else if (accepted.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState("🫶", "No friends yet", "iDL is for a handful of close friends. Share your invite link to get started.") {
                        Button(onClick = onAddFriend) { Text("Invite a friend") }
                    }
                }
            }
            items(accepted, key = { it.friend.userId }) { card -> FriendTile(card, now) { onOpenFriend(card.friend.userId) } }
            items(outgoing, key = { "o_" + it.friend.userId }) { card ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                    Text("⏳", style = MaterialTheme.typography.headlineMedium)
                    Text(card.friend.displayName, style = MaterialTheme.typography.labelLarge)
                    Text("Request sent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.padding(top = 8.dp)) { Text("Refresh") }
            }
        }
    }
}

@Composable
private fun MyCard(
    name: String,
    avatar: AvatarConfig,
    badges: AvatarBadges,
    summary: String,
    note: String?,
    expiry: String?,
    invisible: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AvatarImage(avatar, "Your iDL: $summary", size = 104.dp, badges = badges)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge)
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                note?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (invisible) AssistChip(onClick = onClick, label = { Text("👻 Invisible") })
                    expiry?.let { AssistChip(onClick = onClick, label = { Text("⏱ $it") }) }
                }
                Text("Tap to change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FriendTile(card: FriendCard, now: Instant, onClick: () -> Unit) {
    val view = card.view?.expiredAt(now)
    val status = when {
        view == null -> "Syncing…"
        !view.hasStatus -> "No status"
        else -> listOfNotNull(view.availability?.label, view.mood?.label).joinToString(" · ").ifEmpty { "Status set" }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "${card.friend.displayName}, $status" },
    ) {
        AvatarImage(
            view?.identity ?: AvatarConfiguration(baseAssetId = "base_teardrop", paletteAssetId = "palette_sunny"),
            contentDescription = "",
            size = 88.dp,
            badges = AvatarBadges(view?.availability, view?.activityType),
        )
        Spacer(Modifier.height(4.dp))
        Text(card.friend.displayName, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReactionRow(r: Reaction, sender: String, now: Instant, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.template.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$sender: ${r.template.label}", style = MaterialTheme.typography.bodyLarge)
                Expiry.ago(r.createdAt, now)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "Dismiss reaction from $sender") }
        }
    }
}
