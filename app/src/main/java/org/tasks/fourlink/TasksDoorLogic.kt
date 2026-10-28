package org.tasks.fourlink

import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import uk.mr_biz.fourlink.Outcome
import uk.mr_biz.fourlink.Suggestion
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A list of tasks, as the door needs to know it. */
data class ListInfo(val id: Long, val title: String, val readOnly: Boolean, val googleTasks: Boolean = false)

data class TaskInfo(
    val id: Long,
    val title: String,
    val dueMillis: Long?,
    val dueAllDay: Boolean,
    val listTitle: String?,
    val completed: Boolean,
    val repeats: Boolean,
    val listId: Long? = null,
)

data class NewTask(val title: String, val notes: String?, val due: Moment?, val listId: Long?)

data class TaskFilter(
    val listId: Long?,
    val dueFromMillis: Long?,
    val dueToMillis: Long?,
    val includeCompleted: Boolean,
    val limit: Int,
)

data class TaskPage(val total: Int, val tasks: List<TaskInfo>)

/** What makes a snapshot's stamp change: one per task in any state (not deleted), with its last-modified time. */
data class ChangeMark(val id: Long, val modified: Long?, val completed: Boolean)

/** [task] is the task as it is after completing; [advanced] when a repeating task moved to its next date instead. */
data class CompletionInfo(val task: TaskInfo?, val advanced: Boolean)

/**
 * Everything the door needs from Tasks.org, in terms that need no Android and no
 * database, so the rules below can be tested on the workstation. [EnginePort]
 * is the real one; it calls Tasks.org's own data layer, so alarms and
 * notifications behave exactly as if the user had made the task in the app.
 *
 * Implementations throw [IllegalArgumentException] for a bad request,
 * [UnsupportedOperationException] for a read-only list, anything else for a failure.
 */
interface TasksPort {
    suspend fun lists(): List<ListInfo>
    suspend fun createTask(task: NewTask): TaskInfo
    suspend fun addReminder(taskId: Long, triggerAtMillis: Long)
    suspend fun listTasks(filter: TaskFilter): TaskPage
    suspend fun task(id: Long): TaskInfo?
    suspend fun openTasksMatching(text: String): List<TaskInfo>
    /** Every open task, for matching a title by sound and for naming the nearest ones. */
    suspend fun openTasks(): List<TaskInfo>
    suspend fun complete(id: Long): CompletionInfo
    fun exactAlarmsAllowed(): Boolean
    /** Makes a list in the local account on this phone (creating that account if there is none). */
    suspend fun createLocalList(title: String): ListInfo
    /** Marks the task as a dictation 4Dictate handed over (tasks.add note=true); see [DictatedNotes]. */
    suspend fun markDictatedNote(taskId: Long)
    /** True when the task exists but was deleted (Tasks.org keeps a deleted task until it is purged). */
    suspend fun wasDeleted(id: Long): Boolean
    /** Every task that is not deleted, open or done, with its last-modified time. */
    suspend fun changeMarks(): List<ChangeMark>
    /** For each open task with one, the next reminder still to come (epoch millis), as Tasks.org's own alarms work it out. */
    suspend fun nextReminders(): Map<Long, Long>
}

/**
 * What each 4Link function does (the rules of docs/PHASE0-PLAN.md section 4).
 * Everything is checked before anything is written. The sentences returned are
 * read by the user on 4Dictate's card, and by the model.
 */
class TasksDoorLogic(
    private val port: TasksPort,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val locale: Locale = Locale.getDefault(),
    /**
     * Whether this caller may be told task titles in an error or a suggestion: our own apps, or a
     * paired app that was granted tasks.list. Anyone else could otherwise read titles by guessing
     * words at tasks.complete, which they were never allowed to read.
     */
    private val mayNameTasks: Boolean = true,
    /** The caller's package, which a request_id belongs to. */
    private val callerPackage: String? = null,
    /** Where first answers to a request_id are kept; null turns request_id off (it is then ignored). */
    private val ledger: RequestLedger? = null,
) {
    private val zone get() = clock.zone

    /**
     * A call with a request_id this caller already used (within 14 days) gets the first answer back, marked repeated, and
     * nothing is done. Only an answer that succeeded is kept: a refused or failed call can be sent again and runs again.
     */
    suspend fun perform(functionId: String, args: JSONObject): Outcome {
        val requestId = if (functionId == TasksCatalogue.ADD || functionId == TasksCatalogue.COMPLETE) args.text(TasksCatalogue.REQUEST_ID) else null
        if (requestId == null || ledger == null || callerPackage == null) return run(functionId, args)
        return ledger.mutex.withLock {
            val first = ledger.get(callerPackage, requestId)
            when {
                first == null -> run(functionId, args).also {
                    if (it is Outcome.Ok) ledger.put(callerPackage, requestId, functionId, it.json)
                }
                first.function != functionId -> Outcome.BadArguments(
                    "request_id “$requestId” was already used for ${first.function}. Nothing was done.",
                )
                else -> Outcome.Ok(JSONObject(first.json).put("repeated", true).toString())
            }
        }
    }

    private suspend fun run(functionId: String, args: JSONObject): Outcome = try {
        when (functionId) {
            TasksCatalogue.ADD -> add(args)
            TasksCatalogue.LIST -> list(args)
            TasksCatalogue.COMPLETE -> complete(args)
            TasksCatalogue.LISTS -> lists()
            TasksCatalogue.SNAPSHOT -> snapshot(args)
            else -> Outcome.Failed("4Tasks has no code for “$functionId”.")
        }
    } catch (e: BadArgument) {
        Outcome.BadArguments(e.message.orEmpty(), e.suggestion)
    } catch (e: IllegalArgumentException) {
        Outcome.BadArguments(e.message ?: "An argument was not accepted.")
    } catch (e: UnsupportedOperationException) {
        Outcome.Refused(e.message ?: "That list cannot be changed.")
    }

    private fun now(): LocalDateTime = LocalDateTime.now(clock)

    private fun JSONObject.text(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    private fun fmtDue(t: TaskInfo): String? = t.dueMillis?.let { ms ->
        val at = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
        DoorDates.describe(if (t.dueAllDay) Moment.Day(at.toLocalDate()) else Moment.DayTime(at), now().toLocalDate(), locale)
    }

    // ---- tasks.add -------------------------------------------------------

    private suspend fun add(a: JSONObject): Outcome {
        val title = a.text("title") ?: throw BadArgument("title is empty. Say what the task is.")
        val notes = a.text("notes")
        val due = a.text("due")?.let { DoorDates.parse("due", it) }
        val reminder = a.text("reminder")?.let { DoorDates.parse("reminder", it) }
        if (reminder != null && reminder !is Moment.DayTime) {
            throw BadArgument("reminder needs a time as well as a day, like 2026-10-03T15:00.")
        }
        val allowPast = a.optBoolean("allow_past", false)
        val saidAt = saidAt(a)
        // Delivered late (said_at): a reminder the user asked for at or after the moment they spoke, which has passed while the
        // command waited, goes off at once. One before said_at was already past when they spoke, and keeps the rule below.
        val fireNow = reminder is Moment.DayTime && reminder.at.isBefore(now()) && saidAt != null && !reminder.at.isBefore(saidAt)
        if (reminder is Moment.DayTime && !allowPast && !fireNow && reminder.at.isBefore(now())) {
            throw BadArgument(
                "The reminder time ${DoorDates.describe(reminder, now().toLocalDate(), locale)} has already passed " +
                    "(it is now ${DoorDates.describe(Moment.DayTime(now()), now().toLocalDate(), locale)}). Send a later time.",
            )
        }
        // note=true: 4Dictate handing over a dictation made while the phone was locked. It always goes to the Notes list (found as
        // any other list name is), which is made on this phone when there is none, and the task is marked as a 4Dictate note.
        val note = a.optBoolean(TasksCatalogue.NOTE, false)
        val listName = if (note) NOTES_LIST else a.text("list")
        var list = listName?.let { resolveList(port.lists(), it, forWriting = true, TasksCatalogue.ADD, a, notesMayFallBack = true) }
        val madeNotesList = note && list == null
        if (madeNotesList) list = port.createLocalList(NOTES_LIST)
        // A request for the Notes list with no such list is the one case where a missing list is not an error: it goes to the default list, and the summary says so.
        val fellBackFromNotes = !note && listName != null && list == null

        val created = port.createTask(NewTask(title, notes, due, list?.id))
        if (note) port.markDictatedNote(created.id)
        var reminderNote = ""
        if (reminder is Moment.DayTime) {
            try {
                port.addReminder(created.id, if (fireNow) clock.millis() else reminder.startMillis(zone))
            } catch (e: Exception) {
                return Outcome.Failed(
                    "“$title” was added, but its reminder could not be set (${e.message ?: "unknown reason"}). " +
                        "Set the reminder in 4Tasks.",
                )
            }
            reminderNote = if (!port.exactAlarmsAllowed()) {
                " This phone is not allowing exact alarms for 4Tasks, so the reminder may come late."
            } else ""
        }

        // Google Tasks cannot hold a reminder or a time of day (its API carries a due DATE only), so say what stays on the phone.
        val onGoogle = port.lists().firstOrNull { it.id == (created.listId ?: list?.id) }?.googleTasks == true
        val googleNote = when {
            !onGoogle -> ""
            reminder != null -> " Google Tasks cannot keep reminders, so this reminder stays on this phone and is not synced."
            due is Moment.DayTime -> " Google Tasks keeps only the date of a due time, so the time of day stays on this phone."
            else -> ""
        }

        val today = now().toLocalDate()
        val parts = buildList {
            due?.let { add(DoorDates.describe(it, today, locale)) }
            if (fireNow) {
                add("the reminder for ${DoorDates.describe(reminder!!, today, locale)} had passed when this arrived, so it goes off now")
            } else if (reminder != null) {
                add(if (due == reminder) "reminder set" else "reminder set for ${DoorDates.describe(reminder, today, locale)}")
            }
        }
        val where = list?.title ?: created.listTitle
        val notesNote = when {
            fellBackFromNotes -> " There is no Notes list, so it went to ${where?.let { "$it, " }.orEmpty()}the default list."
            madeNotesList -> " There was no Notes list, so 4Tasks made one on this phone."
            else -> ""
        }
        // 4Dictate shows this line when it acts without asking, so it must say WHICH list the task went to.
        val summary = (if (where != null) "Added to $where: " else "Added: ") + created.title +
            (if (parts.isEmpty()) "" else " — " + parts.joinToString(", ")) +
            saidNote(saidAt) + "." + notesNote + reminderNote + googleNote
        return Outcome.Ok(JSONObject().put("id", created.id).put("summary", summary).toString())
    }

    // ---- tasks.list ------------------------------------------------------

    private suspend fun list(a: JSONObject): Outcome {
        val listName = a.text("list")
        val list = listName?.let { resolveList(port.lists(), it, forWriting = false, TasksCatalogue.LIST, a) }
        val from = a.text("due_from")?.let { DoorDates.parse("due_from", it) }?.startMillis(zone)
        val to = a.text("due_to")?.let { DoorDates.parse("due_to", it) }?.let {
            if (it is Moment.Day) DoorDates.endOfDayMillis(it.date, zone) else it.startMillis(zone)
        }
        if (from != null && to != null && from > to) throw BadArgument("due_from is after due_to.")
        val page = port.listTasks(TaskFilter(list?.id, from, to, a.optBoolean("include_completed", false), MAX_LISTED))
        val lines = page.tasks.joinToString("\n") { t ->
            buildString {
                append('#').append(t.id).append(' ')
                if (t.completed) append("(done) ")
                append(t.title)
                fmtDue(t)?.let { append(" — due ").append(it) }
                t.listTitle?.let { append(" [").append(it).append(']') }
            }
        }
        return Outcome.Ok(
            JSONObject().put("count", page.total).put("tasks", lines.ifEmpty { "No tasks." }).toString(),
        )
    }

    // ---- tasks.complete --------------------------------------------------

    private suspend fun complete(a: JSONObject): Outcome {
        val id = if (a.has("id") && !a.isNull("id")) a.optLong("id", -1L).takeIf { it > 0 } else null
        val title = a.text("title")
        if (id == null && title == null) throw BadArgument("Send the task's id or part of its title.")

        val saidAt = saidAt(a)
        val said = saidNote(saidAt)
        // A command handed over late (4Watch) may find its task already done or deleted: that is an answer, not an error a
        // caller would send again and again.
        val handedOver = a.text(TasksCatalogue.REQUEST_ID) != null || saidAt != null
        val target: TaskInfo = if (id != null) {
            port.task(id) ?: when {
                port.wasDeleted(id) -> return Outcome.Ok(JSONObject().put("summary", "That task was deleted$said.").toString())
                handedOver -> return Outcome.Ok(JSONObject().put("summary", "That task is no longer in 4Tasks$said.").toString())
                else -> throw BadArgument("There is no task with id $id.")
            }
        } else {
            pickByTitle(title!!)
        }
        if (target.completed) return Outcome.Ok(JSONObject().put("summary", "Already done: ${target.title}$said.").toString())

        val done = port.complete(target.id)
        val summary = if (done.advanced) {
            val next = done.task?.let { fmtDue(it) }
            "Done: ${target.title}$said. It repeats" + (next?.let { ", so the next one is $it." } ?: ".")
        } else {
            "Done: ${target.title}$said."
        }
        return Outcome.Ok(JSONObject().put("summary", summary).toString())
    }

    /**
     * Exactly one open task, found by its words. A whole-title match wins over a part-title match.
     * If no title contains the words, tasks that SOUND like them are looked for, but never completed:
     * a single one is offered as a suggestion for the user to confirm (spec 11a), several are named.
     */
    private suspend fun pickByTitle(wanted: String): TaskInfo {
        val candidates = port.openTasksMatching(wanted)
        if (candidates.isNotEmpty()) {
            val exact = candidates.filter { it.title.trim().equals(wanted, ignoreCase = true) }
            if (exact.size == 1) return exact.single()
            if (candidates.size == 1) return candidates.single()
            if (!mayNameTasks) {
                throw BadArgument("“$wanted” matches several open tasks. Nothing was completed. Say more of the title.")
            }
            throw BadArgument("“$wanted” matches several open tasks: ${named(candidates)}. Nothing was completed. Say more of the title, or send the id.")
        }

        val open = port.openTasks()
        val alike = open.filter { SoundsLike.matches(wanted, it.title) }
        if (!mayNameTasks) throw BadArgument("No open task matches “$wanted”.")
        when {
            alike.size == 1 -> {
                val t = alike.single()
                val title = t.title.trim().take(QUOTED_TITLE_MAX)
                throw BadArgument(
                    "No open task contains “$wanted”. One sounds like it: “$title”. Nothing was completed.",
                    Suggestion(
                        question = "Did you mean “$title”?",
                        function = TasksCatalogue.COMPLETE,
                        arguments = JSONObject().put("id", t.id),
                    ),
                )
            }
            alike.size > 1 -> throw BadArgument(
                "No open task contains “$wanted”, and several sound like it: ${named(alike)}. Nothing was completed. Say more of the title, or send the id.",
            )
            open.isEmpty() -> throw BadArgument("No open task matches “$wanted”: there are no open tasks.")
            else -> throw BadArgument(
                "No open task matches “$wanted”. Open tasks: ${named(open, withIds = false)}.",
            )
        }
    }

    private fun named(tasks: List<TaskInfo>, withIds: Boolean = true): String {
        val shown = tasks.take(MAX_CANDIDATES).joinToString("; ") {
            val t = it.title.trim().take(QUOTED_TITLE_MAX)
            if (withIds) "“$t” (id ${it.id})" else "“$t”"
        }
        val more = if (tasks.size > MAX_CANDIDATES) " and ${tasks.size - MAX_CANDIDATES} more" else ""
        return shown + more
    }

    // ---- lists.list ------------------------------------------------------

    private suspend fun lists(): Outcome {
        val all = port.lists()
        return Outcome.Ok(
            JSONObject().put("count", all.size).put("lists", all.joinToString(", ") { it.title }).toString(),
        )
    }

    // ---- tasks.snapshot --------------------------------------------------

    /**
     * Everything a watch needs to answer "what's due" while the phone is out of reach: the list names and the open tasks,
     * soonest due first, with a stamp that changes whenever any task or list changes. With since equal to the current
     * stamp, only unchanged, the stamp and taken_at come back.
     */
    private suspend fun snapshot(a: JSONObject): Outcome {
        val lists = port.lists()
        val next = port.nextReminders()
        val stamp = stamp(lists, port.changeMarks(), next)
        val takenAt = now().withNano(0).format(TAKEN_AT)
        if (a.text("since") == stamp) {
            return Outcome.Ok(JSONObject().put("unchanged", true).put("stamp", stamp).put("taken_at", takenAt).toString())
        }
        val open = port.openTasks()
            .sortedWith(compareBy<TaskInfo, Long?>(nullsLast()) { it.dueMillis }.thenBy { it.id })
        fun answer(shown: List<TaskInfo>): String {
            val tasks = JSONArray()
            shown.forEach { t ->
                tasks.put(
                    JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        t.dueMillis?.let { put("due", localText(it, allDay = t.dueAllDay)) }
                        next[t.id]?.let { put("reminder", localText(it, allDay = false)) }
                        t.listTitle?.let { put("list", it) }
                        put("repeats", t.repeats)
                    },
                )
            }
            return JSONObject()
                .put("unchanged", false)
                .put("stamp", stamp)
                .put("taken_at", takenAt)
                .put("lists", lists.joinToString(", ") { it.title })
                .put("count", open.size)
                .put("tasks", tasks.toString())
                .put("truncated", shown.size < open.size)
                .toString()
        }
        var shown = open.take(TasksCatalogue.SNAPSHOT_MAX_TASKS)
        var json = answer(shown)
        while (json.length > TasksCatalogue.SNAPSHOT_MAX_CHARS && shown.isNotEmpty()) {
            shown = shown.take(shown.size * 9 / 10)
            json = answer(shown)
        }
        return Outcome.Ok(json)
    }

    private fun localText(millis: Long, allDay: Boolean): String {
        val at = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()
        return if (allDay) at.toLocalDate().toString() else at.withSecond(0).withNano(0).format(MINUTE)
    }

    // ---- shared ----------------------------------------------------------

    /** said_at, never later than now (a watch's clock may run ahead of the phone's). */
    private fun saidAt(a: JSONObject): LocalDateTime? {
        val raw = a.text(TasksCatalogue.SAID_AT) ?: return null
        val moment = DoorDates.parse(TasksCatalogue.SAID_AT, raw) as? Moment.DayTime
            ?: throw BadArgument("said_at needs a time as well as a day, like 2026-10-03T14:02.")
        return minOf(moment.at, now())
    }

    /** " (said at 14:02)" when the user spoke more than two minutes before this arrived; "" otherwise. */
    private fun saidNote(saidAt: LocalDateTime?): String {
        if (saidAt == null || !Duration.between(saidAt, now()).minus(SAID_SHOWN_AFTER).isPositive) return ""
        val today = now().toLocalDate()
        return if (saidAt.toLocalDate() == today) " (said at ${saidAt.format(HOUR_MINUTE)})"
        else " (said ${DoorDates.describe(Moment.DayTime(saidAt), today, locale)})"
    }

    /**
     * The list the caller named. An exact name (any case) wins. Failing that, names are compared by [ListNames.key]: if exactly one list has that key it
     * is used. Two or more are never guessed between, and a name that is only close is never used: both answer bad_arguments, with a suggestion when
     * there is one clear candidate. Returns null only for a request for "Notes" when [notesMayFallBack] and there is no such list.
     */
    private fun resolveList(
        all: List<ListInfo>,
        name: String,
        forWriting: Boolean,
        functionId: String,
        args: JSONObject,
        notesMayFallBack: Boolean = false,
    ): ListInfo? {
        val exact = all.firstOrNull { it.title.trim().equals(name, ignoreCase = true) }
        val key = ListNames.key(name)
        val alike = if (exact != null) listOf(exact) else all.filter { key.isNotEmpty() && ListNames.key(it.title) == key }
        val nothingAdded = if (forWriting) " Nothing was added." else ""
        val match = when {
            alike.size == 1 -> alike.single()
            alike.size > 1 -> throw BadArgument(
                "“$name” could be any of these lists: ${listNames(alike)}.$nothingAdded Say which one.",
            )
            notesMayFallBack && ListNames.isNotes(name) -> return null
            else -> {
                val close = all.filter { ListNames.isClose(name, it.title) && !(forWriting && it.readOnly) }
                val known = "The lists are: ${all.joinToString(", ") { it.title }.ifEmpty { "(none)" }}."
                if (close.size == 1) {
                    val title = close.single().title.trim().take(LIST_QUOTED_MAX)
                    throw BadArgument(
                        "There is no list called “$name”. One is close: “$title”.$nothingAdded $known",
                        Suggestion(
                            question = "Did you mean the list “$title”?",
                            function = functionId,
                            arguments = JSONObject(args.toString()).put("list", close.single().title.trim()),
                        ),
                    )
                }
                throw BadArgument("There is no list called “$name”. $known")
            }
        }
        if (forWriting && match.readOnly) throw BadArgument("The list “${match.title}” is read-only.")
        return match
    }

    private fun listNames(lists: List<ListInfo>) = lists.joinToString(", ") { "“${it.title.trim().take(LIST_QUOTED_MAX)}”" }

    companion object {
        private val TAKEN_AT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private val SAID_SHOWN_AFTER: Duration = Duration.ofMinutes(2)

        /**
         * A short text that changes when any list (id, name, read-only) or any task (added, done, edited, removed) changes,
         * or a reminder still to come does. Not a secret: a SHA-256 of those, cut to 16 hex characters.
         */
        fun stamp(lists: List<ListInfo>, marks: List<ChangeMark>, next: Map<Long, Long>): String {
            val text = buildString {
                lists.sortedBy { it.id }.forEach { append("L").append(it.id).append('|').append(it.title).append('|').append(it.readOnly).append('\n') }
                marks.sortedBy { it.id }.forEach { append("T").append(it.id).append('|').append(it.modified).append('|').append(it.completed).append('\n') }
                next.toSortedMap().forEach { (id, at) -> append("R").append(id).append('|').append(at).append('\n') }
            }
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            return digest.take(8).joinToString("") { "%02x".format(it) }
        }

        /** The list a dictated note goes to. */
        const val NOTES_LIST = "Notes"
        const val MAX_LISTED = 50
        const val MAX_CANDIDATES = 5
        /** A task title quoted in a sentence is cut here, so a question stays within 200 characters. */
        const val QUOTED_TITLE_MAX = 120
        const val LIST_QUOTED_MAX = 60
    }
}
