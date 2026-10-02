package org.tasks.fourlink

import uk.mr_biz.fourlink.Catalogue
import uk.mr_biz.fourlink.Effect
import uk.mr_biz.fourlink.FunctionSpec
import uk.mr_biz.fourlink.Schema

/**
 * What 4Tasks offers other apps over 4Link (docs/4LINK-SPEC.md). Every string is
 * written for an AI model: plain, and within the spec's limits (title 60,
 * description 300). The schema subset has no arrays, so lists come back as text.
 * There is no delete function in phase 1.
 */
object TasksCatalogue {
    const val APP = "4Tasks"

    const val ADD = "tasks.add"
    const val LIST = "tasks.list"
    const val COMPLETE = "tasks.complete"
    const val LISTS = "lists.list"

    /** Longest title or note accepted; also the schema's maxLength. */
    const val TITLE_MAX = 200
    const val NOTES_MAX = 2000
    const val LIST_NAME_MAX = 60
    const val WHEN_MAX = 25

    private const val WHEN_HELP = "Local time with no zone: YYYY-MM-DD, or YYYY-MM-DDTHH:MM."

    val CATALOGUE = Catalogue(
        app = APP,
        functions = listOf(
            FunctionSpec(
                id = ADD, version = "1.0", title = "Add a task or reminder",
                description = "Creates a task. title is what to do, in the user's words. due is when it is due and " +
                    "reminder is when to be notified, both local time. For \"remind me at 3\" send both. " +
                    "Omit list for the default list.",
                effect = Effect.CHANGE,
                input = Schema.Obj(
                    properties = linkedMapOf(
                        "title" to Schema.Str(TITLE_MAX, description = "What to do, as a short clean sentence."),
                        "notes" to Schema.Str(NOTES_MAX, description = "Extra detail, only if the user gave some."),
                        "due" to Schema.Str(WHEN_MAX, description = "When it is due. $WHEN_HELP A date alone means all day."),
                        "reminder" to Schema.Str(WHEN_MAX, description = "When to notify. Needs a time: YYYY-MM-DDTHH:MM, local."),
                        "list" to Schema.Str(LIST_NAME_MAX, description = "Name of an existing list. Leave out for the default."),
                        "allow_past" to Schema.Bool(description = "True only if the user asked for a reminder time already past."),
                    ),
                    required = setOf("title"),
                ),
                output = Schema.Obj(
                    linkedMapOf(
                        "id" to Schema.Num(description = "The new task's id."),
                        "summary" to Schema.Str(description = "One line saying what was created."),
                    ),
                ),
            ),
            FunctionSpec(
                id = LIST, version = "1.0", title = "List tasks",
                description = "Lists open tasks, soonest due first, up to 50. Narrow by list name and by due_from and " +
                    "due_to (inclusive local dates or date-times). Set include_completed to also see finished tasks.",
                effect = Effect.READ,
                input = Schema.Obj(
                    linkedMapOf(
                        "list" to Schema.Str(LIST_NAME_MAX, description = "Only this list. Leave out for all lists."),
                        "due_from" to Schema.Str(WHEN_MAX, description = "Only tasks due on or after this. $WHEN_HELP"),
                        "due_to" to Schema.Str(WHEN_MAX, description = "Only tasks due on or before this. A date alone means the end of that day."),
                        "include_completed" to Schema.Bool(description = "True to include finished tasks."),
                    ),
                ),
                output = Schema.Obj(
                    linkedMapOf(
                        "count" to Schema.Num(description = "How many tasks match in all."),
                        "tasks" to Schema.Str(description = "One line per task: id, title, when due, list."),
                    ),
                ),
            ),
            FunctionSpec(
                id = COMPLETE, version = "1.0", title = "Mark a task done",
                description = "Completes one task. Send the task's id, or part of its title, which must match exactly " +
                    "one open task. A repeating task moves to its next date instead of staying done.",
                effect = Effect.CHANGE,
                input = Schema.Obj(
                    linkedMapOf(
                        "id" to Schema.Num(description = "The task's id, if known."),
                        "title" to Schema.Str(TITLE_MAX, description = "Part of the task's title, as the user said it."),
                    ),
                ),
                output = Schema.Obj(linkedMapOf("summary" to Schema.Str(description = "One line saying what was completed."))),
            ),
            FunctionSpec(
                id = LISTS, version = "1.0", title = "List the task lists",
                description = "Returns the names of the user's task lists, so a list can be named when adding or finding tasks.",
                effect = Effect.READ,
                output = Schema.Obj(
                    linkedMapOf(
                        "count" to Schema.Num(description = "How many lists there are."),
                        "lists" to Schema.Str(description = "The list names, separated by commas."),
                    ),
                ),
            ),
        ),
    )
}
