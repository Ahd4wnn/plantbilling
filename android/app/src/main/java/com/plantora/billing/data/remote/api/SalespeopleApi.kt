package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.SalespersonActivateDto
import com.plantora.billing.data.remote.dto.SalespersonCreateDto
import com.plantora.billing.data.remote.dto.SalespersonDto
import com.plantora.billing.data.remote.dto.SalespersonResetPasswordDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** Shop-owner-only staff management (`/shop/users`). */
interface SalespeopleApi {
    @GET("/shop/users")
    suspend fun list(): List<SalespersonDto>

    @POST("/shop/users")
    suspend fun create(@Body body: SalespersonCreateDto): SalespersonDto

    @PATCH("/shop/users/{id}")
    suspend fun setActive(@Path("id") id: String, @Body body: SalespersonActivateDto): SalespersonDto

    @POST("/shop/users/{id}/reset-password")
    suspend fun resetPassword(@Path("id") id: String, @Body body: SalespersonResetPasswordDto): SalespersonDto

    @DELETE("/shop/users/{id}")
    suspend fun delete(@Path("id") id: String)
}
