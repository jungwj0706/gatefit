package com.gatefit.driving

import com.gatefit.domain.model.RestrictedFeature
import com.gatefit.domain.model.RestrictionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UxRestrictionMapperTest {

    @Test
    fun `baseline restrictions while parked allow every feature`() {
        val parked = UxRestrictionMapper.map(requiresDistractionOptimization = false, activeRestrictions = 0)

        assertEquals(RestrictionSource.CAR_UX_RESTRICTIONS, parked.source)
        assertTrue(RestrictedFeature.entries.all(parked::allows))
    }

    @Test
    fun `fully restricted driving state blocks every restricted feature`() {
        val fullyRestricted = 511

        val driving = UxRestrictionMapper.map(requiresDistractionOptimization = true, activeRestrictions = fullyRestricted)

        assertTrue(driving.keyboardBlocked)
        assertTrue(driving.setupBlocked)
        assertTrue(driving.contentLimited)
        assertTrue(RestrictedFeature.entries.none(driving::allows))
    }

    @Test
    fun `each restriction bit maps to its own flag`() {
        val noKeyboard = UxRestrictionMapper.map(false, 8)
        val limitContent = UxRestrictionMapper.map(false, 32)
        val noSetup = UxRestrictionMapper.map(false, 64)
        val unrelated = UxRestrictionMapper.map(false, 1 or 2 or 4 or 16 or 128 or 256)

        assertTrue(noKeyboard.keyboardBlocked && !noKeyboard.setupBlocked && !noKeyboard.contentLimited)
        assertTrue(limitContent.contentLimited && !limitContent.keyboardBlocked && !limitContent.setupBlocked)
        assertTrue(noSetup.setupBlocked && !noSetup.keyboardBlocked && !noSetup.contentLimited)
        assertFalse(unrelated.keyboardBlocked || unrelated.setupBlocked || unrelated.contentLimited)
    }
}
