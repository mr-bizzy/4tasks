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
 * Kept in one small JSON file in the app's private storage, so it survives a restart. Pure JVM, so the
 * rules are tested on the workstation. One instance per file: [mutex] keeps two copies of one request
 * arriving together from both running.
 */
class RequestLedger(private val file: File, private val clock: Clock = Clock.systemUTC()) {

    data class Entry(val function: String, val json: String, val atMillis: Long)

    val mutex = Mutex()

    private var loaded: MutableMap<String, Entry>? = null

    @Synchronized
    fun get(caller: String, requestId: String): Entry? {
        val all = entries()
        if (prune(all)) save(all)
        return all[key(caller, requestId)]
    }

    @Synchronized
    fun put(caller: String, requestId: String, function: String, json: String) {
        val all = entries()
        prune(all)
        all[key(caller, requestId)] = Entry(function, json, clock.millis())
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
            out[k] = Entry(e.optString("f"), e.optString("j"), e.optLong("t"))
        }
        return out
    }

    private fun save(all: Map<String, Entry>) {
        val o = JSONObject()
        all.forEach { (k, e) -> o.put(k, JSONObject().put("f", e.function).put("j", e.json).put("t", e.atMillis)) }
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
