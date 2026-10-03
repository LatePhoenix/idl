package app.idl.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.idl.IdlLog
import app.idl.container
import app.idl.data.repo.SyncScheduler
import app.idl.data.repo.decodeState
import app.idl.data.repo.decodeView
import app.idl.data.repo.idlError
import app.idl.domain.Expiry
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Full reconcile: flush pending writes, refetch friends/presence/inbox, refresh widgets. */
class ReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = applicationContext.container.sync.reconcile()
        return when {
            result.isSuccess -> Result.success()
            result.exceptionOrNull()?.idlError?.retryable == true && runAttemptCount < 5 -> Result.retry()
            else -> Result.failure()
        }
    }
}

/** Fires when a status expires so widgets re-render without the stale state. */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val now = c.clock.now()
        c.dao.purgeExpiredReactions(now.toEpochMilli())
        c.widgets.all()
        val next = Expiry.nextExpiry(
            c.dao.ownPresenceNow().map { decodeState(it.json).expiresAt } +
                c.dao.friendPresenceAllNow().map { decodeView(it.json).expiresAt },
            now,
        )
        IdlLog.i("expiry.tick", "next" to next)
        WorkSyncScheduler.clearScheduled()
        c.scheduler.scheduleExpiry(next)
        return Result.success()
    }
}

class WorkSyncScheduler(context: Context) : SyncScheduler {
    private val wm = WorkManager.getInstance(context)
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun scheduleRetry() {
        wm.enqueueUniqueWork(
            "retry_sync",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReconcileWorker>()
                .setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    override fun scheduleExpiry(at: Instant?) {
        if (at == null) return
        synchronized(lock) {
            val current = scheduledAt
            if (current != null && current.isAfter(Instant.now()) && !at.isBefore(current)) return
            scheduledAt = at
        }
        val delay = Duration.between(Instant.now(), at).plusSeconds(1).coerceAtLeastZero()
        wm.enqueueUniqueWork(
            "expiry",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ExpiryWorker>().setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS).build(),
        )
    }

    fun schedulePeriodicReconcile() {
        wm.enqueueUniquePeriodicWork(
            "reconcile",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReconcileWorker>(6, TimeUnit.HOURS).setConstraints(network).build(),
        )
    }

    companion object {
        private val lock = Any()
        @Volatile private var scheduledAt: Instant? = null
        fun clearScheduled() = synchronized(lock) { scheduledAt = null }
    }
}

private fun Duration.coerceAtLeastZero(): Duration = if (isNegative) Duration.ZERO else this
