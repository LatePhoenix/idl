package app.idl.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.idl.AppContainer
import app.idl.avatar.AvatarImage
import app.idl.data.repo.idlError
import app.idl.domain.AvatarConfig
import app.idl.domain.AvatarPalette
import app.idl.domain.BaseForm
import app.idl.domain.Expression
import app.idl.domain.Usernames
import kotlinx.coroutines.launch

/** Closed-alpha onboarding: a local demo account (real auth arrives with the backend). */
@Composable
fun OnboardingScreen(c: AppContainer, onCreated: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val normalized = Usernames.normalize(username)
    val valid = name.isNotBlank() && Usernames.isValid(normalized)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy((-12).dp), modifier = Modifier.padding(top = 32.dp)) {
                AvatarImage(AvatarConfig(BaseForm.FOX, AvatarPalette.body[10], expression = Expression.HAPPY, themeColor = AvatarPalette.theme[2]), "", size = 72.dp)
                AvatarImage(AvatarConfig(BaseForm.GHOST, AvatarPalette.body[8], expression = Expression.SLEEPY), "", size = 88.dp)
                AvatarImage(AvatarConfig(BaseForm.ROBOT, AvatarPalette.body[9], expression = Expression.FOCUSED, themeColor = AvatarPalette.theme[1]), "", size = 72.dp)
            }
            Text("iDL", style = MaterialTheme.typography.displayMedium)
            Text("Set your vibe. See your people.", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                "A tiny, private status for a few close friends — right on your home screen. No feed, no followers, nothing to keep up with.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(40) },
                label = { Text("Display name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("displayName"),
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it.take(20); error = null },
                label = { Text("Username") }, prefix = { Text("@") }, singleLine = true,
                isError = error != null || (username.isNotEmpty() && !Usernames.isValid(normalized)),
                supportingText = { Text(error ?: "3–20 lowercase letters, numbers or _") },
                modifier = Modifier.fillMaxWidth().testTag("username"),
            )
            Button(
                enabled = valid && !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        c.session.register(name, normalized)
                            .onSuccess { c.sync.reconcile(); onCreated() }
                            .onFailure { error = it.idlError.userMessage }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("createAccount"),
            ) { Text("Create demo account") }
            Text(
                "Alpha build: runs on a local demo server with sample friends. Nothing leaves your device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
