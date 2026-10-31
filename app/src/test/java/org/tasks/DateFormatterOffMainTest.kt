package org.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compose resources give their one lock straight to the next waiter. A coroutine on Main that waits for it can be handed the
 * lock while composition's stringResource blocks Main behind it, and then neither can run: the app froze on launch (0.1.18).
 */
class DateFormatterOffMainTest {
    private val moduleDir = File(".").absoluteFile.normalize().let { if (File(it, "src").isDirectory) it else File(it, "app") }

    @Test
    fun theTaskListLoadsItsDateFormatterOffTheMainThread() {
        val source = File(moduleDir, "src/main/java/com/todoroo/astrid/activity/TaskListFragment.kt").readText()
        assertTrue(source.contains("withContext(Dispatchers.Default) { DateFormatter.create(is24Hour) }"))
        assertFalse(source.contains("viewHolderFactory.dateFormatter = DateFormatter.create("))
    }
}
