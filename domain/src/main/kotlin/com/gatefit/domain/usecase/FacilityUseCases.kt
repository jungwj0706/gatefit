package com.gatefit.domain.usecase

import com.gatefit.domain.model.FacilityAnalysis
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilitySummary
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.EntranceWithFacility
import com.gatefit.domain.repository.FacilityQuery
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.SavedPlacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.ZonedDateTime

class SearchFacilitiesUseCase(private val facilities: FacilityRepository) {
    suspend operator fun invoke(text: String, category: FacilityCategory? = null): List<FacilitySummary> =
        facilities.search(FacilityQuery(text = text.trim(), category = category))
}

class GetFacilityEntrancesUseCase(
    private val facilities: FacilityRepository,
    private val analyzeFacility: AnalyzeFacilityEntrancesUseCase,
) {
    // 차량 프로필이 바뀌면 호출 측에서 다시 실행해 결과 재계산
    suspend operator fun invoke(
        facilityId: String,
        profile: VehicleProfile,
        now: ZonedDateTime,
    ): FacilityAnalysis? {
        val facility = facilities.getFacilityWithEntrances(facilityId) ?: return null
        return analyzeFacility(profile, facility, now)
    }
}

class GetEntranceDetailsUseCase(private val facilities: FacilityRepository) {
    suspend operator fun invoke(entranceId: String): EntranceWithFacility? = facilities.getEntrance(entranceId)
}

class GetRecentFacilitiesUseCase(
    private val savedPlaces: SavedPlacesRepository,
    private val facilities: FacilityRepository,
) {
    operator fun invoke(): Flow<List<FacilitySummary>> =
        combine(savedPlaces.recentIds, facilities.dataVersion) { ids, _ -> ids }
            .map { ids -> facilities.getSummaries(ids) }
}

class GetFavoriteFacilitiesUseCase(
    private val savedPlaces: SavedPlacesRepository,
    private val facilities: FacilityRepository,
) {
    operator fun invoke(): Flow<List<FacilitySummary>> =
        combine(savedPlaces.favoriteIds, facilities.dataVersion) { ids, _ -> ids }
            .map { ids -> facilities.getSummaries(ids) }
}

class ToggleFavoriteFacilityUseCase(private val savedPlaces: SavedPlacesRepository) {
    suspend operator fun invoke(facilityId: String, favorite: Boolean) =
        savedPlaces.setFavorite(facilityId, favorite)
}
