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
    private val strangerDoor = TasksDoorLogic(port, clock, Locale.ENGLISH, mayNameTasks = false)

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
        assertEquals("Added to My tasks: Get back to Sandra Elaine about her reservation — tomorrow 15:00, reminder set.", r.getString("summary"))
    }

    @Test fun `a reminder on a Google Tasks list is kept on the phone and the answer says it is not synced`() {
        port.lists = port.lists + ListInfo(4, "Online", false, googleTasks = true)
        val r = ok(call("tasks.add", """{"title":"Call Sandra","due":"2026-10-03T15:00","reminder":"2026-10-03T15:00","list":"Online"}"""))
        assertEquals(1, port.reminders.size)
        assertTrue(r.getString("summary"), r.getString("summary").endsWith(
            " Google Tasks cannot keep reminders, so this reminder stays on this phone and is not synced."))
    }

    @Test fun `a due time on a Google Tasks list says only the date syncs`() {
        port.lists = port.lists + ListInfo(4, "Online", false, googleTasks = true)
        val r = ok(call("tasks.add", """{"title":"Pay rent","due":"2026-10-03T09:00","list":"Online"}"""))
        assertTrue(port.reminders.isEmpty())
        assertTrue(r.getString("summary"), r.getString("summary").endsWith(
            " Google Tasks keeps only the date of a due time, so the time of day stays on this phone."))
    }

    @Test fun `a date alone on a Google Tasks list needs no warning, and other lists never get one`() {
        port.lists = port.lists + ListInfo(4, "Online", false, googleTasks = true)
        assertTrue(!ok(call("tasks.add", """{"title":"Bins","due":"2026-10-03","list":"Online"}""")).getString("summary").contains("Google"))
        assertTrue(!ok(call("tasks.add", """{"title":"Call","due":"2026-10-03T15:00","reminder":"2026-10-03T15:00","list":"Work"}""")).getString("summary").contains("Google"))
    }

    @Test fun `a due time alone sets no reminder`() {
        ok(call("tasks.add", """{"title":"Pay rent","due":"2026-10-03T09:00"}"""))
        assertTrue(port.reminders.isEmpty())
    }

    @Test fun `a task with a date only is all day and says so`() {
        val r = ok(call("tasks.add", """{"title":"Bins","due":"2026-10-03"}"""))
        assertEquals(Moment.Day(LocalDate.of(2026, 10, 3)), port.created.single().due)
        assertEquals("Added to My tasks: Bins — tomorrow.", r.getString("summary"))
    }

    @Test fun `a task with nothing else is just added to the default list`() {
        val r = ok(call("tasks.add", """{"title":"Buy milk"}"""))
        assertEquals(null, port.created.single().listId)
        assertEquals("Added to My tasks: Buy milk.", r.getString("summary"))
    }

    @Test fun `a reminder at a different time from the due time says when`() {
        val r = ok(call("tasks.add", """{"title":"Call Sam","due":"2026-10-03T15:00","reminder":"2026-10-03T14:30"}"""))
        assertEquals("Added to My tasks: Call Sam — tomorrow 15:00, reminder set for tomorrow 14:30.", r.getString("summary"))
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
        assertEquals("Added to Work: Draft.", r.getString("summary"))
    }

    @Test fun `an unknown list is refused naming the lists, and no list is made`() {
        val m = bad(call("tasks.add", """{"title":"X","list":"Garden"}"""))
        assertTrue(m, m.contains("no list called “Garden”") && m.contains("My tasks, Work, Shared"))
        assertTrue(port.created.isEmpty())
    }

    // ---- long notes -------------------------------------------------------

    private fun longNote(n: Int) = "dictated ".repeat(n / 9 + 1).take(n)

    @Test fun `an 8000 character note is accepted whole by the catalogue and the door`() {
        val note = longNote(8000)
        val args = JSONObject().put("title", "Meeting notes").put("notes", note)
        assertEquals(null, uk.mr_biz.fourlink.Validation.problem(TasksCatalogue.CATALOGUE.find("tasks.add")!!.input, args))
        ok(call("tasks.add", args.toString()))
        assertEquals(note.trim(), port.created.single().notes)
    }

    @Test fun `whatever length the catalogue advertises, the door takes`() {
        val note = longNote(TasksCatalogue.NOTES_MAX)
        val args = JSONObject().put("title", "Long").put("notes", note)
        assertEquals(null, uk.mr_biz.fourlink.Validation.problem(TasksCatalogue.CATALOGUE.find("tasks.add")!!.input, args))
        ok(call("tasks.add", args.toString()))
        assertEquals(note.trim(), port.created.single().notes)
    }

    @Test fun `a five and a half minute dictation fits with room to spare`() {
        assertTrue(TasksCatalogue.NOTES_MAX >= 8000 * 4)
    }

    // ---- list names, forgivingly -------------------------------------------

    private fun listsOf(vararg names: String) { port.lists = names.mapIndexed { i, n -> ListInfo(i + 1L, n, false) } }
    private fun addTo(list: String) = call("tasks.add", JSONObject().put("title", "milk").put("list", list).toString())

    @Test fun `the summary names the list, the default one too`() {
        listsOf("Personal Calendar", "Shopping")
        assertEquals("Added to Shopping: milk.", ok(addTo("Shopping")).getString("summary"))
        assertEquals("Added to Personal Calendar: milk.", ok(call("tasks.add", """{"title":"milk"}""")).getString("summary"))
    }

    @Test fun `the shopping list finds Shopping`() {
        listsOf("Personal Calendar", "Shopping")
        assertEquals("Added to Shopping: milk.", ok(addTo("the shopping list")).getString("summary"))
        assertEquals(2L, port.created.single().listId)
    }

    @Test fun `punctuation, my, a trailing list and a final s are all ignored`() {
        listsOf("Personal Calendar", "Shopping", "Notes", "To-do")
        for ((said, expected) in listOf("My Shopping List!" to 2L, "shopping lists" to 2L, "note" to 3L, "  NOTES. " to 3L, "to do" to 4L, "the todo list" to 4L)) {
            port.created.clear()
            ok(addTo(said))
            assertEquals(said, expected, port.created.single().listId)
        }
    }

    @Test fun `an exact name still wins over a normalised one`() {
        listsOf("Shopping", "Shopping list")
        ok(addTo("Shopping list"))
        assertEquals(2L, port.created.single().listId)
        port.created.clear()
        ok(addTo("shopping"))
        assertEquals(1L, port.created.single().listId)
    }

    @Test fun `two lists that normalise to the same name are asked about, never guessed`() {
        listsOf("Shopping", "Shopping list", "Work")
        val m = bad(addTo("the shopping"))
        assertTrue(m, m.contains("“Shopping”") && m.contains("“Shopping list”") && m.contains("Nothing was added"))
        assertTrue(port.created.isEmpty())
    }

    @Test fun `a near spelling is a question with the same call corrected, and nothing is added`() {
        listsOf("Personal Calendar", "Shopping")
        val o = call("tasks.add", """{"title":"milk","due":"2026-10-03","list":"Shoping"}""")
        assertTrue(bad(o), bad(o).contains("One is close: “Shopping”") && bad(o).contains("Nothing was added"))
        val s = (o as Outcome.BadArguments).suggestion!!
        assertEquals("Did you mean the list “Shopping”?", s.question)
        assertEquals("tasks.add", s.function)
        assertEquals("Shopping", s.arguments.getString("list"))
        assertEquals("milk", s.arguments.getString("title"))
        assertEquals("2026-10-03", s.arguments.getString("due"))
        assertTrue(port.created.isEmpty())
        // saying yes runs it
        val done = call(s.function, s.arguments.toString())
        assertEquals("Added to Shopping: milk — tomorrow.", ok(done).getString("summary"))
    }

    @Test fun `the list suggestion is valid against the catalogue`() {
        listsOf("Personal Calendar", "Shopping")
        val s = (call("tasks.add", """{"title":"milk","list":"Shoping"}""") as Outcome.BadArguments).suggestion!!
        assertEquals(null, uk.mr_biz.fourlink.Validation.problem(TasksCatalogue.CATALOGUE.find(s.function)!!.input, s.arguments))
        assertTrue(s.question.length <= uk.mr_biz.fourlink.Suggestion.QUESTION_MAX)
    }

    @Test fun `tasks list offers the same correction for its list`() {
        listsOf("Personal Calendar", "Shopping")
        val s = (call("tasks.list", """{"list":"Shoping"}""") as Outcome.BadArguments).suggestion!!
        assertEquals("tasks.list", s.function)
        assertEquals("Shopping", s.arguments.getString("list"))
        assertEquals(1, ok(call("tasks.list", """{"list":"the shopping list"}""")).let { port.filters.size })
        assertEquals(2L, port.filters.single().listId)
    }

    @Test fun `two close lists give no suggestion, only the names`() {
        listsOf("Shopping", "Shipping", "Work")
        val o = call("tasks.add", """{"title":"milk","list":"Shoppin"}""")
        assertTrue(bad(o).contains("The lists are: Shopping, Shipping, Work"))
        assertEquals(null, (o as Outcome.BadArguments).suggestion)
        assertTrue(port.created.isEmpty())
    }

    @Test fun `a name that is nothing like any list is the refusal that names the lists`() {
        listsOf("Personal Calendar", "Shopping")
        val o = call("tasks.add", """{"title":"milk","list":"Garden"}""")
        assertTrue(bad(o).contains("no list called “Garden”") && bad(o).contains("Personal Calendar, Shopping"))
        assertEquals(null, (o as Outcome.BadArguments).suggestion)
    }

    @Test fun `a close name is never offered a read-only list for adding`() {
        port.lists = listOf(ListInfo(1, "Work", false), ListInfo(2, "Shoping", true))
        val o = call("tasks.add", """{"title":"x","list":"Shopping"}""")
        assertEquals(null, (o as Outcome.BadArguments).suggestion)
    }

    @Test fun `a note goes to the Notes list`() {
        listsOf("Personal Calendar", "Notes")
        val r = ok(addTo("Notes"))
        assertEquals("Added to Notes: milk.", r.getString("summary"))
        assertEquals(2L, port.created.single().listId)
    }

    @Test fun `a note with no Notes list goes to the default list and says so`() {
        listsOf("Personal Calendar", "Shopping")
        val r = ok(addTo("Notes"))
        assertEquals(null, port.created.single().listId)
        assertEquals("Added to Personal Calendar: milk. There is no Notes list, so it went to Personal Calendar, the default list.", r.getString("summary"))
    }

    @Test fun `only Notes falls back, any other missing list is still refused`() {
        listsOf("Personal Calendar")
        assertTrue(bad(addTo("Garden")).contains("no list called"))
        assertTrue(port.created.isEmpty())
    }

    @Test fun `Notes is not a fallback when two lists both read as Notes`() {
        listsOf("Notes", "My notes")
        assertTrue(bad(addTo("note")).contains("could be any of these lists"))
        assertTrue(port.created.isEmpty())
    }

    // ---- tasks.add note=true: 4Dictate handing over a dictation made while the phone was locked ----

    private fun dictated(extra: JSONObject.() -> Unit = {}) =
        call("tasks.add", JSONObject().put("title", "Dictation").put("notes", "the words").put("note", true).apply(extra).toString())

    @Test fun `a 4Dictate note goes to the Notes list that exists, matched as any list name, and is marked`() {
        listsOf("Personal Calendar", "My notes")
        val r = ok(dictated())
        assertEquals(2L, port.created.single().listId)
        assertEquals("the words", port.created.single().notes)
        assertEquals(listOf(r.getLong("id")), port.dictatedNotes)
        assertTrue(port.createdLists.isEmpty())
        assertEquals("Added to My notes: Dictation.", r.getString("summary"))
    }

    @Test fun `with no Notes list a local Notes list is made on this phone and the note goes there`() {
        listsOf("Personal Calendar", "Shopping")
        val r = ok(dictated())
        assertEquals(listOf("Notes"), port.createdLists)
        assertEquals(3L, port.created.single().listId)
        assertEquals(listOf(r.getLong("id")), port.dictatedNotes)
        assertEquals(
            "Added to Notes: Dictation. There was no Notes list, so 4Tasks made one on this phone.",
            r.getString("summary"),
        )
    }

    @Test fun `a 4Dictate note goes to Notes whatever list was sent`() {
        listsOf("Personal Calendar", "Notes", "Shopping")
        ok(dictated { put("list", "Shopping") })
        assertEquals(2L, port.created.single().listId)
    }

    @Test fun `the Notes list is made only once`() {
        listsOf("Personal Calendar")
        ok(dictated())
        ok(dictated())
        assertEquals(listOf("Notes"), port.createdLists)
        assertEquals(port.created[0].listId, port.created[1].listId)
    }

    @Test fun `note false or left out changes nothing: no marker, no list made, the named list or the old Notes fallback`() {
        listsOf("Personal Calendar", "Work")
        ok(call("tasks.add", """{"title":"milk","list":"Work","note":false}"""))
        ok(call("tasks.add", """{"title":"bread"}"""))
        val r = ok(addTo("Notes"))
        assertEquals(listOf(2L, null, null), port.created.map { it.listId })
        assertTrue(port.dictatedNotes.isEmpty())
        assertTrue(port.createdLists.isEmpty())
        assertTrue(r.getString("summary").contains("There is no Notes list, so it went to"))
    }

    @Test fun `note must be true or false`() {
        val add = TasksCatalogue.CATALOGUE.find("tasks.add")!!
        assertEquals("arguments.note must be true or false", uk.mr_biz.fourlink.Validation.problem(add, """{"title":"x","note":"yes"}"""))
    }

    @Test fun `a read-only list found by a normalised name is still refused`() {
        port.lists = listOf(ListInfo(1, "Work", false), ListInfo(2, "Shared", true))
        assertTrue(bad(addTo("the shared list")).contains("read-only"))
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
        assertEquals("No open task matches “Sandra”. Open tasks: “Pay rent”.", bad(call("tasks.complete", """{"title":"Sandra"}""")))
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

    // ---- tasks.complete: a misheard title, found by sound, never completed ------------------

    private fun suggestionOf(o: Outcome) = (o as Outcome.BadArguments).suggestion

    @Test fun `sounder is offered as Sandra for the user to confirm, and nothing is completed`() {
        port.open(4, "Get back to Sandra Elaine about her reservation")
        port.open(5, "Pay rent")
        val o = call("tasks.complete", """{"title":"sounder"}""")
        assertTrue(bad(o), bad(o).contains("One sounds like it: “Get back to Sandra Elaine about her reservation”") && bad(o).contains("Nothing was completed"))
        val s = suggestionOf(o)!!
        assertEquals("Did you mean “Get back to Sandra Elaine about her reservation”?", s.question)
        assertEquals("tasks.complete", s.function)
        assertEquals(4, s.arguments.getInt("id"))
        assertTrue(port.completedIds.isEmpty())
    }

    @Test fun `saying yes to the suggestion completes exactly that task`() {
        port.open(4, "Get back to Sandra Elaine about her reservation")
        val s = suggestionOf(call("tasks.complete", """{"title":"sounder"}"""))!!
        val o = call(s.function, s.arguments.toString())
        assertEquals("Done: Get back to Sandra Elaine about her reservation.", JSONObject((o as Outcome.Ok).json).getString("summary"))
        assertEquals(listOf(4L), port.completedIds)
    }

    @Test fun `the suggestion is valid against the catalogue, so a caller will accept it`() {
        port.open(4, "Get back to Sandra Elaine about her reservation")
        val s = suggestionOf(call("tasks.complete", """{"title":"sounder"}"""))!!
        val function = TasksCatalogue.CATALOGUE.find(s.function)!!
        assertEquals(null, uk.mr_biz.fourlink.Validation.problem(function.input, s.arguments))
        assertTrue(uk.mr_biz.fourlink.Suggestion.parse(s.toJson()) != null)
        assertTrue(s.question.length <= uk.mr_biz.fourlink.Suggestion.QUESTION_MAX)
    }

    @Test fun `a very long title still gives a question within 200 characters`() {
        port.open(4, "Sandra " + "very long ".repeat(40))
        val s = suggestionOf(call("tasks.complete", """{"title":"sounder"}"""))!!
        assertTrue(s.question.length <= 200)
    }

    @Test fun `several tasks that sound alike complete nothing, name them, and offer no suggestion`() {
        port.open(4, "Call Sandra")
        port.open(5, "Email Sondra the menu")
        val o = call("tasks.complete", """{"title":"sounder"}""")
        assertTrue(bad(o), bad(o).contains("several sound like it") && bad(o).contains("“Call Sandra” (id 4)") && bad(o).contains("“Email Sondra the menu” (id 5)"))
        assertEquals(null, suggestionOf(o))
        assertTrue(port.completedIds.isEmpty())
    }

    @Test fun `a text match is never replaced by a sound match`() {
        port.open(4, "Call Sandra")
        port.open(5, "Meet the center manager")
        val o = call("tasks.complete", """{"title":"Sandra"}""")
        assertTrue(o is Outcome.Ok)
        assertEquals(listOf(4L), port.completedIds)
    }

    @Test fun `nothing by text or sound lists the open titles so the mishearing is visible`() {
        port.open(1, "Pay rent"); port.open(2, "Book the dentist")
        val o = call("tasks.complete", """{"title":"gardening"}""")
        assertEquals("No open task matches “gardening”. Open tasks: “Pay rent”; “Book the dentist”.", bad(o))
        assertEquals(null, suggestionOf(o))
    }

    @Test fun `no more than five open titles are named`() {
        repeat(8) { port.open(it + 1L, "Task number $it") }
        val m = bad(call("tasks.complete", """{"title":"gardening"}"""))
        assertEquals(5, Regex("“Task number").findAll(m).count())
        assertTrue(m, m.contains("and 3 more"))
    }

    @Test fun `no open tasks at all says so`() {
        assertEquals("No open task matches “Sandra”: there are no open tasks.", bad(call("tasks.complete", """{"title":"Sandra"}""")))
    }

    @Test fun `a caller who may not read titles is told no title, no suggestion`() {
        port.open(4, "Get back to Sandra Elaine about her reservation")
        port.open(5, "Call Sandra")
        val none = runBlocking { strangerDoor.perform("tasks.complete", JSONObject("""{"title":"gardening"}""")) }
        assertEquals("No open task matches “gardening”.", bad(none))
        val sound = runBlocking { strangerDoor.perform("tasks.complete", JSONObject("""{"title":"sounder"}""")) }
        assertEquals("No open task matches “sounder”.", bad(sound))
        assertEquals(null, suggestionOf(sound))
        val several = runBlocking { strangerDoor.perform("tasks.complete", JSONObject("""{"title":"Sandra"}""")) }
        assertTrue(bad(several), !bad(several).contains("Get back") && !bad(several).contains("Call Sandra") && bad(several).contains("several"))
        assertTrue(port.completedIds.isEmpty())
    }

    @Test fun `short words in the query do not count as sounds`() {
        assertTrue(!SoundsLike.matches("to a", "Pick up the parcel"))
        assertTrue(!SoundsLike.matches("", "Pick up the parcel"))
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
