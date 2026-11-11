package com.gatefit.domain.analysis

import java.time.LocalDate

// 공식 발표 자료로 확인된 연도만 판단, 그 외 연도는 공휴일 여부 미확인
class StaticHolidayCalendar(
    private val holidaysByYear: Map<Int, Set<LocalDate>>,
) : HolidayCalendar {

    val coveredYears: Set<Int> get() = holidaysByYear.keys

    override fun statusOf(date: LocalDate): HolidayStatus {
        val holidays = holidaysByYear[date.year] ?: return HolidayStatus.UNKNOWN
        return if (date in holidays) HolidayStatus.HOLIDAY else HolidayStatus.NOT_HOLIDAY
    }
}
