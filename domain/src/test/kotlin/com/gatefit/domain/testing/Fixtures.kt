package com.gatefit.domain.testing

import com.gatefit.domain.analysis.DataTrustEvaluator
import com.gatefit.domain.analysis.HolidayCalendar
import com.gatefit.domain.analysis.HolidayStatus
import com.gatefit.domain.analysis.OperatingHoursEvaluator
import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.TimeRange
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleAccessory
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.model.WeeklyHours
import com.gatefit.domain.usecase.AnalyzeEntranceCompatibilityUseCase
import com.gatefit.domain.usecase.CalculateEffectiveVehicleDimensionsUseCase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FakeHolidayCalendar(
    private val holidays: Set<LocalDate> = emptySet(),
    private val unknownDates: Set<LocalDate> = emptySet(),
    private val allUnknown: Boolean = false,
) : HolidayCalendar {
    override fun statusOf(date: LocalDate): HolidayStatus = when {
        allUnknown || date in unknownDates -> HolidayStatus.UNKNOWN
        date in holidays -> HolidayStatus.HOLIDAY
        else -> HolidayStatus.NOT_HOLIDAY
    }
}

object Fixtures {
    val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

    // 2026-11-11은 수요일
    val WEDNESDAY_1PM: ZonedDateTime = ZonedDateTime.of(2026, 11, 11, 13, 0, 0, 0, SEOUL)

    fun range(start: String, end: String) = TimeRange(LocalTime.parse(start), LocalTime.parse(end))

    fun hours(vararg ranges: TimeRange): OperatingHours = OperatingHours.Ranges(ranges.toList())

    val ALWAYS_OPEN = WeeklyHours(
        weekday = OperatingHours.AllDay,
        saturday = OperatingHours.AllDay,
        sunday = OperatingHours.AllDay,
        holiday = OperatingHours.AllDay,
    )

    fun engine(calendar: HolidayCalendar = FakeHolidayCalendar()) = AnalyzeEntranceCompatibilityUseCase(
        calculateEffectiveDimensions = CalculateEffectiveVehicleDimensionsUseCase(),
        hoursEvaluator = OperatingHoursEvaluator(calendar),
        trustEvaluator = DataTrustEvaluator(),
    )

    // 기획서 예시와 같은 가상 차량, 순정 1650mm + 루프박스 450mm + 여유 100mm
    fun suvWithRoofBox(
        height: Int? = 1650,
        boxHeight: Int = 450,
        margin: Int = 100,
        confidence: SpecConfidence = SpecConfidence.USER_CONFIRMED,
        width: Int? = 1900,
        length: Int? = 4800,
        weightKg: Int? = null,
    ) = VehicleProfile(
        id = "vehicle",
        baseDimensions = VehicleDimensions(lengthMm = length, widthMm = width, heightMm = height),
        specConfidence = confidence,
        accessories = if (boxHeight > 0) {
            listOf(VehicleAccessory(id = "box", type = AccessoryType.ROOF_BOX, additionalHeightMm = boxHeight))
        } else {
            emptyList()
        },
        safetyMarginMm = margin,
        weightKg = weightKg,
    )

    // 모든 필수 조건이 확인된 최신 검증 출입구
    fun verifiedEntrance(
        id: String = "gate",
        maxHeightMm: Int? = 2300,
        direction: EntranceDirection = EntranceDirection.ENTRY,
        hours: WeeklyHours = ALWAYS_OPEN,
        trustLevel: TrustLevel = TrustLevel.VERIFIED_OFFICIAL,
        verifiedDaysAgo: Long? = 10,
        status: EntranceStatus = EntranceStatus.ACTIVE,
        now: ZonedDateTime = WEDNESDAY_1PM,
    ) = VehicleEntrance(
        id = id,
        facilityId = "facility",
        name = id,
        latitude = 37.5,
        longitude = 127.0,
        direction = direction,
        maxHeightMm = maxHeightMm,
        hours = hours,
        status = status,
        trustLevel = trustLevel,
        verifiedAt = verifiedDaysAgo?.let { now.minusDays(it).toInstant() },
    )
}
