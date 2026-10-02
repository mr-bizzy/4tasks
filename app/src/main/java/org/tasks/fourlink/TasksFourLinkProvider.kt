package org.tasks.fourlink

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
import uk.mr_biz.fourlink.android.FourLinkProvider

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
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, DoorEntryPoint::class.java)
        val logic = TasksDoorLogic(EnginePort(entry.queryEngine, entry.writer, context))
        return try {
            // A binder thread: Tasks.org's data layer suspends, and needs no main thread.
            runBlocking(Dispatchers.IO) { withTimeout(TIMEOUT_MS) { logic.perform(function.id, arguments) } }
        } catch (e: TimeoutCancellationException) {
            Outcome.Failed("4Tasks took too long to answer.")
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
