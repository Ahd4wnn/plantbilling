package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.BillCreateDto
import com.plantora.billing.data.remote.dto.BillDetailDto
import com.plantora.billing.data.remote.dto.BillListDto
import com.plantora.billing.data.remote.dto.BillOutDto
import com.plantora.billing.data.remote.dto.BillSummaryDto
import com.plantora.billing.data.remote.dto.BillUpdateDto
import com.plantora.billing.data.remote.dto.DetailedReportDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface BillsApi {
    @POST("/bills")
    suspend fun create(@Body body: BillCreateDto): BillOutDto

    @GET("/bills/{id}")
    suspend fun detail(@Path("id") id: String): BillDetailDto

    @GET("/bills")
    suspend fun list(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("customer_id") customerId: String? = null,
        @Query("created_by") createdBy: String? = null,
        @Query("is_edited") isEdited: Boolean? = null,
        @Query("has_due") hasDue: Boolean? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): BillListDto

    @GET("/bills/summary/today")
    suspend fun summary(
        @Query("date") date: String? = null,
        @Query("created_by") createdBy: String? = null,
    ): BillSummaryDto

    @GET("/bills/summary/report")
    suspend fun report(
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("created_by") createdBy: String? = null,
    ): DetailedReportDto

    @Streaming
    @GET("/bills/summary/report/download")
    suspend fun downloadReport(
        @Query("date_from") dateFrom: String,
        @Query("date_to") dateTo: String,
        @Query("created_by") createdBy: String? = null,
    ): ResponseBody

    @PATCH("/bills/{id}")
    suspend fun update(@Path("id") id: String, @Body body: BillUpdateDto): BillDetailDto

    @DELETE("/bills/{id}")
    suspend fun delete(@Path("id") id: String)
}
