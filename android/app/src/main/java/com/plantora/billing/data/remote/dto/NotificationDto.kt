package com.plantora.billing.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val title: String,
    val body: String,
    @SerialName("action_url") val actionUrl: String? = null,
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class NotificationFeedDto(
    val items: List<NotificationDto> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
data class StatusDto(
    val status: String? = null,
)
