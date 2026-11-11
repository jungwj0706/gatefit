package com.gatefit.data.remote

import com.gatefit.data.dto.DataSourceDto
import com.gatefit.data.dto.EntranceDto
import com.gatefit.data.dto.FacilityDto
import com.gatefit.data.dto.GatefitJson
import com.gatefit.data.dto.ReportRequestDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface SupabaseApi {

    @GET("rest/v1/data_sources?select=*&order=id.asc")
    suspend fun dataSources(@Query("limit") limit: Int, @Query("offset") offset: Int): List<DataSourceDto>

    @GET("rest/v1/facilities?select=*&order=id.asc")
    suspend fun facilities(@Query("limit") limit: Int, @Query("offset") offset: Int): List<FacilityDto>

    @GET("rest/v1/entrances?select=*&order=id.asc")
    suspend fun entrances(@Query("limit") limit: Int, @Query("offset") offset: Int): List<EntranceDto>

    // 익명 사용자는 제보 조회 권한이 없으므로 저장 결과를 돌려받지 않음
    @Headers("Prefer: return=minimal")
    @POST("rest/v1/entrance_reports")
    suspend fun createReport(@Body report: ReportRequestDto): Response<Unit>

    companion object {
        fun create(config: SupabaseConfig): SupabaseApi {
            // 요청·응답 본문과 인증 헤더가 로그에 남지 않도록 로깅 인터셉터는 사용하지 않음
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("apikey", config.anonKey)
                        .header("Authorization", "Bearer ${config.anonKey}")
                        .build()
                    chain.proceed(request)
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(client)
                .addConverterFactory(GatefitJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
                .build()
                .create(SupabaseApi::class.java)
        }
    }
}
