package com.gatefit.vehicle

import com.gatefit.data.local.KeyValueStore
import com.gatefit.domain.repository.VehicleInfoRepository

// 디버그 빌드용 구현, 릴리스 소스셋의 같은 이름 객체는 항상 Mock을 제공하지 않음
object DevVehicleProviders {
    const val MOCK_SUPPORTED = true
    private const val KEY = "dev_mock_vehicle"

    fun isMockEnabled(store: KeyValueStore): Boolean = store.getString(KEY) == "true"

    fun setMockEnabled(store: KeyValueStore, enabled: Boolean) {
        store.putString(KEY, if (enabled) "true" else null)
    }

    fun mockOrNull(store: KeyValueStore): VehicleInfoRepository? =
        if (isMockEnabled(store)) MockVehicleInfoRepository() else null
}
