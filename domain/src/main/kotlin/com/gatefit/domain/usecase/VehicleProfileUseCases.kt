package com.gatefit.domain.usecase

import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.SpecSource
import com.gatefit.domain.model.VehicleModelSpec
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.repository.VehicleProfileRepository

sealed interface SaveProfileResult {
    data class Saved(val profile: VehicleProfile) : SaveProfileResult

    // 범위를 벗어난 값이 있으면 저장하지 않고 사용자 확인 요청
    data class Rejected(val issues: List<ProfileIssue>) : SaveProfileResult
}

class SaveVehicleProfileUseCase(
    private val profiles: VehicleProfileRepository,
    private val nowEpochMs: () -> Long,
) {
    suspend operator fun invoke(profile: VehicleProfile): SaveProfileResult {
        val issues = VehicleProfileValidator.validate(profile)
        if (issues.isNotEmpty()) return SaveProfileResult.Rejected(issues)

        val saved = profile.copy(updatedAtEpochMs = nowEpochMs())
        profiles.save(saved)
        return SaveProfileResult.Saved(saved)
    }
}

class ApplyVehicleModelSpecUseCase {
    // 출처가 확인된 제원만 제조사 검증 등급 부여, 그 외에는 추정값으로 취급
    operator fun invoke(profile: VehicleProfile, spec: VehicleModelSpec): VehicleProfile = profile.copy(
        manufacturer = spec.manufacturer,
        modelName = spec.modelName,
        modelYear = spec.modelYear ?: profile.modelYear,
        trimName = spec.trimName,
        baseDimensions = spec.dimensions,
        specSource = SpecSource.MANUFACTURER,
        specConfidence = if (spec.hasSource) SpecConfidence.VERIFIED_BY_MANUFACTURER else SpecConfidence.USER_ESTIMATED,
        specSourceName = spec.sourceName,
        specSourceUrl = spec.sourceUrl,
    )
}
