package lynks.reminder

import lynks.common.exception.InvalidModelException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleTest {

    private val london = ZoneId.of("Europe/London")

    private fun at(text: String, zone: ZoneId = london): ZonedDateTime = LocalDateTime.parse(text).atZone(zone)

    private fun Schedule.fires(from: String, count: Int, zone: ZoneId = london): List<String> {
        val out = mutableListOf<String>()
        var next = next(at(from, zone))
        while (next != null && out.size < count) {
            out += next.toLocalDateTime().toString()
            next = next(next)
        }
        return out
    }

    @Test
    fun testEveryTwelveHours() {
        assertThat(IntervalSchedule(12, IntervalUnit.HOURS).fires("2026-09-23T10:15", 3))
            .containsExactly("2026-09-23T12:00", "2026-09-24T00:00", "2026-09-24T12:00")
    }

    @Test
    fun testIntervalRestartsEachDay() {
        assertThat(IntervalSchedule(7, IntervalUnit.HOURS).fires("2026-09-23T20:00", 3))
            .containsExactly("2026-09-23T21:00", "2026-09-24T00:00", "2026-09-24T07:00")
    }

    @Test
    fun testIntervalWithinWindow() {
        val schedule = IntervalSchedule(5, IntervalUnit.MINUTES, LocalTime.of(10, 0), LocalTime.of(14, 0))
        assertThat(schedule.fires("2026-09-23T13:52", 4))
            .containsExactly("2026-09-23T13:55", "2026-09-23T14:00", "2026-09-24T10:00", "2026-09-24T10:05")
        assertThat(schedule.fires("2026-09-23T08:00", 1)).containsExactly("2026-09-23T10:00")
    }

    @Test
    fun testIntervalIsStrictlyAfter() {
        assertThat(IntervalSchedule(30, IntervalUnit.MINUTES).fires("2026-09-23T10:30", 1))
            .containsExactly("2026-09-23T11:00")
    }

    @Test
    fun testEveryDay() {
        assertThat(CalendarSchedule(LocalTime.MIDNIGHT).fires("2026-09-23T10:00", 2))
            .containsExactly("2026-09-24T00:00", "2026-09-25T00:00")
    }

    @Test
    fun testEveryMonday() {
        // 2026-09-23 is a Wednesday
        assertThat(CalendarSchedule(LocalTime.of(9, 0), weekdays = setOf(MONDAY)).fires("2026-09-23T10:00", 2))
            .containsExactly("2026-09-28T09:00", "2026-10-05T09:00")
    }

    @Test
    fun testTodayWhenTimeNotYetPassed() {
        assertThat(CalendarSchedule(LocalTime.of(17, 0), weekdays = setOf(WEDNESDAY)).fires("2026-09-23T10:00", 1))
            .containsExactly("2026-09-23T17:00")
    }

    @Test
    fun testNthWeekdaysOfMonth() {
        // 2nd,third mon,wed,thu of march 17:00
        val schedule = CalendarSchedule(
            LocalTime.of(17, 0), weekdays = setOf(MONDAY, WEDNESDAY, THURSDAY),
            ordinals = setOf(2, 3), months = setOf(3)
        )
        assertThat(schedule.fires("2027-01-01T00:00", 7)).containsExactly(
            "2027-03-08T17:00", "2027-03-10T17:00", "2027-03-11T17:00",
            "2027-03-15T17:00", "2027-03-17T17:00", "2027-03-18T17:00",
            "2028-03-08T17:00"
        )
    }

    @Test
    fun testFirstMondayOfMonths() {
        // 1st monday of sep,oct,nov 17:00
        val schedule = CalendarSchedule(
            LocalTime.of(17, 0), weekdays = setOf(MONDAY), ordinals = setOf(1),
            months = setOf(9, 10, 11)
        )
        assertThat(schedule.fires("2026-09-23T10:00", 3))
            .containsExactly("2026-10-05T17:00", "2026-11-02T17:00", "2027-09-06T17:00")
    }

    @Test
    fun testDaysOfMonth() {
        // 1 of jan,april,july,oct 00:00
        val schedule = CalendarSchedule(LocalTime.MIDNIGHT, monthDays = setOf(1), months = setOf(1, 4, 7, 10))
        assertThat(schedule.fires("2026-09-23T10:00", 3))
            .containsExactly("2026-10-01T00:00", "2027-01-01T00:00", "2027-04-01T00:00")
    }

    @Test
    fun testMonthDaySkipsShortMonths() {
        assertThat(CalendarSchedule(LocalTime.NOON, monthDays = setOf(31)).fires("2026-09-01T00:00", 2))
            .containsExactly("2026-10-31T12:00", "2026-12-31T12:00")
    }

    @Test
    fun testImpossibleDateNeverFires() {
        assertThat(
            CalendarSchedule(
                LocalTime.NOON,
                monthDays = setOf(30),
                months = setOf(2)
            ).next(at("2026-01-01T00:00"))
        ).isNull()
    }

    @Test
    fun testLeapDay() {
        assertThat(CalendarSchedule(LocalTime.NOON, monthDays = setOf(29), months = setOf(2)).fires("2026-03-01T00:00", 1))
            .containsExactly("2028-02-29T12:00")
    }

    @Test
    fun testFifthWeekdayOfFebruary() {
        val schedule = CalendarSchedule(LocalTime.NOON, weekdays = setOf(MONDAY), ordinals = setOf(5), months = setOf(2))
        assertThat(schedule.fires("2026-09-23T00:00", 1)).containsExactly("2044-02-29T12:00")
    }

    @Test
    fun testKeepsLocalTimeAcrossDst() {
        // clocks go forward in London on 2027-03-28
        val fires = CalendarSchedule(LocalTime.of(9, 0)).let {
            listOf(it.next(at("2027-03-26T10:00"))!!, it.next(at("2027-03-28T10:00"))!!)
        }
        assertThat(fires.map { it.toLocalTime() }).containsOnly(LocalTime.of(9, 0))
        assertThat(fires.map { it.offset.totalSeconds }).containsExactly(0, 3600)
    }

    @Test
    fun testTimeInDstGapMovesForward() {
        assertThat(CalendarSchedule(LocalTime.of(1, 30)).next(at("2027-03-27T12:00"))?.toLocalDateTime().toString())
            .isEqualTo("2027-03-28T02:30")
    }

    @Test
    fun testUsesZoneOfTheInstant() {
        val singapore = ZoneId.of("Asia/Singapore")
        assertThat(CalendarSchedule(LocalTime.of(6, 0)).fires("2026-09-23T07:00", 1, singapore))
            .containsExactly("2026-09-24T06:00")
    }

    @Test
    fun testValidation() {
        assertThrows<InvalidModelException> { IntervalSchedule(0, IntervalUnit.MINUTES).validate() }
        assertThrows<InvalidModelException> { IntervalSchedule(25, IntervalUnit.HOURS).validate() }
        assertThrows<InvalidModelException> { IntervalSchedule(5, IntervalUnit.MINUTES, from = LocalTime.NOON).validate() }
        assertThrows<InvalidModelException> {
            IntervalSchedule(5, IntervalUnit.MINUTES, LocalTime.NOON, LocalTime.of(9, 0)).validate()
        }
        assertThrows<InvalidModelException> {
            CalendarSchedule(LocalTime.NOON, weekdays = setOf(MONDAY), monthDays = setOf(1)).validate()
        }
        assertThrows<InvalidModelException> { CalendarSchedule(LocalTime.NOON, ordinals = setOf(1)).validate() }
        assertThrows<InvalidModelException> {
            CalendarSchedule(LocalTime.NOON, weekdays = setOf(MONDAY), ordinals = setOf(6)).validate()
        }
        assertThrows<InvalidModelException> { CalendarSchedule(LocalTime.NOON, monthDays = setOf(32)).validate() }
        assertThrows<InvalidModelException> { CalendarSchedule(LocalTime.NOON, months = setOf(13)).validate() }

        IntervalSchedule(24, IntervalUnit.HOURS).validate()
        CalendarSchedule(LocalTime.NOON, weekdays = setOf(MONDAY), ordinals = setOf(5), months = setOf(12)).validate()
    }

    @Test
    fun testSpecRoundTrip() {
        val schedules = listOf(
            IntervalSchedule(5, IntervalUnit.MINUTES, LocalTime.of(10, 0), LocalTime.of(14, 0)),
            CalendarSchedule(LocalTime.of(17, 0), weekdays = setOf(MONDAY, WEDNESDAY), ordinals = setOf(2), months = setOf(3)),
            CalendarSchedule(LocalTime.MIDNIGHT, monthDays = setOf(1, 15)),
        )
        schedules.forEach { assertThat(Schedule.fromSpec(it.toSpec())).isEqualTo(it) }
    }

    @Test
    fun testSpecFormat() {
        assertThat(CalendarSchedule(LocalTime.of(9, 0), weekdays = setOf(MONDAY)).toSpec())
            .isEqualTo("""{"kind":"calendar","at":"09:00","weekdays":["monday"],"ordinals":[],"monthDays":[],"months":[]}""")
        assertThat(Schedule.fromSpec("""{"kind":"interval","every":12,"unit":"hours"}"""))
            .isEqualTo(IntervalSchedule(12, IntervalUnit.HOURS))
    }
}
