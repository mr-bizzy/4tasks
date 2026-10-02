package org.tasks.fourlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class DoorDatesTest {
    private fun bad(field: String, text: String): String =
        try { DoorDates.parse(field, text); fail("accepted “$text”"); "" } catch (e: BadArgument) { e.message!! }

    @Test fun `a date alone is an all-day moment`() {
        assertEquals(Moment.Day(LocalDate.of(2026, 10, 3)), DoorDates.parse("due", "2026-10-03"))
        assertEquals("2026-10-03", DoorDates.parse("due", "2026-10-03").engineValue)
    }

    @Test fun `a date and time is a timed moment, with T or a space, with or without seconds`() {
        val expected = Moment.DayTime(LocalDateTime.of(2026, 10, 3, 15, 0))
        assertEquals(expected, DoorDates.parse("due", "2026-10-03T15:00"))
        assertEquals(expected, DoorDates.parse("due", "2026-10-03 15:00"))
        assertEquals(expected, DoorDates.parse("due", "2026-10-03T15:00:00"))
        assertEquals("2026-10-03T15:00:00", expected.engineValue)
        assertEquals("2026-10-03T15:00:30", DoorDates.parse("due", "2026-10-03T15:00:30").engineValue)
    }

    @Test fun `surrounding spaces are ignored`() {
        assertEquals(Moment.Day(LocalDate.of(2026, 10, 3)), DoorDates.parse("due", "  2026-10-03 "))
    }

    @Test fun `a zone or offset is refused, naming the field`() {
        for (t in listOf("2026-10-03T15:00Z", "2026-10-03T15:00+01:00", "2026-10-03T15:00:00-05:00")) {
            assertTrue(bad("due", t).startsWith("due must look like 2026-10-03"))
        }
    }

    @Test fun `words and half dates are refused`() {
        for (t in listOf("tomorrow", "3 Oct", "2026-10", "2026/10/03", "15:00", "")) bad("reminder", t)
    }

    @Test fun `dates that do not exist are refused`() {
        assertTrue(bad("due", "2026-02-30").contains("not a real date"))
        assertTrue(bad("due", "2026-13-01").contains("not a real date"))
        assertTrue(bad("due", "2025-02-29").contains("not a real date"))
        DoorDates.parse("due", "2028-02-29")
    }

    @Test fun `times that do not exist are refused`() {
        assertTrue(bad("reminder", "2026-10-03T25:00").contains("time that does not exist"))
        assertTrue(bad("reminder", "2026-10-03T12:60").contains("time that does not exist"))
    }

    @Test fun `years outside 2000 to 2100 are refused`() {
        assertTrue(bad("due", "1999-12-31").contains("outside 2000 to 2100"))
        assertTrue(bad("due", "2101-01-01").contains("outside 2000 to 2100"))
        DoorDates.parse("due", "2000-01-01"); DoorDates.parse("due", "2100-12-31")
    }

    @Test fun `an inclusive end of day is the last millisecond`() {
        val zone = ZoneId.of("Europe/London")
        val end = DoorDates.endOfDayMillis(LocalDate.of(2026, 10, 3), zone)
        assertEquals(LocalDate.of(2026, 10, 4).atStartOfDay(zone).toInstant().toEpochMilli() - 1, end)
    }

    @Test fun `the day the clocks go back is 25 hours long`() {
        val zone = ZoneId.of("Europe/London")
        val start = Moment.Day(LocalDate.of(2026, 10, 25)).startMillis(zone)
        val end = DoorDates.endOfDayMillis(LocalDate.of(2026, 10, 25), zone)
        assertEquals(25L * 3600_000 - 1, end - start)
    }

    @Test fun `moments are described for people with today and tomorrow`() {
        val today = LocalDate.of(2026, 10, 2)
        val en = Locale.ENGLISH
        assertEquals("today", DoorDates.describe(Moment.Day(today), today, en))
        assertEquals("tomorrow 15:00", DoorDates.describe(Moment.DayTime(LocalDateTime.of(2026, 10, 3, 15, 0)), today, en))
        assertEquals("yesterday", DoorDates.describe(Moment.Day(today.minusDays(1)), today, en))
        assertEquals("Fri 9 Oct", DoorDates.describe(Moment.Day(LocalDate.of(2026, 10, 9)), today, en))
        assertEquals("Fri 8 Jan 2027 09:05", DoorDates.describe(Moment.DayTime(LocalDateTime.of(2027, 1, 8, 9, 5)), today, en))
    }
}
