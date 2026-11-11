package com.gatefit.domain.usecase

import com.gatefit.domain.model.DataOrigin
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceStatus
import com.gatefit.domain.model.Facility
import com.gatefit.domain.model.FacilityCategory
import com.gatefit.domain.model.RouteBlockReason
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTarget
import com.gatefit.domain.model.RouteTargetResolution
import com.gatefit.domain.model.RouteTargetType
import com.gatefit.domain.model.VehicleEntrance
import com.gatefit.domain.repository.NavigationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestEntranceNavigationUseCaseTest {

    private class FakeNavigationRepository : NavigationRepository {
        override val state = MutableStateFlow<RouteRequestState>(RouteRequestState.Idle)
        val requested = mutableListOf<RouteTarget>()

        override suspend fun requestRoute(target: RouteTarget) {
            requested += target
            state.value = RouteRequestState.Requested(target)
        }

        override fun cancelRoute() = Unit
        override fun clearResult() = Unit
        override fun release() = Unit
    }

    private val facility = Facility(
        id = "f1",
        name = "가상 쇼핑센터",
        category = FacilityCategory.SHOPPING,
        roadAddress = "서울특별시 가상구 가상로 1",
        lotAddress = null,
        latitude = 37.5000,
        longitude = 127.0000,
    )

    private fun entrance(
        latitude: Double? = 37.5012,
        longitude: Double? = 127.0034,
        routeTargetType: RouteTargetType = RouteTargetType.ENTRANCE_POINT,
        origin: DataOrigin = DataOrigin.LIVE,
        status: EntranceStatus = EntranceStatus.ACTIVE,
        direction: EntranceDirection = EntranceDirection.ENTRY,
    ) = VehicleEntrance(
        id = "e1",
        facilityId = "f1",
        name = "B게이트",
        latitude = latitude,
        longitude = longitude,
        routeTargetType = routeTargetType,
        origin = origin,
        status = status,
        direction = direction,
    )

    private val resolve = ResolveRouteTargetUseCase()

    private fun blockReason(entrance: VehicleEntrance, target: Facility = facility): RouteBlockReason? =
        (resolve(target, entrance) as? RouteTargetResolution.Blocked)?.reason

    @Test
    fun `entrance coordinates are passed as the destination instead of the facility centroid`() {
        val resolution = resolve(facility, entrance())

        val target = (resolution as RouteTargetResolution.Ready).target
        assertEquals(37.5012, target.latitude, 0.0)
        assertEquals(127.0034, target.longitude, 0.0)
        assertEquals("가상 쇼핑센터 B게이트", target.name)
        assertEquals("서울특별시 가상구 가상로 1", target.address)
        assertEquals(RouteTargetType.ENTRANCE_POINT, target.targetType)
    }

    @Test
    fun `verified road access point is accepted as a destination`() {
        val resolution = resolve(facility, entrance(routeTargetType = RouteTargetType.ROAD_SNAPPED_POINT))

        assertTrue(resolution is RouteTargetResolution.Ready)
    }

    @Test
    fun `missing coordinates are never replaced with facility coordinates`() {
        assertEquals(RouteBlockReason.COORDINATES_MISSING, blockReason(entrance(latitude = null, longitude = null)))
        assertEquals(RouteBlockReason.COORDINATES_MISSING, blockReason(entrance(longitude = null)))
    }

    @Test
    fun `facility centroid is not guided as if it were an entrance`() {
        val centroid = entrance(routeTargetType = RouteTargetType.FACILITY_CENTROID)

        assertEquals(RouteBlockReason.FACILITY_CENTROID_ONLY, blockReason(centroid))
    }

    @Test
    fun `coordinates outside the service area are rejected`() {
        assertEquals(RouteBlockReason.COORDINATES_INVALID, blockReason(entrance(latitude = 0.0, longitude = 0.0)))
        assertEquals(RouteBlockReason.COORDINATES_INVALID, blockReason(entrance(latitude = 127.0034, longitude = 37.5012)))
        assertEquals(RouteBlockReason.COORDINATES_INVALID, blockReason(entrance(latitude = Double.NaN)))
    }

    @Test
    fun `demo data closed entrances and exits are not navigable`() {
        assertEquals(RouteBlockReason.DEMO_DATA, blockReason(entrance(origin = DataOrigin.DEMO)))
        assertEquals(RouteBlockReason.DEMO_DATA, blockReason(entrance(), facility.copy(origin = DataOrigin.DEMO)))
        assertEquals(RouteBlockReason.ENTRANCE_CLOSED, blockReason(entrance(status = EntranceStatus.CLOSED)))
        assertEquals(RouteBlockReason.EXIT_ONLY, blockReason(entrance(direction = EntranceDirection.EXIT)))
    }

    @Test
    fun `navigation is requested only for a resolvable target`() = runTest {
        val navigation = FakeNavigationRepository()
        val request = RequestEntranceNavigationUseCase(navigation)

        val blocked = request(facility, entrance(latitude = null))
        val ready = request(facility, entrance())

        assertEquals(RouteTargetResolution.Blocked(RouteBlockReason.COORDINATES_MISSING), blocked)
        assertEquals(listOf((ready as RouteTargetResolution.Ready).target), navigation.requested)
    }

    @Test
    fun `accepted request is not reported as a completed route`() = runTest {
        val navigation = FakeNavigationRepository()

        RequestEntranceNavigationUseCase(navigation)(facility, entrance())

        assertTrue(navigation.state.value is RouteRequestState.Requested)
        assertTrue(navigation.state.value.isPending)
    }
}
