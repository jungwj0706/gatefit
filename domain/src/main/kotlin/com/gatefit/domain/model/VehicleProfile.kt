package com.gatefit.domain.model

enum class SpecSource { SDK, MANUFACTURER, USER }

enum class SpecConfidence {
    VERIFIED_BY_SDK,
    VERIFIED_BY_MANUFACTURER,
    USER_CONFIRMED,
    USER_ESTIMATED,
    UNKNOWN;

    // 추정·미확인 제원은 적합 판정에 사용 금지
    val usableForAnalysis: Boolean
        get() = this == VERIFIED_BY_SDK || this == VERIFIED_BY_MANUFACTURER || this == USER_CONFIRMED
}

data class VehicleDimensions(
    val lengthMm: Int? = null,
    val widthMm: Int? = null,
    val heightMm: Int? = null,
)

enum class AccessoryType {
    ROOF_BOX,
    ROOF_CARRIER,
    ROOFTOP_TENT,
    BIKE_CARRIER,
    EXTRA_CARGO,
    OTHER,
}

// 추가 치수는 모두 순정 차량 외형 대비 증가량 기준
data class VehicleAccessory(
    val id: String,
    val type: AccessoryType,
    val additionalHeightMm: Int = 0,
    val additionalWidthMm: Int = 0,
    val additionalLengthMm: Int = 0,
    val verified: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

data class VehicleProfile(
    val id: String,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelYear: Int? = null,
    val trimName: String? = null,
    val baseDimensions: VehicleDimensions = VehicleDimensions(),
    val specSource: SpecSource = SpecSource.USER,
    val specConfidence: SpecConfidence = SpecConfidence.UNKNOWN,
    val specSourceName: String? = null,
    val specSourceUrl: String? = null,
    val accessories: List<VehicleAccessory> = emptyList(),
    val heightCorrectionMm: Int = 0,
    val measuredTotalHeightMm: Int? = null,
    val weightKg: Int? = null,
    val safetyMarginMm: Int = DEFAULT_SAFETY_MARGIN_MM,
    val demo: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
) {
    val displayName: String?
        get() = listOfNotNull(manufacturer, modelName).joinToString(" ").ifBlank { null }

    companion object {
        // 서비스 내부의 보수적 판단 파라미터, 법적 안전 기준 아님
        const val DEFAULT_SAFETY_MARGIN_MM = 100
        const val MAX_SAFETY_MARGIN_MM = 500
    }
}

enum class HeightBasis {
    MEASURED_TOTAL,
    FACTORY_WITH_ACCESSORIES,
    UNAVAILABLE,
}

data class EffectiveDimensions(
    val heightMm: Int?,
    val widthMm: Int?,
    val lengthMm: Int?,
    val heightBasis: HeightBasis,
    val accessoryHeightMm: Int,
    val accessoryWidthMm: Int,
    val accessoryLengthMm: Int,
    val usableForAnalysis: Boolean,
) {
    fun requiredHeightMm(safetyMarginMm: Int): Int? = heightMm?.plus(safetyMarginMm)
}
