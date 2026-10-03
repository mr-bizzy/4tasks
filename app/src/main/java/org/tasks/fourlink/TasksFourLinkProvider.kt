package org.tasks.fourlink

import android.os.Handler
import android.os.Looper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.tasks.api.ApiQueryEngine
import org.tasks.api.ApiWriter
import uk.mr_biz.fourlink.Caller
import uk.mr_biz.fourlink.Catalogue
import uk.mr_biz.fourlink.FunctionSpec
import uk.mr_biz.fourlink.Outcome
import uk.mr_biz.fourlink.Standing
import uk.mr_biz.fourlink.android.FourLinkProvider
import uk.mr_biz.fourlink.android.FourLinkStores
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 4Tasks' 4Link door, at authority `<applicationId>.4link`. The library decides
 * WHO may call (family by certificate, or a pairing the user approved); this
 * only says what the functions do. It is the only door into 4Tasks' data: the
 * Tasks.org content providers and AppFunctions service are not in this app.
 *
 * No UI is ever shown during a call (spec section 4).
 */
class TasksFourLinkProvider : FourLinkProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DoorEntryPoint {
        val queryEngine: ApiQueryEngine
        val writer: ApiWriter
    }

    override fun appName() = TasksCatalogue.APP

    override fun catalogue(): Catalogue = TasksCatalogue.CATALOGUE

    override fun perform(function: FunctionSpec, arguments: JSONObject, caller: Caller): Outcome {
        val context = requireNotNull(context)
        awaitApplicationStart()
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, DoorEntryPoint::class.java)
        val logic = TasksDoorLogic(EnginePort(entry.queryEngine, entry.writer, context), mayNameTasks = mayNameTasks(context, caller))
        return try {
            // A binder thread: Tasks.org's data layer suspends, and needs no main thread.
            runBlocking(Dispatchers.IO) { withTimeout(TIMEOUT_MS) { logic.perform(function.id, arguments) } }
        } catch (e: TimeoutCancellationException) {
            Outcome.Failed("4Tasks took too long to answer.")
        }
    }

    /** Family (our own apps), or a paired app that was granted tasks.list, may be told task titles. */
    private fun mayNameTasks(context: android.content.Context, caller: Caller): Boolean {
        val stores = FourLinkStores.of(context)
        return when (stores.gate.standing(caller)) {
            Standing.FAMILY -> true
            Standing.PAIRED -> stores.pairings.get(caller.packageName)?.allows(TasksCatalogue.LIST) == true
            Standing.UNKNOWN -> false
        }
    }

    /**
     * A call can arrive on a binder thread while a cold-started process is still inside
     * Application.onCreate (providers are installed in the same main-thread step), when
     * Tasks.org's WorkManager is not yet configured. A message posted to the main thread
     * only runs after that step, so waiting for it waits for the app to be ready.
     */
    private fun awaitApplicationStart() {
        val main = Looper.getMainLooper()
        if (Looper.myLooper() == main) return // same-process call: the app is already up
        val turn = CountDownLatch(1)
        Handler(main).post { turn.countDown() }
        turn.await(START_WAIT_MS, TimeUnit.MILLISECONDS)
    }

    private companion object {
        const val START_WAIT_MS = 5_000L
        const val TIMEOUT_MS = 10_000L
    }
}
