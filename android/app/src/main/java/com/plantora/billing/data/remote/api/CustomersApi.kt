package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.CustomerCreateDto
import com.plantora.billing.data.remote.dto.CustomerDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface CustomersApi {
    @GET("/customers")
    suspend fun list(): List<CustomerDto>

    @POST("/customers")
    suspend fun create(@Body body: CustomerCreateDto): CustomerDto
}
