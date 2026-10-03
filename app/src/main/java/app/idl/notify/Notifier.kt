package app.idl.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.idl.IdlLog
import app.idl.MainActivity
import app.idl.R

/** Notification channels per IDL_PRODUCT_SPEC; content stays deliberately terse. */
class Notifier(private val context: Context) {

    enum class Channel(val id: String, val title: String, val importance: Int) {
        REACTIONS("reactions", "Reactions", NotificationManager.IMPORTANCE_DEFAULT),
        PRESENCE("presence", "Friend status updates", NotificationManager.IMPORTANCE_LOW),
        FRIEND_REQUESTS("friend_requests", "Friend requests", NotificationManager.IMPORTANCE_DEFAULT),
        SYSTEM("system", "Service notices", NotificationManager.IMPORTANCE_LOW),
    }

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(Channel.entries.map { NotificationChannel(it.id, it.title, it.importance) })
    }

    fun post(channel: Channel, id: Int, title: String, text: String, deepLink: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            IdlLog.i("notify.skipped_no_permission", "channel" to channel.id)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(R.drawable.ic_stat_idl)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
            .onFailure { IdlLog.w("notify.failed", t = it) }
    }
}
