package com.gatefit.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingRestrictionsTest {

    private fun restrictions(
        driving: Boolean = false,
        keyboardBlocked: Boolean = false,
        setupBlocked: Boolean = false,
        contentLimited: Boolean = false,
    ) = DrivingRestrictions(
        source = RestrictionSource.CAR_UX_RESTRICTIONS,
        requiresDistractionOptimization = driving,
        keyboardBlocked = keyboardBlocked,
        setupBlocked = setupBlocked,
        contentLimited = contentLimited,
    )

    @Test
    fun `parked vehicle allows every feature`() {
        val parked = restrictions()

        assertFalse(parked.restricted)
        assertTrue(RestrictedFeature.entries.all(parked::allows))
    }

    @Test
    fun `driving blocks search input profile editing reporting and detail browsing`() {
        val driving = restrictions(driving = true)

        assertTrue(driving.restricted)
        assertTrue(RestrictedFeature.entries.none(driving::allows))
    }

    @Test
    fun `individual platform restrictions block only the related features`() {
        val noKeyboard = restrictions(keyboardBlocked = true)
        val noSetup = restrictions(setupBlocked = true)
        val limited = restrictions(contentLimited = true)

        assertFalse(noKeyboard.allows(RestrictedFeature.FACILITY_SEARCH_INPUT))
        assertFalse(noKeyboard.allows(RestrictedFeature.REPORT_WRITING))
        assertTrue(noKeyboard.allows(RestrictedFeature.DETAIL_BROWSING))
        assertFalse(noSetup.allows(RestrictedFeature.VEHICLE_PROFILE_EDIT))
        assertTrue(noSetup.allows(RestrictedFeature.FACILITY_SEARCH_INPUT))
        assertFalse(limited.allows(RestrictedFeature.DETAIL_BROWSING))
    }

    @Test
    fun `unknown restriction state in a vehicle is treated as restricted`() {
        assertTrue(DrivingRestrictions.RESTRICTED_UNAVAILABLE.restricted)
        assertTrue(RestrictedFeature.entries.none(DrivingRestrictions.RESTRICTED_UNAVAILABLE::allows))
        assertFalse(DrivingRestrictions.UNRESTRICTED_NOT_AUTOMOTIVE.restricted)
    }
}
