package org.tasks.harness

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL

/**
 * DEBUG BUILDS ONLY: a job that does nothing but say when Android ran it and whether a socket works inside it, for the sync harness
 * (`HarnessReceiver` PROBEJOBS). Logs `HarnessProbe|JOB|<mode>|ran|network=<yes/no>|http=<code or error>` so the harness can tell which
 * kinds of job Android runs for a cached app, and whether the network is really usable when they do.
 */
class HarnessProbeWork(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val mode = inputData.getString("mode") ?: "?"
        val url = inputData.getString("url") ?: ""
        val cm = applicationContext.getSystemService(ConnectivityManager::class.java)
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
        val network = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val http = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 5000
            c.readTimeout = 5000
            c.requestMethod = "GET"
            val code = c.responseCode
            c.disconnect()
            code.toString()
        } catch (e: Exception) {
            e.javaClass.simpleName
        }
        Log.i(HarnessReceiver.TAG, "JOB|$mode|ran|network=${if (network) "yes" else "no"}|http=$http|attempt=$runAttemptCount")
        if (mode.startsWith("periodic-kick")) {
            // the design under test: the periodic job only hands the work to an expedited one-time job, which Android lets use the network
            val next = androidx.work.OneTimeWorkRequest.Builder(HarnessProbeWork::class.java)
                .setInputData(androidx.work.Data.Builder().putString("mode", "kicked-expedited").putString("url", url).build())
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork("probe-kicked", androidx.work.ExistingWorkPolicy.REPLACE, next)
        }
        return Result.success()
    }
}
