package com.gatefit.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.gatefit.data.dto.GatefitJson
import com.gatefit.data.dto.HoursDto
import com.gatefit.data.mapper.enumOrDefault
import com.gatefit.data.mapper.enumOrNull
import com.gatefit.data.mapper.parseDate
import com.gatefit.data.mapper.toDomain
import com.gatefit.data.mapper.toDto
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.DataSource
import com.gatefit.domain.model.DataSourceType
import com.gatefit.domain.model.EntranceClass
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilityStatus
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.HeightLimitStructure
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.PermittedVehicleType
import com.gatefit.domain.model.RouteTargetType
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.model.WeeklyHours
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.domain.repository.FacilityQuery
import kotlinx.serialization.SerializationException
import java.time.Instant

class SqlitePlaceStore(private val database: GatefitDatabase) : PlaceLocalSource {

    private val db: SQLiteDatabase get() = database.writableDatabase

    override fun facilityCount(origin: DataOrigin): Int =
        DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM facilities WHERE origin = ?", arrayOf(origin.name)).toInt()

    override fun replaceAll(
        origin: DataOrigin,
        sources: List<DataSource>,
        facilities: List<Facility>,
        entrances: List<VehicleEntrance>,
    ) {
        db.transaction {
            // 출입구는 외래 키 연쇄 삭제로 함께 제거
            delete("facilities", "origin = ?", arrayOf(origin.name))
            if (origin == DataOrigin.LIVE) delete("data_sources", null, null)
            sources.forEach { insertWithOnConflict("data_sources", null, it.toValues(), SQLiteDatabase.CONFLICT_REPLACE) }

            val uniqueFacilities = facilities.distinctBy { it.id }
            uniqueFacilities.forEach { insertOrThrow("facilities", null, it.toValues(origin)) }

            val facilityIds = uniqueFacilities.mapTo(HashSet()) { it.id }
            entrances.asSequence()
                .filter { it.facilityId in facilityIds }
                .distinctBy { it.id }
                .forEach { insertOrThrow("entrances", null, it.toValues(origin)) }
        }
    }

    override fun clear(origin: DataOrigin) {
        db.delete("facilities", "origin = ?", arrayOf(origin.name))
    }

    override fun search(query: FacilityQuery, includeDemo: Boolean, freshSinceEpochMs: Long): List<FacilitySummary> {
        val tokens = SearchText.tokens(query.text)
        val conditions = mutableListOf<String>()
        val whereArgs = mutableListOf<String>()
        if (!includeDemo) conditions += "f.origin = 'LIVE'"
        query.category?.let {
            conditions += "f.category = ?"
            whereArgs += it.name
        }
        tokens.forEach { token ->
            conditions += "f.search_text LIKE ? ESCAPE '\\'"
            whereArgs += "%${SearchText.escapeLike(token)}%"
        }

        val orderArgs = mutableListOf<String>()
        val relevance = if (tokens.isEmpty()) {
            ""
        } else {
            val key = SearchText.escapeLike(tokens.joinToString(""))
            orderArgs += listOf(tokens.joinToString(""), "$key%", "%$key%")
            "CASE WHEN f.name_key = ? THEN 0 WHEN f.name_key LIKE ? ESCAPE '\\' THEN 1 " +
                "WHEN f.name_key LIKE ? ESCAPE '\\' THEN 2 ELSE 3 END, "
        }

        return querySummaries(
            where = conditions.joinToString(" AND ").ifEmpty { "1" },
            orderBy = "(f.origin = 'DEMO') DESC, (entrance_count > 0) DESC, ${relevance}entrance_count DESC, f.name",
            limit = query.limit.coerceIn(1, MAX_RESULTS),
            args = listOf(freshSinceEpochMs.toString()) + whereArgs + orderArgs,
        )
    }

    override fun summaries(
        facilityIds: List<String>,
        includeDemo: Boolean,
        freshSinceEpochMs: Long,
    ): List<FacilitySummary> {
        if (facilityIds.isEmpty()) return emptyList()
        val ids = facilityIds.distinct().take(MAX_RESULTS)
        val placeholders = ids.joinToString(",") { "?" }
        val originFilter = if (includeDemo) "" else " AND f.origin = 'LIVE'"
        val byId = querySummaries(
            where = "f.id IN ($placeholders)$originFilter",
            orderBy = "f.name",
            limit = null,
            args = listOf(freshSinceEpochMs.toString()) + ids,
        ).associateBy { it.facility.id }
        return ids.mapNotNull(byId::get)
    }

    private fun querySummaries(where: String, orderBy: String, limit: Int?, args: List<String>): List<FacilitySummary> {
        val sql = """
            SELECT f.*,
                COUNT(e.id) AS entrance_count,
                COALESCE(SUM(e.max_height_mm IS NOT NULL), 0) AS height_known_count,
                COALESCE(SUM(e.direction IN ('ENTRY', 'BIDIRECTIONAL')), 0) AS entry_capable_count,
                COALESCE(SUM(e.trust_level IN ('VERIFIED_OFFICIAL', 'VERIFIED_FIELD') AND e.verified_at >= ?), 0)
                    AS fresh_count
            FROM facilities f LEFT JOIN entrances e ON e.facility_id = f.id
            WHERE $where
            GROUP BY f.id
            ORDER BY $orderBy
            ${limit?.let { "LIMIT $it" }.orEmpty()}
        """
        return db.rawQuery(sql, args.toTypedArray()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        FacilitySummary(
                            facility = cursor.toFacility(),
                            entranceCount = cursor.int("entrance_count"),
                            heightKnownCount = cursor.int("height_known_count"),
                            entryCapableCount = cursor.int("entry_capable_count"),
                            freshEntranceCount = cursor.int("fresh_count"),
                        )
                    )
                }
            }
        }
    }

    override fun facilityWithEntrances(facilityId: String, includeDemo: Boolean): FacilityWithEntrances? {
        val facility = facility(facilityId, includeDemo) ?: return null
        val sources = sources().associateBy { it.id }
        val entrances = db.rawQuery(
            "SELECT * FROM entrances WHERE facility_id = ? ORDER BY name",
            arrayOf(facilityId),
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.toEntrance(sources)) }
        }
        return FacilityWithEntrances(facility, entrances)
    }

    override fun entrance(entranceId: String, includeDemo: Boolean): EntranceWithFacility? {
        val sources = sources().associateBy { it.id }
        val entrance = db.rawQuery("SELECT * FROM entrances WHERE id = ?", arrayOf(entranceId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.toEntrance(sources) else null
        } ?: return null
        val facility = facility(entrance.facilityId, includeDemo) ?: return null
        return EntranceWithFacility(facility, entrance)
    }

    private fun facility(facilityId: String, includeDemo: Boolean): Facility? =
        db.rawQuery("SELECT * FROM facilities WHERE id = ?", arrayOf(facilityId)).use { cursor ->
            if (cursor.moveToFirst()) cursor.toFacility() else null
        }?.takeIf { includeDemo || it.origin == DataOrigin.LIVE }

    override fun categoryCounts(includeDemo: Boolean): Map<FacilityCategory, Int> {
        val originFilter = if (includeDemo) "" else "AND f.origin = 'LIVE'"
        val sql = """
            SELECT f.category, COUNT(*) FROM facilities f
            WHERE EXISTS (SELECT 1 FROM entrances e WHERE e.facility_id = f.id) $originFilter
            GROUP BY f.category
        """
        return db.rawQuery(sql, null).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val category = enumOrNull<FacilityCategory>(cursor.getString(0)) ?: continue
                    put(category, cursor.getInt(1))
                }
            }
        }
    }

    override fun liveCounts(freshSinceEpochMs: Long): DatasetCounts = DatasetCounts(
        facilities = facilityCount(DataOrigin.LIVE),
        entrances = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM entrances WHERE origin = 'LIVE'", null).toInt(),
        verifiedEntrances = DatabaseUtils.longForQuery(
            db,
            "SELECT COUNT(*) FROM entrances WHERE origin = 'LIVE' " +
                "AND trust_level IN ('VERIFIED_OFFICIAL', 'VERIFIED_FIELD') AND verified_at >= ?",
            arrayOf(freshSinceEpochMs.toString()),
        ).toInt(),
    )

    override fun sources(): List<DataSource> =
        db.rawQuery("SELECT * FROM data_sources ORDER BY name", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DataSource(
                            id = cursor.string("id"),
                            name = cursor.string("name"),
                            type = enumOrDefault(cursor.stringOrNull("type"), DataSourceType.PUBLIC),
                            url = cursor.stringOrNull("url"),
                            license = cursor.stringOrNull("license"),
                            importedAt = cursor.longOrNull("imported_at")?.let(Instant::ofEpochMilli),
                            originalUpdatedAt = parseDate(cursor.stringOrNull("original_updated_at")),
                            notes = cursor.stringOrNull("notes"),
                        )
                    )
                }
            }
        }

    override fun getMeta(key: String): String? =
        db.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    override fun putMeta(key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("meta", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun DataSource.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("url", url)
        put("license", license)
        put("imported_at", importedAt?.toEpochMilli())
        put("original_updated_at", originalUpdatedAt?.toString())
        put("notes", notes)
    }

    private fun Facility.toValues(origin: DataOrigin) = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("name_key", SearchText.normalize(name))
        put("search_text", SearchText.searchable(name, aliases, roadAddress, lotAddress))
        put("category", category.name)
        put("road_address", roadAddress)
        put("lot_address", lotAddress)
        put("latitude", latitude)
        put("longitude", longitude)
        put("aliases", aliases.joinToString(ALIAS_SEPARATOR))
        put("status", status.name)
        put("status_note", statusNote)
        put("origin", origin.name)
        put("updated_at", updatedAtEpochMs)
    }

    private fun VehicleEntrance.toValues(origin: DataOrigin) = ContentValues().apply {
        put("id", id)
        put("facility_id", facilityId)
        put("name", name)
        put("latitude", latitude)
        put("longitude", longitude)
        put("direction", direction.name)
        put("entrance_class", entranceClass?.name)
        put("permitted_vehicle_types", permittedVehicleTypes?.joinToString(TYPE_SEPARATOR) { it.name })
        put("max_height_mm", maxHeightMm)
        put("height_limit_structure", heightLimitStructure.name)
        put("max_width_mm", maxWidthMm)
        put("max_length_mm", maxLengthMm)
        put("max_weight_kg", maxWeightKg)
        put("weekday_hours", hours.weekday.encode())
        put("saturday_hours", hours.saturday.encode())
        put("sunday_hours", hours.sunday.encode())
        put("holiday_hours", hours.holiday.encode())
        put("hours_exception_note", hoursExceptionNote)
        put("restriction_note", restrictionNote)
        put("status", status.name)
        put("data_source_id", dataSource?.id)
        put("trust_level", trustLevel.name)
        put("verified_at", verifiedAt?.toEpochMilli())
        put("route_target_type", routeTargetType.name)
        put("origin", origin.name)
        put("updated_at", updatedAtEpochMs)
    }

    private fun Cursor.toFacility() = Facility(
        id = string("id"),
        name = string("name"),
        category = enumOrDefault(stringOrNull("category"), FacilityCategory.OTHER),
        roadAddress = stringOrNull("road_address"),
        lotAddress = stringOrNull("lot_address"),
        latitude = doubleOrNull("latitude"),
        longitude = doubleOrNull("longitude"),
        aliases = string("aliases").split(ALIAS_SEPARATOR).filter { it.isNotEmpty() },
        status = enumOrDefault(stringOrNull("status"), FacilityStatus.UNKNOWN),
        statusNote = stringOrNull("status_note"),
        origin = enumOrDefault(stringOrNull("origin"), DataOrigin.LIVE),
        updatedAtEpochMs = long("updated_at"),
    )

    private fun Cursor.toEntrance(sources: Map<String, DataSource>) = VehicleEntrance(
        id = string("id"),
        facilityId = string("facility_id"),
        name = string("name"),
        latitude = doubleOrNull("latitude"),
        longitude = doubleOrNull("longitude"),
        direction = enumOrDefault(stringOrNull("direction"), EntranceDirection.UNKNOWN),
        entranceClass = enumOrNull<EntranceClass>(stringOrNull("entrance_class")),
        permittedVehicleTypes = stringOrNull("permitted_vehicle_types")
            ?.split(TYPE_SEPARATOR)
            ?.mapNotNull { enumOrNull<PermittedVehicleType>(it) }
            ?.toSet()
            ?.ifEmpty { null },
        maxHeightMm = intOrNull("max_height_mm"),
        heightLimitStructure = enumOrDefault(stringOrNull("height_limit_structure"), HeightLimitStructure.UNKNOWN),
        maxWidthMm = intOrNull("max_width_mm"),
        maxLengthMm = intOrNull("max_length_mm"),
        maxWeightKg = intOrNull("max_weight_kg"),
        hours = WeeklyHours(
            weekday = decodeHours(stringOrNull("weekday_hours")),
            saturday = decodeHours(stringOrNull("saturday_hours")),
            sunday = decodeHours(stringOrNull("sunday_hours")),
            holiday = decodeHours(stringOrNull("holiday_hours")),
        ),
        hoursExceptionNote = stringOrNull("hours_exception_note"),
        restrictionNote = stringOrNull("restriction_note"),
        status = enumOrDefault(stringOrNull("status"), EntranceStatus.UNKNOWN),
        dataSource = stringOrNull("data_source_id")?.let(sources::get),
        trustLevel = enumOrDefault(stringOrNull("trust_level"), TrustLevel.UNKNOWN),
        verifiedAt = longOrNull("verified_at")?.let(Instant::ofEpochMilli),
        routeTargetType = enumOrDefault(stringOrNull("route_target_type"), RouteTargetType.FACILITY_CENTROID),
        origin = enumOrDefault(stringOrNull("origin"), DataOrigin.LIVE),
        updatedAtEpochMs = long("updated_at"),
    )

    private fun OperatingHours.encode(): String? =
        toDto()?.let { GatefitJson.encodeToString(HoursDto.serializer(), it) }

    private fun decodeHours(json: String?): OperatingHours {
        if (json == null) return OperatingHours.Unknown
        return try {
            GatefitJson.decodeFromString(HoursDto.serializer(), json).toDomain()
        } catch (_: SerializationException) {
            OperatingHours.Unknown
        }
    }

    private companion object {
        const val MAX_RESULTS = 200
        const val ALIAS_SEPARATOR = "\n"
        const val TYPE_SEPARATOR = ","
    }
}

internal fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

internal fun Cursor.stringOrNull(column: String): String? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }

internal fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

internal fun Cursor.intOrNull(column: String): Int? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getInt(it) }

internal fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))

internal fun Cursor.longOrNull(column: String): Long? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }

internal fun Cursor.doubleOrNull(column: String): Double? =
    getColumnIndexOrThrow(column).let { if (isNull(it)) null else getDouble(it) }
