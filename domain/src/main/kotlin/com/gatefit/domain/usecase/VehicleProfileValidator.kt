package com.gatefit.domain.usecase

import com.gatefit.domain.model.VehicleProfile

enum class ProfileField {
    HEIGHT,
    WIDTH,
    LENGTH,
    WEIGHT,
    MEASURED_HEIGHT,
    HEIGHT_CORRECTION,
    SAFETY_MARGIN,
    ACCESSORY_HEIGHT,
    ACCESSORY_WIDTH,
    ACCESSORY_LENGTH,
}

data class ProfileIssue(val field: ProfileField, val min: Int, val max: Int)

// 단위 오입력(cm, m)을 걸러내기 위한 승용·승합·캠핑카 범위 검사
object VehicleProfileValidator {
    val HEIGHT_RANGE = 1000..4500
    val WIDTH_RANGE = 1000..2600
    val LENGTH_RANGE = 2000..13000
    val WEIGHT_RANGE = 300..40000
    val CORRECTION_RANGE = -300..1000
    val SAFETY_MARGIN_RANGE = 0..VehicleProfile.MAX_SAFETY_MARGIN_MM
    val ACCESSORY_RANGE = 0..2500

    fun validate(profile: VehicleProfile): List<ProfileIssue> = buildList {
        check(profile.baseDimensions.heightMm, ProfileField.HEIGHT, HEIGHT_RANGE)
        check(profile.baseDimensions.widthMm, ProfileField.WIDTH, WIDTH_RANGE)
        check(profile.baseDimensions.lengthMm, ProfileField.LENGTH, LENGTH_RANGE)
        check(profile.weightKg, ProfileField.WEIGHT, WEIGHT_RANGE)
        check(profile.measuredTotalHeightMm, ProfileField.MEASURED_HEIGHT, HEIGHT_RANGE)
        check(profile.heightCorrectionMm, ProfileField.HEIGHT_CORRECTION, CORRECTION_RANGE)
        check(profile.safetyMarginMm, ProfileField.SAFETY_MARGIN, SAFETY_MARGIN_RANGE)
        profile.accessories.forEach { accessory ->
            check(accessory.additionalHeightMm, ProfileField.ACCESSORY_HEIGHT, ACCESSORY_RANGE)
            check(accessory.additionalWidthMm, ProfileField.ACCESSORY_WIDTH, ACCESSORY_RANGE)
            check(accessory.additionalLengthMm, ProfileField.ACCESSORY_LENGTH, ACCESSORY_RANGE)
        }
    }.distinct()

    private fun MutableList<ProfileIssue>.check(value: Int?, field: ProfileField, range: IntRange) {
        if (value != null && value !in range) add(ProfileIssue(field, range.first, range.last))
    }
}
