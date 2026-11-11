package com.gatefit.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gatefit.R
import com.gatefit.domain.model.OperatingHours
import com.gatefit.domain.model.TimeRange
import com.gatefit.domain.usecase.AnalyzeEntranceCompatibilityUseCase
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Formatters {
    private val numberFormat: NumberFormat get() = NumberFormat.getIntegerInstance(Locale.KOREA)
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    fun number(value: Int): String = numberFormat.format(value)

    fun signedNumber(value: Int): String = if (value > 0) "+${number(value)}" else number(value)

    fun date(instant: Instant): String =
        dateFormat.format(instant.atZone(AnalyzeEntranceCompatibilityUseCase.FACILITY_ZONE))

    fun date(date: LocalDate): String = dateFormat.format(date)

    fun time(time: LocalTime): String = timeFormat.format(time)

    fun timeRange(range: TimeRange): String = "${time(range.start)}~${time(range.end)}"

    fun coordinate(value: Double): String = String.format(Locale.US, "%.5f", value)
}

@Composable
fun mmText(value: Int?): String =
    if (value == null) stringResource(R.string.value_unknown) else stringResource(R.string.format_mm, Formatters.number(value))

@Composable
fun kgText(value: Int?): String =
    if (value == null) stringResource(R.string.value_unknown) else stringResource(R.string.format_kg, Formatters.number(value))

// 운영 시간 미확인을 상시 개방처럼 보이지 않도록 정보 없음으로 표기
@Composable
fun OperatingHours.text(): String = when (this) {
    OperatingHours.Unknown -> stringResource(R.string.value_not_provided)
    OperatingHours.AllDay -> stringResource(R.string.hours_all_day)
    OperatingHours.Closed -> stringResource(R.string.hours_closed)
    is OperatingHours.Ranges -> ranges.joinToString(", ") { Formatters.timeRange(it) }
}
