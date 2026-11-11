package com.gatefit.domain.usecase

import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.model.TrustLevel
import com.gatefit.domain.testing.Fixtures
import com.gatefit.domain.testing.Fixtures.WEDNESDAY_1PM
import com.gatefit.domain.testing.Fixtures.suvWithRoofBox
import com.gatefit.domain.testing.Fixtures.verifiedEntrance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeFacilityEntrancesUseCaseTest {

    private val analyzeFacility = AnalyzeFacilityEntrancesUseCase(
        calculateEffectiveDimensions = CalculateEffectiveVehicleDimensionsUseCase(),
        analyzeEntrance = Fixtures.engine(),
    )

    private val facility = Facility(
        id = "facility",
        name = "테스트 시설",
        category = FacilityCategory.SHOPPING,
        roadAddress = null,
        lotAddress = null,
        latitude = null,
        longitude = null,
    )

    @Test
    fun `plan example produces mismatch match and unknown for gates a b c`() {
        val analysis = analyzeFacility(
            suvWithRoofBox(),
            FacilityWithEntrances(
                facility,
                listOf(
                    verifiedEntrance(id = "A", maxHeightMm = 2000),
                    verifiedEntrance(id = "B", maxHeightMm = 2300),
                    verifiedEntrance(id = "C", maxHeightMm = null),
                ),
            ),
            WEDNESDAY_1PM,
        )

        val byId = analysis.entrances.associate { it.entrance.id to it.result.status }
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, byId["A"])
        assertEquals(CompatibilityStatus.CONDITION_MATCH, byId["B"])
        assertEquals(CompatibilityStatus.NEEDS_CONFIRMATION, byId["C"])
        assertEquals(2100, analysis.vehicle.heightMm)
        assertEquals(listOf("B", "C", "A"), analysis.entrances.map { it.entrance.id })
        assertFalse(analysis.hasNoMatch)
    }

    @Test
    fun `entrances with fewer open questions are listed first among needs confirmation`() {
        val analysis = analyzeFacility(
            suvWithRoofBox(),
            FacilityWithEntrances(
                facility,
                listOf(
                    verifiedEntrance(id = "unknown-direction", direction = EntranceDirection.UNKNOWN, trustLevel = TrustLevel.PUBLIC_BASELINE),
                    verifiedEntrance(id = "baseline", trustLevel = TrustLevel.PUBLIC_BASELINE),
                    verifiedEntrance(id = "no-height", maxHeightMm = null),
                ),
            ),
            WEDNESDAY_1PM,
        )

        assertEquals(listOf("baseline", "unknown-direction", "no-height"), analysis.entrances.map { it.entrance.id })
        assertTrue(analysis.hasNoMatch)
        assertEquals(3, analysis.needsConfirmationCount)
    }

    @Test
    fun `larger height clearance is preferred among matches`() {
        val analysis = analyzeFacility(
            suvWithRoofBox(),
            FacilityWithEntrances(
                facility,
                listOf(
                    verifiedEntrance(id = "tight", maxHeightMm = 2200),
                    verifiedEntrance(id = "roomy", maxHeightMm = 2700),
                ),
            ),
            WEDNESDAY_1PM,
        )

        assertEquals(listOf("roomy", "tight"), analysis.entrances.map { it.entrance.id })
        assertEquals(2, analysis.matchCount)
    }

    @Test
    fun `facility without entrances reports nothing to recommend`() {
        val analysis = analyzeFacility(suvWithRoofBox(), FacilityWithEntrances(facility, emptyList()), WEDNESDAY_1PM)

        assertTrue(analysis.entrances.isEmpty())
        assertFalse(analysis.hasNoMatch)
    }
}
