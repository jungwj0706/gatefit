package com.gatefit.data.local

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import com.gatefit.data.mapper.enumOrDefault
import com.gatefit.data.mapper.enumOrNull
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceReport
import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.model.ReportType

class SqliteUserStore(private val database: GatefitDatabase) : ReportLocalSource, SavedPlacesLocalSource {

    private val db: SQLiteDatabase get() = database.writableDatabase

    override fun insert(report: EntranceReport) {
        val values = ContentValues().apply {
            put("local_id", report.localId)
            put("entrance_id", report.entranceId)
            put("type", report.type.name)
            put("description", report.description)
            put("proposed_height_mm", report.proposedHeightMm)
            put("proposed_direction", report.proposedDirection?.name)
            put("delivery", report.delivery.name)
            put("created_at", report.createdAtEpochMs)
            put("last_attempt_at", report.lastAttemptAtEpochMs)
        }
        db.insertOrThrow("reports", null, values)
    }

    override fun updateDelivery(localId: String, delivery: ReportDelivery, attemptAtEpochMs: Long) {
        val values = ContentValues().apply {
            put("delivery", delivery.name)
            put("last_attempt_at", attemptAtEpochMs)
        }
        db.update("reports", values, "local_id = ?", arrayOf(localId))
    }

    override fun all(): List<EntranceReport> = queryReports(null, null)

    override fun undelivered(): List<EntranceReport> = queryReports(
        "delivery IN (?, ?)",
        arrayOf(ReportDelivery.QUEUED.name, ReportDelivery.FAILED.name),
    )

    private fun queryReports(where: String?, args: Array<String>?): List<EntranceReport> =
        db.query("reports", null, where, args, null, null, "created_at DESC").use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.toReport()?.let(::add) }
        }

    private fun Cursor.toReport(): EntranceReport? {
        val type = enumOrNull<ReportType>(stringOrNull("type")) ?: return null
        return EntranceReport(
            localId = string("local_id"),
            entranceId = string("entrance_id"),
            type = type,
            description = stringOrNull("description"),
            proposedHeightMm = intOrNull("proposed_height_mm"),
            proposedDirection = enumOrNull<EntranceDirection>(stringOrNull("proposed_direction")),
            delivery = enumOrDefault(stringOrNull("delivery"), ReportDelivery.FAILED),
            createdAtEpochMs = long("created_at"),
            lastAttemptAtEpochMs = longOrNull("last_attempt_at"),
        )
    }

    override fun favoriteIds(): List<String> = queryIds("favorites", "created_at DESC")

    override fun setFavorite(facilityId: String, favorite: Boolean, atEpochMs: Long) {
        if (favorite) {
            val values = ContentValues().apply {
                put("facility_id", facilityId)
                put("created_at", atEpochMs)
            }
            db.insertWithOnConflict("favorites", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        } else {
            db.delete("favorites", "facility_id = ?", arrayOf(facilityId))
        }
    }

    override fun recentIds(): List<String> = queryIds("recents", "visited_at DESC")

    override fun recordRecent(facilityId: String, atEpochMs: Long, keep: Int) {
        db.transaction {
            val values = ContentValues().apply {
                put("facility_id", facilityId)
                put("visited_at", atEpochMs)
            }
            insertWithOnConflict("recents", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            execSQL(
                "DELETE FROM recents WHERE facility_id NOT IN " +
                    "(SELECT facility_id FROM recents ORDER BY visited_at DESC LIMIT ?)",
                arrayOf<Any>(keep),
            )
        }
    }

    override fun clearRecents() {
        db.delete("recents", null, null)
    }

    private fun queryIds(table: String, orderBy: String): List<String> =
        db.query(table, arrayOf("facility_id"), null, null, null, null, orderBy).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
}
