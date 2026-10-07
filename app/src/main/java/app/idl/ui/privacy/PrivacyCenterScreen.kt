package app.idl.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.idl.AppContainer
import app.idl.avatar.AvatarBadges
import app.idl.avatar.AvatarImage
import app.idl.data.repo.idlError
import app.idl.domain.Audience
import app.idl.domain.AudienceRule
import app.idl.domain.AvatarConfig
import app.idl.domain.Friend
import app.idl.domain.FriendStatus
import app.idl.domain.Me
import app.idl.domain.PresenceResolver
import app.idl.domain.PresenceState
import app.idl.domain.PrivacyFilter
import app.idl.domain.PrivacyRules
import app.idl.domain.Relationship
import app.idl.domain.VisibilityCategory
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.avatar.LegacyAvatarMigration
import app.idl.ui.components.ChoiceChips
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.SectionTitle
import app.idl.ui.components.rememberNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PrivacyViewModel(private val c: AppContainer) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val rules = c.privacy.rules.state(PrivacyRules.DEFAULT)
    val me = c.session.me.state(null as Me?)
    val friends = c.friends.friends.state(emptyList<Friend>())
    val ownStates = c.presence.ownStates.state(emptyList<PresenceState>())
    val avatar = c.session.me.flatMapLatest { m -> m?.let { c.avatars.avatar(it.userId) } ?: flowOf(null) }.state(null as AvatarConfig?)
    val blocked = MutableStateFlow<List<Friend>>(emptyList())
    val message = MutableStateFlow<String?>(null)

    fun loadBlocked() = viewModelScope.launch { c.friends.blocked().onSuccess { blocked.value = it } }

    fun setRule(category: VisibilityCategory, audience: Audience) = viewModelScope.launch {
        c.privacy.setRule(rules.value, AudienceRule(category, audience))
            .onSuccess { message.value = null }
            .onFailure { message.value = "Not saved: ${it.idlError.userMessage}" }
    }

    fun setInvisible(on: Boolean) = viewModelScope.launch {
        c.session.setInvisible(on).onFailure { message.value = "Not changed: ${it.idlError.userMessage}" }
    }

    fun unblock(id: String) = viewModelScope.launch { c.friends.unblock(id); loadBlocked() }
}

@Composable
fun PrivacyCenterScreen(c: AppContainer, onBack: () -> Unit, vm: PrivacyViewModel = viewModel { PrivacyViewModel(c) }) {
    val rules by vm.rules.collectAsState()
    val me by vm.me.collectAsState()
    val friends by vm.friends.collectAsState()
    val states by vm.ownStates.collectAsState()
    val avatar by vm.avatar.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val message by vm.message.collectAsState()
    val now = rememberNow(c.clock)
    LaunchedEffect(Unit) { vm.loadBlocked() }
    val accepted = friends.filter { it.status == FriendStatus.ACCEPTED }
    var previewId by remember { mutableStateOf<String?>(null) }
    val previewFriend = accepted.firstOrNull { it.userId == previewId } ?: accepted.firstOrNull()

    Scaffold(topBar = { IdlTopBar("Privacy", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Invisible mode") },
                    supportingContent = { Text("Friends see only your resting avatar — the same as having no status. Stays on until you turn it off.") },
                    trailingContent = { Switch(checked = me?.invisible == true, onCheckedChange = vm::setInvisible, modifier = Modifier.testTag("privacyInvisible")) },
                )
            }
            message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error) }

            SectionTitle("Who can see what")
            Text(
                "Enforced by the iDL server: friends' apps never receive what they aren't allowed to see.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            VisibilityCategory.entries.forEach { cat ->
                AudienceRow(cat, rules.ruleFor(cat).audience) { vm.setRule(cat, it) }
            }

            if (previewFriend != null) {
                SectionTitle("Preview as a friend")
                ChoiceChips(accepted, previewFriend, { it.displayName + if (it.isCloseFriend) " ★" else "" }, { previewId = it?.userId }, allowNone = false)
                val view = PrivacyFilter.viewFor(
                    viewerId = previewFriend.userId,
                    ownerId = me?.userId ?: "",
                    identity = LegacyAvatarMigration.migrate(avatar ?: AvatarConfig(), c.assetRegistry),
                    presence = PresenceResolver.resolve(states, now),
                    rules = rules,
                    rel = Relationship(isFriend = true, isCloseFriend = previewFriend.isCloseFriend),
                    ownerInvisible = me?.invisible == true,
                )
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AvatarImage(view?.identity ?: AvatarConfiguration(baseAssetId = "base_teardrop", paletteAssetId = "palette_sunny"), "What ${previewFriend.displayName} sees", size = 88.dp, badges = AvatarBadges(view?.availability, view?.activityType))
                        Spacer(Modifier.width(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${previewFriend.displayName} sees:", style = MaterialTheme.typography.titleSmall)
                            val lines = listOfNotNull(
                                view?.mood?.let { "Mood: ${it.label}" },
                                view?.availability?.let { "Availability: ${it.label}" },
                                view?.intent?.let { "Intent: ${it.label}" },
                                view?.activityType?.let { "Activity: ${view.activityLabel ?: it.label}" },
                                view?.note?.let { "Note: “$it”" },
                                view?.updatedAt?.let { "Last updated time" },
                            )
                            if (lines.isEmpty()) Text("Avatar only, no status", style = MaterialTheme.typography.bodySmall)
                            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }

            SectionTitle("Blocked")
            if (blocked.isEmpty()) Text("Nobody is blocked.", style = MaterialTheme.typography.bodySmall)
            blocked.forEach { b ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(b.displayName, Modifier.weight(1f))
                    TextButton(onClick = { vm.unblock(b.userId) }) { Text("Unblock") }
                }
            }
            Text(
                "Exact location is never collected. Avatars are drawn on-device; there are no public image links.",
                Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AudienceRow(category: VisibilityCategory, audience: Audience, onChange: (Audience) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Column {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.testTag("audience:${category.name}")) { Text(audience.label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Audience.entries.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(if (a.availableInAlpha) a.label else "${a.label} (coming soon)") },
                        enabled = a.availableInAlpha,
                        onClick = { open = false; onChange(a) },
                    )
                }
            }
        }
    }
}
