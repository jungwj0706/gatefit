package com.gatefit.domain.usecase

import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.ConstraintType
import com.gatefit.domain.model.EntranceAnalysis
import com.gatefit.domain.model.FacilityAnalysis
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.VehicleProfile
import java.time.ZonedDateTime

class AnalyzeFacilityEntrancesUseCase(
    private val calculateEffectiveDimensions: CalculateEffectiveVehicleDimensionsUseCase,
    private val analyzeEntrance: AnalyzeEntranceCompatibilityUseCase,
) {

    operator fun invoke(
        profile: VehicleProfile,
        facility: FacilityWithEntrances,
        now: ZonedDateTime,
    ): FacilityAnalysis {
        val vehicle = calculateEffectiveDimensions(profile)
        val analyses = facility.entrances
            .map { entrance ->
                EntranceAnalysis(
                    entrance = entrance,
                    result = analyzeEntrance.analyze(
                        vehicle = vehicle,
                        safetyMarginMm = profile.safetyMarginMm,
                        weightKg = profile.weightKg,
                        entrance = entrance,
                        now = now,
                    ),
                )
            }
            .sortedWith(RECOMMENDATION_ORDER)

        return FacilityAnalysis(
            facility = facility.facility,
            vehicle = vehicle,
            safetyMarginMm = profile.safetyMarginMm,
            entrances = analyses,
            evaluatedAt = now.withZoneSameInstant(AnalyzeEntranceCompatibilityUseCase.FACILITY_ZONE).toLocalDateTime(),
        )
    }

    companion object {
        // 조건 충족 > 확인 필요(높이 충족·미확인 항목 적은 순) > 부적합, 미확인 출입구는 우선순위 하향
        val RECOMMENDATION_ORDER: Comparator<EntranceAnalysis> =
            compareBy<EntranceAnalysis> {
                when (it.result.status) {
                    CompatibilityStatus.CONDITION_MATCH -> 0
                    CompatibilityStatus.NEEDS_CONFIRMATION -> 1
                    CompatibilityStatus.CONDITION_MISMATCH -> 2
                }
            }
                .thenBy { if (it.result.heightPassed) 0 else 1 }
                .thenBy { it.result.unknownChecks.size }
                .thenByDescending { it.result.check(ConstraintType.HEIGHT)?.marginValue ?: Int.MIN_VALUE }
                .thenBy { it.entrance.name }
    }
}
