package org.tasks.fourlink

import android.content.Context
import org.json.JSONObject
import uk.mr_biz.fourlink.Catalogue
import uk.mr_biz.fourlink.HelloReply
import uk.mr_biz.fourlink.InvokeResult
import uk.mr_biz.fourlink.Validation
import uk.mr_biz.fourlink.android.FourLinkClient

/**
 * "Reprocess" on a 4Dictate note: hands the note's text back to 4Dictate over 4Link (dictation.reprocess), the way the
 * family calls each other (spec section 12): hello first, then the catalogue, then invoke with the version read. It
 * never changes the task, so whatever 4Dictate answers, the note stays as it is.
 */
object FourDictateReprocess {
    const val PACKAGE = "uk.mr_biz.fourdictate"
    const val FUNCTION = "dictation.reprocess"

    /** The three 4Link calls, so the steps can be tested without Android. */
    interface Calls {
        fun hello(): HelloReply?
        fun catalogue(): Pair<Catalogue.Parsed?, InvokeResult.Error?>
        fun invoke(functionId: String, argumentsJson: String, versionRead: String): InvokeResult
    }

    sealed interface Answer {
        /** 4Dictate is not installed, did not answer, or speaks a 4Link this app does not know. */
        data object NotReached : Answer
        /** 4Dictate answered but offers no dictation.reprocess. */
        data object NotOffered : Answer
        /** 4Dictate (or the library, for a call that did not get through) said why not: shown as it is. */
        data class Refused(val reason: String) : Answer
        /** Done; [summary] is 4Dictate's own line, when it sent one. */
        data class Done(val summary: String?) : Answer
    }

    fun reprocess(calls: Calls, text: String): Answer {
        val hello = calls.hello()
        if (hello == null || !hello.usable) return Answer.NotReached
        val (parsed, error) = calls.catalogue()
        error?.let { return Answer.Refused(it.message) }
        val function = parsed?.catalogue?.find(FUNCTION) ?: return Answer.NotOffered
        val arguments = JSONObject().put("text", text).toString()
        Validation.problem(function, arguments)?.let { return Answer.Refused(it) }
        return when (val result = calls.invoke(FUNCTION, arguments, function.version)) {
            is InvokeResult.Ok -> Answer.Done(
                runCatching { JSONObject(result.json).optString("summary") }.getOrNull()?.takeIf { it.isNotBlank() },
            )
            is InvokeResult.Error -> Answer.Refused(result.message)
            is InvokeResult.Unreachable -> Answer.Refused(result.why)
        }
    }

    /** The real calls, through the 4Link client. Never on the main thread. */
    fun calls(context: Context): Calls {
        val client = FourLinkClient(context)
        return object : Calls {
            override fun hello() = client.hello(PACKAGE)
            override fun catalogue() = client.catalogue(PACKAGE)
            override fun invoke(functionId: String, argumentsJson: String, versionRead: String) =
                client.invoke(PACKAGE, functionId, argumentsJson, versionRead)
        }
    }
}
