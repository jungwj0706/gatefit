package com.gatefit.domain.usecase

import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.RouteBlockReason
import com.gatefit.domain.model.RouteTarget
import com.gatefit.domain.model.RouteTargetResolution
import com.gatefit.domain.model.RouteTargetType
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.repository.NavigationRepository

class ResolveRouteTargetUseCase {

    // 안내할 수 없는 출입구를 시설 대표 좌표 등 다른 좌표로 대체하지 않고 차단 사유를 반환
    operator fun invoke(facility: Facility, entrance: VehicleEntrance): RouteTargetResolution {
        if (facility.origin == DataOrigin.DEMO || entrance.origin == DataOrigin.DEMO) {
            return blocked(RouteBlockReason.DEMO_DATA)
        }
        if (entrance.status == EntranceStatus.CLOSED) return blocked(RouteBlockReason.ENTRANCE_CLOSED)
        if (entrance.direction == EntranceDirection.EXIT) return blocked(RouteBlockReason.EXIT_ONLY)

        val latitude = entrance.latitude
        val longitude = entrance.longitude
        if (latitude == null || longitude == null) return blocked(RouteBlockReason.COORDINATES_MISSING)
        if (entrance.routeTargetType == RouteTargetType.FACILITY_CENTROID) {
            return blocked(RouteBlockReason.FACILITY_CENTROID_ONLY)
        }
        if (latitude !in SERVICE_LATITUDES || longitude !in SERVICE_LONGITUDES) {
            return blocked(RouteBlockReason.COORDINATES_INVALID)
        }

        return RouteTargetResolution.Ready(
            RouteTarget(
                entranceId = entrance.id,
                latitude = latitude,
                longitude = longitude,
                name = "${facility.name} ${entrance.name}".trim(),
                address = facility.displayAddress,
                targetType = entrance.routeTargetType,
            )
        )
    }

    private fun blocked(reason: RouteBlockReason) = RouteTargetResolution.Blocked(reason)

    private companion object {
        // 서비스 대상 지역(대한민국) 좌표 범위, 범위를 벗어나면 입력 오류로 판단
        val SERVICE_LATITUDES = 33.0..38.7
        val SERVICE_LONGITUDES = 124.5..132.0
    }
}

class RequestEntranceNavigationUseCase(
    private val navigation: NavigationRepository,
    private val resolveTarget: ResolveRouteTargetUseCase = ResolveRouteTargetUseCase(),
) {
    // 반환값은 요청 가능 여부만 의미, 경로 탐색 성공 여부는 NavigationRepository.state로 확인
    suspend operator fun invoke(facility: Facility, entrance: VehicleEntrance): RouteTargetResolution {
        val resolution = resolveTarget(facility, entrance)
        if (resolution is RouteTargetResolution.Ready) navigation.requestRoute(resolution.target)
        return resolution
    }
}
