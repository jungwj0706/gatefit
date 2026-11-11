package com.gatefit.data.repository

import com.gatefit.data.dto.ReportRequestDto
import com.gatefit.data.local.ReportLocalSource
import com.gatefit.data.remote.ReportRemoteSource
import com.gatefit.data.remote.ReportSendOutcome
import com.gatefit.domain.model.EntranceReport
import com.gatefit.domain.model.EntranceReportDraft
import com.gatefit.domain.model.ReportDelivery
import com.gatefit.domain.repository.ReportRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class OutboxReportRepository(
    private val local: ReportLocalSource,
    private val remote: ReportRemoteSource?,
    private val nowEpochMs: () -> Long,
    private val newId: () -> String,
    private val io: CoroutineDispatcher,
) : ReportRepository {

    private val state = MutableStateFlow<List<EntranceReport>?>(null)
    private val deliveryLock = Mutex()

    override val reports: Flow<List<EntranceReport>> = flow {
        if (state.value == null) reload()
        emitAll(state.filterNotNull())
    }

    override suspend fun submit(draft: EntranceReportDraft): EntranceReport {
        val queued = EntranceReport(
            localId = newId(),
            entranceId = draft.entranceId,
            type = draft.type,
            description = draft.description,
            proposedHeightMm = draft.proposedHeightMm,
            proposedDirection = draft.proposedDirection,
            delivery = ReportDelivery.QUEUED,
            createdAtEpochMs = nowEpochMs(),
            lastAttemptAtEpochMs = null,
        )
        // 전송 실패나 앱 종료에도 제보가 사라지지 않도록 기기에 먼저 저장
        withContext(io) { local.insert(queued) }
        val delivered = deliveryLock.withLock { deliver(queued) }
        reload()
        return delivered
    }

    override suspend fun retryUndelivered(): Int {
        if (remote == null) return 0
        val sent = deliveryLock.withLock {
            withContext(io) { local.undelivered() }.count { deliver(it).delivery == ReportDelivery.SENT }
        }
        reload()
        return sent
    }

    private suspend fun deliver(report: EntranceReport): EntranceReport {
        val source = remote ?: return report
        val delivery = when (source.send(report.toRequest())) {
            ReportSendOutcome.DELIVERED -> ReportDelivery.SENT
            ReportSendOutcome.REJECTED -> ReportDelivery.REJECTED
            ReportSendOutcome.RETRY_LATER -> ReportDelivery.FAILED
        }
        val attemptAt = nowEpochMs()
        withContext(io) { local.updateDelivery(report.localId, delivery, attemptAt) }
        return report.copy(delivery = delivery, lastAttemptAtEpochMs = attemptAt)
    }

    private suspend fun reload() {
        state.value = withContext(io) { local.all() }
    }
}

// 제보에는 차량 식별 정보나 위치를 담지 않고 출입구 ID와 사용자가 입력한 내용만 전송
fun EntranceReport.toRequest(): ReportRequestDto {
    val proposed = buildMap {
        proposedHeightMm?.let { put("max_height_mm", JsonPrimitive(it)) }
        proposedDirection?.let { put("entrance_type", JsonPrimitive(it.name)) }
    }
    return ReportRequestDto(
        id = localId,
        entranceId = entranceId,
        reportType = type.name,
        description = description,
        proposedValue = proposed.takeIf { it.isNotEmpty() }?.let(::JsonObject),
    )
}
