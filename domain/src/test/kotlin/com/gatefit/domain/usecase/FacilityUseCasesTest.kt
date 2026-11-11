package com.gatefit.domain.usecase

import com.gatefit.domain.model.CompatibilityStatus
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.FacilityWithEntrances
import com.gatefit.domain.testing.FakeFacilityRepository
import com.gatefit.domain.testing.FakeSavedPlacesRepository
import com.gatefit.domain.testing.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FacilityUseCasesTest {

    private val mall = FacilityWithEntrances(
        FakeFacilityRepository.facility("mall", "복합쇼핑몰"),
        listOf(
            Fixtures.verifiedEntrance(id = "low", maxHeightMm = 2000),
            Fixtures.verifiedEntrance(id = "high", maxHeightMm = 2300),
        ),
    )
    private val facilities = FakeFacilityRepository(listOf(mall))
    private val savedPlaces = FakeSavedPlacesRepository()

    @Test
    fun `search trims the query text`() = runTest {
        val results = SearchFacilitiesUseCase(facilities)("  쇼핑몰 ", FacilityCategory.SHOPPING)

        assertEquals(listOf("mall"), results.map { it.facility.id })
        assertEquals("쇼핑몰", facilities.queries.single().text)
        assertEquals(FacilityCategory.SHOPPING, facilities.queries.single().category)
    }

    @Test
    fun `facility entrances are analysed for the given vehicle`() = runTest {
        val engine = Fixtures.engine()
        val getEntrances = GetFacilityEntrancesUseCase(
            facilities,
            AnalyzeFacilityEntrancesUseCase(CalculateEffectiveVehicleDimensionsUseCase(), engine),
        )

        val analysis = getEntrances("mall", Fixtures.suvWithRoofBox(), Fixtures.WEDNESDAY_1PM)!!

        val statuses = analysis.entrances.associate { it.entrance.id to it.result.status }
        assertEquals(CompatibilityStatus.CONDITION_MISMATCH, statuses["low"])
        assertEquals(CompatibilityStatus.CONDITION_MATCH, statuses["high"])
    }

    @Test
    fun `unknown facility has no analysis`() = runTest {
        val getEntrances = GetFacilityEntrancesUseCase(
            facilities,
            AnalyzeFacilityEntrancesUseCase(CalculateEffectiveVehicleDimensionsUseCase(), Fixtures.engine()),
        )

        assertNull(getEntrances("missing", Fixtures.suvWithRoofBox(), Fixtures.WEDNESDAY_1PM))
    }

    @Test
    fun `recent facilities follow the recorded order and skip removed facilities`() = runTest {
        facilities.stored["park"] = FacilityWithEntrances(FakeFacilityRepository.facility("park"), emptyList())
        savedPlaces.recordRecent("mall")
        savedPlaces.recordRecent("removed")
        savedPlaces.recordRecent("park")

        val recent = GetRecentFacilitiesUseCase(savedPlaces, facilities)().first()

        assertEquals(listOf("park", "mall"), recent.map { it.facility.id })
    }

    @Test
    fun `favorites can be toggled`() = runTest {
        val toggle = ToggleFavoriteFacilityUseCase(savedPlaces)
        val favorites = GetFavoriteFacilitiesUseCase(savedPlaces, facilities)

        toggle("mall", true)
        assertEquals(listOf("mall"), favorites().first().map { it.facility.id })

        toggle("mall", false)
        assertEquals(emptyList<String>(), favorites().first())
    }
}
