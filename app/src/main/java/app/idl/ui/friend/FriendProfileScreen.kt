package app.idl.ui.friend

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.idl.AppContainer
import app.idl.avatar.AvatarBadges
import app.idl.avatar.AvatarImage
import app.idl.data.repo.idlError
import app.idl.domain.AvatarConfig
import app.idl.domain.Expiry
import app.idl.domain.FriendStatus
import app.idl.domain.ReactionTemplate
import app.idl.ui.components.EmptyState
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.Loading
import app.idl.ui.components.SectionTitle
import app.idl.ui.components.rememberNow
import app.idl.widget.WidgetPinning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FriendViewModel(private val c: AppContainer, val userId: String) : ViewModel() {
    /** first = loaded; avoids flashing "Not connected" before the cache has emitted. */
    val card = c.friends.card(userId).map { true to it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false to null)
    val lastFromThem = c.reactions.all.map { list -> list.filter { it.senderId == userId }.maxByOrNull { it.createdAt } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val message = MutableStateFlow<String?>(null)

    init { viewModelScope.launch { c.presence.refreshFriend(userId) } }

    fun react(t: ReactionTemplate) = viewModelScope.launch {
        c.reactions.send(userId, t)
            .onSuccess { message.value = "Sent ${t.emoji} ${t.label}" }
            .onFailure { message.value = it.idlError.userMessage }
    }

    fun setClose(close: Boolean) = viewModelScope.launch {
        c.friends.setCloseFriend(userId, close).onFailure { message.value = it.idlError.userMessage }
    }

    fun remove(then: () -> Unit) = viewModelScope.launch {
        c.friends.remove(userId).onSuccess { then() }.onFailure { message.value = it.idlError.userMessage }
    }

    fun block(then: () -> Unit) = viewModelScope.launch {
        c.friends.block(userId).onSuccess { then() }.onFailure { message.value = it.idlError.userMessage }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FriendProfileScreen(
    c: AppContainer,
    userId: String,
    onBack: () -> Unit,
    vm: FriendViewModel = viewModel(key = "friend_$userId") { FriendViewModel(c, userId) },
) {
    val loadedCard by vm.card.collectAsState()
    val card = loadedCard.second
    val last by vm.lastFromThem.collectAsState()
    val message by vm.message.collectAsState()
    val now = rememberNow(c.clock)
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = {
        IdlTopBar(card?.friend?.displayName ?: "Friend", onBack = onBack) {
            if (card != null) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove friend") }, onClick = { menu = false; confirm = "remove" })
                    DropdownMenuItem(text = { Text("Block") }, onClick = { menu = false; confirm = "block" })
                }
            }
        }
    }) { pad ->
        val f = card
        if (!loadedCard.first) {
            Loading(Modifier.padding(pad))
            return@Scaffold
        }
        if (f == null || f.friend.status != FriendStatus.ACCEPTED) {
            EmptyState("🫥", "Not connected", "This person isn't sharing with you right now.", Modifier.padding(pad))
            return@Scaffold
        }
        val view = f.view?.expiredAt(now)
        val name = f.friend.displayName
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AvatarImage(
                view?.avatar ?: AvatarConfig(),
                contentDescription = "$name's iDL",
                size = 180.dp,
                badges = AvatarBadges(view?.availability, view?.activityType),
            )
            Text(name, style = MaterialTheme.typography.headlineSmall)
            Text("@${f.friend.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (view == null || !view.hasStatus) {
                        Text("No status right now", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        // Only fields the server returned are shown; absent fields were not shared.
                        view.mood?.let { Line("Mood", "${it.emoji} ${it.label}") }
                        view.availability?.let { Line("Availability", "${it.emoji} ${it.label}") }
                        view.intent?.let { Line("Intent", "${it.emoji} ${it.label}") }
                        view.activityType?.let { Line("Doing", "${it.emoji} ${view.activityLabel ?: it.label}") }
                        view.joinable?.takeIf { it }?.let { Line("Joinable", "Yes") }
                        view.note?.let { Text("“$it”", style = MaterialTheme.typography.bodyLarge) }
                        Expiry.remaining(view.expiresAt, now)?.let { Line("Expires", it) }
                    }
                    Expiry.ago(view?.updatedAt, now)?.let { Line("Updated", it) }
                    Expiry.ago(f.fetchedAt, now)?.let { Text("Synced $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            last?.takeIf { it.isActive(now) }?.let {
                Text("They sent you ${it.template.emoji} ${Expiry.ago(it.createdAt, now)}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }

            SectionTitle("Send a little something", Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ReactionTemplate.entries.forEach { t ->
                    SuggestionChip(
                        onClick = { vm.react(t) },
                        label = { Text("${t.emoji} ${t.label}") },
                        modifier = Modifier
                            .testTag("react:${t.name}")
                            .semantics { contentDescription = "Send ${t.label} to $name" },
                    )
                }
            }
            message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }

            Card(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                ListItem(
                    headlineContent = { Text("Close friend") },
                    supportingContent = { Text("Close friends can see more of your status (see Privacy).") },
                    trailingContent = { Switch(checked = f.friend.isCloseFriend, onCheckedChange = vm::setClose) },
                )
            }
            Row(Modifier.padding(vertical = 16.dp)) {
                OutlinedButton(
                    onClick = { if (!WidgetPinning.canPin(context) || !WidgetPinning.pinFriend(context, userId)) vm.message.value = "Long-press your home screen → Widgets → iDL friend" },
                ) { Text("Add $name to home screen") }
            }
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (action == "block") "Block ${card?.friend?.displayName}?" else "Remove ${card?.friend?.displayName}?") },
            text = {
                Text(
                    if (action == "block") "They immediately lose access to your iDL and can't send you reactions or requests."
                    else "You both stop seeing each other's iDL right away.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    if (action == "block") vm.block(onBack) else vm.remove(onBack)
                }) { Text(if (action == "block") "Block" else "Remove") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
