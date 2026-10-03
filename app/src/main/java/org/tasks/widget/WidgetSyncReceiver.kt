package org.tasks.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.sync.SyncAdapters
import org.tasks.sync.SyncSource
import timber.log.Timber
import javax.inject.Inject

/**
 * The widget header's refresh button: asks for a sync now, as pull-to-refresh does, and redraws the widgets at once (they are
 * redrawn again when the sync finishes). Not exported: only the widget's own PendingIntent reaches it.
 */
@AndroidEntryPoint
class WidgetSyncReceiver : BroadcastReceiver() {
    @Inject lateinit var syncAdapters: SyncAdapters
    @Inject lateinit var appWidgetManager: AppWidgetManager

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("widget refresh: syncing")
        syncAdapters.sync(SyncSource.USER_INITIATED)
        appWidgetManager.updateWidgets()
    }

    companion object {
        const val ACTION = "org.tasks.widget.SYNC_NOW"
    }
}
