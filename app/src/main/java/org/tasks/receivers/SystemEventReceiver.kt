package org.tasks.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.LocalBroadcastManager
import org.tasks.location.RegisterGeofencesWork
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class SystemEventReceiver : BroadcastReceiver() {

    @Inject lateinit var localBroadcastManager: LocalBroadcastManager
    @Inject lateinit var syncAdapters: dagger.Lazy<org.tasks.sync.SyncAdapters>

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("onReceive(context, %s)", intent)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                localBroadcastManager.broadcastRefresh()
                RegisterGeofencesWork.enqueue(context)
                // Building SyncAdapters pushes anything that was dirty; this also pulls what changed while the phone was off.
                syncAdapters.get().sync(org.tasks.sync.SyncSource.BOOT_COMPLETED)
            }
            Intent.ACTION_USER_PRESENT -> {
                localBroadcastManager.broadcastRefresh()
            }
        }
    }
}
