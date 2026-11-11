package com.gatefit.domain.usecase

import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.VehicleCapabilities
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.repository.VehicleInfoRepository

class InitializeVehicleSdkUseCase(private val vehicle: VehicleInfoRepository) {
    suspend operator fun invoke(): VehicleLinkStatus = vehicle.connect()
}

class CheckVehicleCapabilitiesUseCase(private val vehicle: VehicleInfoRepository) {
    // 연결되지 않은 상태에서는 지원 여부를 알 수 없으므로 미지원으로 단정하지 않음
    operator fun invoke(): VehicleCapabilities {
        val status = vehicle.status.value
        return if (status.state == VehicleLinkState.CONNECTED) {
            status.capabilities
        } else {
            VehicleCapabilities(vinInfo = CapabilitySupport.UNKNOWN)
        }
    }
}

class GetVehicleInformationUseCase(private val vehicle: VehicleInfoRepository) {
    suspend operator fun invoke(): VehicleInfoResult {
        val status = vehicle.status.value
        if (status.state != VehicleLinkState.CONNECTED) {
            return VehicleInfoResult.Failed(status.failure ?: VehicleLinkFailure.CONNECTION_FAILED)
        }
        if (status.capabilities.vinInfo == CapabilitySupport.UNSUPPORTED) return VehicleInfoResult.Unsupported
        return vehicle.readIdentityHint()
    }
}
