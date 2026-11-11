package com.gatefit.domain.vehicle

import com.gatefit.domain.model.VehicleIdentityHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class VinHintDecoderTest {

    // 형식만 맞춘 가상 VIN, 실제 차량과 무관
    private fun vin(wmi: String = "KMH", yearCode: Char = 'P') = "${wmi}XX00XX${yearCode}U000000"

    @Test
    fun `manufacturer and model year are decoded from their code positions`() {
        assertEquals(VehicleIdentityHint("현대자동차", 2023), VinHintDecoder.decode(vin("KMH", 'P'), 2026))
        assertEquals(VehicleIdentityHint("기아", 2025), VinHintDecoder.decode(vin("KNA", 'S'), 2026))
        assertEquals(VehicleIdentityHint("제네시스", 2009), VinHintDecoder.decode(vin("KMT", '9'), 2026))
    }

    @Test
    fun `repeating year code resolves to the latest year not after next year`() {
        assertEquals(2010, VinHintDecoder.decode(vin(yearCode = 'A'), 2026)?.modelYear)
        assertEquals(2027, VinHintDecoder.decode(vin(yearCode = 'V'), 2026)?.modelYear)
        assertEquals(1998, VinHintDecoder.decode(vin(yearCode = 'W'), 2026)?.modelYear)
        assertEquals(2040, VinHintDecoder.decode(vin(yearCode = 'A'), 2040)?.modelYear)
    }

    @Test
    fun `unknown manufacturer code keeps the year but does not guess a manufacturer`() {
        assertEquals(VehicleIdentityHint(null, 2023), VinHintDecoder.decode(vin("ZZZ", 'P'), 2026))
    }

    @Test
    fun `invalid year code keeps the manufacturer only`() {
        assertEquals(VehicleIdentityHint("현대자동차", null), VinHintDecoder.decode(vin(yearCode = 'U'), 2026))
        assertEquals(VehicleIdentityHint("현대자동차", null), VinHintDecoder.decode(vin(yearCode = '0'), 2026))
    }

    @Test
    fun `lower case and surrounding spaces are accepted`() {
        assertEquals(VehicleIdentityHint("현대자동차", 2023), VinHintDecoder.decode("  ${vin().lowercase()} ", 2026))
    }

    @Test
    fun `malformed values give no hint`() {
        assertNull(VinHintDecoder.decode("", 2026))
        assertNull(VinHintDecoder.decode("KMH123", 2026))
        assertNull(VinHintDecoder.decode(vin() + "1", 2026))
        assertNull(VinHintDecoder.decode(vin().replace('X', 'O'), 2026))
        assertNull(VinHintDecoder.decode(vin("ZZZ", 'U'), 2026))
    }

    @Test
    fun `hint carries nothing that identifies an individual vehicle`() {
        val source = vin()

        val hint = VinHintDecoder.decode(source, 2026).toString()

        assertFalse(source in hint)
        assertFalse(source.takeLast(6) in hint)
    }
}
