package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.BorrowingCreateDto
import com.plantora.billing.data.remote.dto.BorrowingDto
import com.plantora.billing.data.remote.dto.BorrowingListDto
import com.plantora.billing.data.remote.dto.BorrowingPayDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BorrowingsApi {
    @GET("/borrowings")
    suspend fun list(@Query("status") status: String = "all"): BorrowingListDto

    @POST("/borrowings")
    suspend fun create(@Body body: BorrowingCreateDto): BorrowingDto

    @POST("/borrowings/{id}/pay")
    suspend fun pay(@Path("id") id: String, @Body body: BorrowingPayDto): BorrowingDto

    @DELETE("/borrowings/{id}")
    suspend fun delete(@Path("id") id: String)
}
