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
    const val SNAPSHOT = "tasks.snapshot"

    /** tasks.add's argument for 4Dictate's hand-over of a dictation made while the phone was locked. */
    const val NOTE = "note"

    /**
     * tasks.add and tasks.complete (0.1.16): for a caller handing over commands it queued while the phone was out of reach
     * (4Watch through 4Dictate). The same caller and request_id within 14 days get the first answer back; said_at is when the
     * user spoke. A model leaves both out.
     */
    const val REQUEST_ID = "request_id"
    const val SAID_AT = "said_at"
    const val REQUEST_ID_MAX = 64

    /** tasks.snapshot: at most this many open tasks, and the whole answer within [SNAPSHOT_MAX_CHARS]. */
    const val SNAPSHOT_MAX_TASKS = 300
    /** Well inside a binder transaction (about 1 MB, shared by the process): a Bundle string costs two bytes a character. */
    const val SNAPSHOT_MAX_CHARS = 100_000
    const val STAMP_MAX = 64

    /** Longest title or note accepted; also the schema's maxLength. */
    const val TITLE_MAX = 200
    /**
     * About an hour of speech. A dictated note must just work, so this is generous: whatever is advertised here the door really accepts (the library checks
     * arguments against this catalogue, and nothing below it limits the length). A Google Tasks list keeps the first 8,192 characters on Google's side and the
     * whole note here (docs/SYNC-DESIGN.md, "Long notes").
     */
    const val NOTES_MAX = 50_000
    const val LIST_NAME_MAX = 60
    const val WHEN_MAX = 25

    private const val WHEN_HELP = "Local time with no zone: YYYY-MM-DD, or YYYY-MM-DDTHH:MM."

    private val requestIdField = Schema.Str(
        REQUEST_ID_MAX,
        description = "Only for a caller handing over queued commands: sent again, the first answer comes back. Leave out otherwise.",
    )
    private val saidAtField = Schema.Str(
        WHEN_MAX,
        description = "Only for a caller handing over queued commands: when the user spoke, YYYY-MM-DDTHH:MM local. Leave out otherwise.",
    )
    private val repeatedField = Schema.Bool(description = "True when this request_id was done before: this is that first answer, and nothing was done again.")

    val CATALOGUE = Catalogue(
        app = APP,
        functions = listOf(
            FunctionSpec(
                id = ADD, version = "1.2", title = "Add a task or reminder",
                description = "Creates a task. title is what to do, in the user's words. due is when it is due and " +
                    "reminder is when to be notified, both local time. For \"remind me at 3\" send both. " +
                    "Omit list for the default list. For a note (\"make a note\", \"jot down\") send list \"Notes\".",
                effect = Effect.CREATE,
                input = Schema.Obj(
                    properties = linkedMapOf(
                        "title" to Schema.Str(TITLE_MAX, description = "What to do, as a short clean sentence."),
                        "notes" to Schema.Str(NOTES_MAX, description = "Extra detail. For a note or anything long the user dictated, use their own words.", fromSpeech = true),
                        "due" to Schema.Str(WHEN_MAX, description = "When it is due. $WHEN_HELP A date alone means all day."),
                        "reminder" to Schema.Str(WHEN_MAX, description = "When to notify. Needs a time: YYYY-MM-DDTHH:MM, local."),
                        "list" to Schema.Str(LIST_NAME_MAX, description = "Name of an existing list; \"the shopping list\" finds \"Shopping\". Leave out for the default."),
                        "allow_past" to Schema.Bool(description = "True only if the user asked for a reminder time already past."),
                        // Not for a model to fill: 4Dictate sets it itself, and the function's description does not mention it.
                        NOTE to Schema.Bool(description = "Only for 4Dictate handing over a dictation made while the phone was locked; leave out otherwise."),
                        REQUEST_ID to requestIdField,
                        SAID_AT to saidAtField,
                    ),
                    required = setOf("title"),
                ),
                output = Schema.Obj(
                    linkedMapOf(
                        "id" to Schema.Num(description = "The new task's id."),
                        "summary" to Schema.Str(description = "One line saying what was created."),
                        "repeated" to repeatedField,
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
                id = COMPLETE, version = "1.1", title = "Mark a task done",
                description = "Completes one task. Send the task's id, or part of its title, which must match exactly " +
                    "one open task. A repeating task moves to its next date instead of staying done.",
                effect = Effect.CHANGE,
                input = Schema.Obj(
                    linkedMapOf(
                        "id" to Schema.Num(description = "The task's id, if known."),
                        "title" to Schema.Str(TITLE_MAX, description = "Part of the task's title, as the user said it."),
                        REQUEST_ID to requestIdField,
                        SAID_AT to saidAtField,
                    ),
                ),
                output = Schema.Obj(
                    linkedMapOf(
                        "summary" to Schema.Str(description = "One line saying what was completed, or that it was already done or deleted."),
                        "repeated" to repeatedField,
                    ),
                ),
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
            FunctionSpec(
                id = SNAPSHOT, version = "1.0", title = "Take a copy of the open tasks",
                description = "For a device that answers questions while the phone is out of reach, not for a conversation: the list names " +
                    "and up to $SNAPSHOT_MAX_TASKS open tasks, soonest due first, with a stamp. Send that stamp as since next time; if nothing " +
                    "changed, only unchanged and the stamp come back.",
                effect = Effect.READ,
                input = Schema.Obj(
                    linkedMapOf(
                        "since" to Schema.Str(STAMP_MAX, description = "The stamp from the last snapshot. Leave out for a full one."),
                    ),
                ),
                output = Schema.Obj(
                    linkedMapOf(
                        "unchanged" to Schema.Bool(description = "True when nothing changed since the stamp sent; nothing else but stamp and taken_at then."),
                        "stamp" to Schema.Str(STAMP_MAX, description = "Changes whenever any task or list changes."),
                        "taken_at" to Schema.Str(WHEN_MAX, description = "When this was taken, YYYY-MM-DDTHH:MM:SS local."),
                        "lists" to Schema.Str(description = "The list names, separated by commas, as lists.list."),
                        "count" to Schema.Num(description = "How many open tasks there are in all."),
                        "tasks" to Schema.Str(description = "A JSON array: {id, title, due, reminder, list, repeats} per open task, soonest due first."),
                        "truncated" to Schema.Bool(description = "True when not every open task is in tasks."),
                    ),
                ),
            ),
        ),
    )
}
