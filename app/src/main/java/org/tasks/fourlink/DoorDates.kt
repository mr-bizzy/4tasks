package org.tasks.fourlink

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A date or date-time from a caller, already checked. */
sealed interface Moment {
    val date: LocalDate

    /** What the Tasks.org engine reads: a local date, or a local date and time. */
    val engineValue: String

    data class Day(override val date: LocalDate) : Moment {
        override val engineValue: String get() = date.toString()
    }

    data class DayTime(val at: LocalDateTime) : Moment {
        override val date: LocalDate get() = at.toLocalDate()
        override val engineValue: String get() = at.withNano(0).toString().let { if (it.length == 16) "$it:00" else it }
    }

    fun startMillis(zone: ZoneId): Long = when (this) {
        is Day -> date.atStartOfDay(zone).toInstant().toEpochMilli()
        is DayTime -> at.atZone(zone).toInstant().toEpochMilli()
    }
}

/**
 * An argument that is wrong; the message is a sentence for the caller. A [suggestion] is an optional
 * "did you mean" the caller may put to the user (4Link spec 11a): nothing has been done.
 */
class BadArgument(message: String, val suggestion: uk.mr_biz.fourlink.Suggestion? = null) : Exception(message)

/** Reading dates the way 4Tasks promises to (ISO-8601 local, no zone) and saying them back to people. */
object DoorDates {
    const val MIN_YEAR = 2000
    const val MAX_YEAR = 2100

    private val DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
    private val DATE_TIME = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$""")

    /** @throws BadArgument with a sentence naming [field]. */
    fun parse(field: String, raw: String): Moment {
        val text = raw.trim()
        val shape = "$field must look like 2026-10-03 or 2026-10-03T15:00, in local time with no zone, not “$text”."
        DATE.matchEntire(text)?.let { m ->
            return Moment.Day(date(field, text, m.groupValues[1], m.groupValues[2], m.groupValues[3]))
        }
        DATE_TIME.matchEntire(text)?.let { m ->
            val day = date(field, text, m.groupValues[1], m.groupValues[2], m.groupValues[3])
            val h = m.groupValues[4].toInt()
            val min = m.groupValues[5].toInt()
            val sec = m.groupValues[6].ifEmpty { "0" }.toInt()
            if (h > 23 || min > 59 || sec > 59) throw BadArgument("$field has a time that does not exist: “$text”.")
            return Moment.DayTime(LocalDateTime.of(day, LocalTime.of(h, min, sec)))
        }
        throw BadArgument(shape)
    }

    private fun date(field: String, text: String, y: String, m: String, d: String): LocalDate {
        val year = y.toInt()
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw BadArgument("$field is in the year $year, which is outside $MIN_YEAR to $MAX_YEAR: “$text”.")
        }
        return try {
            LocalDate.of(year, m.toInt(), d.toInt())
        } catch (e: DateTimeException) {
            throw BadArgument("$field is not a real date: “$text”.")
        }
    }

    /** The end of a day, for an inclusive upper bound given as a date alone. */
    fun endOfDayMillis(day: LocalDate, zone: ZoneId): Long =
        day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    private fun dayWord(day: LocalDate, today: LocalDate, locale: Locale): String = when (day) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        today.minusDays(1) -> "yesterday"
        else -> day.format(
            DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale),
        )
    }

    /** "tomorrow 15:00", "Fri 3 Oct", for a one-line summary. */
    fun describe(moment: Moment, today: LocalDate, locale: Locale): String = when (moment) {
        is Moment.Day -> dayWord(moment.date, today, locale)
        is Moment.DayTime ->
            dayWord(moment.date, today, locale) + " " + moment.at.format(DateTimeFormatter.ofPattern("HH:mm"))
    }
}
