package org.tasks

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A task made while the app process was started by something other than the user (the 4Link door, a voice add from 4Dictate)
 * was not synced until the app was next opened, because SyncAdapters, the thing that turns a changed task into a sync, was
 * only built when the list screen first needed it. TasksApplication must build it at process start. (A source check: the
 * application class cannot be started in a unit test; SyncAdaptersDebounceTest holds the behaviour that makes this enough.)
 */
class SyncStartsWithTheProcessTest {
    @Test fun `the application builds the sync debouncer in backgroundWork, not only on resume`() {
        val base = File(".").absoluteFile.normalize().let { if (File(it, "app").isDirectory) File(it, "app") else it }
        val text = File(base, "src/main/java/org/tasks/TasksApplication.kt").readText()
        val body = text.substringAfter("private fun backgroundWork() = scope.launch {").substringBefore("override val workManagerConfiguration")
        assertTrue("backgroundWork() must call syncAdapters.get()", body.contains("syncAdapters.get()"))
    }
}
