package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.CurrentUserDto
import com.plantora.billing.data.remote.dto.LoginRequestDto
import com.plantora.billing.data.remote.dto.TokenDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("/auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenDto

    @GET("/auth/me")
    suspend fun me(): CurrentUserDto
}
