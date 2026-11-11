package com.gatefit.data.mapper

import com.gatefit.data.dto.DataSourceDto
import com.gatefit.data.dto.EntranceDto
import com.gatefit.data.dto.FacilityDto
import com.gatefit.data.dto.HoursDto
import com.gatefit.data.dto.TimeRangeDto
import com.gatefit.data.dto.VehicleModelSpecDto
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.DataSource
import com.gatefit.domain.model.DataSourceType
import com.gatefit.domain.model.EntranceClass
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilityStatus
import com.gatefit.domain.model.HeightLimitStructure
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.PermittedVehicleType
import com.gatefit.domain.model.RouteTargetType
import com.gatefit.domain.model.TimeRange
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.model.VehicleModelSpec
import com.gatefit.domain.model.WeeklyHours
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

// 정의되지 않은 코드값은 임의로 해석하지 않고 기본값(대부분 UNKNOWN)으로 처리
inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
    enumValues<T>().firstOrNull { it.name == value }

fun parseInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }
}

fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value.take(10))
    } catch (_: DateTimeParseException) {
        null
    }
}

fun DataSourceDto.toDomain() = DataSource(
    id = id,
    name = sourceName,
    type = enumOrDefault(sourceType, DataSourceType.PUBLIC),
    url = sourceUrl,
    license = license,
    importedAt = parseInstant(importedAt),
    originalUpdatedAt = parseDate(originalUpdatedAt),
    notes = notes,
)

fun FacilityDto.toDomain(origin: DataOrigin) = Facility(
    id = id,
    name = name,
    category = enumOrDefault(category, FacilityCategory.OTHER),
    roadAddress = roadAddress,
    lotAddress = lotAddress,
    latitude = latitude.takeIf { longitude != null },
    longitude = longitude.takeIf { latitude != null },
    aliases = aliases,
    status = enumOrDefault(status, FacilityStatus.UNKNOWN),
    statusNote = statusNote,
    origin = origin,
    updatedAtEpochMs = parseInstant(updatedAt)?.toEpochMilli() ?: 0L,
)

fun HoursDto?.toDomain(): OperatingHours {
    if (this == null) return OperatingHours.Unknown
    return when (type) {
        "ALL_DAY" -> OperatingHours.AllDay
        "CLOSED" -> OperatingHours.Closed
        "RANGES" -> {
            val parsed = ranges.map { it.toDomain() }
            // 구간 하나라도 해석할 수 없으면 전체를 미확인으로 처리
            if (parsed.isEmpty() || parsed.any { it == null }) {
                OperatingHours.Unknown
            } else {
                OperatingHours.Ranges(parsed.filterNotNull())
            }
        }
        else -> OperatingHours.Unknown
    }
}

private fun TimeRangeDto.toDomain(): TimeRange? = try {
    TimeRange(LocalTime.parse(start), LocalTime.parse(end)).takeIf { it.start != it.end }
} catch (_: DateTimeParseException) {
    null
}

fun OperatingHours.toDto(): HoursDto? = when (this) {
    OperatingHours.Unknown -> null
    OperatingHours.AllDay -> HoursDto("ALL_DAY")
    OperatingHours.Closed -> HoursDto("CLOSED")
    is OperatingHours.Ranges -> HoursDto("RANGES", ranges.map { TimeRangeDto(it.start.toString(), it.end.toString()) })
}

fun EntranceDto.toDomain(origin: DataOrigin, sources: Map<String, DataSource>): VehicleEntrance {
    val trust = enumOrDefault(trustLevel, TrustLevel.UNKNOWN)
    val verified = parseInstant(verifiedAt)
    return VehicleEntrance(
        id = id,
        facilityId = facilityId,
        name = name,
        latitude = latitude.takeIf { longitude != null },
        longitude = longitude.takeIf { latitude != null },
        direction = enumOrDefault(entranceType, EntranceDirection.UNKNOWN),
        entranceClass = enumOrNull<EntranceClass>(entranceClass),
        permittedVehicleTypes = permittedVehicleTypes?.toPermittedTypes(),
        maxHeightMm = maxHeightMm?.takeIf { it > 0 },
        heightLimitStructure = enumOrDefault(heightLimitStructure, HeightLimitStructure.UNKNOWN),
        maxWidthMm = maxWidthMm?.takeIf { it > 0 },
        maxLengthMm = maxLengthMm?.takeIf { it > 0 },
        maxWeightKg = maxWeightKg?.takeIf { it > 0 },
        hours = WeeklyHours(
            weekday = weekdayHours.toDomain(),
            saturday = saturdayHours.toDomain(),
            sunday = sundayHours.toDomain(),
            holiday = holidayHours.toDomain(),
        ),
        hoursExceptionNote = restrictions?.hoursExceptionNote?.takeIf { it.isNotBlank() },
        restrictionNote = restrictions?.note?.takeIf { it.isNotBlank() },
        status = enumOrDefault(status, EntranceStatus.UNKNOWN),
        dataSource = dataSourceId?.let(sources::get),
        trustLevel = trust,
        verifiedAt = verified,
        routeTargetType = enumOrDefault(routeTargetType, RouteTargetType.FACILITY_CENTROID),
        origin = origin,
        updatedAtEpochMs = parseInstant(updatedAt)?.toEpochMilli() ?: 0L,
    )
}

// 해석 가능한 유형이 하나도 없으면 허용 차량 유형 미확인으로 처리
private fun List<String>.toPermittedTypes(): Set<PermittedVehicleType>? =
    mapNotNull { enumOrNull<PermittedVehicleType>(it) }.toSet().ifEmpty { null }

fun VehicleModelSpecDto.toDomain() = VehicleModelSpec(
    id = id,
    manufacturer = manufacturer,
    modelName = modelName,
    modelYear = modelYear,
    trimName = trimName,
    dimensions = VehicleDimensions(lengthMm = lengthMm, widthMm = widthMm, heightMm = heightMm),
    sourceName = sourceName,
    sourceUrl = sourceUrl,
    sourceVerifiedAt = parseDate(sourceVerifiedAt),
)
