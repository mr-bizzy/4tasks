package org.tasks.receivers

import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tasks.R
import org.tasks.data.count
import org.tasks.data.dao.TaskDao
import org.tasks.injection.InjectingJobIntentService
import org.tasks.preferences.DefaultFilterProvider
import org.tasks.preferences.Preferences
import org.tasks.pebble.PebbleRefresher
import org.tasks.wear.WearRefresher
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class RefreshReceiver : InjectingJobIntentService() {
    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var defaultFilterProvider: DefaultFilterProvider
    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var wearRefresher: WearRefresher
    @Inject lateinit var pebbleRefresher: PebbleRefresher

    override suspend fun doWork(intent: Intent) {
        if (preferences.getBoolean(R.string.p_badges_enabled, true)) {
            val badgeFilter = defaultFilterProvider.getBadgeFilter()
            ShortcutBadger.applyCount(context, taskDao.count(badgeFilter))
        }
        wearRefresher.refresh()
        pebbleRefresher.refresh()
    }
}