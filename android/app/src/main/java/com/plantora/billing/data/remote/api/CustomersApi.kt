package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.CustomerCreateDto
import com.plantora.billing.data.remote.dto.CustomerDto
import com.plantora.billing.data.remote.dto.CustomerLookupDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface CustomersApi {
    @GET("/customers")
    suspend fun list(): List<CustomerDto>

    @POST("/customers")
    suspend fun create(@Body body: CustomerCreateDto): CustomerDto

    @GET("/customers/lookup")
    suspend fun lookup(@Query("phone") phone: String): CustomerLookupDto
}
