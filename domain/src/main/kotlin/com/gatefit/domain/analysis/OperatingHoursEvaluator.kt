package com.gatefit.domain.analysis

import com.gatefit.domain.model.CheckOutcome
import com.gatefit.domain.model.CheckReason
import com.gatefit.domain.model.ConstraintCheck
import com.gatefit.domain.model.ConstraintType
import com.gatefit.domain.model.DayType
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.WeeklyHours
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class OperatingHoursEvaluator(private val holidays: HolidayCalendar) {

    fun evaluate(hours: WeeklyHours, at: LocalDateTime): ConstraintCheck {
        val todayTypes = candidateDayTypes(at.toLocalDate())
        val yesterdayTypes = candidateDayTypes(at.toLocalDate().minusDays(1))

        // 공휴일 여부가 불확실하면 가능한 요일 유형을 모두 평가하고 결과가 일치할 때만 확정
        val verdicts = todayTypes.flatMap { today ->
            yesterdayTypes.map { yesterday -> verdictFor(hours, today, yesterday, at.toLocalTime()) }
        }
        val outcomes = verdicts.map { it.first }.distinct()
        val (outcome, reason) = if (outcomes.size == 1) {
            verdicts.first()
        } else {
            CheckOutcome.UNKNOWN to CheckReason.HOLIDAY_STATUS_UNKNOWN
        }
        return ConstraintCheck(
            type = ConstraintType.OPERATING_HOURS,
            outcome = outcome,
            reason = reason,
            required = true,
        )
    }

    fun dayTypeOf(date: LocalDate): DayType? = candidateDayTypes(date).singleOrNull()

    private fun candidateDayTypes(date: LocalDate): List<DayType> {
        val byWeekday = when (date.dayOfWeek) {
            DayOfWeek.SATURDAY -> DayType.SATURDAY
            DayOfWeek.SUNDAY -> DayType.SUNDAY
            else -> DayType.WEEKDAY
        }
        return when (holidays.statusOf(date)) {
            HolidayStatus.HOLIDAY -> listOf(DayType.HOLIDAY)
            HolidayStatus.NOT_HOLIDAY -> listOf(byWeekday)
            HolidayStatus.UNKNOWN -> listOf(byWeekday, DayType.HOLIDAY)
        }
    }

    private fun verdictFor(
        hours: WeeklyHours,
        today: DayType,
        yesterday: DayType,
        time: LocalTime,
    ): Pair<CheckOutcome, CheckReason> {
        val carriedOver = (hours.of(yesterday) as? OperatingHours.Ranges)
            ?.ranges?.any { it.containsCarryOver(time) } == true
        if (carriedOver) return CheckOutcome.PASSED to CheckReason.HOURS_OPEN

        return when (val todayHours = hours.of(today)) {
            OperatingHours.Unknown -> CheckOutcome.UNKNOWN to
                if (today == DayType.HOLIDAY) CheckReason.HOLIDAY_HOURS_NOT_PROVIDED else CheckReason.HOURS_NOT_PROVIDED
            OperatingHours.AllDay -> CheckOutcome.PASSED to CheckReason.HOURS_ALL_DAY
            OperatingHours.Closed -> CheckOutcome.FAILED to CheckReason.HOURS_CLOSED_DAY
            is OperatingHours.Ranges ->
                if (todayHours.ranges.any { it.containsSameDay(time) }) {
                    CheckOutcome.PASSED to CheckReason.HOURS_OPEN
                } else {
                    CheckOutcome.FAILED to CheckReason.HOURS_OUTSIDE
                }
        }
    }
}
