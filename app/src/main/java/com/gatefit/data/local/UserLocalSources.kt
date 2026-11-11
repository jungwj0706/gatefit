package com.gatefit.data.local

import com.gatefit.domain.model.EntranceReport
import com.gatefit.domain.model.ReportDelivery

// 모든 함수는 블로킹 호출, 리포지토리에서 IO 디스패처로 감싸 사용
interface ReportLocalSource {
    fun insert(report: EntranceReport)

    fun updateDelivery(localId: String, delivery: ReportDelivery, attemptAtEpochMs: Long)

    fun all(): List<EntranceReport>

    fun undelivered(): List<EntranceReport>
}

interface SavedPlacesLocalSource {
    fun favoriteIds(): List<String>

    fun setFavorite(facilityId: String, favorite: Boolean, atEpochMs: Long)

    fun recentIds(): List<String>

    fun recordRecent(facilityId: String, atEpochMs: Long, keep: Int)

    fun clearRecents()
}

interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(key: String, value: String?)
}
