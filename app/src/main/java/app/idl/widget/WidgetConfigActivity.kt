package app.idl.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.idl.avatar.AvatarImage
import app.idl.container
import app.idl.domain.AvatarConfig
import app.idl.domain.avatar.AvatarConfiguration
import app.idl.domain.FriendStatus
import app.idl.ui.theme.IdlTheme
import kotlinx.coroutines.launch

/** Launched by the launcher when a Solo friend widget is added from the widget picker. */
class SoloFriendWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val c = container
        setContent {
            IdlTheme {
                val cards by c.friends.cards.collectAsState(initial = emptyList())
                val scope = rememberCoroutineScope()
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Pin which friend?", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.padding(4.dp))
                        val accepted = cards.filter { it.friend.status == FriendStatus.ACCEPTED }
                        if (accepted.isEmpty()) {
                            Text("Add a friend in iDL first, then add this widget again.", style = MaterialTheme.typography.bodyMedium)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(accepted, key = { it.friend.userId }) { card ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                WidgetPinning.attach(this@SoloFriendWidgetConfigActivity, appWidgetId, card.friend.userId)
                                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                                finish()
                                            }
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AvatarImage(card.view?.identity ?: AvatarConfiguration(baseAssetId = "base_teardrop", paletteAssetId = "palette_sunny"), card.friend.displayName, size = 48.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(card.friend.displayName, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
