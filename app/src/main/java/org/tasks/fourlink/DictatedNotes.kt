package org.tasks.fourlink

import android.content.Context
import androidx.core.content.edit

/**
 * Which tasks are 4Dictate notes: dictations 4Dictate handed over with tasks.add note=true, made while the phone was
 * locked. Their edit screen offers Copy and Reprocess. Kept on this phone by task id (ids are never reused), beside the
 * task rather than in the task table, so no database change is needed; it does not sync, and a note that comes back
 * from a backup or another device is an ordinary task.
 */
object DictatedNotes {
    private const val FILE = "fourdictate_notes"
    private const val KEY = "task_ids"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    @Synchronized
    fun mark(context: Context, taskId: Long) {
        val ids = prefs(context).getStringSet(KEY, emptySet()).orEmpty() + taskId.toString()
        prefs(context).edit(commit = true) { putStringSet(KEY, ids) }
    }

    fun isNote(context: Context, taskId: Long): Boolean =
        taskId.toString() in prefs(context).getStringSet(KEY, emptySet()).orEmpty()
}
