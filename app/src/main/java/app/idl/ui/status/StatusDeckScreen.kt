package app.idl.ui.status

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.idl.domain.ActivityType
import app.idl.domain.Availability
import app.idl.domain.AvatarComposer
import app.idl.domain.AvatarConfig
import app.idl.domain.ChargeState
import app.idl.domain.Expiry
import app.idl.domain.Mood
import app.idl.domain.PresenceResolver
import app.idl.domain.PresenceSource
import app.idl.domain.Prop
import app.idl.domain.QuickState
import app.idl.domain.StatusDraft
import app.idl.domain.StatusIntent
import app.idl.ui.components.ChoiceChips
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.SectionTitle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StatusDeckViewModel(private val c: AppContainer) : ViewModel() {
    val draft = MutableStateFlow(StatusDraft())
    val message = MutableStateFlow<String?>(null)
    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val done = _done.asSharedFlow()

    val invisible = c.session.me.map { it?.invisible == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val baseAvatar = c.session.me.flatMapLatest { me -> me?.let { c.avatars.avatar(it.userId) } ?: flowOf(null) }
        .map { it ?: AvatarConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AvatarConfig())
    val charge = c.economy.state

    init {
        viewModelScope.launch { c.economy.evaluate() }
        viewModelScope.launch {
            val manual = c.presence.ownStates.first().firstOrNull { it.source == PresenceSource.MANUAL }
            draft.value = StatusDraft.from(manual, c.clock.now())
        }
    }

    fun update(d: StatusDraft) { draft.value = d }

    fun applyQuick(q: QuickState) {
        draft.value = StatusDraft.from(q).copy(note = draft.value.note)
        share()
    }

    fun share() = viewModelScope.launch {
        val result = c.presence.setStatus(draft.value.toState(c.clock.now()))
        // A local save always succeeds; a failed publish is retried in the background.
        message.value = result.exceptionOrNull()?.let { it.idlError.userMessage }
        _done.tryEmit(Unit)
    }

    fun clear() = viewModelScope.launch {
        c.presence.clearStatus()
        draft.value = StatusDraft()
        _done.tryEmit(Unit)
    }

    fun setInvisible(on: Boolean) = viewModelScope.launch {
        c.session.setInvisible(on).onFailure { message.value = "Couldn't change Invisible: ${it.idlError.userMessage}" }
    }
}

@Composable
fun StatusDeckScreen(c: AppContainer, onDone: () -> Unit, vm: StatusDeckViewModel = viewModel { StatusDeckViewModel(c) }) {
    val draft by vm.draft.collectAsState()
    val invisible by vm.invisible.collectAsState()
    val charge by vm.charge.collectAsState()
    val base by vm.baseAvatar.collectAsState()
    val message by vm.message.collectAsState()
    LaunchedEffect(vm) { vm.done.collect { onDone() } }
    Scaffold(topBar = { IdlTopBar("Status Deck", onBack = onDone) }) { pad ->
        StatusDeckContent(
            draft = draft,
            baseAvatar = base,
            invisible = invisible,
            message = message,
            onDraftChange = vm::update,
            onQuick = vm::applyQuick,
            onShare = { vm.share() },
            onClear = { vm.clear() },
            onInvisible = { vm.setInvisible(it) },
            modifier = Modifier.padding(pad),
            charge = charge,
        )
    }
}

/** Stateless Status Deck, used by the screen and by UI tests. */
@Composable
fun StatusDeckContent(
    draft: StatusDraft,
    baseAvatar: AvatarConfig,
    invisible: Boolean,
    message: String?,
    onDraftChange: (StatusDraft) -> Unit,
    onQuick: (QuickState) -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
    onInvisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    charge: ChargeState = ChargeState.EMPTY,
    now: java.time.Instant = java.time.Instant.now(),
) {
    val previewPresence = PresenceResolver.resolve(listOf(draft.toState(now)), now)
    val preview = AvatarComposer.compose(baseAvatar, previewPresence)
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            AvatarImage(
                preview,
                contentDescription = "Preview of your iDL",
                size = 140.dp,
                badges = AvatarBadges(previewPresence.availability, previewPresence.activity?.type),
                modifier = Modifier.testTag("preview"),
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
        ChargeBanner(charge)

        SectionTitle("Quick states")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickState.ALL.forEach { q ->
                ElevatedCard(
                    onClick = { onQuick(q) },
                    modifier = Modifier
                        .testTag("quick:${q.id}")
                        .semantics { contentDescription = "Set ${q.label} for ${Expiry.label(q.duration)}" },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(q.emoji, style = MaterialTheme.typography.headlineSmall)
                        Text(q.label, style = MaterialTheme.typography.labelLarge)
                        Text(Expiry.label(q.duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        SectionTitle("Mood")
        ChoiceChips(Mood.entries, draft.mood, { "${it.emoji} ${it.label}" }, { onDraftChange(draft.copy(mood = it)) }, testTagPrefix = "mood")
        SectionTitle("Availability")
        ChoiceChips(Availability.entries, draft.availability, { "${it.emoji} ${it.label}" }, { onDraftChange(draft.copy(availability = it)) }, testTagPrefix = "availability")
        SectionTitle("Intent")
        ChoiceChips(StatusIntent.entries.drop(1), draft.intent, { "${it.emoji} ${it.label}" }, { onDraftChange(draft.copy(intent = it)) }, testTagPrefix = "intent")
        SectionTitle("Activity")
        ChoiceChips(ActivityType.entries.drop(1), draft.activity, { "${it.emoji} ${it.label}" }, { onDraftChange(draft.copy(activity = it)) }, testTagPrefix = "activity")
        SectionTitle("Holding")
        ChoiceChips(Prop.entries.drop(1), draft.prop, { "${it.emoji} ${it.label}" }, { onDraftChange(draft.copy(prop = it)) }, testTagPrefix = "prop")

        SectionTitle("Note (optional)")
        OutlinedTextField(
            value = draft.note,
            onValueChange = { onDraftChange(draft.copy(note = it.take(StatusDraft.NOTE_MAX))) },
            placeholder = { Text("brb, snack run") },
            supportingText = { Text("${draft.note.length}/${StatusDraft.NOTE_MAX}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("note"),
        )

        SectionTitle("Expires in")
        ChoiceChips(Expiry.PRESETS, draft.duration, { Expiry.label(it) }, { it?.let { d -> onDraftChange(draft.copy(duration = d)) } }, allowNone = false, testTagPrefix = "duration")

        Spacer(Modifier.height(16.dp))
        Button(onClick = onShare, enabled = !draft.isEmpty, modifier = Modifier.fillMaxWidth().testTag("share")) {
            Text("Share for ${Expiry.label(draft.duration)}")
        }
        TextButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("Clear my status") }

        Card(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
            ListItem(
                headlineContent = { Text("Invisible") },
                supportingContent = { Text("Friends see your avatar with no status. You still see yours.") },
                trailingContent = { Switch(checked = invisible, onCheckedChange = onInvisible, modifier = Modifier.testTag("invisible")) },
            )
        }
    }
}

@Composable
private fun ChargeBanner(state: ChargeState) {
    val tethers = if (state.activeTethers == 1) "1 mutual tether" else "${state.activeTethers} mutual tethers"
    Card(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("charge")
            .semantics { contentDescription = "${state.currentCharge} Charge, plus ${state.hourlyRate} per hour" },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Charge", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${state.currentCharge}", style = MaterialTheme.typography.headlineSmall)
            Text(
                "+${state.hourlyRate} Charge/hr · $tethers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
