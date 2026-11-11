package com.gatefit.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class VehicleSdkManager(
    context: Context,
    private val callTimeoutMs: Long = 3_000L,
) : VehicleSdkClient {

    private val appContext = context.applicationContext
    private val vehicle by lazy { Vehicle(appContext) }
    private var initialized = false

    override fun isServiceInstalled(): Boolean =
        appContext.packageManager.queryIntentServices(Intent(VEHICLE_SERVICE_ACTION), 0).isNotEmpty()

    override fun initialize() {
        if (initialized) return
        vehicle.initialize()
        initialized = true
    }

    override fun release() {
        if (!initialized) return
        initialized = false
        try {
            vehicle.release()
        } catch (_: Exception) {
            // 이미 끊어진 연결을 해제할 때의 오류는 복구할 대상이 없음
        }
    }

    override suspend fun checkVinReadable(): SdkOutcome<Boolean> = call { onComplete, onFailed ->
        vehicle.getCarInfo().checkVINInfoCapabilities(
            onComplete = { config ->
                onComplete(config.accessType == AccessType.READ || config.accessType == AccessType.READ_WRITE)
            },
            onFailed = onFailed,
        )
    }

    override suspend fun readVin(): SdkOutcome<String> {
        val outcome = call<String?> { onComplete, onFailed ->
            vehicle.getCarInfo().getVINInfo(onComplete = onComplete, onFailed = onFailed)
        }
        return when (outcome) {
            is SdkOutcome.Failure -> outcome
            is SdkOutcome.Success -> outcome.value?.takeIf { it.isNotBlank() }
                ?.let { SdkOutcome.Success(it) }
                ?: SdkOutcome.Failure(SdkFailure.EMPTY_RESULT)
        }
    }

    // SDK 콜백을 한 번만 결과로 받아들이고, 응답이 없으면 시간 초과로 처리
    private suspend fun <T> call(
        request: (onComplete: (T) -> Unit, onFailed: (Exception) -> Unit) -> Unit,
    ): SdkOutcome<T> {
        if (!initialized) return SdkOutcome.Failure(SdkFailure.NOT_CONNECTED)
        return withTimeoutOrNull(callTimeoutMs) {
            suspendCancellableCoroutine { continuation ->
                val settled = AtomicBoolean(false)
                fun settle(outcome: SdkOutcome<T>) {
                    if (settled.compareAndSet(false, true) && continuation.isActive) continuation.resume(outcome)
                }
                try {
                    request(
                        { value -> settle(SdkOutcome.Success(value)) },
                        { error -> settle(SdkOutcome.Failure(classify(error))) },
                    )
                } catch (error: Exception) {
                    settle(SdkOutcome.Failure(classify(error)))
                }
            }
        } ?: SdkOutcome.Failure(SdkFailure.TIMEOUT)
    }

    companion object {
        const val VEHICLE_SERVICE_ACTION = "ai.pleos.playground.vehicle.VEHICLE_START"

        // SDK 예외는 메시지에 오류 코드 이름을 담아 전달, 코드 정의 모듈은 컴파일 경로에 노출되지 않아 이름으로 판별
        fun classify(error: Exception): SdkFailure {
            if (error is SecurityException) return SdkFailure.PERMISSION_DENIED
            val message = error.message.orEmpty()
            return when {
                "ERROR_SECURITY_PERMISSION" in message -> SdkFailure.PERMISSION_DENIED
                "ERROR_SERVICE_CONNECTION_FAILURE" in message || "not connected" in message -> SdkFailure.NOT_CONNECTED
                "ERROR_NOT_SUPPORTED" in message -> SdkFailure.NOT_SUPPORTED
                "ERROR_EMPTY_RESULT" in message -> SdkFailure.EMPTY_RESULT
                else -> SdkFailure.OTHER
            }
        }
    }
}
