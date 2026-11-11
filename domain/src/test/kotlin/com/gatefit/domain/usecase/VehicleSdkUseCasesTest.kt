package com.gatefit.domain.usecase

import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.VehicleCapabilities
import com.gatefit.domain.model.VehicleIdentityHint
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.model.VehicleLinkState
import com.gatefit.domain.model.VehicleLinkStatus
import com.gatefit.domain.repository.VehicleInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleSdkUseCasesTest {

    private class FakeVehicleInfoRepository(private val onConnect: VehicleLinkStatus) : VehicleInfoRepository {
        override val status = MutableStateFlow(VehicleLinkStatus())
        var hintReads = 0

        override suspend fun connect(): VehicleLinkStatus {
            status.value = onConnect
            return onConnect
        }

        override suspend fun readIdentityHint(): VehicleInfoResult {
            hintReads++
            return VehicleInfoResult.Available(VehicleIdentityHint("현대자동차", 2025))
        }

        override fun disconnect() {
            status.value = VehicleLinkStatus()
        }
    }

    private fun connected(vinInfo: CapabilitySupport) = VehicleLinkStatus(
        state = VehicleLinkState.CONNECTED,
        capabilities = VehicleCapabilities(vinInfo = vinInfo),
    )

    @Test
    fun `capabilities are unknown before the sdk is connected`() {
        val repository = FakeVehicleInfoRepository(connected(CapabilitySupport.SUPPORTED))

        assertEquals(CapabilitySupport.UNKNOWN, CheckVehicleCapabilitiesUseCase(repository)().vinInfo)
    }

    @Test
    fun `capabilities come from the connected vehicle and never include dimensions`() = runTest {
        val repository = FakeVehicleInfoRepository(connected(CapabilitySupport.SUPPORTED))

        InitializeVehicleSdkUseCase(repository)()
        val capabilities = CheckVehicleCapabilitiesUseCase(repository)()

        assertEquals(CapabilitySupport.SUPPORTED, capabilities.vinInfo)
        assertEquals(CapabilitySupport.UNSUPPORTED, capabilities.dimensions)
    }

    @Test
    fun `vehicle information is read only when the vehicle supports it`() = runTest {
        val supported = FakeVehicleInfoRepository(connected(CapabilitySupport.SUPPORTED)).also { it.connect() }
        val unsupported = FakeVehicleInfoRepository(connected(CapabilitySupport.UNSUPPORTED)).also { it.connect() }

        val available = GetVehicleInformationUseCase(supported)()
        val skipped = GetVehicleInformationUseCase(unsupported)()

        assertEquals(VehicleInfoResult.Available(VehicleIdentityHint("현대자동차", 2025)), available)
        assertEquals(VehicleInfoResult.Unsupported, skipped)
        assertEquals(0, unsupported.hintReads)
    }

    @Test
    fun `vehicle information is not requested while the sdk is unavailable`() = runTest {
        val unavailable = VehicleLinkStatus(
            state = VehicleLinkState.SERVICE_UNAVAILABLE,
            failure = VehicleLinkFailure.SERVICE_NOT_INSTALLED,
        )
        val repository = FakeVehicleInfoRepository(unavailable).also { it.connect() }

        val result = GetVehicleInformationUseCase(repository)()

        assertEquals(VehicleInfoResult.Failed(VehicleLinkFailure.SERVICE_NOT_INSTALLED), result)
        assertEquals(0, repository.hintReads)
    }
}
