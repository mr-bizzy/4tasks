package org.tasks.fourlink

import org.json.JSONObject
import uk.mr_biz.fourlink.Outcome
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/** A list of tasks, as the door needs to know it. */
data class ListInfo(val id: Long, val title: String, val readOnly: Boolean)

data class TaskInfo(
    val id: Long,
    val title: String,
    val dueMillis: Long?,
    val dueAllDay: Boolean,
    val listTitle: String?,
    val completed: Boolean,
    val repeats: Boolean,
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
    suspend fun complete(id: Long): CompletionInfo
    fun exactAlarmsAllowed(): Boolean
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
) {
    private val zone get() = clock.zone

    suspend fun perform(functionId: String, args: JSONObject): Outcome = try {
        when (functionId) {
            TasksCatalogue.ADD -> add(args)
            TasksCatalogue.LIST -> list(args)
            TasksCatalogue.COMPLETE -> complete(args)
            TasksCatalogue.LISTS -> lists()
            else -> Outcome.Failed("4Tasks has no code for “$functionId”.")
        }
    } catch (e: BadArgument) {
        Outcome.BadArguments(e.message.orEmpty())
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
        if (reminder is Moment.DayTime && !allowPast && reminder.at.isBefore(now())) {
            throw BadArgument(
                "The reminder time ${DoorDates.describe(reminder, now().toLocalDate(), locale)} has already passed " +
                    "(it is now ${DoorDates.describe(Moment.DayTime(now()), now().toLocalDate(), locale)}). Send a later time.",
            )
        }
        val listName = a.text("list")
        val list = listName?.let { resolveList(port.lists(), it, forWriting = true) }

        val created = port.createTask(NewTask(title, notes, due, list?.id))
        var reminderNote = ""
        if (reminder is Moment.DayTime) {
            try {
                port.addReminder(created.id, reminder.startMillis(zone))
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

        val today = now().toLocalDate()
        val parts = buildList {
            due?.let { add(DoorDates.describe(it, today, locale)) }
            if (reminder != null) {
                add(if (due == reminder) "reminder set" else "reminder set for ${DoorDates.describe(reminder, today, locale)}")
            }
            (list?.title ?: created.listTitle)?.takeIf { listName != null }?.let { add("on $it") }
        }
        val summary = created.title + (if (parts.isEmpty()) " — added" else " — " + parts.joinToString(", ")) +
            "." + reminderNote
        return Outcome.Ok(JSONObject().put("id", created.id).put("summary", summary).toString())
    }

    // ---- tasks.list ------------------------------------------------------

    private suspend fun list(a: JSONObject): Outcome {
        val listName = a.text("list")
        val list = listName?.let { resolveList(port.lists(), it, forWriting = false) }
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

        val target: TaskInfo = if (id != null) {
            port.task(id) ?: throw BadArgument("There is no task with id $id.")
        } else {
            pickByTitle(title!!, port.openTasksMatching(title))
        }
        if (target.completed) return Outcome.Ok(JSONObject().put("summary", "“${target.title}” was already done.").toString())

        val done = port.complete(target.id)
        val summary = if (done.advanced) {
            val next = done.task?.let { fmtDue(it) }
            "Done: ${target.title}. It repeats" + (next?.let { ", so the next one is $it." } ?: ".")
        } else {
            "Done: ${target.title}."
        }
        return Outcome.Ok(JSONObject().put("summary", summary).toString())
    }

    /** Exactly one open task, or a sentence naming the candidates. A whole-title match wins over a part-title match. */
    private fun pickByTitle(wanted: String, candidates: List<TaskInfo>): TaskInfo {
        if (candidates.isEmpty()) throw BadArgument("No open task matches “$wanted”.")
        val exact = candidates.filter { it.title.trim().equals(wanted, ignoreCase = true) }
        if (exact.size == 1) return exact.single()
        if (candidates.size == 1) return candidates.single()
        val shown = candidates.take(MAX_CANDIDATES).joinToString("; ") { "“${it.title}” (id ${it.id})" }
        val more = if (candidates.size > MAX_CANDIDATES) " and ${candidates.size - MAX_CANDIDATES} more" else ""
        throw BadArgument("“$wanted” matches several open tasks: $shown$more. Nothing was completed. Say more of the title, or send the id.")
    }

    // ---- lists.list ------------------------------------------------------

    private suspend fun lists(): Outcome {
        val all = port.lists()
        return Outcome.Ok(
            JSONObject().put("count", all.size).put("lists", all.joinToString(", ") { it.title }).toString(),
        )
    }

    // ---- shared ----------------------------------------------------------

    private fun resolveList(all: List<ListInfo>, name: String, forWriting: Boolean): ListInfo {
        val match = all.firstOrNull { it.title.trim().equals(name, ignoreCase = true) }
            ?: throw BadArgument(
                "There is no list called “$name”. The lists are: ${all.joinToString(", ") { it.title }.ifEmpty { "(none)" }}.",
            )
        if (forWriting && match.readOnly) throw BadArgument("The list “${match.title}” is read-only.")
        return match
    }

    companion object {
        const val MAX_LISTED = 50
        const val MAX_CANDIDATES = 5
    }
}
