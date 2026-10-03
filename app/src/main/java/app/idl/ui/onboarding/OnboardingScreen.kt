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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.KeyboardType
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

private enum class Step { EMAIL, CODE, PROFILE }

/**
 * Onboarding. With Supabase configured: email -> one-time code -> (first time only) profile.
 * In demo mode it goes straight to profile creation against the local fake backend.
 */
@Composable
fun OnboardingScreen(c: AppContainer, onCreated: () -> Unit, onReturning: () -> Unit) {
    var step by remember { mutableStateOf(if (c.auth.isRemote) Step.EMAIL else Step.PROFILE) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val normalized = Usernames.normalize(username)
    val valid = name.isNotBlank() && Usernames.isValid(normalized)

    fun run(block: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try { block() } finally { busy = false }
        }
    }

    // Already authenticated (e.g. app restarted mid-onboarding): skip straight to profile lookup.
    LaunchedEffect(Unit) {
        if (c.auth.isRemote && c.auth.isSignedIn()) {
            c.session.resumeProfile().onSuccess { me -> if (me != null) onReturning() else step = Step.PROFILE }
        }
    }

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
            when (step) {
                Step.EMAIL -> {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it.take(254); error = null },
                        label = { Text("Email") }, singleLine = true, isError = error != null,
                        supportingText = { Text(error ?: "We'll email you a sign-in code. No password.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth().testTag("email"),
                    )
                    Button(
                        enabled = email.contains('@') && !busy,
                        onClick = { run { c.auth.requestEmailCode(email).onSuccess { step = Step.CODE }.onFailure { error = it.idlError.userMessage } } },
                        modifier = Modifier.fillMaxWidth().testTag("sendCode"),
                    ) { Text("Send code") }
                }
                Step.CODE -> {
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter(Char::isDigit).take(10); error = null },
                        label = { Text("Code from your email") }, singleLine = true, isError = error != null,
                        supportingText = { Text(error ?: "Sent to $email") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("code"),
                    )
                    Button(
                        enabled = code.length >= 6 && !busy,
                        onClick = {
                            run {
                                c.auth.verifyEmailCode(email, code)
                                    .onFailure { error = it.idlError.userMessage }
                                    .onSuccess {
                                        c.session.resumeProfile()
                                            .onSuccess { me -> if (me != null) { c.sync.reconcile(); onReturning() } else step = Step.PROFILE }
                                            .onFailure { error = it.idlError.userMessage }
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("verifyCode"),
                    ) { Text("Sign in") }
                    TextButton(onClick = { step = Step.EMAIL; code = "" }) { Text("Use a different email") }
                }
                Step.PROFILE -> {
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
                            run {
                                c.session.register(name, normalized)
                                    .onSuccess { c.sync.reconcile(); onCreated() }
                                    .onFailure { error = it.idlError.userMessage }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("createAccount"),
                    ) { Text(if (c.auth.isRemote) "Create my iDL" else "Create demo account") }
                }
            }
            Text(
                if (c.auth.isRemote) "Only friends you approve can ever see you."
                else "Demo mode: runs on a local server with sample friends. Nothing leaves your device.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
