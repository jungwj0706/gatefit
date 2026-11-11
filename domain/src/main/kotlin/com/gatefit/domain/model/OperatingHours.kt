package com.gatefit.domain.model

import java.time.LocalTime

data class TimeRange(val start: LocalTime, val end: LocalTime) {
    // 종료 시각이 시작 시각 이하면 자정을 넘기는 구간
    val crossesMidnight: Boolean get() = end <= start

    fun containsSameDay(time: LocalTime): Boolean =
        if (crossesMidnight) time >= start else time >= start && time < end

    fun containsCarryOver(time: LocalTime): Boolean = crossesMidnight && time < end
}

sealed interface OperatingHours {
    // 비어 있는 운영 시간은 상시 개방으로 해석 금지
    data object Unknown : OperatingHours
    data object AllDay : OperatingHours
    data object Closed : OperatingHours
    data class Ranges(val ranges: List<TimeRange>) : OperatingHours
}

enum class DayType { WEEKDAY, SATURDAY, SUNDAY, HOLIDAY }

data class WeeklyHours(
    val weekday: OperatingHours = OperatingHours.Unknown,
    val saturday: OperatingHours = OperatingHours.Unknown,
    val sunday: OperatingHours = OperatingHours.Unknown,
    val holiday: OperatingHours = OperatingHours.Unknown,
) {
    fun of(dayType: DayType): OperatingHours = when (dayType) {
        DayType.WEEKDAY -> weekday
        DayType.SATURDAY -> saturday
        DayType.SUNDAY -> sunday
        DayType.HOLIDAY -> holiday
    }

    val allUnknown: Boolean
        get() = DayType.entries.all { of(it) is OperatingHours.Unknown }
}
