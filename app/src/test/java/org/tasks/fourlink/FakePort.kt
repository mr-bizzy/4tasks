package org.tasks.fourlink

import java.time.Instant
import java.time.ZoneId

/** An in-memory 4Tasks for testing the door's rules. Records every write. */
class FakePort(
    var lists: List<ListInfo> = listOf(ListInfo(1, "My tasks", false), ListInfo(2, "Work", false), ListInfo(3, "Shared", true)),
    var exactAlarms: Boolean = true,
) : TasksPort {
    val tasks = mutableListOf<TaskInfo>()
    val created = mutableListOf<NewTask>()
    val reminders = mutableListOf<Pair<Long, Long>>()
    val completedIds = mutableListOf<Long>()
    var filters = mutableListOf<TaskFilter>()
    var failReminder: Exception? = null
    var failCreate: Exception? = null
    var advanceOnComplete: Long? = null
    private var nextId = 100L

    fun open(id: Long, title: String, dueMillis: Long? = null, list: String = "My tasks", repeats: Boolean = false) =
        TaskInfo(id, title, dueMillis, dueAllDay = false, listTitle = list, completed = false, repeats = repeats).also { tasks += it }

    override suspend fun lists() = lists

    override suspend fun createTask(task: NewTask): TaskInfo {
        failCreate?.let { throw it }
        created += task
        val list = lists.firstOrNull { it.id == task.listId } ?: lists.first()
        val millis = task.due?.startMillis(ZoneId.of("Europe/London"))
        return TaskInfo(nextId++, task.title, millis, task.due is Moment.Day, list.title, false, false).also { tasks += it }
    }

    override suspend fun addReminder(taskId: Long, triggerAtMillis: Long) {
        failReminder?.let { throw it }
        reminders += taskId to triggerAtMillis
    }

    override suspend fun listTasks(filter: TaskFilter): TaskPage {
        filters += filter
        val shown = tasks.filter { filter.includeCompleted || !it.completed }.take(filter.limit)
        return TaskPage(tasks.count { filter.includeCompleted || !it.completed }, shown)
    }

    override suspend fun task(id: Long) = tasks.firstOrNull { it.id == id }

    override suspend fun openTasksMatching(text: String) =
        tasks.filter { !it.completed && it.title.contains(text, ignoreCase = true) }

    override suspend fun openTasks() = tasks.filter { !it.completed }

    override suspend fun complete(id: Long): CompletionInfo {
        completedIds += id
        val t = tasks.first { it.id == id }
        val advanced = advanceOnComplete == id
        val after = if (advanced) t.copy(dueMillis = Instant.parse("2026-10-09T14:00:00Z").toEpochMilli()) else t.copy(completed = true)
        tasks[tasks.indexOf(t)] = after
        return CompletionInfo(after, advanced)
    }

    override fun exactAlarmsAllowed() = exactAlarms
}
