package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.ShopSettingsDto
import com.plantora.billing.data.remote.dto.ShopSettingsUpdateDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface ShopApi {
    @GET("/shop")
    suspend fun get(): ShopSettingsDto

    @PATCH("/shop")
    suspend fun update(@Body body: ShopSettingsUpdateDto): ShopSettingsDto
}
