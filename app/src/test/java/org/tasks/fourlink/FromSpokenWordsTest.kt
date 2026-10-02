package org.tasks.fourlink

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.mr_biz.fourlink.Outcome
import uk.mr_biz.fourlink.SkillPrompt
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/**
 * Everything between the words and the task except the model and the IPC: 4Dictate's own prompt
 * and reply guard (from the 4Link library) with 4Tasks' real catalogue, then the door's rules.
 * The replies are what a model should answer for the owner's example requests.
 */
class FromSpokenWordsTest {
    private val zone = ZoneId.of("Europe/London")
    private val now = ZonedDateTime.of(2026, 10, 2, 14, 5, 0, 0, zone)
    private val sources = listOf(SkillPrompt.Source("uk.mr_biz.fourtasks", TasksCatalogue.CATALOGUE, family = true))
    private val port = FakePort()
    private val door = TasksDoorLogic(port, Clock.fixed(Instant.parse("2026-10-02T13:05:00Z"), zone), Locale.ENGLISH)

    private fun decide(reply: String) = SkillPrompt.parse(reply, sources)

    private fun run(reply: String): Outcome {
        val call = decide(reply) as SkillPrompt.Decision.Call
        return runBlocking { door.perform(call.function.id, call.arguments) }
    }

    @Test fun `the prompt names the four functions and tells the model when now is`() {
        val prompt = SkillPrompt.instructions(sources, now)
        for (id in listOf("tasks.add", "tasks.list", "tasks.complete", "lists.list")) assertTrue(id, prompt.contains(id))
        assertTrue(prompt.contains("Now: Friday 2026-10-02 14:05"))
        assertTrue(prompt.contains("Europe/London"))
    }

    @Test fun `remind me tomorrow at 3 to get back to Sandra Elaine about her reservation`() {
        val o = run(
            """{"function":"tasks.add","arguments":{"title":"Get back to Sandra Elaine about her reservation","due":"2026-10-03T15:00","reminder":"2026-10-03T15:00"}}""",
        )
        val summary = JSONObject((o as Outcome.Ok).json).getString("summary")
        assertEquals("Get back to Sandra Elaine about her reservation — tomorrow 15:00, reminder set.", summary)
        assertEquals(1, port.reminders.size)
    }

    @Test fun `a reply wrapped in a code fence is still read`() {
        val o = run("```json\n{\"function\":\"tasks.add\",\"arguments\":{\"title\":\"Buy milk\"}}\n```")
        assertTrue(o is Outcome.Ok)
    }

    @Test fun `what is due tomorrow`() {
        port.open(7, "Get back to Sandra Elaine about her reservation", Instant.parse("2026-10-03T14:00:00Z").toEpochMilli())
        val o = run("""{"function":"tasks.list","arguments":{"due_from":"2026-10-03","due_to":"2026-10-03"}}""")
        assertTrue((o as Outcome.Ok).json, JSONObject(o.json).getString("tasks").contains("Sandra"))
    }

    @Test fun `mark the Sandra task done`() {
        port.open(7, "Get back to Sandra Elaine about her reservation")
        val o = run("""{"function":"tasks.complete","arguments":{"title":"Sandra"}}""")
        assertEquals("Done: Get back to Sandra Elaine about her reservation.", JSONObject((o as Outcome.Ok).json).getString("summary"))
    }

    @Test fun `which lists do I have`() {
        val o = run("""{"function":"lists.list","arguments":{}}""")
        assertEquals("My tasks, Work, Shared", JSONObject((o as Outcome.Ok).json).getString("lists"))
    }

    @Test fun `a wipe request has no function and the model says none`() {
        assertTrue(decide("""{"none":"no function available to delete tasks"}""") is SkillPrompt.Decision.None)
        assertTrue(decide("""{"function":"tasks.delete","arguments":{"id":1}}""") is SkillPrompt.Decision.None)
    }

    @Test fun `arguments of the wrong type or too long never reach the door`() {
        assertTrue(decide("""{"function":"tasks.add","arguments":{"title":"x","due":5}}""") is SkillPrompt.Decision.None)
        assertTrue(decide("""{"function":"tasks.add","arguments":{"title":"${"x".repeat(201)}"}}""") is SkillPrompt.Decision.None)
        assertTrue(decide("""{"function":"tasks.add","arguments":{"due":"2026-10-03"}}""") is SkillPrompt.Decision.None)
        assertTrue(decide("""{"function":"tasks.add","arguments":{"title":"x","allow_past":"yes"}}""") is SkillPrompt.Decision.None)
    }
}
