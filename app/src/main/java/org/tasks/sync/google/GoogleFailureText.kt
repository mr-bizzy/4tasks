package org.tasks.sync.google

import android.content.Context
import androidx.annotation.StringRes
import org.tasks.R
import org.tasks.googleapis.GoogleAccountState
import org.tasks.googleapis.GoogleFailure
import org.tasks.sync.SyncClients

/** The words for a [GoogleFailure] or a stored account error (strings_sync_google.xml). */
object GoogleFailureText {
    /** The string for a failure, or null when there is nothing to say ([GoogleFailure.Cancelled]) or only the raw message. */
    @StringRes
    fun messageRes(failure: GoogleFailure, testingMode: Boolean = SyncClients.GOOGLE_TESTING_MODE): Int? = when (failure) {
        GoogleFailure.Cancelled -> null
        GoogleFailure.NoAccount -> R.string.google_failure_no_account
        GoogleFailure.NeedsSignIn ->
            if (testingMode) R.string.google_failure_needs_sign_in_testing else R.string.google_failure_needs_sign_in
        GoogleFailure.AdminBlocked -> R.string.google_failure_admin_blocked
        GoogleFailure.NotATester -> R.string.google_failure_not_a_tester
        GoogleFailure.AppNotSetUp -> R.string.google_failure_app_not_set_up
        GoogleFailure.Unavailable -> R.string.google_failure_unavailable
        GoogleFailure.Other -> null
    }

    /** What to show after a failed sign-in: null for a cancel (say nothing). */
    fun forSignIn(context: Context, failure: GoogleFailure, rawMessage: String?): String? {
        if (failure == GoogleFailure.Cancelled) return null
        messageRes(failure)?.let { return context.getString(it) }
        return if (rawMessage.isNullOrBlank()) {
            context.getString(R.string.google_failure_other_plain)
        } else {
            context.getString(R.string.google_failure_other, rawMessage)
        }
    }

    /**
     * What to show on the account's own screen for the error its last sync stored: the words for a coded failure,
     * and the older 401 text as "needs sign-in" too; anything else is shown as it is.
     */
    fun forStoredError(context: Context, error: String?): String? {
        if (error.isNullOrBlank()) return error
        val failure = when (GoogleAccountState.fromError(error)) {
            GoogleAccountState.NeedsSignIn -> GoogleFailure.NeedsSignIn
            GoogleAccountState.AdminBlocked -> GoogleFailure.AdminBlocked
            GoogleAccountState.NotATester -> GoogleFailure.NotATester
            GoogleAccountState.NoAccount -> GoogleFailure.NoAccount
            GoogleAccountState.AppNotSetUp -> GoogleFailure.AppNotSetUp
            GoogleAccountState.Unavailable -> GoogleFailure.Unavailable
            GoogleAccountState.Ok, GoogleAccountState.Error -> return error
        }
        return messageRes(failure)?.let { context.getString(it) } ?: error
    }
}
