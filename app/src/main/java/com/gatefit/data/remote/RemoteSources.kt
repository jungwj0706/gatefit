package com.gatefit.data.remote

import com.gatefit.core.result.DataError
import com.gatefit.core.result.DataResult
import com.gatefit.data.dto.DataSourceDto
import com.gatefit.data.dto.EntranceDto
import com.gatefit.data.dto.FacilityDto
import com.gatefit.data.dto.GatefitJson
import com.gatefit.data.dto.RemoteErrorDto
import com.gatefit.data.dto.ReportRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

data class RemoteDataset(
    val sources: List<DataSourceDto>,
    val facilities: List<FacilityDto>,
    val entrances: List<EntranceDto>,
)

interface PlaceRemoteSource {
    suspend fun fetchAll(): DataResult<RemoteDataset>
}

enum class ReportSendOutcome { DELIVERED, REJECTED, RETRY_LATER }

interface ReportRemoteSource {
    suspend fun send(request: ReportRequestDto): ReportSendOutcome
}

class SupabasePlaceRemoteSource(
    private val api: SupabaseApi,
    private val pageSize: Int = 1000,
) : PlaceRemoteSource {

    override suspend fun fetchAll(): DataResult<RemoteDataset> = try {
        DataResult.Success(
            RemoteDataset(
                sources = paged(api::dataSources),
                facilities = paged(api::facilities),
                entrances = paged(api::entrances),
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        DataResult.Failure(DataError.Server(e.code()))
    } catch (_: IOException) {
        DataResult.Failure(DataError.Network)
    } catch (_: SerializationException) {
        DataResult.Failure(DataError.Invalid("response_format"))
    } catch (_: IncompleteResponseException) {
        DataResult.Failure(DataError.Invalid("page_limit_exceeded"))
    }

    // 서버의 최대 응답 행 수 설정이 요청 크기보다 작아도 누락되지 않도록 빈 페이지가 나올 때까지 조회
    private suspend fun <T> paged(fetch: suspend (limit: Int, offset: Int) -> List<T>): List<T> {
        val rows = ArrayList<T>()
        repeat(MAX_PAGES) {
            val page = fetch(pageSize, rows.size)
            if (page.isEmpty()) return rows
            rows += page
        }
        throw IncompleteResponseException()
    }

    private class IncompleteResponseException : Exception()

    private companion object {
        const val MAX_PAGES = 200
    }
}

class SupabaseReportRemoteSource(private val api: SupabaseApi) : ReportRemoteSource {

    override suspend fun send(request: ReportRequestDto): ReportSendOutcome = try {
        classify(api.createReport(request))
    } catch (e: CancellationException) {
        throw e
    } catch (_: IOException) {
        ReportSendOutcome.RETRY_LATER
    } catch (_: SerializationException) {
        ReportSendOutcome.RETRY_LATER
    }

    private fun classify(response: Response<Unit>): ReportSendOutcome = when {
        response.isSuccessful -> ReportSendOutcome.DELIVERED
        // 같은 ID의 제보가 이미 저장된 경우, 이전 전송이 성공했지만 응답을 받지 못한 상황
        response.code() == 409 && errorCode(response) == UNIQUE_VIOLATION -> ReportSendOutcome.DELIVERED
        response.code() in PERMANENT_FAILURES -> ReportSendOutcome.REJECTED
        else -> ReportSendOutcome.RETRY_LATER
    }

    private fun errorCode(response: Response<Unit>): String? = try {
        response.errorBody()?.string()?.let { GatefitJson.decodeFromString(RemoteErrorDto.serializer(), it).code }
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
        val PERMANENT_FAILURES = setOf(400, 404, 409, 422)
    }
}
