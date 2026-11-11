package com.gatefit.driving

import com.gatefit.domain.model.DrivingRestrictions
import com.gatefit.domain.model.RestrictionSource

// CarUxRestrictions 비트 값을 앱의 기능 제한으로 변환, 상수 값은 android.car.drivingstate.CarUxRestrictions와 동일
object UxRestrictionMapper {
    const val NO_KEYBOARD = 0x1 shl 3
    const val LIMIT_CONTENT = 0x1 shl 5
    const val NO_SETUP = 0x1 shl 6

    fun map(requiresDistractionOptimization: Boolean, activeRestrictions: Int): DrivingRestrictions =
        DrivingRestrictions(
            source = RestrictionSource.CAR_UX_RESTRICTIONS,
            requiresDistractionOptimization = requiresDistractionOptimization,
            keyboardBlocked = activeRestrictions has NO_KEYBOARD,
            setupBlocked = activeRestrictions has NO_SETUP,
            contentLimited = activeRestrictions has LIMIT_CONTENT,
        )

    private infix fun Int.has(flag: Int): Boolean = this and flag == flag
}
