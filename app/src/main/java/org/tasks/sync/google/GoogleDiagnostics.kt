package org.tasks.sync.google

import org.tasks.googleapis.GoogleFailure
import timber.log.Timber

/**
 * One warning line for each Google sign-in or token failure, with a FIXED tag so it survives release minification
 * (Timber's automatic tag is the class name, which R8 renames): `adb logcat -s 4TasksGoogle`. It carries the exception's
 * class and message and what 4Tasks made of it, so the real texts Google sends (admin block, test user not listed,
 * expired grant) can be read from a tester's phone and the classifier corrected. Never a token.
 */
object GoogleDiagnostics {
    const val TAG = "4TasksGoogle"

    /** A stage reached, at warning level because release builds send nothing lower to logcat. */
    fun stage(message: String) {
        Timber.tag(TAG).w("%s", message)
    }

    fun failure(stage: String, e: Throwable, failure: GoogleFailure) {
        Timber.tag(TAG).w("%s: %s: %s -> %s", stage, e.javaClass.name, e.message, failure)
    }
}
