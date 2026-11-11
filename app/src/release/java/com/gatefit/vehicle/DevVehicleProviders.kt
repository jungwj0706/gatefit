package com.gatefit.vehicle

import com.gatefit.data.local.KeyValueStore
import com.gatefit.domain.repository.VehicleInfoRepository

// 릴리스 빌드에는 개발용 Mock 차량 공급자가 포함되지 않음
object DevVehicleProviders {
    const val MOCK_SUPPORTED = false

    @Suppress("UNUSED_PARAMETER")
    fun isMockEnabled(store: KeyValueStore): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun setMockEnabled(store: KeyValueStore, enabled: Boolean) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun mockOrNull(store: KeyValueStore): VehicleInfoRepository? = null
}
