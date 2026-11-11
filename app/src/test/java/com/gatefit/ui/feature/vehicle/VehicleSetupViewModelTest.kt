package com.gatefit.ui.feature.vehicle

import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.HeightBasis
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.SpecSource
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.usecase.ApplyVehicleModelSpecUseCase
import com.gatefit.domain.usecase.GetVehicleInformationUseCase
import com.gatefit.domain.usecase.ProfileField
import com.gatefit.domain.usecase.SaveVehicleProfileUseCase
import com.gatefit.ui.testing.FakeVehicleInfoRepository
import com.gatefit.ui.testing.MainDispatcherRule
import com.gatefit.ui.testing.TestEnvironment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VehicleSetupViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private val environment = TestEnvironment()
    private var idSequence = 0

    private fun TestScope.viewModel(): VehicleSetupViewModel {
        val viewModel = VehicleSetupViewModel(
            profiles = environment.profiles,
            specs = environment.specs,
            settings = environment.settings,
            vehicleInfo = environment.vehicleInfo,
            getVehicleInformation = GetVehicleInformationUseCase(environment.vehicleInfo),
            saveProfile = SaveVehicleProfileUseCase(environment.profiles) { 1_000L },
            applySpec = ApplyVehicleModelSpecUseCase(),
            calculateEffectiveDimensions = environment.calculateEffectiveDimensions,
            newId = { "id-${++idSequence}" },
        )
        // 화면이 구독 중인 상태와 같게 유지
        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private suspend fun VehicleSetupViewModel.firstSpec(query: String) = run {
        onQueryChange(query)
        state.first { it.query == query && it.specs.isNotEmpty() }.specs.first()
    }

    @Test
    fun `catalog model applies manufacturer dimensions with verified confidence`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        val spec = viewModel.firstSpec("팰리세이드")

        viewModel.selectSpec(spec)
        viewModel.save()
        advanceUntilIdle()

        val saved = environment.profiles.profile.value
        assertNotNull(saved)
        assertEquals(spec.dimensions, saved?.baseDimensions)
        assertEquals(SpecSource.MANUFACTURER, saved?.specSource)
        assertEquals(SpecConfidence.VERIFIED_BY_MANUFACTURER, saved?.specConfidence)
        assertEquals(spec.sourceUrl, saved?.specSourceUrl)
        assertTrue(environment.settings.settings.value.onboardingCompleted)
        assertTrue(viewModel.state.value.saved)
    }

    @Test
    fun `manual dimensions stay estimated until the user confirms them`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.switchToManual()
        viewModel.onHeightChange("1950")
        advanceUntilIdle()

        assertEquals(SpecConfidence.USER_ESTIMATED, viewModel.state.value.previewConfidence)
        assertFalse(viewModel.state.value.preview.usableForAnalysis)

        viewModel.onManualConfirmedChange(true)
        advanceUntilIdle()

        assertEquals(SpecConfidence.USER_CONFIRMED, viewModel.state.value.previewConfidence)
        assertTrue(viewModel.state.value.preview.usableForAnalysis)
    }

    @Test
    fun `changing a confirmed manual value asks for confirmation again`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.switchToManual()
        viewModel.onHeightChange("1950")
        viewModel.onManualConfirmedChange(true)

        viewModel.onHeightChange("2050")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.form.manualConfirmed)
        assertEquals(SpecConfidence.USER_ESTIMATED, viewModel.state.value.previewConfidence)
    }

    @Test
    fun `switching a catalog model to manual input drops the manufacturer grade`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.selectSpec(viewModel.firstSpec("싼타페"))

        viewModel.switchToManual()
        advanceUntilIdle()

        assertEquals(SpecConfidence.USER_ESTIMATED, viewModel.state.value.previewConfidence)
    }

    @Test
    fun `accessory type alone adds no height until a dimension is entered`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.switchToManual()
        viewModel.onHeightChange("1700")
        viewModel.addAccessory(AccessoryType.ROOF_BOX)
        advanceUntilIdle()
        assertEquals(1700, viewModel.state.value.preview.heightMm)

        val accessory = viewModel.state.value.form.accessories.single()
        viewModel.updateAccessory(accessory.copy(height = "450"))
        advanceUntilIdle()

        assertEquals(2150, viewModel.state.value.preview.heightMm)
        assertEquals(2250, viewModel.state.value.preview.requiredHeightMm(viewModel.state.value.form.safetyMarginMm))
    }

    @Test
    fun `measured total height replaces the calculated height without double counting`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.switchToManual()
        viewModel.onHeightChange("1700")
        viewModel.addAccessory(AccessoryType.ROOF_BOX)
        advanceUntilIdle()
        viewModel.updateAccessory(viewModel.state.value.form.accessories.single().copy(height = "450"))

        viewModel.onMeasuredHeightChange("2120")
        advanceUntilIdle()

        assertEquals(2120, viewModel.state.value.preview.heightMm)
        assertEquals(HeightBasis.MEASURED_TOTAL, viewModel.state.value.preview.heightBasis)
    }

    @Test
    fun `out of range values are rejected and not saved`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()
        viewModel.switchToManual()
        viewModel.onHeightChange("195")

        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf(ProfileField.HEIGHT), viewModel.state.value.issues.map { it.field })
        assertNull(environment.profiles.profile.value)
        assertFalse(environment.settings.settings.value.onboardingCompleted)
    }

    @Test
    fun `profile cannot be saved without any height`() = runTest(mainRule.dispatcher) {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `vehicle hint prefills the model search but selects nothing by itself`() = runTest(mainRule.dispatcher) {
        environment.vehicleInfo.connectResult = FakeVehicleInfoRepository.connected()
        environment.vehicleInfo.connect()
        environment.vehicleInfo.hintResult = FakeVehicleInfoRepository.hint("현대자동차", 2025)
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.canReadFromVehicle)

        viewModel.readFromVehicle()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("현대자동차", state.query)
        assertEquals(2025, state.form.modelYear)
        assertNull(state.form.selectedSpec)
        assertNull(state.preview.heightMm)
        assertTrue(state.specs.all { it.manufacturer == "현대자동차" })
    }

    @Test
    fun `failed vehicle read keeps manual registration available`() = runTest(mainRule.dispatcher) {
        environment.vehicleInfo.connectResult = FakeVehicleInfoRepository.connected()
        environment.vehicleInfo.connect()
        environment.vehicleInfo.hintResult = VehicleInfoResult.Failed(VehicleLinkFailure.NO_DATA)
        val viewModel = viewModel()

        viewModel.readFromVehicle()
        advanceUntilIdle()

        assertEquals(VehicleReadState.Failed(VehicleLinkFailure.NO_DATA), viewModel.state.value.readState)
        assertNull(viewModel.state.value.form.modelYear)
    }

    @Test
    fun `saved catalog profile is restored and keeps its grade when only the margin changes`() =
        runTest(mainRule.dispatcher) {
            val first = viewModel()
            val spec = first.firstSpec("스타리아")
            first.selectSpec(spec)
            first.save()
            advanceUntilIdle()

            val second = viewModel()
            assertTrue(second.state.value.hasCatalogSelection)
            second.onSafetyMarginChange(200)
            second.save()
            advanceUntilIdle()

            val saved = environment.profiles.profile.value
            assertEquals(SpecConfidence.VERIFIED_BY_MANUFACTURER, saved?.specConfidence)
            assertEquals(spec.dimensions, saved?.baseDimensions)
            assertEquals(spec.sourceName, saved?.specSourceName)
            assertEquals(200, saved?.safetyMarginMm)
        }
}
