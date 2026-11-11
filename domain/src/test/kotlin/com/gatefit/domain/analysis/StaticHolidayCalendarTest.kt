package com.gatefit.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StaticHolidayCalendarTest {

    private val calendar = StaticHolidayCalendar(
        mapOf(2026 to setOf(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 12, 25)))
    )

    @Test
    fun `listed date in a covered year is a holiday`() {
        assertEquals(HolidayStatus.HOLIDAY, calendar.statusOf(LocalDate.of(2026, 5, 1)))
    }

    @Test
    fun `unlisted date in a covered year is not a holiday`() {
        assertEquals(HolidayStatus.NOT_HOLIDAY, calendar.statusOf(LocalDate.of(2026, 11, 11)))
    }

    @Test
    fun `date in an uncovered year is unknown instead of not a holiday`() {
        assertEquals(HolidayStatus.UNKNOWN, calendar.statusOf(LocalDate.of(2028, 1, 1)))
        assertEquals(setOf(2026), calendar.coveredYears)
    }
}
