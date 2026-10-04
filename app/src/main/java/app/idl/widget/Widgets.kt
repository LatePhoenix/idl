package app.idl.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.idl.IdlLog
import app.idl.MainActivity
import app.idl.avatar.AvatarBadges
import app.idl.avatar.AvatarRenderer
import app.idl.container
import app.idl.data.local.IdlDao
import app.idl.data.local.WidgetSubscriptionEntity
import app.idl.data.repo.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private const val AVATAR_PX = 256

/** Renders a widget model; shared by both widget types. Static: no animation. */
private suspend fun render(context: Context, model: WidgetModel): Bitmap? = withContext(Dispatchers.Default) {
    runCatching {
        model.avatar?.let {
            AvatarRenderer.bitmap(
                it, AVATAR_PX,
                AvatarBadges(model.availability, model.activity, resonating = model.resonating),
            )
        }
    }.onFailure { IdlLog.e("widget.render_failed", it) }.getOrNull()
}

private fun openIntent(context: Context, deepLink: String) =
    Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

@Composable
private fun WidgetBody(context: Context, model: WidgetModel, bitmap: Bitmap?) {
    val wide = LocalSize.current.width >= WIDE.width
    val onSurface = GlanceTheme.colors.onSurface
    val muted = GlanceTheme.colors.onSurfaceVariant
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp)
            .clickable(actionStartActivity(openIntent(context, model.deepLink)))
            .semantics { contentDescription = model.contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (bitmap != null) {
                    Image(
                        provider = ImageProvider(bitmap),
                        contentDescription = model.contentDescription,
                        modifier = GlanceModifier.size(if (wide) 92.dp else 76.dp),
                    )
                } else {
                    // Text-only fallback when nothing can be drawn.
                    Text("🙂", style = TextStyle(fontSize = 40.sp))
                }
                if (!wide) {
                    Text(
                        model.title,
                        maxLines = 1,
                        style = TextStyle(color = onSurface, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                    model.staleHint?.let { Text("⟳ $it", maxLines = 1, style = TextStyle(color = muted, fontSize = 10.sp)) }
                }
            }
            if (wide) {
                Spacer(GlanceModifier.width(10.dp))
                Column {
                    Text(model.title, maxLines = 1, style = TextStyle(color = onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    model.subtitle?.let { Text(it, maxLines = 1, style = TextStyle(color = muted, fontSize = 13.sp)) }
                    model.note?.let { Text("“$it”", maxLines = 2, style = TextStyle(color = onSurface, fontSize = 13.sp)) }
                    model.staleHint?.let { Text("⟳ $it", maxLines = 1, style = TextStyle(color = muted, fontSize = 11.sp)) }
                }
            }
        }
    }
}

/**
 * While a Glance session is alive, `update()` only recomposes; it does not re-run
 * provideGlance. So data is (re)loaded inside the composition, keyed on the refresher's
 * version counter, which every WidgetRefresher call bumps.
 */
@Composable
private fun LiveBody(
    context: Context,
    kind: String,
    initial: Pair<WidgetModel, Bitmap?>,
    load: suspend () -> Pair<WidgetModel, Bitmap?>,
) {
    val version by context.container.widgets.version.collectAsState()
    val state by produceState(initial, version) {
        if (version > 0) value = load()
        IdlLog.i("widget.render", "kind" to kind, "version" to version, "hasAvatar" to (value.second != null))
    }
    GlanceTheme { WidgetBody(context, state.first, state.second) }
}

class SoloFriendWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        c.economy.evaluate()
        suspend fun load() = WidgetData.friend(c.dao, c.clock, appWidgetId).let { it to render(context, it) }
        val initial = load()
        provideContent { LiveBody(context, "solo", initial) { load() } }
    }
}

class SelfWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = context.container
        c.economy.evaluate()
        suspend fun load() = WidgetData.self(c.dao, c.clock).let { it to render(context, it) }
        val initial = load()
        provideContent { LiveBody(context, "self", initial) { load() } }
    }
}

class SoloFriendWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SoloFriendWidget()

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val c = context.container
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { c.dao.deleteWidgetSubscription(it) }
            c.economy.syncLocalWidgets()
        }
    }
}

class SelfWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SelfWidget()
}

/**
 * Receives the success callback of [WidgetPinning.pinFriend]. The system adds the new
 * appWidgetId; we attach the chosen friend and render.
 */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val friendId = intent.getStringExtra(EXTRA_FRIEND_ID) ?: return
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetPinning.attach(context, appWidgetId, friendId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_FRIEND_ID = "friend_id"
    }
}

object WidgetPinning {
    fun canPin(context: Context) = AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun pinSelf(context: Context): Boolean =
        AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, SelfWidgetReceiver::class.java), null, null)

    fun pinFriend(context: Context, friendId: String): Boolean {
        val callback = android.app.PendingIntent.getBroadcast(
            context,
            friendId.hashCode(),
            Intent(context, WidgetPinnedReceiver::class.java).putExtra(WidgetPinnedReceiver.EXTRA_FRIEND_ID, friendId),
            // Mutable: the launcher fills in EXTRA_APPWIDGET_ID.
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
        )
        return AppWidgetManager.getInstance(context)
            .requestPinAppWidget(ComponentName(context, SoloFriendWidgetReceiver::class.java), null, callback)
    }

    suspend fun attach(context: Context, appWidgetId: Int, friendId: String) {
        val dao = context.container.dao
        dao.upsertWidgetSubscription(WidgetSubscriptionEntity(appWidgetId, WidgetData.KIND_SOLO, friendId))
        context.container.economy.syncLocalWidgets()
        IdlLog.i("widget.subscribed")
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        SoloFriendWidget().update(context, glanceId)
    }
}

class GlanceWidgetRefresher(private val context: Context, private val dao: IdlDao) : WidgetRefresher {
    /** Bumped on every refresh so live widget sessions reload their data. */
    val version = MutableStateFlow(0L)

    override suspend fun selfChanged() = safely { SelfWidget().updateAll(context) }

    override suspend fun friendsChanged(userIds: Collection<String>) = safely {
        val manager = GlanceAppWidgetManager(context)
        val widget = SoloFriendWidget()
        userIds.flatMap { dao.widgetSubscriptionsFor(it) }.forEach { sub ->
            runCatching { widget.update(context, manager.getGlanceIdBy(sub.appWidgetId)) }
                .onFailure { IdlLog.w("widget.update_failed", "id" to sub.appWidgetId, t = it) }
        }
    }

    override suspend fun all() = safely {
        SelfWidget().updateAll(context)
        SoloFriendWidget().updateAll(context)
    }

    private suspend fun safely(block: suspend () -> Unit) {
        version.update { it + 1 }
        runCatching { context.container.economy.evaluate() }
            .onFailure { IdlLog.w("charge.evaluate_failed", t = it) }
        runCatching { block() }.onFailure { IdlLog.w("widget.refresh_failed", t = it) }
    }
}
