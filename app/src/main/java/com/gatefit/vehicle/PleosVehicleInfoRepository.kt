package com.gatefit.vehicle

import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.repository.VehicleInfoRepository
import com.gatefit.domain.vehicle.VinHintDecoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 실제 Pleos Vehicle SDK에 연결하는 구현, 개발용 Mock 구현과 분리
class PleosVehicleInfoRepository(
    private val client: VehicleSdkClient,
    private val capabilityChecker: VehicleCapabilityChecker,
    private val currentYear: () -> Int,
) : VehicleInfoRepository {

    private val state = MutableStateFlow(VehicleLinkStatus())
    override val status: StateFlow<VehicleLinkStatus> = state.asStateFlow()

    private val connectLock = Mutex()

    override suspend fun connect(): VehicleLinkStatus {
        connectLock.withLock {
            if (state.value.state != VehicleLinkState.CONNECTED) establish()
        }
        return state.value
    }

    private suspend fun establish() {
        state.value = VehicleLinkStatus(state = VehicleLinkState.CONNECTING)

        if (!client.isServiceInstalled()) {
            state.value = VehicleLinkStatus(
                state = VehicleLinkState.SERVICE_UNAVAILABLE,
                failure = VehicleLinkFailure.SERVICE_NOT_INSTALLED,
            )
            return
        }
        try {
            client.initialize()
        } catch (_: SecurityException) {
            state.value = failed(VehicleLinkFailure.PERMISSION_DENIED)
            return
        } catch (_: Exception) {
            state.value = failed(VehicleLinkFailure.CONNECTION_FAILED)
            return
        }

        val check = capabilityChecker.check()
        if (!check.reachable) {
            client.release()
            state.value = failed(check.failure)
            return
        }
        state.value = VehicleLinkStatus(
            state = VehicleLinkState.CONNECTED,
            capabilities = check.capabilities,
            failure = check.failure,
        )
    }

    override suspend fun readIdentityHint(): VehicleInfoResult {
        val current = state.value
        if (current.state != VehicleLinkState.CONNECTED) {
            return VehicleInfoResult.Failed(current.failure ?: VehicleLinkFailure.CONNECTION_FAILED)
        }
        if (current.capabilities.vinInfo == CapabilitySupport.UNSUPPORTED) return VehicleInfoResult.Unsupported

        return when (val outcome = client.readVin()) {
            // VIN 원문은 이 지역 변수 밖으로 내보내지 않고 저장·전송·로그 기록 금지
            is SdkOutcome.Success -> VinHintDecoder.decode(outcome.value, currentYear())
                ?.let(VehicleInfoResult::Available)
                ?: VehicleInfoResult.Failed(VehicleLinkFailure.NO_DATA)
            is SdkOutcome.Failure -> when (outcome.failure) {
                SdkFailure.NOT_SUPPORTED -> VehicleInfoResult.Unsupported
                SdkFailure.EMPTY_RESULT -> VehicleInfoResult.Failed(VehicleLinkFailure.NO_DATA)
                SdkFailure.PERMISSION_DENIED -> VehicleInfoResult.Failed(VehicleLinkFailure.PERMISSION_DENIED)
                SdkFailure.NOT_CONNECTED -> VehicleInfoResult.Failed(VehicleLinkFailure.CONNECTION_FAILED)
                SdkFailure.TIMEOUT -> VehicleInfoResult.Failed(VehicleLinkFailure.NO_RESPONSE)
                SdkFailure.OTHER -> VehicleInfoResult.Failed(VehicleLinkFailure.SDK_ERROR)
            }
        }
    }

    override fun disconnect() {
        client.release()
        state.value = VehicleLinkStatus()
    }

    private fun failed(failure: VehicleLinkFailure?) =
        VehicleLinkStatus(state = VehicleLinkState.FAILED, failure = failure)
}
