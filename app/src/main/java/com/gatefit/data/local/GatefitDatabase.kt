package com.gatefit.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class GatefitDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        (CACHE_TABLES + USER_TABLES).forEach(db::execSQL)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 시설·출입구는 다시 받을 수 있는 캐시이므로 재생성, 제보·즐겨찾기 등 사용자 데이터는 유지
        CACHE_TABLE_NAMES.forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        CACHE_TABLES.forEach(db::execSQL)
    }

    companion object {
        const val NAME = "gatefit.db"
        const val VERSION = 1

        private val CACHE_TABLE_NAMES = listOf("entrances", "facilities", "data_sources", "meta")

        private val CACHE_TABLES = listOf(
            """
            CREATE TABLE data_sources (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                url TEXT,
                license TEXT,
                imported_at INTEGER,
                original_updated_at TEXT,
                notes TEXT
            )
            """,
            """
            CREATE TABLE facilities (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                name_key TEXT NOT NULL,
                search_text TEXT NOT NULL,
                category TEXT NOT NULL,
                road_address TEXT,
                lot_address TEXT,
                latitude REAL,
                longitude REAL,
                aliases TEXT NOT NULL,
                status TEXT NOT NULL,
                status_note TEXT,
                origin TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """,
            "CREATE INDEX facilities_category_idx ON facilities (category)",
            """
            CREATE TABLE entrances (
                id TEXT PRIMARY KEY,
                facility_id TEXT NOT NULL REFERENCES facilities (id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                latitude REAL,
                longitude REAL,
                direction TEXT NOT NULL,
                entrance_class TEXT,
                permitted_vehicle_types TEXT,
                max_height_mm INTEGER,
                height_limit_structure TEXT NOT NULL,
                max_width_mm INTEGER,
                max_length_mm INTEGER,
                max_weight_kg INTEGER,
                weekday_hours TEXT,
                saturday_hours TEXT,
                sunday_hours TEXT,
                holiday_hours TEXT,
                hours_exception_note TEXT,
                restriction_note TEXT,
                status TEXT NOT NULL,
                data_source_id TEXT,
                trust_level TEXT NOT NULL,
                verified_at INTEGER,
                route_target_type TEXT NOT NULL,
                origin TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """,
            "CREATE INDEX entrances_facility_idx ON entrances (facility_id)",
            "CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        )

        private val USER_TABLES = listOf(
            """
            CREATE TABLE reports (
                local_id TEXT PRIMARY KEY,
                entrance_id TEXT NOT NULL,
                type TEXT NOT NULL,
                description TEXT,
                proposed_height_mm INTEGER,
                proposed_direction TEXT,
                delivery TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                last_attempt_at INTEGER
            )
            """,
            "CREATE TABLE favorites (facility_id TEXT PRIMARY KEY, created_at INTEGER NOT NULL)",
            "CREATE TABLE recents (facility_id TEXT PRIMARY KEY, visited_at INTEGER NOT NULL)",
        )
    }
}
