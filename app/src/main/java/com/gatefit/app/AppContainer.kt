package com.gatefit.app

import android.content.Context
import com.gatefit.BuildConfig
import com.gatefit.data.local.AndroidAssetSource
import com.gatefit.data.local.AssetPaths
import com.gatefit.data.local.BundledData
import com.gatefit.data.local.GatefitDatabase
import com.gatefit.data.local.SharedPreferencesStore
import com.gatefit.data.local.SqlitePlaceStore
import com.gatefit.data.local.SqliteUserStore
import com.gatefit.data.remote.SupabaseApi
import com.gatefit.data.remote.SupabaseConfig
import com.gatefit.data.remote.SupabasePlaceRemoteSource
import com.gatefit.data.remote.SupabaseReportRemoteSource
import com.gatefit.data.repository.BundledVehicleSpecRepository
import com.gatefit.data.repository.LocalSavedPlacesRepository
import com.gatefit.data.repository.LocalSettingsRepository
import com.gatefit.data.repository.LocalVehicleProfileRepository
import com.gatefit.data.repository.OfflineFirstFacilityRepository
import com.gatefit.data.repository.OutboxReportRepository
import com.gatefit.domain.analysis.DataTrustEvaluator
import com.gatefit.domain.analysis.HolidayCalendar
import com.gatefit.domain.analysis.HolidayStatus
import com.gatefit.domain.analysis.OperatingHoursEvaluator
import com.gatefit.domain.repository.DrivingRestrictionRepository
import com.gatefit.domain.repository.NavigationRepository
import com.gatefit.domain.repository.ReportRepository
import com.gatefit.domain.repository.SavedPlacesRepository
import com.gatefit.domain.repository.SettingsRepository
import com.gatefit.domain.repository.VehicleInfoRepository
import com.gatefit.domain.repository.VehicleProfileRepository
import com.gatefit.domain.repository.VehicleSpecRepository
import com.gatefit.domain.usecase.AnalyzeEntranceCompatibilityUseCase
import com.gatefit.domain.usecase.AnalyzeFacilityEntrancesUseCase
import com.gatefit.domain.usecase.ApplyVehicleModelSpecUseCase
import com.gatefit.domain.usecase.CalculateEffectiveVehicleDimensionsUseCase
import com.gatefit.domain.usecase.CheckVehicleCapabilitiesUseCase
import com.gatefit.domain.usecase.GetEntranceDetailsUseCase
import com.gatefit.domain.usecase.GetFacilityEntrancesUseCase
import com.gatefit.domain.usecase.GetFavoriteFacilitiesUseCase
import com.gatefit.domain.usecase.GetRecentFacilitiesUseCase
import com.gatefit.domain.usecase.GetVehicleInformationUseCase
import com.gatefit.domain.usecase.InitializeVehicleSdkUseCase
import com.gatefit.domain.usecase.RequestEntranceNavigationUseCase
import com.gatefit.domain.usecase.ResolveRouteTargetUseCase
import com.gatefit.domain.usecase.SaveVehicleProfileUseCase
import com.gatefit.domain.usecase.SearchFacilitiesUseCase
import com.gatefit.domain.usecase.SubmitEntranceReportUseCase
import com.gatefit.domain.usecase.ToggleFavoriteFacilityUseCase
import com.gatefit.driving.DrivingRestrictionManager
import com.gatefit.navigation.NaviHelperManager
import com.gatefit.navigation.PleosNavigationRepository
import com.gatefit.vehicle.DevVehicleProviders
import com.gatefit.vehicle.PleosVehicleInfoRepository
import com.gatefit.vehicle.VehicleCapabilityChecker
import com.gatefit.vehicle.VehicleSdkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

// 수동 의존성 주입 컨테이너, 앱 프로세스당 하나만 생성
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val io = Dispatchers.IO

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val database = GatefitDatabase(appContext)
    private val userStore = SqliteUserStore(database)
    private val assets = AndroidAssetSource(appContext)
    val preferences = SharedPreferencesStore(appContext, "gatefit_prefs")

    private val supabaseApi = SupabaseConfig.from(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
        ?.let(SupabaseApi::create)

    val settingsRepository: SettingsRepository = LocalSettingsRepository(preferences, io)
    val vehicleProfileRepository: VehicleProfileRepository = LocalVehicleProfileRepository(preferences, io)
    val vehicleSpecRepository: VehicleSpecRepository = BundledVehicleSpecRepository(assets, io)
    val savedPlacesRepository: SavedPlacesRepository =
        LocalSavedPlacesRepository(userStore, System::currentTimeMillis, io)

    val facilityRepository = OfflineFirstFacilityRepository(
        local = SqlitePlaceStore(database),
        assets = assets,
        remote = supabaseApi?.let { SupabasePlaceRemoteSource(it) },
        demoEnabled = { settingsRepository.settings.value.demoDataEnabled },
        seedVersion = "${BuildConfig.VERSION_NAME}-${BuildConfig.VERSION_CODE}",
        clock = Instant::now,
        io = io,
    )

    val reportRepository: ReportRepository = OutboxReportRepository(
        local = userStore,
        remote = supabaseApi?.let(::SupabaseReportRemoteSource),
        nowEpochMs = System::currentTimeMillis,
        newId = { UUID.randomUUID().toString() },
        io = io,
    )

    // 개발용 Mock은 디버그 빌드에서 직접 켠 경우에만 사용, 그 외에는 항상 실제 Pleos SDK 구현
    val vehicleInfoRepository: VehicleInfoRepository = DevVehicleProviders.mockOrNull(preferences)
        ?: VehicleSdkManager(appContext).let { client ->
            PleosVehicleInfoRepository(client, VehicleCapabilityChecker(client)) { LocalDate.now().year }
        }

    val navigationRepository: NavigationRepository = PleosNavigationRepository(NaviHelperManager(appContext), appScope)
    val drivingRestrictionRepository: DrivingRestrictionRepository = DrivingRestrictionManager(appContext)

    val clock: () -> ZonedDateTime = ZonedDateTime::now

    // 공휴일 자료를 읽지 못하면 모든 날짜의 공휴일 여부를 미확인으로 처리
    private val holidayCalendar: HolidayCalendar by lazy {
        try {
            BundledData.parseHolidayCalendar(assets.read(AssetPaths.HOLIDAYS))
        } catch (_: Exception) {
            HolidayCalendar { HolidayStatus.UNKNOWN }
        }
    }

    val calculateEffectiveDimensions = CalculateEffectiveVehicleDimensionsUseCase()
    private val analyzeEntrance by lazy {
        AnalyzeEntranceCompatibilityUseCase(
            calculateEffectiveDimensions,
            OperatingHoursEvaluator(holidayCalendar),
            DataTrustEvaluator(),
        )
    }
    val analyzeEntranceCompatibility: AnalyzeEntranceCompatibilityUseCase get() = analyzeEntrance
    val getFacilityEntrances by lazy {
        GetFacilityEntrancesUseCase(
            facilityRepository,
            AnalyzeFacilityEntrancesUseCase(calculateEffectiveDimensions, analyzeEntrance),
        )
    }

    val searchFacilities = SearchFacilitiesUseCase(facilityRepository)
    val getEntranceDetails = GetEntranceDetailsUseCase(facilityRepository)
    val getRecentFacilities = GetRecentFacilitiesUseCase(savedPlacesRepository, facilityRepository)
    val getFavoriteFacilities = GetFavoriteFacilitiesUseCase(savedPlacesRepository, facilityRepository)
    val toggleFavoriteFacility = ToggleFavoriteFacilityUseCase(savedPlacesRepository)

    val saveVehicleProfile = SaveVehicleProfileUseCase(vehicleProfileRepository, System::currentTimeMillis)
    val applyVehicleModelSpec = ApplyVehicleModelSpecUseCase()

    val initializeVehicleSdk = InitializeVehicleSdkUseCase(vehicleInfoRepository)
    val checkVehicleCapabilities = CheckVehicleCapabilitiesUseCase(vehicleInfoRepository)
    val getVehicleInformation = GetVehicleInformationUseCase(vehicleInfoRepository)

    val resolveRouteTarget = ResolveRouteTargetUseCase()
    val requestEntranceNavigation = RequestEntranceNavigationUseCase(navigationRepository, resolveRouteTarget)
    val submitEntranceReport = SubmitEntranceReportUseCase(reportRepository, facilityRepository)
}
