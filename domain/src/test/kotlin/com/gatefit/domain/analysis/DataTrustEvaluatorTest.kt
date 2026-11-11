package com.gatefit.domain.analysis

import com.gatefit.domain.model.CheckOutcome
import com.gatefit.domain.model.CheckReason
import com.gatefit.domain.model.DataFreshness
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.testing.Fixtures.WEDNESDAY_1PM
import com.gatefit.domain.testing.Fixtures.verifiedEntrance
import org.junit.Assert.assertEquals
import org.junit.Test

class DataTrustEvaluatorTest {

    private val evaluator = DataTrustEvaluator()
    private val now = WEDNESDAY_1PM.toInstant()

    @Test
    fun `verification within 180 days is fresh`() {
        val verdict = evaluator.evaluate(verifiedEntrance(verifiedDaysAgo = 180), now)

        assertEquals(CheckOutcome.PASSED, verdict.check.outcome)
        assertEquals(DataFreshness.FRESH, verdict.freshness)
    }

    @Test
    fun `verification older than 180 days needs a recheck`() {
        val verdict = evaluator.evaluate(verifiedEntrance(verifiedDaysAgo = 181), now)

        assertEquals(CheckOutcome.UNKNOWN, verdict.check.outcome)
        assertEquals(CheckReason.TRUST_STALE_VERIFICATION, verdict.check.reason)
        assertEquals(DataFreshness.NEEDS_RECHECK, verdict.freshness)
    }

    @Test
    fun `verified level without a verification date is not trusted as fresh`() {
        val verdict = evaluator.evaluate(verifiedEntrance(verifiedDaysAgo = null), now)

        assertEquals(CheckReason.TRUST_VERIFICATION_DATE_MISSING, verdict.check.reason)
        assertEquals(DataFreshness.NEEDS_RECHECK, verdict.freshness)
    }

    @Test
    fun `verification dated in the future is rejected`() {
        val verdict = evaluator.evaluate(verifiedEntrance(verifiedDaysAgo = -3), now)

        assertEquals(CheckOutcome.UNKNOWN, verdict.check.outcome)
    }

    @Test
    fun `public baseline stays stale even with a recent timestamp`() {
        val entrance = verifiedEntrance(trustLevel = TrustLevel.PUBLIC_BASELINE, verifiedDaysAgo = 1)

        val verdict = evaluator.evaluate(entrance, now)

        assertEquals(CheckReason.TRUST_BASELINE_NOT_REVERIFIED, verdict.check.reason)
        assertEquals(DataFreshness.STALE_BASELINE, verdict.freshness)
    }

    @Test
    fun `community reports and unknown sources are never fresh`() {
        val community = evaluator.evaluate(verifiedEntrance(trustLevel = TrustLevel.COMMUNITY_REPORTED), now)
        val unknown = evaluator.evaluate(verifiedEntrance(trustLevel = TrustLevel.UNKNOWN), now)

        assertEquals(CheckReason.TRUST_COMMUNITY_UNVERIFIED, community.check.reason)
        assertEquals(DataFreshness.UNTRUSTED, community.freshness)
        assertEquals(DataFreshness.UNTRUSTED, unknown.freshness)
    }

    @Test
    fun `freshness window is configurable`() {
        val strict = DataTrustEvaluator(FreshnessPolicy(maxVerifiedAgeDays = 30))

        assertEquals(CheckOutcome.UNKNOWN, strict.evaluate(verifiedEntrance(verifiedDaysAgo = 31), now).check.outcome)
        assertEquals(CheckOutcome.PASSED, strict.evaluate(verifiedEntrance(verifiedDaysAgo = 30), now).check.outcome)
    }
}
