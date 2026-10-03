package app.idl.ui.widgets

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.idl.AppContainer
import app.idl.avatar.AvatarImage
import app.idl.domain.AvatarConfig
import app.idl.domain.FriendStatus
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.SectionTitle
import app.idl.widget.WidgetPinning

@Composable
fun WidgetsScreen(c: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val cards by c.friends.cards.collectAsState(initial = emptyList())
    val canPin = remember { WidgetPinning.canPin(context) }
    var hint by remember { mutableStateOf<String?>(null) }
    val manual = "Long-press an empty spot on your home screen → Widgets → iDL."

    Scaffold(topBar = { IdlTopBar("Home-screen widgets", onBack = onBack) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Widgets are the heart of iDL: pin a few people and see their vibe without opening anything. " +
                    "Widgets update when friends change status and fall back honestly when they can't refresh.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SectionTitle("Your iDL")
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Shows your current status. Tap it to open the Status Deck.", Modifier.weight(1f))
                    Button(onClick = { if (!canPin || !WidgetPinning.pinSelf(context)) hint = manual }) { Text("Add") }
                }
            }
            SectionTitle("A friend")
            val accepted = cards.filter { it.friend.status == FriendStatus.ACCEPTED }
            if (accepted.isEmpty()) Text("Add a friend first.", style = MaterialTheme.typography.bodySmall)
            accepted.forEach { card ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    AvatarImage(card.view?.avatar ?: AvatarConfig(), card.friend.displayName, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(card.friend.displayName, Modifier.weight(1f))
                    OutlinedButton(onClick = { if (!canPin || !WidgetPinning.pinFriend(context, card.friend.userId)) hint = manual }) { Text("Pin") }
                }
            }
            hint?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.primary) }
            if (!canPin) Text(manual, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
