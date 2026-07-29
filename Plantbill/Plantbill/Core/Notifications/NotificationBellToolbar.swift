import SwiftUI

/// Shared top-bar bell, added to every top-level tab's root screen — tap to
/// see notifications, badge shows the unread count.
private struct NotificationBellToolbar: ViewModifier {
    @Environment(NotificationsStore.self) private var store
    @State private var showingNotifications = false

    func body(content: Content) -> some View {
        content
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showingNotifications = true
                    } label: {
                        ZStack(alignment: .topTrailing) {
                            Image(systemName: "bell")
                                .font(.system(size: 19, weight: .semibold))
                                .foregroundStyle(PlantbillColor.textPrimary)
                            if store.unreadCount > 0 {
                                Circle()
                                    .fill(PlantbillColor.error)
                                    .frame(width: 9, height: 9)
                                    .offset(x: 4, y: -2)
                            }
                        }
                    }
                    .accessibilityLabel("Notifications")
                }
            }
            .sheet(isPresented: $showingNotifications) {
                NotificationsView()
            }
    }
}

extension View {
    func notificationBell() -> some View {
        modifier(NotificationBellToolbar())
    }
}
