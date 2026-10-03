package app.idl

import android.app.Application
import android.content.Context
import app.idl.data.local.AppSettings
import app.idl.data.local.IdlDatabase
import app.idl.data.push.MockPushSource
import app.idl.data.push.PushHandler
import app.idl.data.remote.FakeIdlBackend
import app.idl.data.remote.FileFakeWorldStore
import app.idl.data.remote.IdlBackend
import app.idl.data.remote.supabase.AuthGateway
import app.idl.data.remote.supabase.LocalAuthGateway
import app.idl.data.remote.supabase.SupabaseAuth
import app.idl.data.remote.supabase.SupabaseConfig
import app.idl.data.remote.supabase.SupabaseIdlBackend
import app.idl.data.local.DataStoreSessionStore
import okhttp3.OkHttpClient
import app.idl.data.repo.AvatarRepository
import app.idl.data.repo.FriendsRepository
import app.idl.data.repo.PresenceRepository
import app.idl.data.repo.PrivacyRepository
import app.idl.data.repo.ReactionRepository
import app.idl.data.repo.SessionRepository
import app.idl.data.repo.SyncManager
import app.idl.notify.Notifier
import app.idl.widget.GlanceWidgetRefresher
import app.idl.work.WorkSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual DI: the whole object graph, created once per process (IDL_DECISIONS D-02). */
class AppContainer(context: Context, val clock: IdlClock = IdlClock.SYSTEM) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db = IdlDatabase.create(app)
    val dao = db.dao()
    val settings = AppSettings(app)
    val notifier = Notifier(app)

    val mockPush = MockPushSource()

    /** Supabase when configured in local.properties; otherwise the in-process demo backend. */
    val supabase = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
    val isRemote = supabase.isConfigured
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
    private val supabaseAuth by lazy { SupabaseAuth(http, supabase, DataStoreSessionStore(app), clock) }

    /** Debug-only simulation hooks exist only on the fake backend. */
    val fakeBackend: FakeIdlBackend? =
        if (isRemote) null else FakeIdlBackend(FileFakeWorldStore(File(app.filesDir, "fake_world.json")), clock, mockPush, scope)
    val auth: AuthGateway = if (isRemote) supabaseAuth else LocalAuthGateway
    val backend: IdlBackend = fakeBackend ?: SupabaseIdlBackend(http, supabase, supabaseAuth)

    val widgets = GlanceWidgetRefresher(app, dao)
    val scheduler = WorkSyncScheduler(app)

    val session = SessionRepository(db, dao, backend, auth, widgets)
    val avatars = AvatarRepository(dao, backend, widgets)
    val presence = PresenceRepository(dao, backend, clock, widgets, scheduler)
    val friends = FriendsRepository(dao, backend, presence, widgets)
    val reactions = ReactionRepository(dao, backend, clock)
    val privacy = PrivacyRepository(dao, backend)
    val sync = SyncManager(dao, clock, presence, friends, reactions, privacy, widgets, onUnauthorized = { session.signOut() })
    val pushHandler = PushHandler(dao, presence, friends, reactions, widgets, settings, notifier)

    fun start() {
        notifier.createChannels()
        scope.launch {
            mockPush.events.collect { event ->
                runCatching { pushHandler.handle(event) }.onFailure { IdlLog.e("push.handle_failed", it) }
            }
        }
        fakeBackend?.let { fake ->
            scope.launch { settings.simulateOffline.distinctUntilChanged().collect { fake.simulateOffline = it } }
        }
        IdlLog.i("app.start", "backend" to if (isRemote) "supabase" else "fake")
        scheduler.schedulePeriodicReconcile()
        scope.launch { sync.reconcile() }
    }
}

class IdlApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

val Context.container: AppContainer get() = (applicationContext as IdlApp).container
