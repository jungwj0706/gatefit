package com.gatefit.data.local

import com.gatefit.data.testing.BundledAssets
import com.gatefit.domain.analysis.DataTrustEvaluator
import com.gatefit.domain.analysis.HolidayStatus
import com.gatefit.domain.analysis.OperatingHoursEvaluator
import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.RouteTargetType
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleAccessory
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.usecase.AnalyzeEntranceCompatibilityUseCase
import com.gatefit.domain.usecase.CalculateEffectiveVehicleDimensionsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class BundledDataTest {

    private val now = ZonedDateTime.of(2026, 11, 11, 13, 0, 0, 0, ZoneId.of("Asia/Seoul"))
    private val calendar = BundledData.parseHolidayCalendar(BundledAssets.read(AssetPaths.HOLIDAYS))
    private val engine = AnalyzeEntranceCompatibilityUseCase(
        calculateEffectiveDimensions = CalculateEffectiveVehicleDimensionsUseCase(),
        hoursEvaluator = OperatingHoursEvaluator(calendar),
        trustEvaluator = DataTrustEvaluator(),
    )

    // 순정 1650mm + 루프박스 450mm + 여유 100mm, 필요 높이 2200mm
    private val suvWithRoofBox = VehicleProfile(
        id = "vehicle",
        baseDimensions = VehicleDimensions(lengthMm = 4800, widthMm = 1900, heightMm = 1650),
        specConfidence = SpecConfidence.USER_CONFIRMED,
        accessories = listOf(VehicleAccessory(id = "box", type = AccessoryType.ROOF_BOX, additionalHeightMm = 450)),
        safetyMarginMm = 100,
    )

    private val baseline by lazy {
        BundledData.parseSnapshot(BundledAssets.read(AssetPaths.BASELINE_SNAPSHOT), DataOrigin.LIVE)
    }

    private val demo by lazy {
        BundledData.parseDemoSnapshot(BundledAssets.read(AssetPaths.DEMO_PLACES), now.minusDays(7).toInstant())
    }

    @Test
    fun `baseline snapshot keeps every pipeline record`() {
        assertEquals(2, baseline.sources.size)
        assertEquals(3603, baseline.facilities.size)
        assertEquals(2269, baseline.entrances.size)
        assertEquals(864, baseline.entrances.count { it.maxHeightMm != null })
        assertEquals(1179, baseline.entrances.count { !it.hours.allUnknown })

        val facilityIds = baseline.facilities.map { it.id }.toSet()
        assertEquals(baseline.facilities.size, facilityIds.size)
        assertTrue(baseline.entrances.all { it.facilityId in facilityIds })
    }

    @Test
    fun `baseline entrances are never presented as verified`() {
        assertTrue(baseline.entrances.all { it.trustLevel == TrustLevel.PUBLIC_BASELINE && it.verifiedAt == null })
        assertTrue(baseline.entrances.all { it.origin == DataOrigin.LIVE && it.dataSource != null })
        assertTrue(baseline.sources.all { it.originalUpdatedAt?.year == 2022 && !it.license.isNullOrBlank() })
    }

    @Test
    fun `baseline data alone never yields a condition match`() {
        val statuses = baseline.entrances.map { engine(suvWithRoofBox, it, now).status }

        assertFalse(CompatibilityStatus.CONDITION_MATCH in statuses)
        assertTrue(CompatibilityStatus.NEEDS_CONFIRMATION in statuses)
        assertTrue(CompatibilityStatus.CONDITION_MISMATCH in statuses)
    }

    @Test
    fun `baseline width, length and weight limits are not invented`() {
        assertTrue(baseline.entrances.all { it.maxWidthMm == null && it.maxLengthMm == null && it.maxWeightKg == null })
    }

    @Test
    fun `demo data is tagged as demo and cannot be navigated to`() {
        assertTrue(demo.sources.isEmpty())
        assertTrue(demo.facilities.all { it.origin == DataOrigin.DEMO && it.name.startsWith("DEMO") })
        assertTrue(demo.facilities.all { it.latitude == null && it.longitude == null })
        assertTrue(demo.entrances.all { it.origin == DataOrigin.DEMO && !it.hasCoordinates && it.dataSource == null })
        assertTrue(demo.entrances.all { it.id.startsWith("demo-") })
    }

    @Test
    fun `demo ids never collide with baseline ids`() {
        val liveIds = (baseline.facilities.map { it.id } + baseline.entrances.map { it.id }).toSet()

        assertTrue((demo.facilities.map { it.id } + demo.entrances.map { it.id }).none { it in liveIds })
    }

    @Test
    fun `demo mall shows match, needs confirmation and mismatch for the roof box scenario`() {
        val results = demo.entrances.associate { it.id to engine(suvWithRoofBox, it, now) }

        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, results.getValue("demo-entrance-mall-a").status)
        assertEquals(CompatibilityStatus.CONDITION_MATCH, results.getValue("demo-entrance-mall-b").status)
        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, results.getValue("demo-entrance-mall-c").status)
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, results.getValue("demo-entrance-mall-d").status)
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, results.getValue("demo-entrance-hospital-er").status)
        assertTrue(results.values.all { it.demo })
    }

    @Test
    fun `demo verification time is only stamped on verified entrances`() {
        val unverified = demo.entrances.single { it.id == "demo-entrance-mall-c" }

        assertNull(unverified.verifiedAt)
        assertEquals(OperatingHours.Unknown, unverified.hours.weekday)
        assertEquals(RouteTargetType.ENTRANCE_POINT, unverified.routeTargetType)
    }

    @Test
    fun `holiday calendar covers only officially announced years`() {
        assertEquals(setOf(2026, 2027), calendar.coveredYears)
        assertEquals(HolidayStatus.HOLIDAY, calendar.statusOf(LocalDate.of(2026, 5, 1)))
        assertEquals(HolidayStatus.HOLIDAY, calendar.statusOf(LocalDate.of(2026, 7, 17)))
        assertEquals(HolidayStatus.NOT_HOLIDAY, calendar.statusOf(LocalDate.of(2026, 11, 11)))
        assertEquals(HolidayStatus.HOLIDAY, calendar.statusOf(LocalDate.of(2027, 12, 27)))
        assertEquals(HolidayStatus.UNKNOWN, calendar.statusOf(LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun `holiday year with an unreadable date is dropped entirely`() {
        val broken = BundledData.parseHolidayCalendar(
            """{"schema_version":1,"years":{"2026":["2026-01-01","2026-13-40"],"2027":["2027-01-01"]}}"""
        )

        assertEquals(setOf(2027), broken.coveredYears)
        assertEquals(HolidayStatus.UNKNOWN, broken.statusOf(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `every bundled vehicle spec has an official https source`() {
        val specs = BundledData.parseVehicleSpecs(BundledAssets.read(AssetPaths.VEHICLE_SPECS))

        assertTrue(specs.isNotEmpty())
        assertEquals(specs.size, specs.map { it.id }.toSet().size)
        assertTrue(specs.all { it.hasSource && it.sourceUrl!!.startsWith("https://www.hyundai.com/") })
        assertTrue(specs.all { it.dimensions.heightMm!! in 1000..4500 })
    }

    @Test
    fun `unsupported schema version is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            BundledData.parseSnapshot("""{"schema_version":2}""", DataOrigin.LIVE)
        }
    }
}
