package org.tasks.jobs

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.tasks.analytics.Firebase
import org.tasks.injection.BaseWorker
import org.tasks.sync.SyncSource

/**
 * The periodic background sync, as a hand-off. The periodic job itself has no network constraint and does no network work: it only
 * asks for an EXPEDITED sync. Measured (docs/SYNC-DESIGN.md section 3, devtools/sync-harness/probe_jobs.py): when the app is cached,
 * Android runs an ordinary job (delayed or periodic) but gives its UID no network (`blocked=APP_BACKGROUND`: a socket times out, and
 * on some phones the job is not even started because its CONNECTIVITY constraint is never satisfied), while an expedited job has the
 * network at once. So the periodic job must not be the thing that syncs.
 */
@HiltWorker
class PeriodicSyncWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    firebase: Firebase,
    private val workManager: WorkManager,
) : BaseWorker(context, workerParams, firebase) {

    override suspend fun run(): Result {
        workManager.sync(SyncSource.BACKGROUND)
        return Result.success()
    }
}
