package com.gatefit.domain.usecase

import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.HeightBasis
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.VehicleAccessory
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateEffectiveVehicleDimensionsUseCaseTest {

    private val calculate = CalculateEffectiveVehicleDimensionsUseCase()

    private fun profile(
        height: Int? = 1650,
        width: Int? = 1900,
        length: Int? = 4800,
        confidence: SpecConfidence = SpecConfidence.USER_CONFIRMED,
        accessories: List<VehicleAccessory> = emptyList(),
        correction: Int = 0,
        measured: Int? = null,
    ) = VehicleProfile(
        id = "test",
        baseDimensions = VehicleDimensions(lengthMm = length, widthMm = width, heightMm = height),
        specConfidence = confidence,
        accessories = accessories,
        heightCorrectionMm = correction,
        measuredTotalHeightMm = measured,
    )

    private fun accessory(type: AccessoryType, height: Int = 0, width: Int = 0, length: Int = 0) =
        VehicleAccessory(id = type.name, type = type, additionalHeightMm = height, additionalWidthMm = width, additionalLengthMm = length)

    @Test
    fun `factory height is used when no accessory is mounted`() {
        val result = calculate(profile())

        assertEquals(1650, result.heightMm)
        assertEquals(HeightBasis.FACTORY_WITH_ACCESSORIES, result.heightBasis)
        assertEquals(0, result.accessoryHeightMm)
    }

    @Test
    fun `roof box height is added to factory height`() {
        val result = calculate(profile(accessories = listOf(accessory(AccessoryType.ROOF_BOX, height = 450))))

        assertEquals(2100, result.heightMm)
        assertEquals(2200, result.requiredHeightMm(100))
    }

    @Test
    fun `tallest accessory decides the added height`() {
        val result = calculate(
            profile(
                accessories = listOf(
                    accessory(AccessoryType.ROOF_BOX, height = 450),
                    accessory(AccessoryType.BIKE_CARRIER, height = 900),
                ),
            ),
        )

        assertEquals(2550, result.heightMm)
        assertEquals(900, result.accessoryHeightMm)
    }

    @Test
    fun `length additions are summed and width uses the widest accessory`() {
        val result = calculate(
            profile(
                accessories = listOf(
                    accessory(AccessoryType.BIKE_CARRIER, length = 600, width = 100),
                    accessory(AccessoryType.EXTRA_CARGO, length = 300, width = 40),
                ),
            ),
        )

        assertEquals(5700, result.lengthMm)
        assertEquals(2000, result.widthMm)
    }

    @Test
    fun `user correction is applied on top of accessories`() {
        val result = calculate(
            profile(accessories = listOf(accessory(AccessoryType.ROOF_BOX, height = 450)), correction = 30),
        )

        assertEquals(2130, result.heightMm)
    }

    @Test
    fun `measured total height replaces factory and accessory sum`() {
        val result = calculate(
            profile(
                accessories = listOf(accessory(AccessoryType.ROOF_BOX, height = 450)),
                correction = 30,
                measured = 2085,
            ),
        )

        assertEquals(2085, result.heightMm)
        assertEquals(HeightBasis.MEASURED_TOTAL, result.heightBasis)
    }

    @Test
    fun `measured total height makes an unconfirmed profile usable`() {
        val result = calculate(profile(height = null, confidence = SpecConfidence.UNKNOWN, measured = 2085))

        assertEquals(2085, result.heightMm)
        assertTrue(result.usableForAnalysis)
    }

    @Test
    fun `missing factory height yields unavailable height`() {
        val result = calculate(profile(height = null, confidence = SpecConfidence.UNKNOWN))

        assertNull(result.heightMm)
        assertEquals(HeightBasis.UNAVAILABLE, result.heightBasis)
        assertNull(result.requiredHeightMm(100))
    }

    @Test
    fun `estimated specs are not usable for analysis`() {
        assertFalse(calculate(profile(confidence = SpecConfidence.USER_ESTIMATED)).usableForAnalysis)
        assertFalse(calculate(profile(confidence = SpecConfidence.UNKNOWN)).usableForAnalysis)
        assertTrue(calculate(profile(confidence = SpecConfidence.VERIFIED_BY_MANUFACTURER)).usableForAnalysis)
    }

    @Test
    fun `negative accessory values never shrink the vehicle`() {
        val result = calculate(profile(accessories = listOf(accessory(AccessoryType.OTHER, height = -200, length = -50))))

        assertEquals(1650, result.heightMm)
        assertEquals(4800, result.lengthMm)
    }
}
