package com.gatefit.domain.usecase

import com.gatefit.domain.analysis.DataTrustEvaluator
import com.gatefit.domain.analysis.OperatingHoursEvaluator
import com.gatefit.domain.model.CheckOutcome
import com.gatefit.domain.model.CheckReason
import com.gatefit.domain.model.CompatibilityResult
import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.ConstraintCheck
import com.gatefit.domain.model.ConstraintType
import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EffectiveDimensions
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.HeightLimitStructure
import com.gatefit.domain.model.PermittedVehicleType
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.model.VehicleProfile
import java.time.ZoneId
import java.time.ZonedDateTime

class AnalyzeEntranceCompatibilityUseCase(
    private val calculateEffectiveDimensions: CalculateEffectiveVehicleDimensionsUseCase,
    private val hoursEvaluator: OperatingHoursEvaluator,
    private val trustEvaluator: DataTrustEvaluator,
) {

    operator fun invoke(
        profile: VehicleProfile,
        entrance: VehicleEntrance,
        now: ZonedDateTime,
    ): CompatibilityResult = analyze(
        vehicle = calculateEffectiveDimensions(profile),
        safetyMarginMm = profile.safetyMarginMm,
        weightKg = profile.weightKg,
        entrance = entrance,
        now = now,
    )

    fun analyze(
        vehicle: EffectiveDimensions,
        safetyMarginMm: Int,
        weightKg: Int?,
        entrance: VehicleEntrance,
        now: ZonedDateTime,
    ): CompatibilityResult {
        // 출입 시간은 시설 현지 시각(서울) 기준으로 평가
        val localNow = now.withZoneSameInstant(FACILITY_ZONE).toLocalDateTime()
        val trust = trustEvaluator.evaluate(entrance, now.toInstant())

        val checks = listOf(
            checkHeight(vehicle, safetyMarginMm, entrance),
            checkOptionalLimit(ConstraintType.WIDTH, vehicle.widthMm, vehicle.usableForAnalysis, entrance.maxWidthMm),
            checkOptionalLimit(ConstraintType.LENGTH, vehicle.lengthMm, vehicle.usableForAnalysis, entrance.maxLengthMm),
            checkOptionalLimit(ConstraintType.WEIGHT, weightKg, weightKg != null, entrance.maxWeightKg),
            checkDirection(entrance.direction),
            checkVehicleType(entrance.permittedVehicleTypes),
            checkEntranceStatus(entrance.status),
            hoursEvaluator.evaluate(entrance.hours, localNow),
            trust.check,
        )

        return CompatibilityResult(
            entranceId = entrance.id,
            status = classify(checks),
            checks = checks,
            freshness = trust.freshness,
            evaluatedAt = localNow,
            demo = entrance.origin == DataOrigin.DEMO,
        )
    }

    private fun checkHeight(
        vehicle: EffectiveDimensions,
        safetyMarginMm: Int,
        entrance: VehicleEntrance,
    ): ConstraintCheck {
        val vehicleHeight = vehicle.heightMm
        val limit = entrance.maxHeightMm
        return when {
            vehicleHeight == null -> unknown(ConstraintType.HEIGHT, CheckReason.VEHICLE_VALUE_MISSING, limit = limit)
            !vehicle.usableForAnalysis ->
                unknown(ConstraintType.HEIGHT, CheckReason.VEHICLE_VALUE_UNCONFIRMED, limit = limit)
            limit == null -> unknown(
                ConstraintType.HEIGHT,
                // 높이 제한 구조물이 없다는 기록은 통과 가능 높이 정보가 아님
                if (entrance.heightLimitStructure == HeightLimitStructure.NOT_INSTALLED) {
                    CheckReason.HEIGHT_STRUCTURE_NOT_INSTALLED
                } else {
                    CheckReason.LIMIT_NOT_PROVIDED
                },
                vehicleValue = vehicleHeight,
            )
            else -> {
                val margin = limit - vehicleHeight
                val (outcome, reason) = when {
                    vehicleHeight > limit -> CheckOutcome.FAILED to CheckReason.EXCEEDS_LIMIT
                    vehicleHeight + safetyMarginMm > limit -> CheckOutcome.FAILED to CheckReason.MARGIN_INSUFFICIENT
                    else -> CheckOutcome.PASSED to CheckReason.WITHIN_LIMIT
                }
                ConstraintCheck(
                    type = ConstraintType.HEIGHT,
                    outcome = outcome,
                    reason = reason,
                    required = true,
                    vehicleValue = vehicleHeight,
                    limitValue = limit,
                    marginValue = margin,
                )
            }
        }
    }

    private fun checkOptionalLimit(
        type: ConstraintType,
        vehicleValue: Int?,
        vehicleValueUsable: Boolean,
        limit: Int?,
    ): ConstraintCheck = when {
        limit == null -> ConstraintCheck(
            type = type,
            outcome = CheckOutcome.NOT_PROVIDED,
            reason = CheckReason.LIMIT_NOT_PROVIDED,
            required = false,
            vehicleValue = vehicleValue,
        )
        vehicleValue == null -> unknown(type, CheckReason.VEHICLE_VALUE_MISSING, limit = limit)
        !vehicleValueUsable -> unknown(type, CheckReason.VEHICLE_VALUE_UNCONFIRMED, limit = limit)
        else -> ConstraintCheck(
            type = type,
            outcome = if (vehicleValue > limit) CheckOutcome.FAILED else CheckOutcome.PASSED,
            reason = if (vehicleValue > limit) CheckReason.EXCEEDS_LIMIT else CheckReason.WITHIN_LIMIT,
            required = true,
            vehicleValue = vehicleValue,
            limitValue = limit,
            marginValue = limit - vehicleValue,
        )
    }

    private fun checkDirection(direction: EntranceDirection): ConstraintCheck {
        val (outcome, reason) = when (direction) {
            EntranceDirection.ENTRY -> CheckOutcome.PASSED to CheckReason.DIRECTION_ENTRY
            EntranceDirection.BIDIRECTIONAL -> CheckOutcome.PASSED to CheckReason.DIRECTION_BIDIRECTIONAL
            EntranceDirection.EXIT -> CheckOutcome.FAILED to CheckReason.DIRECTION_EXIT_ONLY
            EntranceDirection.UNKNOWN -> CheckOutcome.UNKNOWN to CheckReason.DIRECTION_UNKNOWN
        }
        return ConstraintCheck(ConstraintType.DIRECTION, outcome, reason, required = true)
    }

    private fun checkVehicleType(permitted: Set<PermittedVehicleType>?): ConstraintCheck = when {
        permitted == null -> ConstraintCheck(
            type = ConstraintType.VEHICLE_TYPE,
            outcome = CheckOutcome.NOT_PROVIDED,
            reason = CheckReason.VEHICLE_TYPE_NOT_PROVIDED,
            required = false,
        )
        PermittedVehicleType.GENERAL in permitted -> ConstraintCheck(
            type = ConstraintType.VEHICLE_TYPE,
            outcome = CheckOutcome.PASSED,
            reason = CheckReason.VEHICLE_TYPE_PERMITTED,
            required = true,
        )
        else -> ConstraintCheck(
            type = ConstraintType.VEHICLE_TYPE,
            outcome = CheckOutcome.FAILED,
            reason = CheckReason.VEHICLE_TYPE_NOT_PERMITTED,
            required = true,
        )
    }

    private fun checkEntranceStatus(status: EntranceStatus): ConstraintCheck {
        val (outcome, reason) = when (status) {
            EntranceStatus.ACTIVE -> CheckOutcome.PASSED to CheckReason.ENTRANCE_ACTIVE
            EntranceStatus.CLOSED -> CheckOutcome.FAILED to CheckReason.ENTRANCE_CLOSED
            EntranceStatus.UNKNOWN -> CheckOutcome.UNKNOWN to CheckReason.ENTRANCE_STATUS_UNKNOWN
        }
        return ConstraintCheck(ConstraintType.ENTRANCE_STATUS, outcome, reason, required = true)
    }

    private fun unknown(
        type: ConstraintType,
        reason: CheckReason,
        vehicleValue: Int? = null,
        limit: Int? = null,
    ) = ConstraintCheck(
        type = type,
        outcome = CheckOutcome.UNKNOWN,
        reason = reason,
        required = true,
        vehicleValue = vehicleValue,
        limitValue = limit,
    )

    private fun classify(checks: List<ConstraintCheck>): CompatibilityStatus = when {
        checks.any { it.outcome == CheckOutcome.FAILED } -> CompatibilityStatus.CONDITION_MISMATCH
        checks.any { it.outcome == CheckOutcome.UNKNOWN } -> CompatibilityStatus.NEEDS_CONFIRMATION
        else -> CompatibilityStatus.CONDITION_MATCH
    }

    companion object {
        val FACILITY_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
