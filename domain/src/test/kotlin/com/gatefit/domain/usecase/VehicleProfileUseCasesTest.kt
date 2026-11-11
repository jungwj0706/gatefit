package com.gatefit.domain.usecase

import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.SpecSource
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleModelSpec
import com.gatefit.domain.model.VehicleProfile
import com.gatefit.domain.testing.FakeVehicleProfileRepository
import com.gatefit.domain.testing.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VehicleProfileUseCasesTest {

    private val repository = FakeVehicleProfileRepository()
    private val save = SaveVehicleProfileUseCase(repository) { 1_000L }
    private val applySpec = ApplyVehicleModelSpecUseCase()

    private fun spec(sourceUrl: String? = "https://example.com/catalog.pdf") = VehicleModelSpec(
        id = "spec",
        manufacturer = "제조사",
        modelName = "모델",
        modelYear = null,
        trimName = "루프랙 적용",
        dimensions = VehicleDimensions(lengthMm = 4830, widthMm = 1900, heightMm = 1770),
        sourceName = "공식 카탈로그",
        sourceUrl = sourceUrl,
        sourceVerifiedAt = LocalDate.of(2026, 11, 11),
    )

    @Test
    fun `valid profile is stored with the save time`() = runTest {
        val result = save(Fixtures.suvWithRoofBox())

        assertTrue(result is SaveProfileResult.Saved)
        assertEquals(1_000L, repository.profile.value?.updatedAtEpochMs)
    }

    @Test
    fun `profile with a centimetre height is rejected and not stored`() = runTest {
        val result = save(Fixtures.suvWithRoofBox(height = 165))

        val issues = (result as SaveProfileResult.Rejected).issues
        assertEquals(listOf(ProfileField.HEIGHT), issues.map { it.field })
        assertNull(repository.profile.value)
    }

    @Test
    fun `sourced spec is applied as manufacturer verified`() {
        val profile = applySpec(VehicleProfile(id = "vehicle", modelYear = 2025), spec())

        assertEquals(1770, profile.baseDimensions.heightMm)
        assertEquals(SpecSource.MANUFACTURER, profile.specSource)
        assertEquals(SpecConfidence.VERIFIED_BY_MANUFACTURER, profile.specConfidence)
        assertEquals("https://example.com/catalog.pdf", profile.specSourceUrl)
        assertEquals(2025, profile.modelYear)
    }

    @Test
    fun `spec without a source is only an estimate and cannot be used for analysis`() {
        val profile = applySpec(VehicleProfile(id = "vehicle"), spec(sourceUrl = null))

        assertEquals(SpecConfidence.USER_ESTIMATED, profile.specConfidence)
        assertEquals(false, profile.specConfidence.usableForAnalysis)
    }

    @Test
    fun `applying a spec keeps accessories and safety margin`() {
        val original = Fixtures.suvWithRoofBox(margin = 150)

        val profile = applySpec(original, spec())

        assertEquals(original.accessories, profile.accessories)
        assertEquals(150, profile.safetyMarginMm)
    }
}
