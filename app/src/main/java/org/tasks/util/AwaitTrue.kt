package org.tasks.util

import kotlinx.coroutines.delay

/**
 * Checks [check] up to [attempts] times, [delayMs] apart (no wait after the last), and says whether it ever held.
 * For a condition that the system reports a moment late, such as "there is a network" just after a process start.
 */
suspend fun awaitTrue(attempts: Int, delayMs: Long, check: () -> Boolean): Boolean {
    repeat(attempts) { attempt ->
        if (check()) return true
        if (attempt < attempts - 1) delay(delayMs)
    }
    return false
}
