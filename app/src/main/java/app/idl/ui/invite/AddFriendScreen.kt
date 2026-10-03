package app.idl.ui.invite

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.idl.AppContainer
import app.idl.BuildConfig
import app.idl.avatar.AvatarImage
import app.idl.data.remote.DemoData
import app.idl.data.repo.idlError
import app.idl.domain.AvatarConfig
import app.idl.domain.FriendStatus
import app.idl.domain.Invite
import app.idl.ui.components.IdlTopBar
import app.idl.ui.components.SectionTitle
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddFriendViewModel(private val c: AppContainer) : ViewModel() {
    val invite = MutableStateFlow<Invite?>(null)
    val message = MutableStateFlow<String?>(null)
    val incoming = c.friends.cards.map { l -> l.filter { it.friend.status == FriendStatus.INCOMING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            c.friends.createInvite().onSuccess { invite.value = it }.onFailure { message.value = it.idlError.userMessage }
        }
    }

    fun redeem(code: String) = viewModelScope.launch {
        c.friends.redeem(code)
            .onSuccess { message.value = if (it.status == FriendStatus.ACCEPTED) "You're now connected with ${it.displayName}" else "Request sent to ${it.displayName}" }
            .onFailure { message.value = it.idlError.userMessage }
    }

    fun accept(id: String) = viewModelScope.launch { c.friends.accept(id).onFailure { message.value = it.idlError.userMessage } }
    fun decline(id: String) = viewModelScope.launch { c.friends.decline(id).onFailure { message.value = it.idlError.userMessage } }
}

@Composable
fun AddFriendScreen(c: AppContainer, onBack: () -> Unit, vm: AddFriendViewModel = viewModel { AddFriendViewModel(c) }) {
    val invite by vm.invite.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val message by vm.message.collectAsState()
    var code by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(topBar = { IdlTopBar("Add a friend", onBack = onBack) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            if (incoming.isNotEmpty()) {
                SectionTitle("Requests")
                incoming.forEach { card ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            AvatarImage(card.view?.avatar ?: AvatarConfig(), card.friend.displayName, size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(card.friend.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("@${card.friend.username}", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { vm.decline(card.friend.userId) }) { Text("Decline") }
                            Button(onClick = { vm.accept(card.friend.userId) }, modifier = Modifier.testTag("accept:${card.friend.username}")) { Text("Accept") }
                        }
                    }
                }
            }

            SectionTitle("Your invite")
            invite?.let { inv ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val qr = remember(inv.url) { qrBitmap(inv.url, 512) }
                        Image(
                            qr.asImageBitmap(),
                            contentDescription = "QR code for your invite link",
                            modifier = Modifier.size(200.dp).background(Color.White).padding(8.dp),
                        )
                        Text(inv.code, style = MaterialTheme.typography.headlineSmall)
                        Text("Valid for 7 days. Only people you approve can see you.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Add me on iDL: ${inv.url} (code ${inv.code})")
                            context.startActivity(Intent.createChooser(send, "Share invite"))
                        }) { Text("Share invite link") }
                    }
                }
            }

            SectionTitle("Have a code?")
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.take(40) },
                label = { Text("Invite code or link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("inviteCode"),
            )
            Button(onClick = { vm.redeem(code) }, enabled = code.isNotBlank(), modifier = Modifier.padding(top = 8.dp).testTag("redeem")) { Text("Add") }
            message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary) }
            if (BuildConfig.DEBUG) {
                Text(
                    "Demo codes: ${DemoData.INVITE_CODES.joinToString()}  •  QR scanning arrives with the real backend.",
                    Modifier.padding(vertical = 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
