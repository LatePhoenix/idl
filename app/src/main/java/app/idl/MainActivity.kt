package app.idl

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.idl.ui.avatar.AvatarStudioScreen
import app.idl.ui.components.Loading
import app.idl.ui.friend.FriendProfileScreen
import app.idl.ui.home.HomeScreen
import app.idl.ui.invite.AddFriendScreen
import app.idl.ui.onboarding.OnboardingScreen
import app.idl.ui.privacy.PrivacyCenterScreen
import app.idl.ui.settings.SettingsScreen
import app.idl.ui.status.StatusDeckScreen
import app.idl.ui.theme.IdlTheme
import app.idl.ui.widgets.WidgetsScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var nav: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val c = container
        setContent {
            IdlTheme {
                // Start destination is decided once; later session changes navigate explicitly.
                val start by produceState<String?>(null) {
                    value = if (c.session.me.first() != null) "home" else "onboarding"
                }
                val s = start
                if (s == null) {
                    Loading()
                } else {
                    val navController = rememberNavController().also { nav = it }
                    IdlNavHost(c, navController, start = s)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nav?.handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Foreground reconcile fills any push gaps (IDL_ARCHITECTURE §5).
        container.scope.launch { container.sync.reconcile() }
    }
}

@Composable
private fun IdlNavHost(c: AppContainer, nav: NavHostController, start: String) {
    val back: () -> Unit = { if (!nav.popBackStack()) nav.navigate("home") }
    NavHost(nav, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(c) {
                nav.navigate("avatar?firstRun=true") { popUpTo("onboarding") { inclusive = true } }
            }
        }
        composable(
            "avatar?firstRun={firstRun}",
            arguments = listOf(navArgument("firstRun") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val firstRun = entry.arguments?.getBoolean("firstRun") == true
            AvatarStudioScreen(c, firstRun, onDone = {
                if (firstRun) nav.navigate("home") { popUpTo(0) } else back()
            })
        }
        composable("home", deepLinks = listOf(navDeepLink { uriPattern = "idl://home" })) {
            NotificationPermissionOnce(c)
            HomeScreen(
                c,
                onOpenStatus = { nav.navigate("status") },
                onOpenFriend = { nav.navigate("friend/$it") },
                onAddFriend = { nav.navigate("add") },
                onPrivacy = { nav.navigate("privacy") },
                onWidgets = { nav.navigate("widgets") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("status", deepLinks = listOf(navDeepLink { uriPattern = "idl://status" })) {
            StatusDeckScreen(c, onDone = back)
        }
        composable(
            "friend/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "idl://friend/{id}" }),
        ) { entry ->
            FriendProfileScreen(c, entry.arguments?.getString("id").orEmpty(), onBack = back)
        }
        composable("add", deepLinks = listOf(navDeepLink { uriPattern = "idl://add" })) { AddFriendScreen(c, onBack = back) }
        composable("privacy") { PrivacyCenterScreen(c, onBack = back) }
        composable("widgets", deepLinks = listOf(navDeepLink { uriPattern = "idl://widgets" })) { WidgetsScreen(c, onBack = back) }
        composable("settings") { SettingsScreen(c, onBack = back, onAvatarStudio = { nav.navigate("avatar") }) }
    }
}

/** Asks for POST_NOTIFICATIONS once, after onboarding, never on first launch. */
@Composable
private fun NotificationPermissionOnce(c: AppContainer) {
    if (Build.VERSION.SDK_INT < 33) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!c.settings.askedNotificationPermission.first()) {
            c.settings.setAskedNotificationPermission()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
