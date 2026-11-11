package com.gatefit.domain.analysis

import com.gatefit.domain.model.CheckOutcome
import com.gatefit.domain.model.CheckReason
import com.gatefit.domain.model.DayType
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.WeeklyHours
import com.gatefit.domain.testing.FakeHolidayCalendar
import com.gatefit.domain.testing.Fixtures.hours
import com.gatefit.domain.testing.Fixtures.range
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OperatingHoursEvaluatorTest {

    private val wednesday = LocalDate.of(2026, 11, 11)
    private val saturday = LocalDate.of(2026, 11, 14)
    private val sunday = LocalDate.of(2026, 11, 15)

    private fun evaluator(calendar: HolidayCalendar = FakeHolidayCalendar()) = OperatingHoursEvaluator(calendar)

    private fun at(date: LocalDate, time: String): LocalDateTime = date.atTime(java.time.LocalTime.parse(time))

    @Test
    fun `time inside a weekday range is open`() {
        val weekly = WeeklyHours(weekday = hours(range("09:00", "22:00")))

        val check = evaluator().evaluate(weekly, at(wednesday, "13:00"))

        assertEquals(CheckOutcome.PASSED, check.outcome)
        assertEquals(CheckReason.HOURS_OPEN, check.reason)
    }

    @Test
    fun `range start is inclusive and range end is exclusive`() {
        val weekly = WeeklyHours(weekday = hours(range("09:00", "22:00")))

        assertEquals(CheckOutcome.PASSED, evaluator().evaluate(weekly, at(wednesday, "09:00")).outcome)
        assertEquals(CheckOutcome.FAILED, evaluator().evaluate(weekly, at(wednesday, "22:00")).outcome)
        assertEquals(CheckOutcome.FAILED, evaluator().evaluate(weekly, at(wednesday, "08:59")).outcome)
    }

    @Test
    fun `each day type uses its own hours`() {
        val weekly = WeeklyHours(
            weekday = OperatingHours.AllDay,
            saturday = hours(range("09:00", "13:00")),
            sunday = OperatingHours.Closed,
        )

        assertEquals(CheckReason.HOURS_ALL_DAY, evaluator().evaluate(weekly, at(wednesday, "03:00")).reason)
        assertEquals(CheckReason.HOURS_OUTSIDE, evaluator().evaluate(weekly, at(saturday, "15:00")).reason)
        assertEquals(CheckReason.HOURS_CLOSED_DAY, evaluator().evaluate(weekly, at(sunday, "10:00")).reason)
    }

    @Test
    fun `empty hours are unknown instead of always open`() {
        val check = evaluator().evaluate(WeeklyHours(), at(wednesday, "13:00"))

        assertEquals(CheckOutcome.UNKNOWN, check.outcome)
        assertEquals(CheckReason.HOURS_NOT_PROVIDED, check.reason)
    }

    @Test
    fun `holiday uses holiday hours even on a weekday`() {
        val calendar = FakeHolidayCalendar(holidays = setOf(wednesday))
        val weekly = WeeklyHours(weekday = OperatingHours.AllDay, holiday = OperatingHours.Closed)

        val check = evaluator(calendar).evaluate(weekly, at(wednesday, "13:00"))

        assertEquals(CheckOutcome.FAILED, check.outcome)
        assertEquals(CheckReason.HOURS_CLOSED_DAY, check.reason)
    }

    @Test
    fun `weekday hours are not applied when the holiday status is unknown`() {
        val calendar = FakeHolidayCalendar(allUnknown = true)
        val weekly = WeeklyHours(weekday = OperatingHours.AllDay, holiday = OperatingHours.Closed)

        val check = evaluator(calendar).evaluate(weekly, at(wednesday, "13:00"))

        assertEquals(CheckOutcome.UNKNOWN, check.outcome)
        assertEquals(CheckReason.HOLIDAY_STATUS_UNKNOWN, check.reason)
    }

    @Test
    fun `unknown holiday status is harmless when weekday and holiday hours agree`() {
        val calendar = FakeHolidayCalendar(allUnknown = true)
        val weekly = WeeklyHours(weekday = OperatingHours.AllDay, holiday = OperatingHours.AllDay)

        val check = evaluator(calendar).evaluate(weekly, at(wednesday, "13:00"))

        assertEquals(CheckOutcome.PASSED, check.outcome)
    }

    @Test
    fun `overnight range stays open after midnight on the next day`() {
        val weekly = WeeklyHours(weekday = hours(range("18:00", "02:00")))
        val thursday = wednesday.plusDays(1)

        assertEquals(CheckOutcome.PASSED, evaluator().evaluate(weekly, at(wednesday, "23:30")).outcome)
        assertEquals(CheckOutcome.PASSED, evaluator().evaluate(weekly, at(thursday, "01:30")).outcome)
        assertEquals(CheckOutcome.FAILED, evaluator().evaluate(weekly, at(thursday, "02:00")).outcome)
        assertEquals(CheckOutcome.FAILED, evaluator().evaluate(weekly, at(wednesday, "17:59")).outcome)
    }

    @Test
    fun `overnight carry over follows the previous day type`() {
        // 금요일 야간 구간은 토요일 새벽까지 이어지고, 토요일 자체는 휴무
        val friday = LocalDate.of(2026, 11, 13)
        val weekly = WeeklyHours(weekday = hours(range("20:00", "03:00")), saturday = OperatingHours.Closed)

        assertEquals(CheckOutcome.PASSED, evaluator().evaluate(weekly, at(friday, "21:00")).outcome)
        assertEquals(CheckOutcome.PASSED, evaluator().evaluate(weekly, at(saturday, "02:00")).outcome)
        assertEquals(CheckOutcome.FAILED, evaluator().evaluate(weekly, at(saturday, "04:00")).outcome)
    }

    @Test
    fun `day type is resolved only when the holiday status is known`() {
        assertEquals(DayType.WEEKDAY, evaluator().dayTypeOf(wednesday))
        assertEquals(DayType.SATURDAY, evaluator().dayTypeOf(saturday))
        assertEquals(DayType.HOLIDAY, evaluator(FakeHolidayCalendar(holidays = setOf(wednesday))).dayTypeOf(wednesday))
        assertNull(evaluator(FakeHolidayCalendar(allUnknown = true)).dayTypeOf(wednesday))
    }
}
