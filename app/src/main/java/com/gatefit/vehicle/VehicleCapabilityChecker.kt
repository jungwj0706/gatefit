package com.gatefit.vehicle

import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.VehicleCapabilities
import com.gatefit.domain.model.VehicleLinkFailure
import kotlinx.coroutines.delay

data class CapabilityCheck(
    val reachable: Boolean,
    val capabilities: VehicleCapabilities,
    val failure: VehicleLinkFailure?,
)

class VehicleCapabilityChecker(
    private val client: VehicleSdkClient,
    private val attempts: Int = 6,
    private val retryDelayMs: Long = 500L,
) {

    // SDK는 서비스 연결 완료를 알려 주지 않으므로 지원 여부 조회가 응답할 때까지 짧게 재시도
    suspend fun check(): CapabilityCheck {
        var outcome = client.checkVinReadable()
        var attempt = 1
        while (outcome.isNotConnected() && attempt < attempts) {
            delay(retryDelayMs)
            outcome = client.checkVinReadable()
            attempt++
        }
        return when (outcome) {
            is SdkOutcome.Success -> connected(if (outcome.value) CapabilitySupport.SUPPORTED else CapabilitySupport.UNSUPPORTED)
            is SdkOutcome.Failure -> when (outcome.failure) {
                SdkFailure.NOT_SUPPORTED, SdkFailure.EMPTY_RESULT -> connected(CapabilitySupport.UNSUPPORTED)
                // 서비스는 응답했지만 권한이 없어 지원 여부를 알 수 없는 상태
                SdkFailure.PERMISSION_DENIED -> connected(CapabilitySupport.UNKNOWN, VehicleLinkFailure.PERMISSION_DENIED)
                SdkFailure.NOT_CONNECTED -> unreachable(VehicleLinkFailure.CONNECTION_FAILED)
                SdkFailure.TIMEOUT -> unreachable(VehicleLinkFailure.NO_RESPONSE)
                SdkFailure.OTHER -> unreachable(VehicleLinkFailure.SDK_ERROR)
            }
        }
    }

    private fun SdkOutcome<Boolean>.isNotConnected(): Boolean =
        this is SdkOutcome.Failure && failure == SdkFailure.NOT_CONNECTED

    private fun connected(vinInfo: CapabilitySupport, failure: VehicleLinkFailure? = null) =
        CapabilityCheck(reachable = true, capabilities = VehicleCapabilities(vinInfo = vinInfo), failure = failure)

    private fun unreachable(failure: VehicleLinkFailure) =
        CapabilityCheck(reachable = false, capabilities = VehicleCapabilities(), failure = failure)
}
