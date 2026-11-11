package com.gatefit.domain.analysis

import com.gatefit.domain.model.CheckOutcome
import com.gatefit.domain.model.CheckReason
import com.gatefit.domain.model.ConstraintCheck
import com.gatefit.domain.model.ConstraintType
import com.gatefit.domain.model.DataFreshness
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.model.VehicleEntrance
import java.time.Duration
import java.time.Instant

// 180일은 초기 운영 정책 값, 공식 안전 기준 아님
data class FreshnessPolicy(val maxVerifiedAgeDays: Long = 180)

data class TrustVerdict(val check: ConstraintCheck, val freshness: DataFreshness)

class DataTrustEvaluator(private val policy: FreshnessPolicy = FreshnessPolicy()) {

    fun evaluate(entrance: VehicleEntrance, now: Instant): TrustVerdict {
        val (outcome, reason, freshness) = when (entrance.trustLevel) {
            TrustLevel.VERIFIED_OFFICIAL, TrustLevel.VERIFIED_FIELD -> verifiedVerdict(entrance.verifiedAt, now)
            TrustLevel.PUBLIC_BASELINE ->
                Triple(CheckOutcome.UNKNOWN, CheckReason.TRUST_BASELINE_NOT_REVERIFIED, DataFreshness.STALE_BASELINE)
            TrustLevel.COMMUNITY_REPORTED ->
                Triple(CheckOutcome.UNKNOWN, CheckReason.TRUST_COMMUNITY_UNVERIFIED, DataFreshness.UNTRUSTED)
            TrustLevel.UNKNOWN ->
                Triple(CheckOutcome.UNKNOWN, CheckReason.TRUST_SOURCE_UNKNOWN, DataFreshness.UNTRUSTED)
        }
        return TrustVerdict(
            check = ConstraintCheck(
                type = ConstraintType.DATA_TRUST,
                outcome = outcome,
                reason = reason,
                required = true,
            ),
            freshness = freshness,
        )
    }

    private fun verifiedVerdict(verifiedAt: Instant?, now: Instant): Triple<CheckOutcome, CheckReason, DataFreshness> {
        if (verifiedAt == null) {
            return Triple(CheckOutcome.UNKNOWN, CheckReason.TRUST_VERIFICATION_DATE_MISSING, DataFreshness.NEEDS_RECHECK)
        }
        val ageDays = Duration.between(verifiedAt, now).toDays()
        // 미래 시각으로 기록된 검증일은 신뢰 불가
        return if (ageDays in 0..policy.maxVerifiedAgeDays) {
            Triple(CheckOutcome.PASSED, CheckReason.TRUST_FRESH, DataFreshness.FRESH)
        } else {
            Triple(CheckOutcome.UNKNOWN, CheckReason.TRUST_STALE_VERIFICATION, DataFreshness.NEEDS_RECHECK)
        }
    }
}
