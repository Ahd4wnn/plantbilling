package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.DetailedReportDto
import com.plantora.billing.data.remote.dto.OwnerOverviewDto
import com.plantora.billing.data.remote.dto.OwnerShopDto
import com.plantora.billing.data.remote.dto.OwnerShopUpdateDto
import com.plantora.billing.data.remote.dto.OwnerStaffCreateDto
import com.plantora.billing.data.remote.dto.OwnerStaffDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OwnerApi {
    @GET("/owner/shops")
    suspend fun shops(): List<OwnerShopDto>

    @PATCH("/owner/shops/{id}")
    suspend fun updateShop(@Path("id") id: String, @Body body: OwnerShopUpdateDto): OwnerShopDto

    @GET("/owner/overview")
    suspend fun overview(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): OwnerOverviewDto

    @GET("/owner/shops/{id}/report")
    suspend fun report(
        @Path("id") id: String,
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): DetailedReportDto

    @GET("/owner/shops/{id}/staff")
    suspend fun staff(@Path("id") id: String): List<OwnerStaffDto>

    @POST("/owner/shops/{id}/staff")
    suspend fun createStaff(@Path("id") id: String, @Body body: OwnerStaffCreateDto): OwnerStaffDto

    @POST("/owner/shops/{shopId}/staff/{userId}/reset-password")
    suspend fun resetStaffPassword(
        @Path("shopId") shopId: String,
        @Path("userId") userId: String,
        @Body body: Map<String, String>,
    )

    @DELETE("/owner/shops/{shopId}/staff/{userId}")
    suspend fun deleteStaff(@Path("shopId") shopId: String, @Path("userId") userId: String)
}
