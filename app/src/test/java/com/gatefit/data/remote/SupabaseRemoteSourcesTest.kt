package com.gatefit.data.remote

import com.gatefit.core.result.DataError
import com.gatefit.core.result.DataResult
import com.gatefit.data.dto.DataSourceDto
import com.gatefit.data.dto.EntranceDto
import com.gatefit.data.dto.FacilityDto
import com.gatefit.data.dto.ReportRequestDto
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SupabaseRemoteSourcesTest {

    private class FakeApi(
        private val facilityCount: Int = 0,
        private val entranceCount: Int = 0,
        private val serverMaxRows: Int = Int.MAX_VALUE,
        private val failure: Exception? = null,
        private val reportResponse: () -> Response<Unit> = { Response.success(Unit) },
    ) : SupabaseApi {
        val facilityRequests = mutableListOf<Pair<Int, Int>>()

        private fun <T> page(total: Int, limit: Int, offset: Int, make: (Int) -> T): List<T> {
            failure?.let { throw it }
            val end = minOf(total, offset + minOf(limit, serverMaxRows))
            return (offset until end).map(make)
        }

        override suspend fun dataSources(limit: Int, offset: Int): List<DataSourceDto> =
            page(1, limit, offset) { DataSourceDto(id = "source", sourceName = "출처") }

        override suspend fun facilities(limit: Int, offset: Int): List<FacilityDto> {
            facilityRequests += limit to offset
            return page(facilityCount, limit, offset) { FacilityDto(id = "f$it", name = "시설 $it") }
        }

        override suspend fun entrances(limit: Int, offset: Int): List<EntranceDto> =
            page(entranceCount, limit, offset) { EntranceDto(id = "e$it", facilityId = "f0", name = "출입구 $it") }

        override suspend fun createReport(report: ReportRequestDto): Response<Unit> {
            failure?.let { throw it }
            return reportResponse()
        }
    }

    private fun error(code: Int, body: String = "{}"): Response<Unit> =
        Response.error(code, body.toResponseBody("application/json".toMediaType()))

    private val report = ReportRequestDto(id = "r", entranceId = "e", reportType = "OTHER", description = "내용")

    @Test
    fun `all pages are fetched until an empty page`() = runTest {
        val api = FakeApi(facilityCount = 250, entranceCount = 120)

        val result = SupabasePlaceRemoteSource(api, pageSize = 100).fetchAll() as DataResult.Success

        assertEquals(250, result.value.facilities.size)
        assertEquals(250, result.value.facilities.map { it.id }.toSet().size)
        assertEquals(120, result.value.entrances.size)
        assertEquals(listOf(100 to 0, 100 to 100, 100 to 200, 100 to 250), api.facilityRequests)
    }

    @Test
    fun `server row cap smaller than the page size does not truncate the dataset`() = runTest {
        val api = FakeApi(facilityCount = 250, entranceCount = 10, serverMaxRows = 60)

        val result = SupabasePlaceRemoteSource(api, pageSize = 100).fetchAll() as DataResult.Success

        assertEquals(250, result.value.facilities.size)
    }

    @Test
    fun `network and server failures are reported without partial data`() = runTest {
        val offline = SupabasePlaceRemoteSource(FakeApi(failure = IOException("offline"))).fetchAll()
        val forbidden = SupabasePlaceRemoteSource(FakeApi(failure = HttpException(error(403)))).fetchAll()

        assertEquals(DataResult.Failure(DataError.Network), offline)
        assertEquals(DataResult.Failure(DataError.Server(403)), forbidden)
    }

    @Test
    fun `created report is delivered`() = runTest {
        val outcome = SupabaseReportRemoteSource(FakeApi(reportResponse = { Response.success(201, Unit) })).send(report)

        assertEquals(ReportSendOutcome.DELIVERED, outcome)
    }

    @Test
    fun `duplicate report id means the earlier attempt already arrived`() = runTest {
        val api = FakeApi(reportResponse = { error(409, """{"code":"23505","message":"duplicate key"}""") })

        assertEquals(ReportSendOutcome.DELIVERED, SupabaseReportRemoteSource(api).send(report))
    }

    @Test
    fun `report for an entrance unknown to the server is rejected permanently`() = runTest {
        val api = FakeApi(reportResponse = { error(409, """{"code":"23503","message":"foreign key"}""") })

        assertEquals(ReportSendOutcome.REJECTED, SupabaseReportRemoteSource(api).send(report))
    }

    @Test
    fun `invalid report is rejected and temporary failures are retried later`() = runTest {
        val outcomes = listOf(400, 422, 401, 429, 500, 503).associateWith { code ->
            SupabaseReportRemoteSource(FakeApi(reportResponse = { error(code) })).send(report)
        }

        assertEquals(ReportSendOutcome.REJECTED, outcomes[400])
        assertEquals(ReportSendOutcome.REJECTED, outcomes[422])
        assertTrue(listOf(401, 429, 500, 503).all { outcomes[it] == ReportSendOutcome.RETRY_LATER })
    }

    @Test
    fun `offline report send is retried later`() = runTest {
        val api = FakeApi(failure = IOException("offline"))

        assertEquals(ReportSendOutcome.RETRY_LATER, SupabaseReportRemoteSource(api).send(report))
    }
}
