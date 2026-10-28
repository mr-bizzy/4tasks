package org.tasks.fourlink

import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.io.File
import java.time.Clock

/**
 * The first answer to each `request_id`, per caller, for 14 days (0.1.16). A watch that queued a
 * command while the phone was out of reach may send it again when an acknowledgement was lost; the
 * same caller and request_id then get the first call's answer back, and nothing is done twice.
 *
 * It holds no task details (privacy policy: the 4Link records never record the details of a task): only the caller's
 * package, the request_id, the function, the task id, what happened ([Kind]) and when. A repeat's summary is rebuilt from
 * the task as it is now.
 *
 * Kept in one small JSON file in the app's private storage, so it survives a restart. Pure JVM, so the
 * rules are tested on the workstation. One instance per file: [mutex] keeps two copies of one request
 * arriving together from both running.
 */
class RequestLedger(private val file: File, private val clock: Clock = Clock.systemUTC()) {

    /** What the first call did. [wire] is what the file holds. */
    enum class Kind(val wire: String) {
        ADDED("added"), DONE("done"), ALREADY_DONE("already_done"), DELETED("deleted"), GONE("gone");

        companion object {
            fun fromWire(w: String?): Kind? = entries.firstOrNull { it.wire == w }
        }
    }

    data class Entry(val function: String, val taskId: Long?, val kind: Kind, val atMillis: Long)

    val mutex = Mutex()

    private var loaded: MutableMap<String, Entry>? = null

    @Synchronized
    fun get(caller: String, requestId: String): Entry? {
        val all = entries()
        if (prune(all)) save(all)
        return all[key(caller, requestId)]
    }

    @Synchronized
    fun put(caller: String, requestId: String, function: String, taskId: Long?, kind: Kind) {
        val all = entries()
        prune(all)
        all[key(caller, requestId)] = Entry(function, taskId, kind, clock.millis())
        // A ceiling on the file whatever a caller does: the oldest answers go first.
        if (all.size > MAX_ENTRIES) {
            all.entries.sortedBy { it.value.atMillis }.take(all.size - MAX_ENTRIES).forEach { all.remove(it.key) }
        }
        save(all)
    }

    private fun key(caller: String, requestId: String) = "$caller\n$requestId"

    private fun prune(all: MutableMap<String, Entry>): Boolean {
        val oldest = clock.millis() - KEEP_MILLIS
        return all.entries.removeIf { it.value.atMillis < oldest }
    }

    private fun entries(): MutableMap<String, Entry> = loaded ?: read().also { loaded = it }

    private fun read(): MutableMap<String, Entry> {
        val out = LinkedHashMap<String, Entry>()
        val text = runCatching { if (file.isFile) file.readText() else null }.getOrNull() ?: return out
        val o = runCatching { JSONObject(text) }.getOrNull() ?: return out
        for (k in o.keys()) {
            val e = o.optJSONObject(k) ?: continue
            val kind = Kind.fromWire(e.optString("k")) ?: continue
            out[k] = Entry(e.optString("f"), if (e.has("i")) e.optLong("i") else null, kind, e.optLong("t"))
        }
        return out
    }

    private fun save(all: Map<String, Entry>) {
        val o = JSONObject()
        all.forEach { (k, e) ->
            o.put(k, JSONObject().put("f", e.function).put("k", e.kind.wire).put("t", e.atMillis).also { j -> e.taskId?.let { j.put("i", it) } })
        }
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(o.toString())
                tmp.delete()
            }
        }
    }

    companion object {
        const val KEEP_DAYS = 14L
        const val KEEP_MILLIS = KEEP_DAYS * 24 * 60 * 60 * 1000
        const val MAX_ENTRIES = 5_000
        const val FILE_NAME = "4link-requests.json"
    }
}
