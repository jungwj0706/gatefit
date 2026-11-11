package com.gatefit.domain.vehicle

import com.gatefit.domain.model.VehicleIdentityHint

// VIN에서 제조사 코드(1~3번째)와 연식 코드(10번째)만 해석, 차량 고유 일련번호는 사용하지 않음
object VinHintDecoder {
    private const val VIN_LENGTH = 17
    private const val YEAR_CODES = "ABCDEFGHJKLMNPRSTVWXY123456789"
    private const val YEAR_CYCLE = 30
    private const val FIRST_CODED_YEAR = 1980
    private val VIN_PATTERN = Regex("[A-HJ-NPR-Z0-9]{$VIN_LENGTH}")

    private val MANUFACTURERS = mapOf(
        "KMH" to "현대자동차",
        "KMF" to "현대자동차",
        "KMJ" to "현대자동차",
        "KMT" to "제네시스",
        "KNA" to "기아",
        "KNC" to "기아",
        "KND" to "기아",
    )

    // 해석 결과는 추정값이므로 사용자 확인 전에는 확정 정보로 취급 금지
    fun decode(vin: String, currentYear: Int): VehicleIdentityHint? {
        val normalized = vin.trim().uppercase()
        if (!VIN_PATTERN.matches(normalized)) return null

        val manufacturer = MANUFACTURERS[normalized.substring(0, 3)]
        val modelYear = modelYear(normalized[9], currentYear)
        if (manufacturer == null && modelYear == null) return null
        return VehicleIdentityHint(manufacturer = manufacturer, modelYear = modelYear)
    }

    // 연식 코드는 30년 주기로 반복되므로 내년 이하의 가장 최근 연도로 해석
    private fun modelYear(code: Char, currentYear: Int): Int? {
        val index = YEAR_CODES.indexOf(code)
        if (index < 0) return null
        val base = FIRST_CODED_YEAR + index
        val latest = currentYear + 1
        if (base > latest) return null
        return base + (latest - base) / YEAR_CYCLE * YEAR_CYCLE
    }
}
