package com.gatefit.domain.usecase

import com.gatefit.domain.model.EffectiveDimensions
import com.gatefit.domain.model.HeightBasis
import com.gatefit.domain.model.VehicleProfile

class CalculateEffectiveVehicleDimensionsUseCase {

    operator fun invoke(profile: VehicleProfile): EffectiveDimensions {
        val base = profile.baseDimensions
        // 높이·너비 증가량은 순정 외형 기준이므로 최댓값 적용, 길이는 전·후방 장착을 고려해 합산
        val accessoryHeight = profile.accessories.maxOfOrNull { it.additionalHeightMm.coerceAtLeast(0) } ?: 0
        val accessoryWidth = profile.accessories.maxOfOrNull { it.additionalWidthMm.coerceAtLeast(0) } ?: 0
        val accessoryLength = profile.accessories.sumOf { it.additionalLengthMm.coerceAtLeast(0) }

        val measured = profile.measuredTotalHeightMm
        val (height, basis) = when {
            // 직접 측정값은 장착물이 이미 포함된 값이므로 추가 높이와 중복 합산 금지
            measured != null -> measured to HeightBasis.MEASURED_TOTAL
            base.heightMm != null ->
                base.heightMm + accessoryHeight + profile.heightCorrectionMm to HeightBasis.FACTORY_WITH_ACCESSORIES
            else -> null to HeightBasis.UNAVAILABLE
        }

        return EffectiveDimensions(
            heightMm = height,
            widthMm = base.widthMm?.plus(accessoryWidth),
            lengthMm = base.lengthMm?.plus(accessoryLength),
            heightBasis = basis,
            accessoryHeightMm = accessoryHeight,
            accessoryWidthMm = accessoryWidth,
            accessoryLengthMm = accessoryLength,
            // 직접 측정한 전체 높이는 사용자 확인값으로 취급
            usableForAnalysis = profile.specConfidence.usableForAnalysis || measured != null,
        )
    }
}
