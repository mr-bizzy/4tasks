package org.tasks.harness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.tasks.api.ApiQueryEngine
import org.tasks.api.ListQuery
import org.tasks.api.TaskQuery
import org.tasks.api.findLists
import org.tasks.api.findTasks
import org.tasks.data.dao.CaldavDao
import org.tasks.data.entity.CaldavAccount
import org.tasks.security.KeyStoreEncryption
import org.tasks.time.DateTimeUtils2.currentTimeMillis

/**
 * DEBUG BUILDS ONLY: the hands of the two-device sync harness (devtools/sync-harness). Two broadcasts, both answered on logcat
 * tag HarnessProbe:
 *
 *  - SETUP --es url U --es user N --es password P: adds a CalDAV account the way the sign-in screen stores one (no UI to drive).
 *  - DUMP: one line per task, `T|id|title|completed|due|listTitle`, then `END|<now ms>`; the harness reads the receiving device with it.
 *
 * `adb shell am broadcast -n uk.mr_biz.fourtasks/org.tasks.harness.HarnessReceiver -a org.tasks.harness.DUMP`
 */
class HarnessReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface HarnessEntryPoint {
        val queryEngine: ApiQueryEngine
        val caldavDao: CaldavDao
        val encryption: KeyStoreEncryption
        val defaultFilterProvider: org.tasks.preferences.DefaultFilterProvider
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, HarnessEntryPoint::class.java)
        val pending = goAsync()
        Thread {
            try {
                runBlocking {
                    when (intent.action) {
                        ACTION_SETUP -> setup(entry, intent)
                        ACTION_DUMP -> dump(entry)
                        ACTION_SETDEFAULT -> setDefault(entry, intent)
                        ACTION_ADDWIDGET -> addWidget(context)
                        ACTION_PROBEJOBS -> probeJobs(context, intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "failed", e)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private suspend fun setup(entry: HarnessEntryPoint, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val user = intent.getStringExtra("user") ?: "test"
        val password = intent.getStringExtra("password") ?: "test"
        if (entry.caldavDao.getAccounts(CaldavAccount.TYPE_CALDAV).any { it.url == url && it.username == user }) {
            Log.i(TAG, "SETUP|exists")
            return
        }
        entry.caldavDao.insert(
            CaldavAccount(
                accountType = CaldavAccount.TYPE_CALDAV,
                name = user,
                url = url,
                username = user,
                password = entry.encryption.encrypt(password),
                uuid = org.tasks.data.UUIDHelper.newUUID(),
            )
        )
        Log.i(TAG, "SETUP|added")
    }

    /** SETDEFAULT --es list NAME: new tasks go to that CalDAV list, as a user would choose in Settings, Tasks, Task defaults. */
    private suspend fun setDefault(entry: HarnessEntryPoint, intent: Intent) {
        val name = intent.getStringExtra("list") ?: return
        val calendar = entry.caldavDao.getCalendars().firstOrNull { it.name == name } ?: run { Log.i(TAG, "SETDEFAULT|no such list"); return }
        val account = entry.caldavDao.getAccountByUuid(calendar.account ?: return) ?: return
        entry.defaultFilterProvider.defaultList = org.tasks.filters.CaldavFilter(calendar, account)
        Log.i(TAG, "SETDEFAULT|done")
    }

    /** ADDWIDGET: asks the launcher to pin a 4Tasks widget (it shows its own "add" prompt, which the harness taps). */
    private fun addWidget(context: Context) {
        val manager = android.appwidget.AppWidgetManager.getInstance(context)
        val provider = android.content.ComponentName(context, org.tasks.widget.TasksWidget::class.java)
        Log.i(TAG, "ADDWIDGET|supported=${manager.isRequestPinAppWidgetSupported}")
        if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(provider, null, null)
    }

    /**
     * PROBEJOBS --es url U [--ei delay S] [--ei period M]: enqueues one probe job of each kind (delayed or expedited, with or without a
     * network constraint, one-time or periodic every M minutes) and logs `JOB|<mode>|enqueued`. See [HarnessProbeWork].
     */
    private fun probeJobs(context: Context, intent: Intent) {
        val url = intent.getStringExtra("url") ?: return
        val delay = intent.getIntExtra("delay", 20).toLong()
        val period = intent.getIntExtra("period", 15).toLong()
        val wm = androidx.work.WorkManager.getInstance(context)
        val connected = androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
        fun data(mode: String) = androidx.work.Data.Builder().putString("mode", mode).putString("url", url).build()
        fun once(mode: String, constrained: Boolean, expedited: Boolean) {
            val b = androidx.work.OneTimeWorkRequest.Builder(HarnessProbeWork::class.java).setInputData(data(mode))
            if (constrained) b.setConstraints(connected)
            if (expedited) b.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            else b.setInitialDelay(delay, java.util.concurrent.TimeUnit.SECONDS)
            wm.enqueueUniqueWork("probe-$mode", androidx.work.ExistingWorkPolicy.REPLACE, b.build())
            Log.i(TAG, "JOB|$mode|enqueued")
        }
        fun periodic(mode: String, constrained: Boolean) {
            val b = androidx.work.PeriodicWorkRequest.Builder(HarnessProbeWork::class.java, period, java.util.concurrent.TimeUnit.MINUTES)
                .setInputData(data(mode))
            if (constrained) b.setConstraints(connected)
            wm.enqueueUniquePeriodicWork("probe-$mode", androidx.work.ExistingPeriodicWorkPolicy.REPLACE, b.build())
            Log.i(TAG, "JOB|$mode|enqueued")
        }
        once("delayed-connected", constrained = true, expedited = false)
        once("delayed-free", constrained = false, expedited = false)
        once("expedited-connected", constrained = true, expedited = true)
        once("expedited-free", constrained = false, expedited = true)
        periodic("periodic-connected", constrained = true)
        periodic("periodic-free", constrained = false)
        periodic("periodic-kick", constrained = false)
    }

    private suspend fun dump(entry: HarnessEntryPoint) {
        val lists = entry.queryEngine.findLists(ListQuery(limit = 500)).rows.associate { it.id to it.title }
        entry.queryEngine.findTasks(TaskQuery(status = "any", limit = 2000)).rows.forEach {
            Log.i(TAG, "T|${it.id}|${it.title}|${it.completed ?: 0}|${it.due ?: 0}|${lists[it.listId] ?: ""}")
        }
        Log.i(TAG, "END|${currentTimeMillis()}")
    }

    companion object {
        const val TAG = "HarnessProbe"
        const val ACTION_SETUP = "org.tasks.harness.SETUP"
        const val ACTION_DUMP = "org.tasks.harness.DUMP"
        const val ACTION_SETDEFAULT = "org.tasks.harness.SETDEFAULT"
        const val ACTION_ADDWIDGET = "org.tasks.harness.ADDWIDGET"
        const val ACTION_PROBEJOBS = "org.tasks.harness.PROBEJOBS"
    }
}
