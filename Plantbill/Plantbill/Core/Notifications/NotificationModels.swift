import Foundation

/// Mirrors backend `NotificationOut` / `NotificationFeedOut`
/// (app/schemas/notification.py). No push (FCM) exists on Android either —
/// this is a pure pull/poll model, refreshed on foreground.
struct NotificationItem: Decodable, Identifiable, Equatable {
    let id: UUID
    let title: String
    let body: String
    let actionUrl: String?
    let read: Bool
    let createdAt: Date
}

struct NotificationFeedResponse: Decodable {
    let items: [NotificationItem]
    let unreadCount: Int
}
