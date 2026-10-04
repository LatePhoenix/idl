package app.idl.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.idl.data.remote.supabase.AuthSession
import app.idl.data.remote.supabase.SessionStore
import app.idl.domain.IdlJson
import kotlinx.coroutines.flow.first

private val Context.authStore by preferencesDataStore("idl_auth")

/**
 * Supabase session in app-private storage. Excluded from cloud backup and device transfer by
 * data_extraction_rules.xml. Hardware-backed encryption is a Milestone 1 follow-up (D-20).
 */
class DataStoreSessionStore(private val context: Context) : SessionStore {
    private val key = stringPreferencesKey("session")

    override suspend fun load(): AuthSession? =
        context.authStore.data.first()[key]?.let { runCatching { IdlJson.decodeFromString(AuthSession.serializer(), it) }.getOrNull() }

    override suspend fun save(session: AuthSession?) {
        context.authStore.edit {
            if (session == null) it.remove(key) else it[key] = IdlJson.encodeToString(AuthSession.serializer(), session)
        }
    }
}
