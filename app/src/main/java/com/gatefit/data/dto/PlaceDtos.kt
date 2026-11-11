package com.gatefit.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

// Supabase REST 응답과 앱 내장 스냅샷이 같은 형식을 공유
val GatefitJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class DataSourceDto(
    val id: String,
    @SerialName("source_name") val sourceName: String,
    @SerialName("source_type") val sourceType: String = "PUBLIC",
    @SerialName("source_url") val sourceUrl: String? = null,
    val license: String? = null,
    @SerialName("imported_at") val importedAt: String? = null,
    @SerialName("original_updated_at") val originalUpdatedAt: String? = null,
    val notes: String? = null,
)

@Serializable
data class FacilityDto(
    val id: String,
    val name: String,
    val category: String = "OTHER",
    @SerialName("road_address") val roadAddress: String? = null,
    @SerialName("lot_address") val lotAddress: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val aliases: List<String> = emptyList(),
    val status: String = "UNKNOWN",
    @SerialName("status_note") val statusNote: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class TimeRangeDto(val start: String, val end: String)

@Serializable
data class HoursDto(
    val type: String,
    val ranges: List<TimeRangeDto> = emptyList(),
)

@Serializable
data class RestrictionsDto(
    val note: String? = null,
    @SerialName("hours_exception_note") val hoursExceptionNote: String? = null,
)

@Serializable
data class EntranceDto(
    val id: String,
    @SerialName("facility_id") val facilityId: String,
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("entrance_type") val entranceType: String = "UNKNOWN",
    @SerialName("entrance_class") val entranceClass: String? = null,
    @SerialName("permitted_vehicle_types") val permittedVehicleTypes: List<String>? = null,
    @SerialName("max_height_mm") val maxHeightMm: Int? = null,
    @SerialName("height_limit_structure") val heightLimitStructure: String = "UNKNOWN",
    @SerialName("max_width_mm") val maxWidthMm: Int? = null,
    @SerialName("max_length_mm") val maxLengthMm: Int? = null,
    @SerialName("max_weight_kg") val maxWeightKg: Int? = null,
    val restrictions: RestrictionsDto? = null,
    @SerialName("weekday_hours") val weekdayHours: HoursDto? = null,
    @SerialName("saturday_hours") val saturdayHours: HoursDto? = null,
    @SerialName("sunday_hours") val sundayHours: HoursDto? = null,
    @SerialName("holiday_hours") val holidayHours: HoursDto? = null,
    @SerialName("data_source_id") val dataSourceId: String? = null,
    @SerialName("trust_level") val trustLevel: String = "UNKNOWN",
    @SerialName("verified_at") val verifiedAt: String? = null,
    @SerialName("route_target_type") val routeTargetType: String = "ENTRANCE_POINT",
    val status: String = "UNKNOWN",
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SnapshotDto(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("data_sources") val dataSources: List<DataSourceDto> = emptyList(),
    val facilities: List<FacilityDto> = emptyList(),
    val entrances: List<EntranceDto> = emptyList(),
)

@Serializable
data class VehicleModelSpecDto(
    val id: String,
    val manufacturer: String,
    @SerialName("model_name") val modelName: String,
    @SerialName("model_year") val modelYear: Int? = null,
    @SerialName("trim_name") val trimName: String? = null,
    @SerialName("height_mm") val heightMm: Int,
    @SerialName("width_mm") val widthMm: Int,
    @SerialName("length_mm") val lengthMm: Int,
    @SerialName("source_name") val sourceName: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_verified_at") val sourceVerifiedAt: String? = null,
)

@Serializable
data class VehicleSpecCatalogDto(
    @SerialName("schema_version") val schemaVersion: Int,
    val specs: List<VehicleModelSpecDto> = emptyList(),
)

@Serializable
data class ReportRequestDto(
    val id: String,
    @SerialName("entrance_id") val entranceId: String,
    @SerialName("report_type") val reportType: String,
    val description: String? = null,
    @SerialName("proposed_value") val proposedValue: JsonObject? = null,
)

@Serializable
data class RemoteErrorDto(val code: String? = null)

@Serializable
data class HolidayCalendarDto(
    @SerialName("schema_version") val schemaVersion: Int,
    val years: Map<String, List<String>> = emptyMap(),
)
