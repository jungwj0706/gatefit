package com.gatefit.ui.testing

import com.gatefit.data.local.AssetPaths
import com.gatefit.data.local.BundledData
import com.gatefit.data.remote.PlaceRemoteSource
import com.gatefit.data.remote.ReportRemoteSource
import com.gatefit.data.repository.BundledVehicleSpecRepository
import com.gatefit.data.repository.LocalSavedPlacesRepository
import com.gatefit.data.repository.LocalSettingsRepository
import com.gatefit.data.repository.LocalVehicleProfileRepository
import com.gatefit.data.repository.OfflineFirstFacilityRepository
import com.gatefit.data.repository.OutboxReportRepository
import com.gatefit.data.testing.BundledAssets
import com.gatefit.data.testing.InMemoryKeyValueStore
import com.gatefit.data.testing.InMemoryPlaceLocalSource
import com.gatefit.data.testing.InMemoryReportLocalSource
import com.gatefit.data.testing.InMemorySavedPlacesLocalSource
import com.gatefit.domain.analysis.DataTrustEvaluator
import com.gatefit.domain.analysis.OperatingHoursEvaluator
import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.RouteRequestState
import com.gatefit.domain.model.RouteTarget
import com.gatefit.domain.model.VehicleCapabilities
import com.gatefit.domain.model.VehicleIdentityHint
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.repository.NavigationRepository
import com.gatefit.domain.repository.VehicleInfoRepository
import com.gatefit.domain.usecase.AnalyzeEntranceCompatibilityUseCase
import com.gatefit.domain.usecase.AnalyzeFacilityEntrancesUseCase
import com.gatefit.domain.usecase.CalculateEffectiveVehicleDimensionsUseCase
import com.gatefit.domain.usecase.GetFacilityEntrancesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

class FakeVehicleInfoRepository(
    var connectResult: VehicleLinkStatus = VehicleLinkStatus(
        state = VehicleLinkState.SERVICE_UNAVAILABLE,
        failure = VehicleLinkFailure.SERVICE_NOT_INSTALLED,
    ),
    var hintResult: VehicleInfoResult = VehicleInfoResult.Unsupported,
) : VehicleInfoRepository {
    override val status = MutableStateFlow(VehicleLinkStatus())
    var connectCount = 0
    var hintReads = 0

    override suspend fun connect(): VehicleLinkStatus {
        connectCount++
        status.value = connectResult
        return connectResult
    }

    override suspend fun readIdentityHint(): VehicleInfoResult {
        hintReads++
        return hintResult
    }

    override fun disconnect() {
        status.value = VehicleLinkStatus()
    }

    companion object {
        fun connected(vinInfo: CapabilitySupport = CapabilitySupport.SUPPORTED) = VehicleLinkStatus(
            state = VehicleLinkState.CONNECTED,
            capabilities = VehicleCapabilities(vinInfo = vinInfo),
        )

        fun hint(manufacturer: String? = "현대자동차", modelYear: Int? = 2025) =
            VehicleInfoResult.Available(VehicleIdentityHint(manufacturer, modelYear))
    }
}

class FakeNavigationRepository : NavigationRepository {
    override val state = MutableStateFlow<RouteRequestState>(RouteRequestState.Idle)
    val requested = mutableListOf<RouteTarget>()
    var cancelCount = 0

    override suspend fun requestRoute(target: RouteTarget) {
        requested += target
        state.value = RouteRequestState.Requested(target)
    }

    override fun cancelRoute() {
        cancelCount++
        state.value = RouteRequestState.Idle
    }

    override fun clearResult() {
        if (!state.value.isPending) state.value = RouteRequestState.Idle
    }

    override fun release() = Unit
}

// 실제 리포지토리를 메모리 저장소 위에 구성한 화면 로직 테스트 환경
class TestEnvironment(
    val now: ZonedDateTime = ZonedDateTime.parse("2026-11-11T10:00:00+09:00[Asia/Seoul]"),
    placeRemote: PlaceRemoteSource? = null,
    reportRemote: ReportRemoteSource? = null,
) {
    private val io = Dispatchers.Unconfined
    val preferences = InMemoryKeyValueStore()
    val placeStore = InMemoryPlaceLocalSource()
    val reportStore = InMemoryReportLocalSource()

    val settings = LocalSettingsRepository(preferences, io)
    val profiles = LocalVehicleProfileRepository(preferences, io)
    val specs = BundledVehicleSpecRepository(BundledAssets, io)
    val savedPlaces = LocalSavedPlacesRepository(InMemorySavedPlacesLocalSource(), { now.toInstant().toEpochMilli() }, io)
    val facilities = OfflineFirstFacilityRepository(
        local = placeStore,
        assets = BundledAssets,
        remote = placeRemote,
        demoEnabled = { settings.settings.value.demoDataEnabled },
        seedVersion = "test",
        clock = { now.toInstant() },
        io = io,
    )
    private var reportSequence = 0
    val reports = OutboxReportRepository(
        local = reportStore,
        remote = reportRemote,
        nowEpochMs = { now.toInstant().toEpochMilli() },
        newId = { "report-${++reportSequence}" },
        io = io,
    )
    val vehicleInfo = FakeVehicleInfoRepository()
    val navigation = FakeNavigationRepository()

    val calculateEffectiveDimensions = CalculateEffectiveVehicleDimensionsUseCase()
    val analyzeEntrance = AnalyzeEntranceCompatibilityUseCase(
        calculateEffectiveDimensions,
        OperatingHoursEvaluator(BundledData.parseHolidayCalendar(BundledAssets.read(AssetPaths.HOLIDAYS))),
        DataTrustEvaluator(),
    )
    val getFacilityEntrances = GetFacilityEntrancesUseCase(
        facilities,
        AnalyzeFacilityEntrancesUseCase(calculateEffectiveDimensions, analyzeEntrance),
    )

    val clock: () -> ZonedDateTime = { now }
    val nowInstant: Instant get() = now.toInstant()
}
