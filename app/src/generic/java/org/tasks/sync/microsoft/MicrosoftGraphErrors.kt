package org.tasks.sync.microsoft

import android.content.Context
import org.tasks.R
import org.tasks.http.HttpException
import org.tasks.http.NetworkException

/**
 * Plain words for the Microsoft Graph errors a work or school account can meet that a personal account does not: no usable
 * mailbox, or To Do not allowed. Used for the text on the account when a sync fails.
 *
 * What Microsoft documents (https://learn.microsoft.com/en-us/graph/errors, read 2026-10-03): HTTP 403 "Forbidden - Access is denied
 * to the requested resource. The user does not have enough permission or does not have a required license." The error `code` is the
 * thing to react to, never the `message`. The To Do pages (https://learn.microsoft.com/en-us/graph/api/todo-list-lists) say nothing
 * about a mailbox.
 *
 * What is NOT documented for To Do, and was not tried against a real tenant: the code `MailboxNotEnabledForRESTAPI` with the text "The
 * mailbox is either inactive, soft-deleted, or is hosted on-premise" is what Microsoft Q&A threads show Graph returning for the Mail and
 * Calendar APIs of a user with no usable Exchange Online mailbox. To Do for work and school accounts is likely to meet the same, but this is
 * a guess to be tested with a tenant that has such a user. Unknown codes fall through to the exception's own message.
 */
object MicrosoftGraphErrors {
    private val MAILBOX_CODES = setOf(
        "MailboxNotEnabledForRESTAPI",
        "MailboxNotSupportedForRESTAPI",
    )

    private val FORBIDDEN_CODES = setOf("ErrorAccessDenied", "accessDenied", "Forbidden")

    sealed interface Explanation {
        data object Mailbox : Explanation
        data object Forbidden : Explanation
    }

    /** Which explanation fits a Graph [graphCode] and HTTP [status] (0 when unknown), or null. */
    fun explain(graphCode: String?, status: Int): Explanation? = when {
        graphCode in MAILBOX_CODES -> Explanation.Mailbox
        graphCode in FORBIDDEN_CODES || status == 403 -> Explanation.Forbidden
        else -> null
    }

    fun describe(context: Context, e: Exception): String? {
        val status = (e as? HttpException)?.code ?: 0
        val code = (e as? NetworkException)?.graphCode
        return when (explain(code, status)) {
            Explanation.Mailbox -> context.getString(R.string.microsoft_error_mailbox)
            Explanation.Forbidden -> context.getString(R.string.microsoft_error_forbidden)
            null -> null
        }
    }
}
