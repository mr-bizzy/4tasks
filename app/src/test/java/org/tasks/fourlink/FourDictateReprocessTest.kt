package org.tasks.fourlink

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.mr_biz.fourlink.Catalogue
import uk.mr_biz.fourlink.Effect
import uk.mr_biz.fourlink.ErrorCode
import uk.mr_biz.fourlink.FunctionSpec
import uk.mr_biz.fourlink.HelloReply
import uk.mr_biz.fourlink.InvokeResult
import uk.mr_biz.fourlink.Schema
import uk.mr_biz.fourlink.Standing
import org.tasks.fourlink.FourDictateReprocess.Answer

class FourDictateReprocessTest {
    private val reprocess = FunctionSpec(
        id = "dictation.reprocess", version = "1.2", title = "Reprocess a dictation", description = "d",
        effect = Effect.CREATE,
        input = Schema.Obj(linkedMapOf("text" to Schema.Str(maxLength = 100)), required = setOf("text")),
    )

    private class Fake(
        val hello: HelloReply? = HelloReply("4Dictate", "1.0", Standing.FAMILY),
        val functions: List<FunctionSpec>,
        val catalogueError: InvokeResult.Error? = null,
        val result: InvokeResult = InvokeResult.Ok("{}"),
    ) : FourDictateReprocess.Calls {
        val steps = mutableListOf<String>()
        var sent: Triple<String, String, String>? = null
        override fun hello(): HelloReply? = hello.also { steps += "hello" }
        override fun catalogue(): Pair<Catalogue.Parsed?, InvokeResult.Error?> {
            steps += "catalogue"
            return (if (catalogueError == null) Catalogue.Parsed(Catalogue("4Dictate", functions), emptyList()) else null) to catalogueError
        }
        override fun invoke(functionId: String, argumentsJson: String, versionRead: String): InvokeResult {
            steps += "invoke"
            sent = Triple(functionId, argumentsJson, versionRead)
            return result
        }
    }

    @Test fun `hello first, then the catalogue, then invoke with the text and the version read`() {
        val fake = Fake(functions = listOf(reprocess), result = InvokeResult.Ok("""{"summary":"Tidied and typed."}"""))
        assertEquals(Answer.Done("Tidied and typed."), FourDictateReprocess.reprocess(fake, "the words"))
        assertEquals(listOf("hello", "catalogue", "invoke"), fake.steps)
        assertEquals("dictation.reprocess", fake.sent!!.first)
        assertEquals("the words", JSONObject(fake.sent!!.second).getString("text"))
        assertEquals("1.2", fake.sent!!.third)
    }

    @Test fun `done without a summary is still done`() {
        assertEquals(Answer.Done(null), FourDictateReprocess.reprocess(Fake(functions = listOf(reprocess)), "x"))
    }

    @Test fun `no answer to hello, or a 4Link it does not know, goes no further`() {
        val silent = Fake(hello = null, functions = listOf(reprocess))
        assertEquals(Answer.NotReached, FourDictateReprocess.reprocess(silent, "x"))
        assertEquals(listOf("hello"), silent.steps)
        val newer = Fake(hello = HelloReply("4Dictate", "2.0", Standing.FAMILY), functions = listOf(reprocess))
        assertEquals(Answer.NotReached, FourDictateReprocess.reprocess(newer, "x"))
    }

    @Test fun `a 4Dictate without dictation reprocess is said so, and nothing is invoked`() {
        val fake = Fake(functions = emptyList())
        assertEquals(Answer.NotOffered, FourDictateReprocess.reprocess(fake, "x"))
        assertTrue("invoke" !in fake.steps)
    }

    @Test fun `4Dictate's own reason is passed on as it is`() {
        val refused = Fake(functions = listOf(reprocess), result = InvokeResult.Error(ErrorCode.REFUSED, "Reprocessing is switched off."))
        assertEquals(Answer.Refused("Reprocessing is switched off."), FourDictateReprocess.reprocess(refused, "x"))
        val gone = Fake(functions = listOf(reprocess), result = InvokeResult.Unreachable("4Dictate did not answer."))
        assertEquals(Answer.Refused("4Dictate did not answer."), FourDictateReprocess.reprocess(gone, "x"))
        val notPaired = Fake(functions = emptyList(), catalogueError = InvokeResult.Error(ErrorCode.NOT_PAIRED, "Not paired."))
        assertEquals(Answer.Refused("Not paired."), FourDictateReprocess.reprocess(notPaired, "x"))
    }

    @Test fun `a text over 4Dictate's limit is refused here, before any invoke`() {
        val fake = Fake(functions = listOf(reprocess))
        val answer = FourDictateReprocess.reprocess(fake, "w".repeat(101))
        assertTrue(answer is Answer.Refused)
        assertTrue("invoke" !in fake.steps)
    }
}
