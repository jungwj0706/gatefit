package com.gatefit.domain.analysis

import java.time.LocalDate

enum class HolidayStatus { HOLIDAY, NOT_HOLIDAY, UNKNOWN }

interface HolidayCalendar {
    // 공휴일 데이터가 없는 날짜는 반드시 UNKNOWN 반환
    fun statusOf(date: LocalDate): HolidayStatus
}
