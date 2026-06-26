package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.ExpenseCreateDto
import com.plantora.billing.data.remote.dto.ExpenseDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ExpensesApi {
    @GET("/expenses")
    suspend fun list(
        @Query("date_from") dateFrom: String? = null,
        @Query("date_to") dateTo: String? = null,
    ): List<ExpenseDto>

    @POST("/expenses")
    suspend fun create(@Body body: ExpenseCreateDto): ExpenseDto

    @PATCH("/expenses/{id}")
    suspend fun update(@Path("id") id: String, @Body body: ExpenseCreateDto): ExpenseDto

    @DELETE("/expenses/{id}")
    suspend fun delete(@Path("id") id: String)
}
