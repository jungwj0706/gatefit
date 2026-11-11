package com.gatefit.domain.repository

import com.gatefit.domain.model.EntranceReport
import com.gatefit.domain.model.EntranceReportDraft
import com.gatefit.domain.model.VehicleModelSpec
import com.gatefit.domain.model.VehicleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VehicleProfileRepository {
    val profile: StateFlow<VehicleProfile?>

    suspend fun save(profile: VehicleProfile)

    suspend fun clear()
}

interface VehicleSpecRepository {
    suspend fun search(text: String): List<VehicleModelSpec>
}

interface ReportRepository {
    val reports: Flow<List<EntranceReport>>

    // 제보는 항상 기기에 먼저 저장, 서버 전송 결과는 반환된 제보의 delivery로 확인
    suspend fun submit(draft: EntranceReportDraft): EntranceReport

    suspend fun retryUndelivered(): Int
}

interface SavedPlacesRepository {
    val favoriteIds: Flow<List<String>>

    val recentIds: Flow<List<String>>

    suspend fun setFavorite(facilityId: String, favorite: Boolean)

    suspend fun recordRecent(facilityId: String)

    suspend fun clearRecents()
}

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    // DEMO 데이터는 사용자가 직접 켠 경우에만 표시
    val demoDataEnabled: Boolean = false,
)

interface SettingsRepository {
    val settings: StateFlow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}
