package lynks.reminder

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import lynks.common.exception.InvalidModelException
import lynks.util.JsonMapper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private const val TIME_PATTERN = "HH:mm"

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes(
    JsonSubTypes.Type(IntervalSchedule::class, name = "interval"),
    JsonSubTypes.Type(CalendarSchedule::class, name = "calendar"),
)
sealed interface Schedule {

    // null when the schedule can never fire again, such as the 31st of February
    fun next(after: ZonedDateTime): ZonedDateTime?

    fun validate()

    fun toSpec(): String = JsonMapper.defaultMapper.writeValueAsString(this)

    companion object {
        fun fromSpec(spec: String): Schedule = JsonMapper.defaultMapper.readValue(spec, Schedule::class.java)
    }
}

enum class IntervalUnit(val chronoUnit: ChronoUnit, val max: Int) {
    MINUTES(ChronoUnit.MINUTES, 24 * 60),
    HOURS(ChronoUnit.HOURS, 24),
}

// Aligned to the clock rather than to when it was created, so a restart never shifts it:
// each day restarts at `from` (default midnight) and steps by `every` until `to`.
data class IntervalSchedule(
    val every: Int,
    val unit: IntervalUnit,
    @get:JsonFormat(pattern = TIME_PATTERN) val from: LocalTime? = null,
    @get:JsonFormat(pattern = TIME_PATTERN) val to: LocalTime? = null,
) : Schedule {

    override fun next(after: ZonedDateTime): ZonedDateTime? {
        val start = from ?: LocalTime.MIDNIGHT
        val end = to ?: LocalTime.MAX
        var date = after.toLocalDate()
        // three days covers a window that has already closed today plus a DST shift
        repeat(3) {
            var candidate = date.atTime(start)
            val last = date.atTime(end)
            while (!candidate.isAfter(last)) {
                val zoned = candidate.atZone(after.zone)
                if (zoned.isAfter(after)) return zoned
                candidate = candidate.plus(every.toLong(), unit.chronoUnit)
            }
            date = date.plusDays(1)
        }
        return null
    }

    override fun validate() {
        if (every !in 1..unit.max) invalid("Interval must be between 1 and ${unit.max} ${unit.name.lowercase()}")
        if ((from == null) != (to == null)) invalid("A time window needs both a start and an end")
        if (from != null && to != null && !from.isBefore(to)) invalid("The time window must start before it ends")
    }
}

// Empty sets mean "every". Days are either days of the month or weekdays, optionally narrowed
// to their nth occurrence in the month ("2nd and 3rd Monday").
data class CalendarSchedule(
    @get:JsonFormat(pattern = TIME_PATTERN) val at: LocalTime,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val ordinals: Set<Int> = emptySet(),
    val monthDays: Set<Int> = emptySet(),
    val months: Set<Int> = emptySet(),
) : Schedule {

    override fun next(after: ZonedDateTime): ZonedDateTime? {
        var date = after.toLocalDate()
        val limit = date.plusYears(SEARCH_YEARS)
        while (!date.isAfter(limit)) {
            if (matches(date)) {
                val zoned = date.atTime(at).atZone(after.zone)
                if (zoned.isAfter(after)) return zoned
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun matches(date: LocalDate): Boolean {
        if (months.isNotEmpty() && date.monthValue !in months) return false
        if (monthDays.isNotEmpty()) return date.dayOfMonth in monthDays
        if (weekdays.isNotEmpty() && date.dayOfWeek !in weekdays) return false
        return ordinals.isEmpty() || (date.dayOfMonth - 1) / 7 + 1 in ordinals
    }

    override fun validate() {
        if (monthDays.isNotEmpty() && weekdays.isNotEmpty()) invalid("Choose days of the month or weekdays, not both")
        if (ordinals.isNotEmpty() && weekdays.isEmpty()) invalid("Choose which weekdays the occurrences apply to")
        if (ordinals.any { it !in 1..5 }) invalid("Occurrences must be between 1st and 5th")
        if (monthDays.any { it !in 1..31 }) invalid("Days of the month must be between 1 and 31")
        if (months.any { it !in 1..12 }) invalid("Months must be between 1 and 12")
    }

    private companion object {
        // the rarest real match, a 5th weekday of February, needs the 29th to fall on it: up to 28 years apart
        const val SEARCH_YEARS = 28L
    }
}

private fun invalid(message: String): Nothing = throw InvalidModelException(message)
