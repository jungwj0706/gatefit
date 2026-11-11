package com.gatefit.data.repository

import com.gatefit.data.remote.ReportSendOutcome
import com.gatefit.data.testing.FakeReportRemoteSource
import com.gatefit.data.testing.InMemoryReportLocalSource
import com.gatefit.domain.model.EntranceDirection
import com.gatefit.domain.model.EntranceReportDraft
import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.model.ReportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxReportRepositoryTest {

    private val local = InMemoryReportLocalSource()
    private val remote = FakeReportRemoteSource()
    private var now = 1_000L
    private var nextId = 0

    private fun repository(remote: FakeReportRemoteSource? = this.remote) = OutboxReportRepository(
        local = local,
        remote = remote,
        nowEpochMs = { now++ },
        newId = { "report-${++nextId}" },
        io = Dispatchers.Unconfined,
    )

    private val heightDraft = EntranceReportDraft(
        entranceId = "gate",
        type = ReportType.HEIGHT_CHANGED,
        description = "제한 높이 표지판 변경",
        proposedHeightMm = 2100,
    )

    @Test
    fun `delivered report is marked as sent`() = runTest {
        val repository = repository()

        val report = repository.submit(heightDraft)

        assertEquals(ReportDelivery.SENT, report.delivery)
        assertEquals(listOf(ReportDelivery.SENT), repository.reports.first().map { it.delivery })
        assertEquals("report-1", remote.sent.single().id)
    }

    @Test
    fun `request carries only the entrance id and what the user entered`() = runTest {
        repository().submit(heightDraft)
        repository().submit(EntranceReportDraft("gate", ReportType.WRONG_DIRECTION, null, null, EntranceDirection.EXIT))
        repository().submit(EntranceReportDraft("gate", ReportType.ENTRANCE_CLOSED, null))

        val (height, direction, closed) = remote.sent
        assertEquals("gate", height.entranceId)
        assertEquals("HEIGHT_CHANGED", height.reportType)
        assertEquals(mapOf("max_height_mm" to JsonPrimitive(2100)), height.proposedValue)
        assertEquals(mapOf("entrance_type" to JsonPrimitive("EXIT")), direction.proposedValue)
        assertNull(closed.proposedValue)
        assertNull(closed.description)
    }

    @Test
    fun `report is kept on the device when sending fails and is retried later`() = runTest {
        val repository = repository()
        remote.outcome = ReportSendOutcome.RETRY_LATER

        val failed = repository.submit(heightDraft)
        assertEquals(ReportDelivery.FAILED, failed.delivery)
        assertEquals(1, local.undelivered().size)

        remote.outcome = ReportSendOutcome.DELIVERED
        assertEquals(1, repository.retryUndelivered())

        assertTrue(local.undelivered().isEmpty())
        assertEquals(ReportDelivery.SENT, repository.reports.first().single().delivery)
        assertEquals(listOf("report-1", "report-1"), remote.sent.map { it.id })
    }

    @Test
    fun `rejected report is not retried`() = runTest {
        val repository = repository()
        remote.outcome = ReportSendOutcome.REJECTED

        val rejected = repository.submit(heightDraft)
        remote.outcome = ReportSendOutcome.DELIVERED

        assertEquals(ReportDelivery.REJECTED, rejected.delivery)
        assertEquals(0, repository.retryUndelivered())
        assertEquals(1, remote.sent.size)
    }

    @Test
    fun `without remote configuration the report stays queued and is never claimed as sent`() = runTest {
        val offline = repository(remote = null)

        val report = offline.submit(heightDraft)

        assertEquals(ReportDelivery.QUEUED, report.delivery)
        assertNull(report.lastAttemptAtEpochMs)
        assertEquals(0, offline.retryUndelivered())
        assertEquals(ReportDelivery.QUEUED, offline.reports.first().single().delivery)
    }

    @Test
    fun `queued report is sent once remote becomes configured`() = runTest {
        repository(remote = null).submit(heightDraft)

        assertEquals(1, repository().retryUndelivered())
        assertEquals(ReportDelivery.SENT, local.all().single().delivery)
    }

    @Test
    fun `reports are listed newest first`() = runTest {
        val repository = repository()

        repository.submit(heightDraft)
        repository.submit(heightDraft.copy(description = "두 번째"))

        assertEquals(listOf("두 번째", "제한 높이 표지판 변경"), repository.reports.first().map { it.description })
    }
}
