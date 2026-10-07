package app.idl.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.idl.AppContainer
import app.idl.BuildConfig
import app.idl.FeatureFlags
import app.idl.data.local.AppSettings
import app.idl.domain.QuickState
import app.idl.domain.avatar.WallpaperContrastPreference
import app.idl.domain.ReactionTemplate
import app.idl.ui.components.ChoiceChips
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.SectionTitle
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    c: AppContainer,
    onBack: () -> Unit,
    onAvatarStudio: () -> Unit,
    onSignedOut: () -> Unit,
    onVectorSlice: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val notif by c.settings.notifications.collectAsState(initial = AppSettings.Notifications(true, false, true))
    val wallpaper by c.settings.wallpaperContrast.collectAsState(initial = WallpaperContrastPreference.AUTO)
    val offline by c.settings.simulateOffline.collectAsState(initial = false)
    val unlockAll by c.settings.unlockAllItems.collectAsState(initial = false)
    val sync by c.sync.lastSync.collectAsState(initial = null)
    var target by remember { mutableStateOf<String?>(null) }
    var log by remember { mutableStateOf<String?>(null) }
    val fake = c.fakeBackend
    val friendIds = remember(sync) { fake?.demoFriendIds().orEmpty() }
    val chosen = target ?: friendIds.firstOrNull()
    var remotePinned by remember(chosen) { mutableStateOf(false) }
    var localPinned by remember(chosen) { mutableStateOf(false) }
    LaunchedEffect(chosen) {
        val tether = chosen?.let { c.economy.tether(it) }
        remotePinned = tether?.hasRemoteWidgetInstalled == true
        localPinned = tether?.hasLocalWidgetInstalled == true
    }

    Scaffold(topBar = { IdlTopBar("Settings", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedButton(onClick = onAvatarStudio, modifier = Modifier.fillMaxWidth()) { Text("Edit avatar") }
            if (BuildConfig.DEBUG) {
                OutlinedButton(
                    onClick = onVectorSlice,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("vectorSlice"),
                ) { Text("Vector slice") }
            }

            SectionTitle("Widgets")
            Text(
                "Outline contrast on the home screen. Auto follows a light or dark wallpaper.",
                style = MaterialTheme.typography.bodySmall,
            )
            ChoiceChips(
                WallpaperContrastPreference.entries,
                wallpaper,
                { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
                { choice ->
                    if (choice != null) scope.launch {
                        c.settings.setWallpaperContrast(choice)
                        c.widgets.all()
                    }
                },
                allowNone = false,
                testTagPrefix = "wallpaperContrast",
            )

            SectionTitle("Notifications")
            Toggle("Reactions", "When a friend sends you something", notif.reactions) { scope.launch { c.settings.setNotifyReactions(it) } }
            Toggle("Friend requests", "New requests and accepted invites", notif.friendRequests) { scope.launch { c.settings.setNotifyRequests(it) } }
            Toggle("Status updates", "Off by default — widgets already show this quietly", notif.presence) { scope.launch { c.settings.setNotifyPresence(it) } }

            SectionTitle("Integrations")
            Text(
                "Optional extras for later. iDL works fully without them, and each needs your explicit permission.",
                style = MaterialTheme.typography.bodySmall,
            )
            listOf(
                "Discord" to FeatureFlags.INTEGRATION_DISCORD,
                "Steam" to FeatureFlags.INTEGRATION_STEAM,
                "Xbox" to FeatureFlags.INTEGRATION_XBOX,
                "VRChat via VRCQ" to FeatureFlags.INTEGRATION_VRCQ,
                "Desktop bridge" to FeatureFlags.INTEGRATION_DESKTOP_BRIDGE,
            ).forEach { (name, enabled) ->
                ListItem(headlineContent = { Text(name) }, supportingContent = { Text(if (enabled) "Available" else "Not available in this alpha") })
            }

            SectionTitle("Account")
            Text(
                if (c.isRemote) "Connected to the iDL server." else "Demo mode: a local server with sample friends.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (c.isRemote) {
                OutlinedButton(onClick = { scope.launch { c.session.signOut(); onSignedOut() } }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Sign out")
                }
            }

            if (BuildConfig.DEBUG && fake != null) {
                SectionTitle("Alpha / debug")
                Toggle("Simulate offline", "Every backend call fails; the app runs from cache", offline, Modifier.testTag("simulateOffline")) {
                    scope.launch { c.settings.setSimulateOffline(it) }
                }
                Toggle(
                    "Unlock all items",
                    "Own every premium catalog item (debug). Saving still hits the server free-tier check until C.3.",
                    unlockAll,
                    Modifier.testTag("unlockAllItems"),
                ) {
                    scope.launch { c.settings.setUnlockAllItems(it) }
                }
                Text("Demo friend", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                ChoiceChips(friendIds, chosen, { fake.displayNameOf(it) }, { target = it }, allowNone = false)
                Toggle(
                    "Their widget is on my home screen",
                    "Demo stand-in for pinning their widget here",
                    localPinned,
                    Modifier.testTag("tetherLocal"),
                ) { on ->
                    localPinned = on
                    val id = chosen ?: return@Toggle
                    scope.launch { localPinned = c.economy.setLocalWidgetInstalled(id, on) }
                }
                Toggle(
                    "Friend has your widget installed",
                    "Their device has you pinned. Both switches make a mutual tether.",
                    remotePinned,
                    Modifier.testTag("tetherRemote"),
                ) { on ->
                    remotePinned = on
                    val id = chosen ?: return@Toggle
                    scope.launch { c.economy.setRemoteWidgetInstalled(id, on) }
                }
                OutlinedButton(
                    onClick = { scope.launch { c.economy.debugAccrueHours(1); log = "Accrued 1 hour of Charge" } },
                    modifier = Modifier.padding(top = 8.dp).testTag("accrueHour"),
                ) { Text("Accrue 1 hour") }
                Text("Simulate a status change (mock push)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickState.ALL.forEach { q ->
                        OutlinedButton(onClick = {
                            chosen ?: return@OutlinedButton
                            scope.launch { fake.simulatePresence(chosen, q); log = "${fake.displayNameOf(chosen)} → ${q.label}" }
                        }, modifier = Modifier.testTag("simulate:${q.id}")) { Text("${q.emoji} ${q.label}") }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        chosen ?: return@OutlinedButton
                        scope.launch { fake.simulateExpiresSoon(chosen, 60); log = "Status expires in 60s" }
                    }) { Text("Expire in 1 min") }
                    OutlinedButton(onClick = {
                        chosen ?: return@OutlinedButton
                        scope.launch { fake.simulateIncomingReaction(chosen, ReactionTemplate.entries.random()); log = "Reaction sent to you" }
                    }) { Text("Send me a reaction") }
                    OutlinedButton(onClick = {
                        chosen ?: return@OutlinedButton
                        scope.launch { fake.simulateFriendRemovedMe(chosen); log = "Removed you (cache purged)"; target = null }
                    }) { Text("They remove me") }
                    OutlinedButton(onClick = { scope.launch { c.sync.reconcile(); log = "Reconciled" } }) { Text("Reconcile now") }
                    OutlinedButton(onClick = { scope.launch { fake.resetDemo(); c.sync.reconcile(); log = "Demo world reset" } }) { Text("Reset demo friends") }
                }
                log?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
                Card(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Last sync attempt: ${sync?.lastAttemptMs?.let { java.time.Instant.ofEpochMilli(it) } ?: "never"}", style = MaterialTheme.typography.bodySmall)
                        Text("Last success: ${sync?.lastSuccessMs?.let { java.time.Instant.ofEpochMilli(it) } ?: "never"}", style = MaterialTheme.typography.bodySmall)
                        Text("Last error: ${sync?.lastError ?: "none"}", style = MaterialTheme.typography.bodySmall)
                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, modifier = modifier) },
    )
}
