package com.gatefit.domain.usecase

import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.VehicleAccessory
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleProfileValidatorTest {

    private fun profile(height: Int? = 1650, margin: Int = 100, accessoryHeight: Int = 0) = VehicleProfile(
        id = "test",
        baseDimensions = VehicleDimensions(lengthMm = 4800, widthMm = 1900, heightMm = height),
        safetyMarginMm = margin,
        accessories = listOf(
            VehicleAccessory(id = "a", type = AccessoryType.ROOF_BOX, additionalHeightMm = accessoryHeight),
        ),
    )

    @Test
    fun `plausible profile has no issues`() {
        assertTrue(VehicleProfileValidator.validate(profile()).isEmpty())
    }

    @Test
    fun `height entered in centimeters is rejected`() {
        val issues = VehicleProfileValidator.validate(profile(height = 165))

        assertEquals(listOf(ProfileField.HEIGHT), issues.map { it.field })
    }

    @Test
    fun `missing dimensions are allowed and reported elsewhere as unknown`() {
        assertTrue(VehicleProfileValidator.validate(profile(height = null)).isEmpty())
    }

    @Test
    fun `safety margin and accessory size are bounded`() {
        val issues = VehicleProfileValidator.validate(profile(margin = 900, accessoryHeight = 4000))

        assertEquals(setOf(ProfileField.SAFETY_MARGIN, ProfileField.ACCESSORY_HEIGHT), issues.map { it.field }.toSet())
    }

    @Test
    fun `boundary values are accepted`() {
        assertTrue(VehicleProfileValidator.validate(profile(height = 1000, margin = 0)).isEmpty())
        assertTrue(VehicleProfileValidator.validate(profile(height = 4500, margin = 500, accessoryHeight = 2500)).isEmpty())
    }
}
