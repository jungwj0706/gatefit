package com.gatefit.driving

import android.content.Context
import android.content.pm.PackageManager
import com.gatefit.domain.model.DrivingRestrictions
import com.gatefit.domain.repository.DrivingRestrictionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DrivingRestrictionManager(context: Context) : DrivingRestrictionRepository {

    private val appContext = context.applicationContext
    private val automotive = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

    private val state = MutableStateFlow(
        if (automotive) DrivingRestrictions.RESTRICTED_UNAVAILABLE else DrivingRestrictions.UNRESTRICTED_NOT_AUTOMOTIVE
    )
    override val restrictions: StateFlow<DrivingRestrictions> = state.asStateFlow()

    private var source: CarUxRestrictionSource? = null

    override fun start() {
        if (!automotive || source != null) return
        // android.car 클래스는 차량 환경에만 있으므로 차량으로 확인된 뒤에만 참조
        source = try {
            CarUxRestrictionSource(appContext) { state.value = it }.also { it.start() }
        } catch (_: Throwable) {
            state.value = DrivingRestrictions.RESTRICTED_UNAVAILABLE
            null
        }
    }

    override fun stop() {
        source?.stop()
        source = null
    }
}
