package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.BulkPhotosResponseDto
import com.plantora.billing.data.remote.dto.ProductCreateDto
import com.plantora.billing.data.remote.dto.ProductDto
import com.plantora.billing.data.remote.dto.ProductUpdateDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ProductsApi {
    @GET("/products")
    suspend fun list(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null,
        @Query("active") active: String = "true",
    ): List<ProductDto>

    @POST("/products")
    suspend fun create(@Body body: ProductCreateDto): ProductDto

    @PATCH("/products/{id}")
    suspend fun update(@Path("id") id: String, @Body body: ProductUpdateDto): ProductDto

    @DELETE("/products/{id}")
    suspend fun delete(@Path("id") id: String)

    @Multipart
    @POST("/products/{id}/image")
    suspend fun uploadImage(@Path("id") id: String, @Part file: MultipartBody.Part): ProductDto

    @DELETE("/products/{id}/image")
    suspend fun deleteImage(@Path("id") id: String): ProductDto

    @Multipart
    @POST("/products/bulk-upload")
    suspend fun bulkUpload(@Part file: MultipartBody.Part): List<ProductDto>

    @Multipart
    @POST("/products/bulk-photos")
    suspend fun bulkPhotos(@Part file: MultipartBody.Part): BulkPhotosResponseDto

    @Streaming
    @GET("/products/sample-file")
    suspend fun sampleFile(): ResponseBody
}
