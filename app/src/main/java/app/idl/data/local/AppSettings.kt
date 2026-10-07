package app.idl.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.idl.domain.avatar.WallpaperContrastPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("idl_settings")

/** Local-only preferences (DataStore). Notification preferences sync server-side in Milestone 1. */
class AppSettings(private val context: Context) {

    data class Notifications(val reactions: Boolean, val presence: Boolean, val friendRequests: Boolean)

    private object Keys {
        val notifyReactions = booleanPreferencesKey("notify_reactions")
        val notifyPresence = booleanPreferencesKey("notify_presence")
        val notifyRequests = booleanPreferencesKey("notify_requests")
        val simulateOffline = booleanPreferencesKey("debug_simulate_offline")
        val unlockAllItems = booleanPreferencesKey("debug_unlock_all_items")
        val askedNotificationPermission = booleanPreferencesKey("asked_notification_permission")
        val wallpaperContrast = stringPreferencesKey("wallpaper_contrast")
    }

    val notifications: Flow<Notifications> = context.dataStore.data.map {
        Notifications(
            reactions = it[Keys.notifyReactions] ?: true,
            // Off by default: presence pings would turn ambient presence into an obligation engine.
            presence = it[Keys.notifyPresence] ?: false,
            friendRequests = it[Keys.notifyRequests] ?: true,
        )
    }

    val simulateOffline: Flow<Boolean> = context.dataStore.data.map { it[Keys.simulateOffline] ?: false }
    /** Debug-only: treat every premium catalog item as owned (AP-10 / D-48). */
    val unlockAllItems: Flow<Boolean> = context.dataStore.data.map { it[Keys.unlockAllItems] ?: false }
    val askedNotificationPermission: Flow<Boolean> = context.dataStore.data.map { it[Keys.askedNotificationPermission] ?: false }

    val wallpaperContrast: Flow<WallpaperContrastPreference> = context.dataStore.data.map {
        WallpaperContrastPreference.fromStored(it[Keys.wallpaperContrast])
    }

    suspend fun notificationsNow() = notifications.first()

    suspend fun setNotifyReactions(v: Boolean) = set(Keys.notifyReactions, v)
    suspend fun setNotifyPresence(v: Boolean) = set(Keys.notifyPresence, v)
    suspend fun setNotifyRequests(v: Boolean) = set(Keys.notifyRequests, v)
    suspend fun setSimulateOffline(v: Boolean) = set(Keys.simulateOffline, v)
    suspend fun setUnlockAllItems(v: Boolean) = set(Keys.unlockAllItems, v)
    suspend fun setAskedNotificationPermission() = set(Keys.askedNotificationPermission, true)
    suspend fun wallpaperContrastNow() = wallpaperContrast.first()
    suspend fun setWallpaperContrast(value: WallpaperContrastPreference) = set(Keys.wallpaperContrast, value.name.lowercase())

    private suspend fun set(key: Preferences.Key<Boolean>, v: Boolean) {
        context.dataStore.edit { it[key] = v }
    }

    private suspend fun set(key: Preferences.Key<String>, v: String) {
        context.dataStore.edit { it[key] = v }
    }
}
