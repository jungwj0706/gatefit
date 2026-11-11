package com.gatefit.domain.usecase

import com.gatefit.domain.model.CheckOutcome
import com.gatefit.domain.model.CheckReason
import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.ConstraintType
import com.gatefit.domain.model.DataFreshness
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.HeightLimitStructure
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.PermittedVehicleType
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.WeeklyHours
import com.gatefit.domain.testing.FakeHolidayCalendar
import com.gatefit.domain.testing.Fixtures
import com.gatefit.domain.testing.Fixtures.WEDNESDAY_1PM
import com.gatefit.domain.testing.Fixtures.hours
import com.gatefit.domain.testing.Fixtures.range
import com.gatefit.domain.testing.Fixtures.suvWithRoofBox
import com.gatefit.domain.testing.Fixtures.verifiedEntrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeEntranceCompatibilityUseCaseTest {

    private val analyze = Fixtures.engine()

    @Test
    fun `limit lower than vehicle height is a mismatch`() {
        val result = analyze(suvWithRoofBox(), verifiedEntrance(maxHeightMm = 2000), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, result.status)
        val height = result.check(ConstraintType.HEIGHT)!!
        assertEquals(CheckReason.EXCEEDS_LIMIT, height.reason)
        assertEquals(2100, height.vehicleValue)
        assertEquals(2000, height.limitValue)
        assertEquals(-100, height.marginValue)
    }

    @Test
    fun `limit with enough clearance satisfies the height condition`() {
        val result = analyze(suvWithRoofBox(), verifiedEntrance(maxHeightMm = 2300), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MATCH, result.status)
        assertEquals(CheckOutcome.PASSED, result.check(ConstraintType.HEIGHT)!!.outcome)
        assertEquals(200, result.check(ConstraintType.HEIGHT)!!.marginValue)
    }

    @Test
    fun `missing vehicle height needs confirmation`() {
        val result = analyze(suvWithRoofBox(height = null, boxHeight = 0), verifiedEntrance(), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(CheckReason.VEHICLE_VALUE_MISSING, result.check(ConstraintType.HEIGHT)!!.reason)
    }

    @Test
    fun `missing entrance height needs confirmation`() {
        val result = analyze(suvWithRoofBox(), verifiedEntrance(maxHeightMm = null), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(CheckReason.LIMIT_NOT_PROVIDED, result.check(ConstraintType.HEIGHT)!!.reason)
    }

    @Test
    fun `no height limit structure is not treated as unlimited height`() {
        val entrance = verifiedEntrance(maxHeightMm = null)
            .copy(heightLimitStructure = HeightLimitStructure.NOT_INSTALLED)

        val result = analyze(suvWithRoofBox(), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(CheckReason.HEIGHT_STRUCTURE_NOT_INSTALLED, result.check(ConstraintType.HEIGHT)!!.reason)
    }

    @Test
    fun `accessory height changes the result for the same entrance`() {
        val entrance = verifiedEntrance(maxHeightMm = 2000)

        val stock = analyze(suvWithRoofBox(boxHeight = 0), entrance, WEDNESDAY_1PM)
        val withBox = analyze(suvWithRoofBox(boxHeight = 450), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MATCH, stock.status)
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, withBox.status)
    }

    @Test
    fun `exit only entrance is a mismatch`() {
        val result = analyze(suvWithRoofBox(), verifiedEntrance(direction = EntranceDirection.EXIT), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, result.status)
        assertEquals(CheckReason.DIRECTION_EXIT_ONLY, result.check(ConstraintType.DIRECTION)!!.reason)
    }

    @Test
    fun `unknown direction needs confirmation and bidirectional passes`() {
        val unknown = analyze(suvWithRoofBox(), verifiedEntrance(direction = EntranceDirection.UNKNOWN), WEDNESDAY_1PM)
        val both = analyze(suvWithRoofBox(), verifiedEntrance(direction = EntranceDirection.BIDIRECTIONAL), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, unknown.status)
        assertEquals(CompatibilityStatus.CONDITION_MATCH, both.status)
    }

    @Test
    fun `visit outside operating hours is a mismatch`() {
        val weekdayMorningOnly = WeeklyHours(weekday = hours(range("09:00", "12:00")))

        val result = analyze(suvWithRoofBox(), verifiedEntrance(hours = weekdayMorningOnly), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, result.status)
        assertEquals(CheckReason.HOURS_OUTSIDE, result.check(ConstraintType.OPERATING_HOURS)!!.reason)
    }

    @Test
    fun `recorded schedule exception keeps open hours from being confirmed`() {
        val weekdayOpen = WeeklyHours(weekday = hours(range("09:00", "18:00")))
        val entrance = verifiedEntrance(hours = weekdayOpen).copy(hoursExceptionNote = "월요일 휴무")

        val inside = analyze(suvWithRoofBox(), entrance, WEDNESDAY_1PM)
        val outside = analyze(suvWithRoofBox(), entrance, WEDNESDAY_1PM.withHour(20))

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, inside.status)
        assertEquals(CheckReason.HOURS_EXCEPTION_NOTED, inside.check(ConstraintType.OPERATING_HOURS)!!.reason)
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, outside.status)
    }

    @Test
    fun `missing holiday hours on a holiday need confirmation`() {
        val engine = Fixtures.engine(FakeHolidayCalendar(holidays = setOf(WEDNESDAY_1PM.toLocalDate())))
        val weekdayOnly = WeeklyHours(weekday = OperatingHours.AllDay)

        val result = engine(suvWithRoofBox(), verifiedEntrance(hours = weekdayOnly), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(CheckReason.HOLIDAY_HOURS_NOT_PROVIDED, result.check(ConstraintType.OPERATING_HOURS)!!.reason)
    }

    @Test
    fun `stale verification needs confirmation`() {
        val result = analyze(suvWithRoofBox(), verifiedEntrance(verifiedDaysAgo = 181), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(CheckReason.TRUST_STALE_VERIFICATION, result.check(ConstraintType.DATA_TRUST)!!.reason)
        assertEquals(DataFreshness.NEEDS_RECHECK, result.freshness)
    }

    @Test
    fun `public baseline data is never classified as a match`() {
        val entrance = verifiedEntrance(trustLevel = TrustLevel.PUBLIC_BASELINE, verifiedDaysAgo = null)

        val result = analyze(suvWithRoofBox(), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, result.status)
        assertTrue(result.heightPassed)
        assertEquals(DataFreshness.STALE_BASELINE, result.freshness)
    }

    @Test
    fun `one clear failure wins over other unknown conditions`() {
        val entrance = verifiedEntrance(
            maxHeightMm = 2000,
            direction = EntranceDirection.UNKNOWN,
            hours = WeeklyHours(),
            trustLevel = TrustLevel.PUBLIC_BASELINE,
            verifiedDaysAgo = null,
        )

        val result = analyze(suvWithRoofBox(), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, result.status)
        assertEquals(1, result.failedChecks.size)
        assertEquals(3, result.unknownChecks.size)
    }

    @Test
    fun `zero clearance fails only when a safety margin is required`() {
        val entrance = verifiedEntrance(maxHeightMm = 2100)

        val withMargin = analyze(suvWithRoofBox(margin = 100), entrance, WEDNESDAY_1PM)
        val withoutMargin = analyze(suvWithRoofBox(margin = 0), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, withMargin.status)
        assertEquals(CheckReason.MARGIN_INSUFFICIENT, withMargin.check(ConstraintType.HEIGHT)!!.reason)
        assertEquals(0, withMargin.check(ConstraintType.HEIGHT)!!.marginValue)
        assertEquals(CompatibilityStatus.CONDITION_MATCH, withoutMargin.status)
    }

    @Test
    fun `clearance equal to the safety margin passes and one millimeter less fails`() {
        val exact = analyze(suvWithRoofBox(), verifiedEntrance(maxHeightMm = 2200), WEDNESDAY_1PM)
        val short = analyze(suvWithRoofBox(), verifiedEntrance(maxHeightMm = 2199), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MATCH, exact.status)
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, short.status)
        assertEquals(CheckReason.MARGIN_INSUFFICIENT, short.check(ConstraintType.HEIGHT)!!.reason)
    }

    @Test
    fun `estimated vehicle specs are not used for the verdict`() {
        val estimated = suvWithRoofBox(confidence = SpecConfidence.USER_ESTIMATED)

        val result = analyze(estimated, verifiedEntrance(maxHeightMm = 2000), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, result.status)
        assertEquals(CheckReason.VEHICLE_VALUE_UNCONFIRMED, result.check(ConstraintType.HEIGHT)!!.reason)
    }

    @Test
    fun `limits without data stay not provided instead of passing`() {
        val result = analyze(suvWithRoofBox(), verifiedEntrance(), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MATCH, result.status)
        assertEquals(
            setOf(ConstraintType.WIDTH, ConstraintType.LENGTH, ConstraintType.WEIGHT, ConstraintType.VEHICLE_TYPE),
            result.notProvidedChecks.map { it.type }.toSet(),
        )
    }

    @Test
    fun `known width limit is compared and a missing vehicle value needs confirmation`() {
        val entrance = verifiedEntrance().copy(maxWidthMm = 1850)

        val tooWide = analyze(suvWithRoofBox(width = 1900), entrance, WEDNESDAY_1PM)
        val unknownWidth = analyze(suvWithRoofBox(width = null), entrance, WEDNESDAY_1PM)
        val fits = analyze(suvWithRoofBox(width = 1850), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, tooWide.status)
        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, unknownWidth.status)
        assertEquals(CompatibilityStatus.CONDITION_MATCH, fits.status)
    }

    @Test
    fun `known weight limit requires a registered vehicle weight`() {
        val entrance = verifiedEntrance().copy(maxWeightKg = 2500)

        val unregistered = analyze(suvWithRoofBox(weightKg = null), entrance, WEDNESDAY_1PM)
        val heavy = analyze(suvWithRoofBox(weightKg = 2600), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, unregistered.status)
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, heavy.status)
    }

    @Test
    fun `resident only entrance does not permit a general visitor`() {
        val entrance = verifiedEntrance().copy(permittedVehicleTypes = setOf(PermittedVehicleType.RESIDENT))

        val result = analyze(suvWithRoofBox(), entrance, WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, result.status)
        assertEquals(CheckReason.VEHICLE_TYPE_NOT_PERMITTED, result.check(ConstraintType.VEHICLE_TYPE)!!.reason)
    }

    @Test
    fun `closed entrance is a mismatch and unknown status needs confirmation`() {
        val closed = analyze(suvWithRoofBox(), verifiedEntrance(status = EntranceStatus.CLOSED), WEDNESDAY_1PM)
        val unknown = analyze(suvWithRoofBox(), verifiedEntrance(status = EntranceStatus.UNKNOWN), WEDNESDAY_1PM)

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, closed.status)
        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, unknown.status)
    }

    @Test
    fun `demo entrance results are flagged`() {
        val demo = analyze(suvWithRoofBox(), verifiedEntrance().copy(origin = DataOrigin.DEMO), WEDNESDAY_1PM)
        val live = analyze(suvWithRoofBox(), verifiedEntrance(), WEDNESDAY_1PM)

        assertTrue(demo.demo)
        assertFalse(live.demo)
    }

    @Test
    fun `evaluation time is converted to the facility time zone`() {
        val eveningOnly = WeeklyHours(weekday = hours(range("18:00", "22:00")))
        // UTC 10:00은 서울 19:00
        val utcMorning = WEDNESDAY_1PM.withHour(19).withZoneSameInstant(java.time.ZoneOffset.UTC)

        val result = analyze(suvWithRoofBox(), verifiedEntrance(hours = eveningOnly), utcMorning)

        assertEquals(CheckOutcome.PASSED, result.check(ConstraintType.OPERATING_HOURS)!!.outcome)
        assertEquals(19, result.evaluatedAt.hour)
    }
}
