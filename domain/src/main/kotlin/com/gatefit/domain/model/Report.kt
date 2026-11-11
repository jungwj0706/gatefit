package com.gatefit.domain.model

enum class ReportType {
    WRONG_LOCATION,
    ENTRANCE_CLOSED,
    HEIGHT_CHANGED,
    WRONG_DIRECTION,
    HOURS_CHANGED,
    RESTRICTION_CHANGED,
    OTHER,
}

enum class ReportStatus { PENDING, REVIEWING, APPROVED, REJECTED }

// 서버 전송 상태, 서버 검토 상태(ReportStatus)와 별개
enum class ReportDelivery { QUEUED, SENT, FAILED }

data class EntranceReportDraft(
    val entranceId: String,
    val type: ReportType,
    val description: String?,
    val proposedHeightMm: Int? = null,
    val proposedDirection: EntranceDirection? = null,
)

data class EntranceReport(
    val localId: String,
    val entranceId: String,
    val type: ReportType,
    val description: String?,
    val proposedHeightMm: Int?,
    val proposedDirection: EntranceDirection?,
    val delivery: ReportDelivery,
    val createdAtEpochMs: Long,
    val lastAttemptAtEpochMs: Long?,
)
