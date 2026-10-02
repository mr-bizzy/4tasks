package org.tasks.fourlink

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.mr_biz.fourlink.Outcome
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/** The rules of docs/PHASE0-PLAN.md section 4, on Friday 2 October 2026, 14:05 in London (BST). */
class TasksDoorLogicTest {
    private val zone = ZoneId.of("Europe/London")
    private val clock = Clock.fixed(Instant.parse("2026-10-02T13:05:00Z"), zone)
    private val port = FakePort()
    private val door = TasksDoorLogic(port, clock, Locale.ENGLISH)

    private fun call(id: String, args: String = "{}"): Outcome = runBlocking { door.perform(id, JSONObject(args)) }
    private fun ok(o: Outcome): JSONObject = (o as? Outcome.Ok ?: error("expected Ok, got $o")).json.let(::JSONObject)
    private fun bad(o: Outcome): String = (o as? Outcome.BadArguments ?: error("expected BadArguments, got $o")).message
    private fun ms(text: String) = Instant.parse(text).toEpochMilli()

    // ---- tasks.add --------------------------------------------------------

    @Test fun `remind me tomorrow at 3 makes a task due 15-00 with that reminder`() {
        val r = ok(call("tasks.add", """{"title":"Get back to Sandra Elaine about her reservation","due":"2026-10-03T15:00","reminder":"2026-10-03T15:00"}"""))
        val task = port.created.single()
        assertEquals("Get back to Sandra Elaine about her reservation", task.title)
        assertEquals(Moment.DayTime(java.time.LocalDateTime.of(2026, 10, 3, 15, 0)), task.due)
        assertEquals(listOf(r.getLong("id") to ms("2026-10-03T14:00:00Z")), port.reminders)
        assertEquals("Get back to Sandra Elaine about her reservation — tomorrow 15:00, reminder set.", r.getString("summary"))
    }

    @Test fun `a due time alone sets no reminder`() {
        ok(call("tasks.add", """{"title":"Pay rent","due":"2026-10-03T09:00"}"""))
        assertTrue(port.reminders.isEmpty())
    }

    @Test fun `a task with a date only is all day and says so`() {
        val r = ok(call("tasks.add", """{"title":"Bins","due":"2026-10-03"}"""))
        assertEquals(Moment.Day(LocalDate.of(2026, 10, 3)), port.created.single().due)
        assertEquals("Bins — tomorrow.", r.getString("summary"))
    }

    @Test fun `a task with nothing else is just added to the default list`() {
        val r = ok(call("tasks.add", """{"title":"Buy milk"}"""))
        assertEquals(null, port.created.single().listId)
        assertEquals("Buy milk — added.", r.getString("summary"))
    }

    @Test fun `a reminder at a different time from the due time says when`() {
        val r = ok(call("tasks.add", """{"title":"Call Sam","due":"2026-10-03T15:00","reminder":"2026-10-03T14:30"}"""))
        assertEquals("Call Sam — tomorrow 15:00, reminder set for tomorrow 14:30.", r.getString("summary"))
    }

    @Test fun `notes are kept and trimmed, an empty note is none`() {
        ok(call("tasks.add", """{"title":"A","notes":"  bring the receipt "}"""))
        ok(call("tasks.add", """{"title":"B","notes":"   "}"""))
        assertEquals("bring the receipt", port.created[0].notes)
        assertEquals(null, port.created[1].notes)
    }

    @Test fun `an empty title is refused and nothing is written`() {
        assertTrue(bad(call("tasks.add", """{"title":"   "}""")).startsWith("title is empty"))
        assertTrue(port.created.isEmpty())
    }

    @Test fun `a reminder in the past is refused, with the time, and nothing is written`() {
        val m = bad(call("tasks.add", """{"title":"X","reminder":"2026-10-02T09:00"}"""))
        assertTrue(m, m.contains("already passed") && m.contains("today 14:05"))
        assertTrue(port.created.isEmpty() && port.reminders.isEmpty())
    }

    @Test fun `a reminder in the past is accepted when the user said so`() {
        ok(call("tasks.add", """{"title":"X","reminder":"2026-10-02T09:00","allow_past":true}"""))
        assertEquals(1, port.reminders.size)
    }

    @Test fun `a reminder right now is not in the past`() {
        ok(call("tasks.add", """{"title":"X","reminder":"2026-10-02T14:05"}"""))
        assertEquals(1, port.reminders.size)
    }

    @Test fun `a due date in the past is accepted, the task is just overdue`() {
        ok(call("tasks.add", """{"title":"Late","due":"2026-09-30"}"""))
        assertEquals(1, port.created.size)
    }

    @Test fun `a reminder needs a time`() {
        assertTrue(bad(call("tasks.add", """{"title":"X","reminder":"2026-10-03"}""")).contains("needs a time"))
        assertTrue(port.created.isEmpty())
    }

    @Test fun `an impossible date is refused before anything is written`() {
        assertTrue(bad(call("tasks.add", """{"title":"X","due":"2026-02-30"}""")).contains("not a real date"))
        assertTrue(port.created.isEmpty())
    }

    @Test fun `a list is matched by name ignoring case`() {
        val r = ok(call("tasks.add", """{"title":"Draft","list":"  work "}"""))
        assertEquals(2L, port.created.single().listId)
        assertEquals("Draft — on Work.", r.getString("summary"))
    }

    @Test fun `an unknown list is refused naming the lists, and no list is made`() {
        val m = bad(call("tasks.add", """{"title":"X","list":"Garden"}"""))
        assertTrue(m, m.contains("no list called “Garden”") && m.contains("My tasks, Work, Shared"))
        assertTrue(port.created.isEmpty())
    }

    @Test fun `a read-only list is refused for adding`() {
        assertTrue(bad(call("tasks.add", """{"title":"X","list":"Shared"}""")).contains("read-only"))
    }

    @Test fun `if the reminder fails the answer says the task WAS created`() {
        port.failReminder = IllegalStateException("alarm service down")
        val o = call("tasks.add", """{"title":"Call Sam","reminder":"2026-10-03T14:30"}""")
        assertTrue(o is Outcome.Failed)
        val m = (o as Outcome.Failed).message
        assertTrue(m, m.startsWith("“Call Sam” was added, but its reminder could not be set"))
        assertEquals(1, port.created.size)
    }

    @Test fun `when exact alarms are off the summary says the reminder may be late`() {
        port.exactAlarms = false
        val r = ok(call("tasks.add", """{"title":"Call Sam","reminder":"2026-10-03T14:30"}"""))
        assertTrue(r.getString("summary").endsWith("so the reminder may come late."))
    }

    @Test fun `no warning about alarms when there is no reminder`() {
        port.exactAlarms = false
        assertTrue(!ok(call("tasks.add", """{"title":"X"}""")).getString("summary").contains("exact alarms"))
    }

    @Test fun `a bad request from Tasks_org becomes bad_arguments, a read-only one refused, others failed`() {
        port.failCreate = IllegalArgumentException("No list with id 9")
        assertEquals("No list with id 9", bad(call("tasks.add", """{"title":"X"}""")))
        port.failCreate = UnsupportedOperationException("That list is read-only")
        assertTrue(call("tasks.add", """{"title":"X"}""") is Outcome.Refused)
    }

    // ---- tasks.list -------------------------------------------------------

    @Test fun `listing asks for open tasks, soonest first, at most 50, and prints id title due list`() {
        port.open(7, "Pay rent", ms("2026-10-03T08:00:00Z"))
        port.open(8, "Bins")
        val r = ok(call("tasks.list"))
        assertEquals(2, r.getInt("count"))
        assertEquals("#7 Pay rent — due tomorrow 09:00 [My tasks]\n#8 Bins [My tasks]", r.getString("tasks"))
        assertEquals(TaskFilter(null, null, null, false, 50), port.filters.single())
    }

    @Test fun `nothing to list says so`() {
        val r = ok(call("tasks.list"))
        assertEquals(0, r.getInt("count"))
        assertEquals("No tasks.", r.getString("tasks"))
    }

    @Test fun `due_to as a date alone runs to the end of that day, due_from from its start`() {
        ok(call("tasks.list", """{"due_from":"2026-10-03","due_to":"2026-10-03"}"""))
        val f = port.filters.single()
        assertEquals(ms("2026-10-02T23:00:00Z"), f.dueFromMillis)
        assertEquals(ms("2026-10-03T23:00:00Z") - 1, f.dueToMillis)
    }

    @Test fun `due bounds with a time are used as given`() {
        ok(call("tasks.list", """{"due_from":"2026-10-03T09:00","due_to":"2026-10-03T17:00"}"""))
        val f = port.filters.single()
        assertEquals(ms("2026-10-03T08:00:00Z"), f.dueFromMillis)
        assertEquals(ms("2026-10-03T16:00:00Z"), f.dueToMillis)
    }

    @Test fun `from after to is refused`() {
        assertEquals("due_from is after due_to.", bad(call("tasks.list", """{"due_from":"2026-10-05","due_to":"2026-10-03"}""")))
    }

    @Test fun `listing can be limited to a list and can include finished tasks`() {
        ok(call("tasks.list", """{"list":"work","include_completed":true}"""))
        val f = port.filters.single()
        assertEquals(2L, f.listId)
        assertTrue(f.includeCompleted)
    }

    @Test fun `listing an unknown list is refused naming the lists`() {
        assertTrue(bad(call("tasks.list", """{"list":"Garden"}""")).contains("My tasks, Work, Shared"))
    }

    @Test fun `finished tasks are marked done`() {
        port.tasks += TaskInfo(5, "Old", null, false, "Work", completed = true, repeats = false)
        assertEquals("#5 (done) Old [Work]", ok(call("tasks.list", """{"include_completed":true}""")).getString("tasks"))
    }

    @Test fun `the count is the true total even when only 50 are shown`() {
        repeat(60) { port.open(it + 1L, "T$it") }
        val r = ok(call("tasks.list"))
        assertEquals(60, r.getInt("count"))
        assertEquals(50, r.getString("tasks").lines().size)
    }

    // ---- tasks.complete ---------------------------------------------------

    @Test fun `a part of the title that matches one open task completes it`() {
        port.open(7, "Get back to Sandra Elaine about her reservation")
        port.open(8, "Pay rent")
        val r = ok(call("tasks.complete", """{"title":"Sandra"}"""))
        assertEquals(listOf(7L), port.completedIds)
        assertEquals("Done: Get back to Sandra Elaine about her reservation.", r.getString("summary"))
    }

    @Test fun `no match completes nothing`() {
        port.open(7, "Pay rent")
        assertEquals("No open task matches “Sandra”.", bad(call("tasks.complete", """{"title":"Sandra"}""")))
        assertTrue(port.completedIds.isEmpty())
    }

    @Test fun `several matches complete nothing and name the candidates`() {
        port.open(7, "Call Sandra about rent")
        port.open(8, "Call Sandra about the car")
        val m = bad(call("tasks.complete", """{"title":"Sandra"}"""))
        assertTrue(m, m.contains("“Call Sandra about rent” (id 7)") && m.contains("“Call Sandra about the car” (id 8)") && m.contains("Nothing was completed"))
        assertTrue(port.completedIds.isEmpty())
    }

    @Test fun `no more than five candidates are named`() {
        repeat(8) { port.open(it + 1L, "Call Sandra $it") }
        val m = bad(call("tasks.complete", """{"title":"Sandra"}"""))
        assertEquals(5, Regex("id \\d+").findAll(m).count())
        assertTrue(m.contains("and 3 more"))
    }

    @Test fun `a whole-title match wins over longer titles that contain it`() {
        port.open(7, "Call mum")
        port.open(8, "Call mum about dinner")
        ok(call("tasks.complete", """{"title":"call mum"}"""))
        assertEquals(listOf(7L), port.completedIds)
    }

    @Test fun `an id completes that task`() {
        port.open(7, "Pay rent")
        ok(call("tasks.complete", """{"id":7}"""))
        assertEquals(listOf(7L), port.completedIds)
    }

    @Test fun `an unknown id is refused`() {
        assertEquals("There is no task with id 99.", bad(call("tasks.complete", """{"id":99}""")))
    }

    @Test fun `neither id nor title is refused`() {
        assertEquals("Send the task's id or part of its title.", bad(call("tasks.complete", "{}")))
    }

    @Test fun `an id wins over a title`() {
        port.open(7, "Pay rent"); port.open(8, "Bins")
        ok(call("tasks.complete", """{"id":8,"title":"rent"}"""))
        assertEquals(listOf(8L), port.completedIds)
    }

    @Test fun `a task already done is not completed again, which would advance a repeating one`() {
        port.tasks += TaskInfo(7, "Water plants", null, false, null, completed = true, repeats = true)
        val r = ok(call("tasks.complete", """{"id":7}"""))
        assertTrue(port.completedIds.isEmpty())
        assertEquals("“Water plants” was already done.", r.getString("summary"))
    }

    @Test fun `a repeating task moves to its next date and the answer says so`() {
        port.open(7, "Water plants", ms("2026-10-02T10:00:00Z"), repeats = true)
        port.advanceOnComplete = 7
        val r = ok(call("tasks.complete", """{"id":7}"""))
        assertEquals("Done: Water plants. It repeats, so the next one is Fri 9 Oct 15:00.", r.getString("summary"))
    }

    // ---- lists.list and the rest -----------------------------------------

    @Test fun `lists are counted and named`() {
        val r = ok(call("lists.list"))
        assertEquals(3, r.getInt("count"))
        assertEquals("My tasks, Work, Shared", r.getString("lists"))
    }

    @Test fun `an unknown function fails with a sentence`() {
        assertTrue(call("tasks.delete") is Outcome.Failed)
    }
}
