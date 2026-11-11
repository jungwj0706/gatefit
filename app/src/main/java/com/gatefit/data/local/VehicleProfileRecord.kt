package com.gatefit.data.local

import com.gatefit.data.mapper.enumOrDefault
import com.gatefit.domain.model.AccessoryType
import com.gatefit.domain.model.SpecConfidence
import com.gatefit.domain.model.SpecSource
import com.gatefit.domain.model.VehicleAccessory
import com.gatefit.domain.model.VehicleDimensions
import com.gatefit.domain.model.VehicleProfile
import kotlinx.serialization.Serializable

// 기기에 저장되는 차량 프로필 형식, VIN·CRN 등 차량 식별 정보는 필드 자체를 두지 않음
@Serializable
data class VehicleProfileRecord(
    val id: String,
    val manufacturer: String? = null,
    val modelName: String? = null,
    val modelYear: Int? = null,
    val trimName: String? = null,
    val lengthMm: Int? = null,
    val widthMm: Int? = null,
    val heightMm: Int? = null,
    val specSource: String = SpecSource.USER.name,
    val specConfidence: String = SpecConfidence.UNKNOWN.name,
    val specSourceName: String? = null,
    val specSourceUrl: String? = null,
    val accessories: List<AccessoryRecord> = emptyList(),
    val heightCorrectionMm: Int = 0,
    val measuredTotalHeightMm: Int? = null,
    val weightKg: Int? = null,
    val safetyMarginMm: Int = VehicleProfile.DEFAULT_SAFETY_MARGIN_MM,
    val demo: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

@Serializable
data class AccessoryRecord(
    val id: String,
    val type: String,
    val additionalHeightMm: Int = 0,
    val additionalWidthMm: Int = 0,
    val additionalLengthMm: Int = 0,
    val verified: Boolean = false,
    val updatedAtEpochMs: Long = 0L,
)

fun VehicleProfile.toRecord() = VehicleProfileRecord(
    id = id,
    manufacturer = manufacturer,
    modelName = modelName,
    modelYear = modelYear,
    trimName = trimName,
    lengthMm = baseDimensions.lengthMm,
    widthMm = baseDimensions.widthMm,
    heightMm = baseDimensions.heightMm,
    specSource = specSource.name,
    specConfidence = specConfidence.name,
    specSourceName = specSourceName,
    specSourceUrl = specSourceUrl,
    accessories = accessories.map {
        AccessoryRecord(
            id = it.id,
            type = it.type.name,
            additionalHeightMm = it.additionalHeightMm,
            additionalWidthMm = it.additionalWidthMm,
            additionalLengthMm = it.additionalLengthMm,
            verified = it.verified,
            updatedAtEpochMs = it.updatedAtEpochMs,
        )
    },
    heightCorrectionMm = heightCorrectionMm,
    measuredTotalHeightMm = measuredTotalHeightMm,
    weightKg = weightKg,
    safetyMarginMm = safetyMarginMm,
    demo = demo,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun VehicleProfileRecord.toDomain() = VehicleProfile(
    id = id,
    manufacturer = manufacturer,
    modelName = modelName,
    modelYear = modelYear,
    trimName = trimName,
    baseDimensions = VehicleDimensions(lengthMm = lengthMm, widthMm = widthMm, heightMm = heightMm),
    specSource = enumOrDefault(specSource, SpecSource.USER),
    // 알 수 없는 신뢰 등급은 분석에 쓸 수 없는 미확인 등급으로 처리
    specConfidence = enumOrDefault(specConfidence, SpecConfidence.UNKNOWN),
    specSourceName = specSourceName,
    specSourceUrl = specSourceUrl,
    accessories = accessories.map {
        VehicleAccessory(
            id = it.id,
            type = enumOrDefault(it.type, AccessoryType.OTHER),
            additionalHeightMm = it.additionalHeightMm,
            additionalWidthMm = it.additionalWidthMm,
            additionalLengthMm = it.additionalLengthMm,
            verified = it.verified,
            updatedAtEpochMs = it.updatedAtEpochMs,
        )
    },
    heightCorrectionMm = heightCorrectionMm,
    measuredTotalHeightMm = measuredTotalHeightMm,
    weightKg = weightKg,
    safetyMarginMm = safetyMarginMm,
    demo = demo,
    updatedAtEpochMs = updatedAtEpochMs,
)
