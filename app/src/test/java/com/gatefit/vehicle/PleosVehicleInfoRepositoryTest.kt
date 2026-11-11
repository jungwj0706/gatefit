package com.gatefit.vehicle

import com.gatefit.domain.model.CapabilitySupport
import com.gatefit.domain.model.VehicleIdentityHint
import com.gatefit.domain.model.VehicleInfoResult
import com.gatefit.domain.model.VehicleLinkFailure
import com.gatefit.domain.model.VehicleLinkState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PleosVehicleInfoRepositoryTest {

    private class FakeVehicleSdkClient(
        var installed: Boolean = true,
        var initializeError: Exception? = null,
        capabilityOutcomes: List<SdkOutcome<Boolean>> = listOf(SdkOutcome.Success(true)),
        var vinOutcome: SdkOutcome<String> = SdkOutcome.Success(FAKE_VIN),
    ) : VehicleSdkClient {
        private val capabilityOutcomes = ArrayDeque(capabilityOutcomes)
        var initializeCount = 0
        var releaseCount = 0
        var capabilityChecks = 0
        var vinReads = 0

        override fun isServiceInstalled(): Boolean = installed

        override fun initialize() {
            initializeCount++
            initializeError?.let { throw it }
        }

        override fun release() {
            releaseCount++
        }

        override suspend fun checkVinReadable(): SdkOutcome<Boolean> {
            capabilityChecks++
            return if (capabilityOutcomes.size > 1) capabilityOutcomes.removeFirst() else capabilityOutcomes.first()
        }

        override suspend fun readVin(): SdkOutcome<String> {
            vinReads++
            return vinOutcome
        }
    }

    private fun repository(client: FakeVehicleSdkClient) = PleosVehicleInfoRepository(
        client = client,
        capabilityChecker = VehicleCapabilityChecker(client, attempts = 4, retryDelayMs = 500L),
        currentYear = { 2026 },
    )

    private fun failure(kind: SdkFailure) = SdkOutcome.Failure(kind)

    @Test
    fun `missing vehicle service is reported without touching the sdk`() = runTest {
        val client = FakeVehicleSdkClient(installed = false)

        val status = repository(client).connect()

        assertEquals(VehicleLinkState.SERVICE_UNAVAILABLE, status.state)
        assertEquals(VehicleLinkFailure.SERVICE_NOT_INSTALLED, status.failure)
        assertEquals(0, client.initializeCount)
        assertFalse(status.demo)
    }

    @Test
    fun `connected vehicle reports vin support and never dimension support`() = runTest {
        val status = repository(FakeVehicleSdkClient()).connect()

        assertEquals(VehicleLinkState.CONNECTED, status.state)
        assertEquals(CapabilitySupport.SUPPORTED, status.capabilities.vinInfo)
        assertEquals(CapabilitySupport.UNSUPPORTED, status.capabilities.dimensions)
        assertNull(status.failure)
    }

    @Test
    fun `capability check is retried while the service is still binding`() = runTest {
        val client = FakeVehicleSdkClient(
            capabilityOutcomes = listOf(
                failure(SdkFailure.NOT_CONNECTED),
                failure(SdkFailure.NOT_CONNECTED),
                SdkOutcome.Success(false),
            )
        )

        val status = repository(client).connect()

        assertEquals(VehicleLinkState.CONNECTED, status.state)
        assertEquals(CapabilitySupport.UNSUPPORTED, status.capabilities.vinInfo)
        assertEquals(3, client.capabilityChecks)
    }

    @Test
    fun `service that never connects fails after the retry limit and is released`() = runTest {
        val client = FakeVehicleSdkClient(capabilityOutcomes = listOf(failure(SdkFailure.NOT_CONNECTED)))

        val status = repository(client).connect()

        assertEquals(VehicleLinkState.FAILED, status.state)
        assertEquals(VehicleLinkFailure.CONNECTION_FAILED, status.failure)
        assertEquals(4, client.capabilityChecks)
        assertEquals(1, client.releaseCount)
    }

    @Test
    fun `unsupported property means connected without vin support`() = runTest {
        val client = FakeVehicleSdkClient(capabilityOutcomes = listOf(failure(SdkFailure.NOT_SUPPORTED)))
        val repository = repository(client)

        val status = repository.connect()

        assertEquals(VehicleLinkState.CONNECTED, status.state)
        assertEquals(CapabilitySupport.UNSUPPORTED, status.capabilities.vinInfo)
        assertEquals(VehicleInfoResult.Unsupported, repository.readIdentityHint())
        assertEquals(0, client.vinReads)
    }

    @Test
    fun `permission denial keeps support unknown instead of unsupported`() = runTest {
        val client = FakeVehicleSdkClient(capabilityOutcomes = listOf(failure(SdkFailure.PERMISSION_DENIED)))

        val status = repository(client).connect()

        assertEquals(VehicleLinkState.CONNECTED, status.state)
        assertEquals(CapabilitySupport.UNKNOWN, status.capabilities.vinInfo)
        assertEquals(VehicleLinkFailure.PERMISSION_DENIED, status.failure)
    }

    @Test
    fun `timeout and sdk errors are distinguished`() = runTest {
        val timeout = repository(FakeVehicleSdkClient(capabilityOutcomes = listOf(failure(SdkFailure.TIMEOUT)))).connect()
        val error = repository(FakeVehicleSdkClient(capabilityOutcomes = listOf(failure(SdkFailure.OTHER)))).connect()
        val initFailure = repository(FakeVehicleSdkClient(initializeError = IllegalStateException("bind"))).connect()

        assertEquals(VehicleLinkFailure.NO_RESPONSE, timeout.failure)
        assertEquals(VehicleLinkFailure.SDK_ERROR, error.failure)
        assertEquals(VehicleLinkFailure.CONNECTION_FAILED, initFailure.failure)
        assertTrue(listOf(timeout, error, initFailure).all { it.state == VehicleLinkState.FAILED })
    }

    @Test
    fun `failed connection can be retried`() = runTest {
        val client = FakeVehicleSdkClient(installed = false)
        val repository = repository(client)
        repository.connect()

        client.installed = true

        assertEquals(VehicleLinkState.CONNECTED, repository.connect().state)
    }

    @Test
    fun `connecting twice does not initialize the sdk again`() = runTest {
        val client = FakeVehicleSdkClient()
        val repository = repository(client)

        repository.connect()
        repository.connect()

        assertEquals(1, client.initializeCount)
    }

    @Test
    fun `identity hint exposes only manufacturer and model year`() = runTest {
        val repository = repository(FakeVehicleSdkClient())
        repository.connect()

        val result = repository.readIdentityHint()

        assertEquals(VehicleInfoResult.Available(VehicleIdentityHint("현대자동차", 2023)), result)
        assertFalse(FAKE_VIN in result.toString())
        assertFalse(FAKE_VIN in repository.status.value.toString())
    }

    @Test
    fun `identity hint is not read before a connection exists`() = runTest {
        val client = FakeVehicleSdkClient()

        val result = repository(client).readIdentityHint()

        assertEquals(VehicleInfoResult.Failed(VehicleLinkFailure.CONNECTION_FAILED), result)
        assertEquals(0, client.vinReads)
    }

    @Test
    fun `unreadable or empty vehicle information is reported as no data`() = runTest {
        val garbled = repository(FakeVehicleSdkClient(vinOutcome = SdkOutcome.Success("UNKNOWN"))).also { it.connect() }
        val empty = repository(FakeVehicleSdkClient(vinOutcome = failure(SdkFailure.EMPTY_RESULT))).also { it.connect() }

        assertEquals(VehicleInfoResult.Failed(VehicleLinkFailure.NO_DATA), garbled.readIdentityHint())
        assertEquals(VehicleInfoResult.Failed(VehicleLinkFailure.NO_DATA), empty.readIdentityHint())
    }

    @Test
    fun `disconnect releases the sdk and resets the status`() = runTest {
        val client = FakeVehicleSdkClient()
        val repository = repository(client)
        repository.connect()

        repository.disconnect()

        assertEquals(VehicleLinkState.IDLE, repository.status.value.state)
        assertEquals(1, client.releaseCount)
    }

    @Test
    fun `sdk error codes are classified from the exception message`() {
        assertEquals(SdkFailure.PERMISSION_DENIED, VehicleSdkManager.classify(Exception("ERROR_SECURITY_PERMISSION")))
        assertEquals(SdkFailure.PERMISSION_DENIED, VehicleSdkManager.classify(SecurityException("denied")))
        assertEquals(SdkFailure.NOT_SUPPORTED, VehicleSdkManager.classify(Exception("ERROR_NOT_SUPPORTED_PROPERTY")))
        assertEquals(SdkFailure.NOT_CONNECTED, VehicleSdkManager.classify(Exception("ERROR_SERVICE_CONNECTION_FAILURE")))
        assertEquals(
            SdkFailure.NOT_CONNECTED,
            VehicleSdkManager.classify(Exception("sdk not initialize so service is not connected")),
        )
        assertEquals(SdkFailure.EMPTY_RESULT, VehicleSdkManager.classify(Exception("ERROR_EMPTY_RESULT")))
        assertEquals(SdkFailure.OTHER, VehicleSdkManager.classify(Exception("ERROR_UNDEFINED")))
        assertEquals(SdkFailure.OTHER, VehicleSdkManager.classify(Exception()))
    }

    private companion object {
        // 형식만 맞춘 가상 VIN, 실제 차량과 무관
        const val FAKE_VIN = "KMHXX00XXPU000000"
    }
}
