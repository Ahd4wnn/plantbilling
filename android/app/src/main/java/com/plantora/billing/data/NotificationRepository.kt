package com.plantora.billing.data

import com.plantora.billing.data.remote.api.NotificationsApi
import com.plantora.billing.data.remote.dto.NotificationDto
import com.plantora.billing.domain.Notification
import javax.inject.Inject
import javax.inject.Singleton

/** Feed of admin-authored notifications for the current shop user + read state. */
data class NotificationFeed(
    val items: List<Notification>,
    val unreadCount: Int,
)

@Singleton
class NotificationRepository @Inject constructor(
    private val api: NotificationsApi,
) {
    suspend fun feed(): NotificationFeed {
        val dto = api.feed()
        return NotificationFeed(
            items = dto.items.map { it.toDomain() },
            unreadCount = dto.unreadCount,
        )
    }

    suspend fun markRead(id: String) {
        api.markRead(id)
    }
}

private fun NotificationDto.toDomain() = Notification(
    id = id,
    title = title,
    body = body,
    actionUrl = actionUrl,
    read = read,
    createdAt = createdAt,
)
