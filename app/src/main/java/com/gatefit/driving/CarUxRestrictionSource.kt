package com.gatefit.driving

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import com.gatefit.domain.model.DrivingRestrictions

internal class CarUxRestrictionSource(
    private val context: Context,
    private val onChanged: (DrivingRestrictions) -> Unit,
) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        val connected: Car? = Car.createCar(context)
        car = connected
        val restrictionsManager = connected?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (restrictionsManager == null) {
            onChanged(DrivingRestrictions.RESTRICTED_UNAVAILABLE)
            return
        }
        manager = restrictionsManager
        restrictionsManager.registerListener { restrictions -> publish(restrictions) }
        publish(restrictionsManager.currentCarUxRestrictions)
    }

    fun stop() {
        try {
            manager?.unregisterListener()
            car?.disconnect()
        } catch (_: Exception) {
            // 이미 끊어진 차량 서비스 연결을 해제할 때의 오류는 복구할 대상이 없음
        }
        manager = null
        car = null
    }

    private fun publish(restrictions: CarUxRestrictions?) {
        onChanged(
            if (restrictions == null) {
                DrivingRestrictions.RESTRICTED_UNAVAILABLE
            } else {
                UxRestrictionMapper.map(restrictions.isRequiresDistractionOptimization, restrictions.activeRestrictions)
            }
        )
    }
}
