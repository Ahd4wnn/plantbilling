package com.plantora.billing.data.remote.api

import com.plantora.billing.data.remote.dto.NotificationFeedDto
import com.plantora.billing.data.remote.dto.StatusDto
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface NotificationsApi {
    @GET("/notifications")
    suspend fun feed(): NotificationFeedDto

    @POST("/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): StatusDto
}
