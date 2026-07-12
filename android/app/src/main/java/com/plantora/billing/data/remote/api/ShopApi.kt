package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.CashInHandOutDto
import com.plantora.billing.data.remote.dto.CashInHandSetDto
import com.plantora.billing.data.remote.dto.ShopSettingsDto
import com.plantora.billing.data.remote.dto.ShopSettingsUpdateDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

interface ShopApi {
    @GET("/shop")
    suspend fun get(): ShopSettingsDto

    @PATCH("/shop")
    suspend fun update(@Body body: ShopSettingsUpdateDto): ShopSettingsDto

    /** Manager/admin: reset the running cash-in-hand to an exact amount, now. */
    @POST("/shop/cash-in-hand")
    suspend fun setCashInHand(@Body body: CashInHandSetDto): CashInHandOutDto
}
