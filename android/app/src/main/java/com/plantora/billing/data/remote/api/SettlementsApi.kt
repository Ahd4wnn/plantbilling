package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.SettlementActionResultDto
import com.plantora.billing.data.remote.dto.SettlementCreateDto
import com.plantora.billing.data.remote.dto.SettlementListDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SettlementsApi {
    /** Collect a due. Salesperson → pending request; manager/admin → applied now. */
    @POST("/settlements")
    suspend fun create(@Body body: SettlementCreateDto): SettlementActionResultDto

    /** The manager's approval queue. */
    @GET("/settlements/pending")
    suspend fun pending(): SettlementListDto

    @POST("/settlements/{id}/approve")
    suspend fun approve(@Path("id") id: String): SettlementActionResultDto

    @POST("/settlements/{id}/reject")
    suspend fun reject(@Path("id") id: String): SettlementActionResultDto
}
