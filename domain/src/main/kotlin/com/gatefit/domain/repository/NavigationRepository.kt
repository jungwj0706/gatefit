package com.gatefit.domain.repository

import com.gatefit.domain.model.DrivingRestrictions
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTarget
import kotlinx.coroutines.flow.StateFlow

interface NavigationRepository {
    val state: StateFlow<RouteRequestState>

    // 요청 접수까지만 수행, 경로 탐색 결과는 state로 전달
    suspend fun requestRoute(target: RouteTarget)

    // 진행 중이거나 설정된 목적지를 내비게이션에서 취소
    fun cancelRoute()

    // 화면에 남은 결과 표시만 초기화, 내비게이션의 목적지는 유지
    fun clearResult()

    fun release()
}

interface DrivingRestrictionRepository {
    val restrictions: StateFlow<DrivingRestrictions>

    fun start()

    fun stop()
}
