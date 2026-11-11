package com.gatefit.domain.repository

import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkStatus
import kotlinx.coroutines.flow.StateFlow

interface VehicleInfoRepository {
    val status: StateFlow<VehicleLinkStatus>

    suspend fun connect(): VehicleLinkStatus

    // 차량 식별 정보를 읽는 동작이므로 사용자가 명시적으로 요청한 경우에만 호출
    suspend fun readIdentityHint(): VehicleInfoResult

    fun disconnect()
}
