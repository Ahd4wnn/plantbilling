package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.LabourPaymentCreateDto
import com.plantora.billing.data.remote.dto.LabourPaymentDto
import com.plantora.billing.data.remote.dto.LabourPaymentUpdateDto
import com.plantora.billing.data.remote.dto.LabourerCreateDto
import com.plantora.billing.data.remote.dto.LabourerDto
import com.plantora.billing.data.remote.dto.LabourerUpdateDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface LabourApi {
    @GET("/labour/labourers")
    suspend fun listLabourers(): List<LabourerDto>

    @POST("/labour/labourers")
    suspend fun createLabourer(@Body body: LabourerCreateDto): LabourerDto

    @PATCH("/labour/labourers/{id}")
    suspend fun updateLabourer(@Path("id") id: String, @Body body: LabourerUpdateDto): LabourerDto

    @DELETE("/labour/labourers/{id}")
    suspend fun deleteLabourer(@Path("id") id: String)

    @GET("/labour/payments")
    suspend fun listPayments(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int = 0,
    ): List<LabourPaymentDto>

    @POST("/labour/payments")
    suspend fun createPayment(@Body body: LabourPaymentCreateDto): LabourPaymentDto

    @PATCH("/labour/payments/{id}")
    suspend fun updatePayment(@Path("id") id: String, @Body body: LabourPaymentUpdateDto): LabourPaymentDto

    @DELETE("/labour/payments/{id}")
    suspend fun deletePayment(@Path("id") id: String)
}
