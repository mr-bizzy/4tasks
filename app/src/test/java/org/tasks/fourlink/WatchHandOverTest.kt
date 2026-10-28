package org.tasks.fourlink

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.mr_biz.fourlink.Catalogue
import uk.mr_biz.fourlink.Outcome
import uk.mr_biz.fourlink.Validation
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * 0.1.16, the framework for 4Watch without the phone: request_id and said_at on tasks.add and tasks.complete, and
 * tasks.snapshot. Friday 2 October 2026, 14:05 in London (BST), as TasksDoorLogicTest.
 */
class WatchHandOverTest {
    @get:Rule val folder = TemporaryFolder()

    private class MovingClock(var now: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone() = zone
        override fun withZone(zone: ZoneId) = MovingClock(now, zone)
        override fun instant() = now
        fun forward(d: Duration) { now = now.plus(d) }
    }

    private val zone = ZoneId.of("Europe/London")
    private val clock = MovingClock(Instant.parse("2026-10-02T13:05:00Z"), zone)
    private val port = FakePort()
    private val ledgerFile by lazy { File(folder.root, RequestLedger.FILE_NAME) }
    private var ledger = lazy { RequestLedger(ledgerFile, clock) }

    private fun door(caller: String = "uk.mr_biz.fourdictate") =
        TasksDoorLogic(port, clock, Locale.ENGLISH, callerPackage = caller, ledger = ledger.value)

    private fun call(id: String, args: String, caller: String = "uk.mr_biz.fourdictate"): Outcome =
        runBlocking { door(caller).perform(id, JSONObject(args)) }
    private fun ok(o: Outcome): JSONObject = (o as? Outcome.Ok ?: error("expected Ok, got $o")).json.let(::JSONObject)
    private fun bad(o: Outcome): String = (o as? Outcome.BadArguments ?: error("expected BadArguments, got $o")).message
    private fun ms(text: String) = Instant.parse(text).toEpochMilli()
    private fun restart() { ledger = lazy { RequestLedger(ledgerFile, clock) } }

    // ---- request_id ---------------------------------------------------------

    @Test fun `the same request_id twice adds one task and answers the first result, marked repeated`() {
        val first = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        val again = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        assertEquals(1, port.created.size)
        assertEquals(first.getLong("id"), again.getLong("id"))
        assertEquals(first.getString("summary"), again.getString("summary"))
        assertFalse(first.optBoolean("repeated", false))
        assertTrue(again.getBoolean("repeated"))
    }

    @Test fun `the first answer is kept across a restart`() {
        val first = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        restart()
        val again = ok(call("tasks.add", """{"title":"Something else entirely","request_id":"w-1"}"""))
        assertEquals(1, port.created.size)
        assertEquals(first.getLong("id"), again.getLong("id"))
        assertTrue(again.getBoolean("repeated"))
    }

    @Test fun `after 14 days the request_id is forgotten and the command runs again`() {
        ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        clock.forward(Duration.ofDays(13))
        assertTrue(ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}""")).getBoolean("repeated"))
        clock.forward(Duration.ofDays(1).plusMinutes(1))
        restart()
        val later = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        assertFalse(later.optBoolean("repeated", false))
        assertEquals(2, port.created.size)
    }

    @Test fun `a request_id belongs to its caller`() {
        ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}""", caller = "a.one"))
        val other = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}""", caller = "a.two"))
        assertFalse(other.optBoolean("repeated", false))
        assertEquals(2, port.created.size)
    }

    @Test fun `a refused first call is not kept, so sending it again runs it again`() {
        bad(call("tasks.add", """{"title":"Buy milk","list":"Nowhere","request_id":"w-1"}"""))
        assertEquals(0, port.created.size)
        ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        assertEquals(1, port.created.size)
    }

    @Test fun `a request_id used for another function is refused and nothing is done`() {
        ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        val t = port.open(7, "Call the bank")
        assertTrue(bad(call("tasks.complete", """{"id":${t.id},"request_id":"w-1"}""")).contains("already used for tasks.add"))
        assertTrue(port.completedIds.isEmpty())
    }

    @Test fun `complete with a request_id is done once`() {
        val t = port.open(7, "Call the bank")
        val first = ok(call("tasks.complete", """{"id":${t.id},"request_id":"w-2"}"""))
        val again = ok(call("tasks.complete", """{"id":${t.id},"request_id":"w-2"}"""))
        assertEquals(listOf(7L), port.completedIds)
        assertEquals("Done: Call the bank.", first.getString("summary"))
        assertEquals("Already done: Call the bank.", again.getString("summary"))
        assertTrue(again.getBoolean("repeated"))
    }

    @Test fun `the record holds no task details, only caller, request_id, function, task id, kind and time`() {
        ok(call("tasks.add", """{"title":"Secret surprise party","notes":"for Sandra","due":"2026-10-03","request_id":"w-1"}"""))
        port.open(7, "Call the bank")
        ok(call("tasks.complete", """{"id":7,"request_id":"w-2"}"""))
        val text = ledgerFile.readText()
        for (word in listOf("Secret", "surprise", "Sandra", "Call the bank", "bank", "My tasks", "summary", "2026-10-03")) {
            assertFalse("the record names “$word”: $text", text.contains(word))
        }
        val o = JSONObject(text)
        assertEquals(2, o.length())
        val fields = o.keys().asSequence().flatMap { o.getJSONObject(it).keys().asSequence() }.toSet()
        assertEquals(setOf("f", "k", "i", "t"), fields)
        assertEquals(setOf("added", "done"), o.keys().asSequence().map { o.getJSONObject(it).getString("k") }.toSet())
    }

    @Test fun `a repeat is told about the task as it is now`() {
        val id = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}""")).getLong("id")
        val t = port.tasks.first { it.id == id }
        port.tasks[port.tasks.indexOf(t)] = t.copy(title = "Buy oat milk", listTitle = "Work")
        val again = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        assertEquals(id, again.getLong("id"))
        assertEquals("Added to Work: Buy oat milk.", again.getString("summary"))
    }

    @Test fun `a repeat for a task that is gone says only Already handled`() {
        val id = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}""")).getLong("id")
        port.delete(id)
        val again = ok(call("tasks.add", """{"title":"Buy milk","request_id":"w-1"}"""))
        assertEquals("Already handled.", again.getString("summary"))
        assertEquals(id, again.getLong("id"))
        assertEquals(1, port.created.size)
        port.open(8, "Pay rent")
        ok(call("tasks.complete", """{"id":8,"request_id":"w-5"}"""))
        port.delete(8)
        assertEquals("Already handled.", ok(call("tasks.complete", """{"id":8,"request_id":"w-5"}""")).getString("summary"))
    }

    @Test fun `repeats of deleted and gone keep their answers`() {
        port.open(7, "Call the bank")
        port.delete(7)
        ok(call("tasks.complete", """{"id":7,"request_id":"w-6"}"""))
        assertEquals("That task was deleted.", ok(call("tasks.complete", """{"id":7,"request_id":"w-6"}""")).getString("summary"))
        ok(call("tasks.complete", """{"id":404,"request_id":"w-7"}"""))
        assertEquals("That task is no longer in 4Tasks.", ok(call("tasks.complete", """{"id":404,"request_id":"w-7"}""")).getString("summary"))
    }

    @Test fun `two copies arriving together are answered once`() = runBlocking {
        val results = (1..8).map { async { door().perform("tasks.add", JSONObject("""{"title":"Buy milk","request_id":"w-9"}""")) } }.awaitAll()
        assertEquals(1, port.created.size)
        assertEquals(1, results.map { ok(it).getLong("id") }.distinct().size)
    }

    @Test fun `without a ledger request_id is ignored, as for an old provider`() {
        val plain = TasksDoorLogic(port, clock, Locale.ENGLISH)
        runBlocking {
            plain.perform("tasks.add", JSONObject("""{"title":"Buy milk","request_id":"w-1"}"""))
            plain.perform("tasks.add", JSONObject("""{"title":"Buy milk","request_id":"w-1"}"""))
        }
        assertEquals(2, port.created.size)
    }

    // ---- said_at ------------------------------------------------------------

    @Test fun `a reminder after said_at that passed on the way goes off at once`() {
        // Said at 13:50 for 14:00; it arrives at 14:05.
        val r = ok(call("tasks.add", """{"title":"Take the cake out","reminder":"2026-10-02T14:00","said_at":"2026-10-02T13:50"}"""))
        assertEquals(listOf(r.getLong("id") to clock.millis()), port.reminders)
        assertEquals(
            "Added to My tasks: Take the cake out — the reminder for today 14:00 had passed when this arrived, so it goes off now (said at 13:50).",
            r.getString("summary"),
        )
    }

    @Test fun `a reminder at exactly said_at counts as after it`() {
        ok(call("tasks.add", """{"title":"Now","reminder":"2026-10-02T13:50","said_at":"2026-10-02T13:50"}"""))
        assertEquals(clock.millis(), port.reminders.single().second)
    }

    @Test fun `a reminder before said_at keeps today's rule`() {
        val m = bad(call("tasks.add", """{"title":"Old","reminder":"2026-10-02T13:00","said_at":"2026-10-02T13:50"}"""))
        assertTrue(m, m.contains("has already passed"))
        assertTrue(port.created.isEmpty())
        // and allow_past still lets it through, at its own time
        ok(call("tasks.add", """{"title":"Old","reminder":"2026-10-02T13:00","said_at":"2026-10-02T13:50","allow_past":true}"""))
        assertEquals(ms("2026-10-02T12:00:00Z"), port.reminders.single().second)
    }

    @Test fun `a future reminder is unchanged by said_at`() {
        ok(call("tasks.add", """{"title":"Later","reminder":"2026-10-02T16:00","said_at":"2026-10-02T13:50"}"""))
        assertEquals(ms("2026-10-02T15:00:00Z"), port.reminders.single().second)
    }

    @Test fun `said within two minutes is not mentioned, longer ago is`() {
        val near = ok(call("tasks.add", """{"title":"Buy milk","said_at":"2026-10-02T14:03"}""")).getString("summary")
        assertEquals("Added to My tasks: Buy milk.", near)
        val far = ok(call("tasks.add", """{"title":"Buy eggs","said_at":"2026-10-02T14:02"}""")).getString("summary")
        assertEquals("Added to My tasks: Buy eggs (said at 14:02).", far)
        val yesterday = ok(call("tasks.add", """{"title":"Buy jam","said_at":"2026-10-01T21:30"}""")).getString("summary")
        assertEquals("Added to My tasks: Buy jam (said yesterday 21:30).", yesterday)
    }

    @Test fun `a said_at ahead of the phone's clock counts as now`() {
        val s = ok(call("tasks.add", """{"title":"Buy milk","said_at":"2026-10-02T15:00"}""")).getString("summary")
        assertEquals("Added to My tasks: Buy milk.", s)
    }

    @Test fun `said_at needs a time`() {
        assertTrue(bad(call("tasks.add", """{"title":"Buy milk","said_at":"2026-10-02"}""")).contains("said_at needs a time"))
    }

    // ---- complete on done or deleted ----------------------------------------

    @Test fun `completing a task that is already done says so and is not an error`() {
        val t = port.open(7, "Call the bank")
        port.tasks[port.tasks.indexOf(t)] = t.copy(completed = true)
        assertEquals("Already done: Call the bank.", ok(call("tasks.complete", """{"id":7}""")).getString("summary"))
        assertTrue(port.completedIds.isEmpty())
    }

    @Test fun `completing a deleted task says so and is not an error`() {
        port.open(7, "Call the bank")
        port.delete(7)
        assertEquals("That task was deleted.", ok(call("tasks.complete", """{"id":7,"request_id":"w-3"}""")).getString("summary"))
        assertEquals("That task was deleted (said at 13:00).", ok(call("tasks.complete", """{"id":7,"said_at":"2026-10-02T13:00"}""")).getString("summary"))
    }

    @Test fun `a handed-over id that is gone altogether is an answer, but a model's wrong id is still an error`() {
        assertEquals("That task is no longer in 4Tasks.", ok(call("tasks.complete", """{"id":404,"request_id":"w-4"}""")).getString("summary"))
        assertTrue(bad(call("tasks.complete", """{"id":404}""")).contains("There is no task with id 404"))
    }

    @Test fun `complete says when it was said`() {
        port.open(7, "Call the bank")
        assertEquals("Done: Call the bank (said at 13:40).", ok(call("tasks.complete", """{"id":7,"said_at":"2026-10-02T13:40"}""")).getString("summary"))
    }

    // ---- tasks.snapshot -----------------------------------------------------

    private fun snap(args: String = "{}") = ok(call("tasks.snapshot", args))

    @Test fun `a snapshot has the lists and the open tasks soonest due first, with reminders and repeats`() {
        port.open(1, "No date")
        port.open(2, "Later", dueMillis = ms("2026-10-05T09:00:00Z"), list = "Work")
        port.open(3, "Sooner", dueMillis = ms("2026-10-03T14:00:00Z"), repeats = true)
        port.open(4, "Done one")
        port.tasks[port.tasks.lastIndex] = port.tasks.last().copy(completed = true)
        port.next[3] = ms("2026-10-03T13:45:00Z")
        val s = snap()
        assertFalse(s.getBoolean("unchanged"))
        assertEquals("My tasks, Work, Shared", s.getString("lists"))
        assertEquals(3, s.getInt("count"))
        assertFalse(s.getBoolean("truncated"))
        assertEquals("2026-10-02T14:05:00", s.getString("taken_at"))
        val tasks = JSONArray(s.getString("tasks"))
        assertEquals(listOf(3L, 2L, 1L), (0 until tasks.length()).map { tasks.getJSONObject(it).getLong("id") })
        val first = tasks.getJSONObject(0)
        assertEquals("Sooner", first.getString("title"))
        assertEquals("2026-10-03T15:00", first.getString("due"))
        assertEquals("2026-10-03T14:45", first.getString("reminder"))
        assertEquals("My tasks", first.getString("list"))
        assertTrue(first.getBoolean("repeats"))
        assertFalse(tasks.getJSONObject(2).has("due"))
        assertFalse(tasks.getJSONObject(2).has("reminder"))
    }

    @Test fun `since the current stamp returns only unchanged, the stamp and taken_at`() {
        port.open(1, "Buy milk")
        val stamp = snap().getString("stamp")
        val again = snap("""{"since":"$stamp"}""")
        assertTrue(again.getBoolean("unchanged"))
        assertEquals(stamp, again.getString("stamp"))
        assertEquals(setOf("unchanged", "stamp", "taken_at"), again.keys().asSequence().toSet())
    }

    @Test fun `the stamp changes on add, complete, edit, list rename and reminder`() {
        port.open(1, "Buy milk")
        val stamps = mutableListOf(snap().getString("stamp"))
        ok(call("tasks.add", """{"title":"Buy eggs"}"""))
        stamps += snap().getString("stamp")
        ok(call("tasks.complete", """{"id":1}"""))
        stamps += snap().getString("stamp")
        port.modified[100] = ms("2026-10-02T13:06:00Z") // an edit made in the app
        stamps += snap().getString("stamp")
        port.lists = port.lists.map { if (it.id == 2L) it.copy(title = "Office") else it }
        stamps += snap().getString("stamp")
        port.next[100] = ms("2026-10-02T18:00:00Z")
        stamps += snap().getString("stamp")
        port.delete(100)
        stamps += snap().getString("stamp")
        assertEquals(stamps.size, stamps.distinct().size)
        assertTrue(stamps.all { it.length == 16 })
        // and an old stamp gets a full answer
        assertFalse(snap("""{"since":"${stamps.first()}"}""").getBoolean("unchanged"))
    }

    @Test fun `more than 300 open tasks are cut to 300 and say truncated`() {
        repeat(350) { port.open(1000L + it, "Task $it") }
        val s = snap()
        assertEquals(350, s.getInt("count"))
        assertEquals(300, JSONArray(s.getString("tasks")).length())
        assertTrue(s.getBoolean("truncated"))
    }

    @Test fun `a snapshot that would not fit is cut until it does`() {
        // A title typed in the app has no 200-character limit.
        repeat(300) { port.open(1000L + it, "x".repeat(500) + it) }
        val s = ok(call("tasks.snapshot", "{}"))
        val json = (call("tasks.snapshot", "{}") as Outcome.Ok).json
        assertTrue(json.length <= TasksCatalogue.SNAPSHOT_MAX_CHARS)
        val shown = JSONArray(s.getString("tasks")).length()
        assertTrue("$shown", shown in 1 until 300)
        assertTrue(s.getBoolean("truncated"))
    }

    // ---- the catalogue ------------------------------------------------------

    @Test fun `the new fields and function are in the catalogue and survive the reader`() {
        val read = Catalogue.parse(TasksCatalogue.CATALOGUE.toJson().toString())!!.catalogue
        val add = read.functions.first { it.id == "tasks.add" }
        val complete = read.functions.first { it.id == "tasks.complete" }
        val snapshot = read.functions.first { it.id == "tasks.snapshot" }
        assertEquals("1.2", add.version)
        assertEquals("1.1", complete.version)
        assertEquals("1.0", snapshot.version)
        for (f in listOf(add, complete)) {
            assertTrue(f.input.properties.keys.containsAll(listOf("request_id", "said_at")))
            assertTrue(f.input.properties["request_id"]!!.description!!.contains("Leave out otherwise"))
        }
        assertEquals(uk.mr_biz.fourlink.Effect.READ, snapshot.effect)
        // A request_id over 64 characters is refused by the library before the door sees it.
        assertTrue(Validation.problem(add, """{"title":"a","request_id":"${"x".repeat(65)}"}""") != null)
        assertEquals(null, Validation.problem(add, """{"title":"a","request_id":"${"x".repeat(64)}","said_at":"2026-10-02T13:50"}"""))
    }

    @Test fun `old stamps of different content differ`() {
        val a = TasksDoorLogic.stamp(listOf(ListInfo(1, "A", false)), emptyList(), emptyMap())
        val b = TasksDoorLogic.stamp(listOf(ListInfo(1, "B", false)), emptyList(), emptyMap())
        assertNotEquals(a, b)
    }
}
