package org.tasks.fourlink

import android.content.Context
import org.tasks.api.ApiQueryEngine
import org.tasks.api.ApiWriter
import org.tasks.api.ListQuery
import org.tasks.api.ReminderWrite
import org.tasks.api.TaskQuery
import org.tasks.api.TaskRow
import org.tasks.api.TaskWrite
import org.tasks.api.TasksContract
import org.tasks.api.completeTasks
import org.tasks.api.createTasks
import org.tasks.api.findLists
import org.tasks.api.findTasks
import org.tasks.api.setTaskReminders
import org.tasks.extensions.Context.canScheduleExactAlarms
import java.util.regex.Pattern

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
) : TasksPort {

    override suspend fun lists(): List<ListInfo> =
        engine.findLists(ListQuery(limit = 500)).rows.map { ListInfo(it.id, it.title, it.isReadOnly) }

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

    override suspend fun openTasksMatching(text: String): List<TaskInfo> {
        val lists = lists()
        return engine.findTasks(
            TaskQuery(
                status = "open",
                matches = Pattern.quote(text),
                matchFields = listOf("title"),
                limit = 50,
            ),
        ).rows.map { info(it, lists) }
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

    private fun info(row: TaskRow, lists: List<ListInfo>) = TaskInfo(
        id = row.id,
        title = row.title,
        dueMillis = row.due?.takeIf { it > 0 },
        dueAllDay = row.dueAllDay,
        listTitle = lists.firstOrNull { it.id == row.listId }?.title,
        completed = row.completed != null && row.completed!! > 0,
        repeats = !row.recurrence.isNullOrEmpty(),
    )
}
