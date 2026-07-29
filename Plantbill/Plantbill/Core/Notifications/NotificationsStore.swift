import Foundation
import Observation

/// Shell-scoped notification state shared by the bell badge (on every
/// top-level tab) and the full notifications list — one fetch serves both,
/// mirroring Android's shell-scoped NotificationsViewModel.
@Observable
@MainActor
final class NotificationsStore {
    private(set) var items: [NotificationItem] = []
    private(set) var unreadCount: Int = 0
    private(set) var isLoading = false

    /// IDs that were unread when the notifications screen was opened this
    /// session — kept visually highlighted even after being marked read,
    /// matching Android's "mark all unread as read on open, keep them
    /// highlighted for the session" behavior.
    private(set) var highlightedIDs: Set<UUID> = []

    func refresh() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let response: NotificationFeedResponse = try await APIClient.shared.send(
                Endpoint(path: "notifications", queryItems: [URLQueryItem(name: "limit", value: "100")])
            )
            items = response.items
            unreadCount = response.unreadCount
        } catch {
            // Silent failure — the bell badge just stays at its last known
            // value; this is a non-critical background refresh.
        }
    }

    /// Call when the notifications list appears: marks every currently
    /// unread item as read (optimistic + fire-and-forget per item) while
    /// keeping them highlighted for the rest of this session.
    func markAllVisibleAsRead() {
        let unreadIDs = items.filter { !$0.read }.map(\.id)
        guard !unreadIDs.isEmpty else { return }

        highlightedIDs.formUnion(unreadIDs)
        items = items.map { item in
            guard !item.read else { return item }
            var updated = item
            updated = NotificationItem(
                id: item.id, title: item.title, body: item.body,
                actionUrl: item.actionUrl, read: true, createdAt: item.createdAt
            )
            return updated
        }
        unreadCount = 0

        for id in unreadIDs {
            Task {
                try? await APIClient.shared.sendNoContent(
                    Endpoint(path: "notifications/\(id)/read", method: .post)
                )
            }
        }
    }
}
