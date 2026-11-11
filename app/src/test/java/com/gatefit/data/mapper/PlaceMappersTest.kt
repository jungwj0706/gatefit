package com.gatefit.data.mapper

import com.gatefit.data.dto.EntranceDto
import com.gatefit.data.dto.FacilityDto
import com.gatefit.data.dto.GatefitJson
import com.gatefit.data.dto.HoursDto
import com.gatefit.data.dto.RestrictionsDto
import com.gatefit.data.dto.TimeRangeDto
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.HeightLimitStructure
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.PermittedVehicleType
import com.gatefit.domain.model.RouteTargetType
import com.gatefit.domain.model.TrustLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class PlaceMappersTest {

    private fun entrance(json: String): EntranceDto = GatefitJson.decodeFromString(EntranceDto.serializer(), json)

    @Test
    fun `row with only required columns maps every optional condition to unknown`() {
        val mapped = entrance("""{"id":"e","facility_id":"f","name":"정문"}""").toDomain(DataOrigin.LIVE, emptyMap())

        assertEquals(EntranceDirection.UNKNOWN, mapped.direction)
        assertEquals(EntranceStatus.UNKNOWN, mapped.status)
        assertEquals(TrustLevel.UNKNOWN, mapped.trustLevel)
        assertEquals(HeightLimitStructure.UNKNOWN, mapped.heightLimitStructure)
        assertNull(mapped.maxHeightMm)
        assertNull(mapped.permittedVehicleTypes)
        assertNull(mapped.verifiedAt)
        assertTrue(mapped.hours.allUnknown)
    }

    @Test
    fun `unrecognised server codes fall back to unknown instead of a permissive value`() {
        val mapped = entrance(
            """
            {"id":"e","facility_id":"f","name":"정문","entrance_type":"SIDEWAYS","trust_level":"SUPER_VERIFIED",
             "status":"MAYBE","route_target_type":"SOMEWHERE","weekday_hours":{"type":"SOMETIMES"},
             "future_column":123}
            """
        ).toDomain(DataOrigin.LIVE, emptyMap())

        assertEquals(EntranceDirection.UNKNOWN, mapped.direction)
        assertEquals(TrustLevel.UNKNOWN, mapped.trustLevel)
        assertEquals(EntranceStatus.UNKNOWN, mapped.status)
        assertEquals(RouteTargetType.FACILITY_CENTROID, mapped.routeTargetType)
        assertEquals(OperatingHours.Unknown, mapped.hours.weekday)
    }

    @Test
    fun `verified row keeps its limits, hours and verification time`() {
        val mapped = entrance(
            """
            {"id":"e","facility_id":"f","name":"B 게이트","latitude":37.5,"longitude":127.0,
             "entrance_type":"ENTRY","permitted_vehicle_types":["GENERAL","RESIDENT"],"max_height_mm":2300,
             "height_limit_structure":"INSTALLED","restrictions":{"note":"화물차 불가","hours_exception_note":"둘째 주 월요일 휴무"},
             "weekday_hours":{"type":"RANGES","ranges":[{"start":"09:00","end":"18:00"},{"start":"22:00","end":"02:00"}]},
             "saturday_hours":{"type":"ALL_DAY"},"sunday_hours":{"type":"CLOSED"},
             "trust_level":"VERIFIED_FIELD","verified_at":"2026-11-01T03:00:00+00:00",
             "route_target_type":"ENTRANCE_POINT","status":"ACTIVE","updated_at":"2026-11-02T00:00:00Z"}
            """
        ).toDomain(DataOrigin.LIVE, emptyMap())

        assertEquals(2300, mapped.maxHeightMm)
        assertEquals(setOf(PermittedVehicleType.GENERAL, PermittedVehicleType.RESIDENT), mapped.permittedVehicleTypes)
        assertEquals(Instant.parse("2026-11-01T03:00:00Z"), mapped.verifiedAt)
        assertEquals("화물차 불가", mapped.restrictionNote)
        assertEquals("둘째 주 월요일 휴무", mapped.hoursExceptionNote)
        assertEquals(OperatingHours.AllDay, mapped.hours.saturday)
        assertEquals(OperatingHours.Closed, mapped.hours.sunday)
        assertEquals(OperatingHours.Unknown, mapped.hours.holiday)

        val ranges = (mapped.hours.weekday as OperatingHours.Ranges).ranges
        assertEquals(LocalTime.of(9, 0), ranges[0].start)
        assertTrue(ranges[1].crossesMidnight)
        assertEquals(Instant.parse("2026-11-02T00:00:00Z").toEpochMilli(), mapped.updatedAtEpochMs)
    }

    @Test
    fun `hours with any unreadable range are unknown as a whole`() {
        val broken = HoursDto("RANGES", listOf(TimeRangeDto("09:00", "18:00"), TimeRangeDto("25:00", "26:00")))

        assertEquals(OperatingHours.Unknown, broken.toDomain())
        assertEquals(OperatingHours.Unknown, HoursDto("RANGES", emptyList()).toDomain())
        assertEquals(OperatingHours.Unknown, HoursDto("RANGES", listOf(TimeRangeDto("09:00", "09:00"))).toDomain())
        assertEquals(OperatingHours.Unknown, (null as HoursDto?).toDomain())
    }

    @Test
    fun `hours survive a round trip through the storage format`() {
        val hours = HoursDto("RANGES", listOf(TimeRangeDto("22:00", "02:00"))).toDomain()

        assertEquals(hours, hours.toDto().toDomain())
        assertEquals(OperatingHours.AllDay, OperatingHours.AllDay.toDto().toDomain())
        assertEquals(OperatingHours.Closed, OperatingHours.Closed.toDto().toDomain())
        assertNull(OperatingHours.Unknown.toDto())
    }

    @Test
    fun `non positive limits and blank notes are treated as missing`() {
        val mapped = EntranceDto(
            id = "e",
            facilityId = "f",
            name = "정문",
            maxHeightMm = 0,
            maxWidthMm = -1,
            restrictions = RestrictionsDto(note = " ", hoursExceptionNote = ""),
            permittedVehicleTypes = listOf("SPACESHIP"),
        ).toDomain(DataOrigin.LIVE, emptyMap())

        assertNull(mapped.maxHeightMm)
        assertNull(mapped.maxWidthMm)
        assertNull(mapped.restrictionNote)
        assertNull(mapped.hoursExceptionNote)
        assertNull(mapped.permittedVehicleTypes)
    }

    @Test
    fun `half of a coordinate pair is treated as no coordinates`() {
        val entranceOnlyLat = EntranceDto(id = "e", facilityId = "f", name = "정문", latitude = 37.5)
            .toDomain(DataOrigin.LIVE, emptyMap())
        val facilityOnlyLng = FacilityDto(id = "f", name = "시설", longitude = 127.0).toDomain(DataOrigin.LIVE)

        assertEquals(false, entranceOnlyLat.hasCoordinates)
        assertNull(entranceOnlyLat.latitude)
        assertNull(facilityOnlyLng.longitude)
    }

    @Test
    fun `facility keeps the requested origin and falls back on unknown codes`() {
        val facility = FacilityDto(id = "f", name = "시설", category = "SPACEPORT", status = "???")
            .toDomain(DataOrigin.DEMO)

        assertEquals(DataOrigin.DEMO, facility.origin)
        assertEquals(FacilityCategory.OTHER, facility.category)
    }

    @Test
    fun `timestamps without a readable format are missing instead of now`() {
        assertNull(parseInstant("yesterday"))
        assertNull(parseInstant(null))
        assertNull(parseDate("2026/11/11"))
        assertEquals("2026-11-11", parseDate("2026-11-11T00:00:00+00:00").toString())
    }
}
