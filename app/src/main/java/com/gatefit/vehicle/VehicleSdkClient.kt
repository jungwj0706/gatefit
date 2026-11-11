package com.gatefit.vehicle

enum class SdkFailure {
    NOT_CONNECTED,
    PERMISSION_DENIED,
    NOT_SUPPORTED,
    EMPTY_RESULT,
    TIMEOUT,
    OTHER,
}

sealed interface SdkOutcome<out T> {
    data class Success<T>(val value: T) : SdkOutcome<T>
    data class Failure(val failure: SdkFailure) : SdkOutcome<Nothing>
}

// Pleos Vehicle SDK 호출 경계, 실제 SDK 구현과 테스트용 구현을 분리하기 위한 인터페이스
interface VehicleSdkClient {
    fun isServiceInstalled(): Boolean

    fun initialize()

    fun release()

    // 결과 값은 VIN 조회 가능 여부
    suspend fun checkVinReadable(): SdkOutcome<Boolean>

    suspend fun readVin(): SdkOutcome<String>
}
