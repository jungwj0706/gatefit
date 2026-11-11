package com.gatefit.ui.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.repository.FacilityRepository
import com.gatefit.domain.repository.ReportRepository
import com.gatefit.domain.repository.SettingsRepository
import com.gatefit.domain.repository.VehicleInfoRepository
import com.gatefit.domain.repository.VehicleProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class SplashDestination { VEHICLE_SETUP, HOME }

enum class DataPreparation { PREPARING, READY, FAILED }

data class SplashUiState(
    val data: DataPreparation = DataPreparation.PREPARING,
    val vehicleLink: VehicleLinkStatus = VehicleLinkStatus(),
    val destination: SplashDestination? = null,
)

class SplashViewModel(
    private val facilities: FacilityRepository,
    private val vehicleInfo: VehicleInfoRepository,
    private val settings: SettingsRepository,
    private val profiles: VehicleProfileRepository,
    private val reports: ReportRepository,
    // 차량 연결과 서버 동기화는 화면을 벗어나도 계속되도록 앱 범위에서 실행
    private val appScope: CoroutineScope,
    private val vehicleWaitMs: Long = 4_000L,
) : ViewModel() {

    private val progress = MutableStateFlow(SplashUiState())

    val state: StateFlow<SplashUiState> = combine(progress, vehicleInfo.status) { current, link ->
        current.copy(vehicleLink = link)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SplashUiState())

    private var startJob: Job? = null

    init {
        start()
    }

    fun start() {
        if (startJob?.isActive == true) return
        progress.value = SplashUiState()
        startJob = viewModelScope.launch {
            val vehicleConnection = appScope.launch { runCatching { vehicleInfo.connect() } }

            val seeded = runCatching { facilities.ensureSeeded() }.isSuccess
            if (!seeded) {
                progress.value = SplashUiState(data = DataPreparation.FAILED)
                return@launch
            }
            progress.value = SplashUiState(data = DataPreparation.READY)
            appScope.launch {
                runCatching { facilities.refreshFromRemote() }
                runCatching { reports.retryUndelivered() }
            }

            // 차량 연결이 늦어져도 수동 등록으로 진행할 수 있도록 제한 시간까지만 대기
            withTimeoutOrNull(vehicleWaitMs) { vehicleConnection.join() }
            val onboarded = settings.settings.value.onboardingCompleted && profiles.profile.value != null
            progress.value = SplashUiState(
                data = DataPreparation.READY,
                destination = if (onboarded) SplashDestination.HOME else SplashDestination.VEHICLE_SETUP,
            )
        }
    }
}
