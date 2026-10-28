package org.tasks.fourlink

import android.content.Context
import com.todoroo.astrid.alarms.AlarmCalculator
import org.tasks.data.entity.Alarm
import org.tasks.api.AccountQuery
import org.tasks.api.ApiQueryEngine
import org.tasks.api.ApiWriter
import org.tasks.api.ListQuery
import org.tasks.api.ListWrite
import org.tasks.api.ReminderWrite
import org.tasks.api.TaskQuery
import org.tasks.api.TaskRow
import org.tasks.api.TaskWrite
import org.tasks.api.TasksContract
import org.tasks.api.completeTasks
import org.tasks.api.createList
import org.tasks.api.createTasks
import org.tasks.api.findAccounts
import org.tasks.api.findLists
import org.tasks.api.findTasks
import org.tasks.api.setTaskReminders
import org.tasks.data.dao.AlarmDao
import org.tasks.data.dao.CaldavDao
import org.tasks.data.dao.TaskDao
import org.tasks.data.getOrCreateLocalAccount
import org.tasks.extensions.Context.canScheduleExactAlarms
import org.tasks.preferences.AppPreferences
import java.util.Locale

/**
 * The door's view of Tasks.org, through the same write layer ([ApiWriter]) and
 * read layer ([ApiQueryEngine]) that Tasks.org's own API uses. Writes go through
 * its TaskSaver, TaskCompleter and AlarmService, so alarms, notifications and
 * widgets behave exactly as if the task were made in the app.
 */
class EnginePort(
    private val engine: ApiQueryEngine,
    private val writer: ApiWriter,
    private val context: Context,
    private val caldavDao: CaldavDao,
    private val taskDao: TaskDao,
    private val alarmDao: AlarmDao,
    private val alarmCalculator: AlarmCalculator,
    private val preferences: AppPreferences,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : TasksPort {

    override suspend fun lists(): List<ListInfo> {
        val google = engine.findAccounts(AccountQuery(limit = 100)).rows
            .filter { it.type == TasksContract.Accounts.TYPE_GOOGLE_TASKS }
            .map { it.id }
            .toSet()
        return engine.findLists(ListQuery(limit = 500)).rows
            .map { ListInfo(it.id, it.title, it.isReadOnly, googleTasks = it.accountId in google) }
    }

    override suspend fun createTask(task: NewTask): TaskInfo {
        val creation = engine.createTasks(
            writer,
            listOf(
                TaskWrite(
                    title = task.title,
                    notes = task.notes,
                    due = task.due?.engineValue,
                    listId = task.listId,
                ),
            ),
        )
        return info(creation.tasks.single(), lists())
    }

    override suspend fun addReminder(taskId: Long, triggerAtMillis: Long) {
        engine.setTaskReminders(
            writer,
            taskId = taskId,
            add = listOf(ReminderWrite(type = TasksContract.Reminders.TYPE_DATE_TIME, triggerAt = triggerAtMillis)),
            removeReminderIds = emptyList(),
        )
    }

    override suspend fun listTasks(filter: TaskFilter): TaskPage {
        val page = engine.findTasks(
            TaskQuery(
                listIds = listOfNotNull(filter.listId),
                status = if (filter.includeCompleted) "any" else "open",
                // The engine's bounds are strict; ours are inclusive.
                dueAfter = filter.dueFromMillis?.let { it - 1 },
                dueBefore = filter.dueToMillis?.let { it + 1 },
                sort = "due",
                limit = filter.limit,
            ),
        )
        val lists = lists()
        return TaskPage(page.total, page.rows.map { info(it, lists) })
    }

    override suspend fun task(id: Long): TaskInfo? =
        engine.findTasks(TaskQuery(ids = listOf(id), status = "any")).rows.firstOrNull()?.let { info(it, lists()) }

    /**
     * Open tasks whose title contains [text], ignoring case. Done here rather than with the
     * engine's own `matches` filter, which is a regular expression and, on a phone, never
     * matched (see the note in ApiModels.kt); a plain substring is also what a person means.
     */
    override suspend fun openTasksMatching(text: String): List<TaskInfo> {
        val needle = text.trim().lowercase(Locale.ROOT)
        return openTasks().filter { it.title.lowercase(Locale.ROOT).contains(needle) }
    }

    /**
     * Every open task, a page at a time. The engine's own `matches` filter is a regular expression
     * and, on a phone, never matched (see the note in ApiModels.kt), so titles are compared here.
     */
    override suspend fun openTasks(): List<TaskInfo> {
        val lists = lists()
        val found = mutableListOf<TaskInfo>()
        var offset = 0
        while (true) {
            val page = engine.findTasks(TaskQuery(status = "open", limit = PAGE, offset = offset))
            page.rows.forEach { found += info(it, lists) }
            offset += page.rows.size
            if (page.rows.isEmpty() || offset >= page.total) break
        }
        return found
    }

    override suspend fun complete(id: Long): CompletionInfo {
        val completion = engine.completeTasks(writer, listOf(id), completed = true)
        val lists = lists()
        return CompletionInfo(
            task = completion.tasks.firstOrNull()?.let { info(it, lists) },
            advanced = id in completion.advancedTaskIds,
        )
    }

    override fun exactAlarmsAllowed(): Boolean = context.canScheduleExactAlarms()

    override suspend fun createLocalList(title: String): ListInfo {
        val account = caldavDao.getOrCreateLocalAccount()
        val row = engine.createList(writer, ListWrite(title = title, accountId = account.id))
        return ListInfo(row.id, row.title, row.isReadOnly)
    }

    override suspend fun markDictatedNote(taskId: Long) = DictatedNotes.mark(context, taskId)

    override suspend fun wasDeleted(id: Long): Boolean = taskDao.fetch(id)?.isDeleted == true

    override suspend fun changeMarks(): List<ChangeMark> {
        val marks = mutableListOf<ChangeMark>()
        var offset = 0
        while (true) {
            val page = engine.findTasks(TaskQuery(status = "any", limit = PAGE, offset = offset))
            page.rows.forEach { marks += ChangeMark(it.id, it.modified, it.completed != null && it.completed!! > 0) }
            offset += page.rows.size
            if (page.rows.isEmpty() || offset >= page.total) break
        }
        return marks
    }

    /**
     * Tasks.org's own reckoning (AlarmCalculator, as AlarmService uses it): for each open task, the soonest alarm still to
     * come; a snooze still to come wins, as it does there. Random reminders are left out: they have no fixed time.
     */
    override suspend fun nextReminders(): Map<Long, Long> {
        val now = nowMillis()
        val defaultDueTime = preferences.defaultDueTime()
        val byTask = alarmDao.getActiveAlarms().filter { it.type != Alarm.TYPE_RANDOM }.groupBy { it.task }
        val tasks = taskDao.fetch(byTask.keys.toList()).associateBy { it.id }
        val out = HashMap<Long, Long>()
        byTask.forEach { (taskId, alarms) ->
            val task = tasks[taskId]?.takeIf { !it.isCompleted && !it.isDeleted } ?: return@forEach
            val coming = alarms.mapNotNull { alarmCalculator.toAlarmEntry(task, it, defaultDueTime) }.filter { it.timestamp >= now }
            val at = coming.filter { it.type == Alarm.TYPE_SNOOZE }.maxOfOrNull { it.timestamp } ?: coming.minOfOrNull { it.timestamp }
            at?.let { out[taskId] = it }
        }
        return out
    }

    private fun info(row: TaskRow, lists: List<ListInfo>) = TaskInfo(
        id = row.id,
        title = row.title,
        dueMillis = row.due?.takeIf { it > 0 },
        dueAllDay = row.dueAllDay,
        listTitle = lists.firstOrNull { it.id == row.listId }?.title,
        listId = row.listId,
        completed = row.completed != null && row.completed!! > 0,
        repeats = !row.recurrence.isNullOrEmpty(),
    )

    private companion object {
        const val PAGE = 500
    }
}
