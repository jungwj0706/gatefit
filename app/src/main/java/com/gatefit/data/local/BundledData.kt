package com.gatefit.data.local

import com.gatefit.data.dto.GatefitJson
import com.gatefit.data.dto.HolidayCalendarDto
import com.gatefit.data.dto.SnapshotDto
import com.gatefit.data.dto.VehicleSpecCatalogDto
import com.gatefit.data.mapper.toDomain
import com.gatefit.domain.analysis.StaticHolidayCalendar
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.DataSource
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.model.VehicleModelSpec
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class PlaceSnapshot(
    val sources: List<DataSource>,
    val facilities: List<Facility>,
    val entrances: List<VehicleEntrance>,
)

object BundledData {
    private const val SUPPORTED_SCHEMA_VERSION = 1

    fun parseSnapshot(json: String, origin: DataOrigin): PlaceSnapshot {
        val dto = GatefitJson.decodeFromString(SnapshotDto.serializer(), json)
        requireSupported(dto.schemaVersion)
        val sources = dto.dataSources.map { it.toDomain() }
        val sourcesById = sources.associateBy { it.id }
        return PlaceSnapshot(
            sources = sources,
            facilities = dto.facilities.map { it.toDomain(origin) },
            entrances = dto.entrances.map { it.toDomain(origin, sourcesById) },
        )
    }

    // DEMO 시나리오가 시간이 지나도 같은 결과를 보이도록 검증 시각을 불러온 시점 기준으로 설정,
    // DEMO 출처가 아닌 데이터에는 사용 금지
    fun parseDemoSnapshot(json: String, verifiedAt: Instant): PlaceSnapshot {
        val snapshot = parseSnapshot(json, DataOrigin.DEMO)
        return snapshot.copy(
            sources = emptyList(),
            entrances = snapshot.entrances.map { entrance ->
                val verified = entrance.trustLevel == TrustLevel.VERIFIED_FIELD ||
                    entrance.trustLevel == TrustLevel.VERIFIED_OFFICIAL
                entrance.copy(dataSource = null, verifiedAt = verifiedAt.takeIf { verified })
            },
        )
    }

    fun parseHolidayCalendar(json: String): StaticHolidayCalendar {
        val dto = GatefitJson.decodeFromString(HolidayCalendarDto.serializer(), json)
        requireSupported(dto.schemaVersion)
        val byYear = dto.years.mapNotNull { (yearText, dates) ->
            val year = yearText.toIntOrNull() ?: return@mapNotNull null
            val parsed = dates.map(::parseDateOrNull)
            // 해석할 수 없는 날짜가 있는 연도는 통째로 미확인 처리
            if (parsed.any { it == null || it.year != year }) return@mapNotNull null
            year to parsed.filterNotNull().toSet()
        }.toMap()
        return StaticHolidayCalendar(byYear)
    }

    fun parseVehicleSpecs(json: String): List<VehicleModelSpec> {
        val dto = GatefitJson.decodeFromString(VehicleSpecCatalogDto.serializer(), json)
        requireSupported(dto.schemaVersion)
        return dto.specs.map { it.toDomain() }
    }

    private fun requireSupported(schemaVersion: Int) {
        require(schemaVersion == SUPPORTED_SCHEMA_VERSION) { "unsupported bundled data schema: $schemaVersion" }
    }

    private fun parseDateOrNull(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }
}
