package org.tasks.jobs

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import org.tasks.analytics.Firebase
import org.tasks.feed.BlogFeedChecker
import org.tasks.injection.BaseWorker
import timber.log.Timber

@HiltWorker
class BlogFeedWork @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    firebase: Firebase,
    private val blogFeedChecker: BlogFeedChecker,
    private val workManager: WorkManager,
) : BaseWorker(context, workerParams, firebase) {

    override fun doWork(): Result {
        return super.doWork()
    }

    /** Does nothing: 4Tasks does not contact Tasks.org's blog (see [WorkManager.scheduleBlogFeedCheck]). */
    override suspend fun run(): Result {
        Timber.d("BlogFeedWork: disabled")
        return Result.success()
    }
}
