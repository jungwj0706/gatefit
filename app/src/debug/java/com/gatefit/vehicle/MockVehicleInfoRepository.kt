package com.gatefit.vehicle

import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.VehicleCapabilities
import com.gatefit.domain.model.VehicleIdentityHint
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.repository.VehicleInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 디버그 소스셋 전용 개발용 차량 공급자, 모든 결과에 DEMO 표시를 달아 실제 차량 정보와 구분
class MockVehicleInfoRepository : VehicleInfoRepository {

    private val state = MutableStateFlow(VehicleLinkStatus(demo = true))
    override val status: StateFlow<VehicleLinkStatus> = state.asStateFlow()

    override suspend fun connect(): VehicleLinkStatus {
        state.value = VehicleLinkStatus(
            state = VehicleLinkState.CONNECTED,
            capabilities = VehicleCapabilities(vinInfo = CapabilitySupport.SUPPORTED),
            demo = true,
        )
        return state.value
    }

    override suspend fun readIdentityHint(): VehicleInfoResult =
        VehicleInfoResult.Available(VehicleIdentityHint(manufacturer = "현대자동차", modelYear = 2025, demo = true))

    override fun disconnect() {
        state.value = VehicleLinkStatus(demo = true)
    }
}
