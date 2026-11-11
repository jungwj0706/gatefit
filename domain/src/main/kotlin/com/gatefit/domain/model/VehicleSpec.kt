package com.gatefit.domain.model

import java.time.LocalDate

// 출처 없는 레코드는 공식 제원으로 취급 금지
data class VehicleModelSpec(
    val id: String,
    val manufacturer: String,
    val modelName: String,
    val modelYear: Int?,
    val trimName: String?,
    val dimensions: VehicleDimensions,
    val sourceName: String?,
    val sourceUrl: String?,
    val sourceVerifiedAt: LocalDate?,
) {
    val hasSource: Boolean
        get() = !sourceName.isNullOrBlank() && !sourceUrl.isNullOrBlank() && sourceVerifiedAt != null
}
