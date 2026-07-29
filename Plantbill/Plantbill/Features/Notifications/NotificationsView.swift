import SwiftUI

struct NotificationsView: View {
    @Environment(NotificationsStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            Group {
                if store.isLoading && store.items.isEmpty {
                    LoadingStateView(message: "Loading notifications…")
                } else if store.items.isEmpty {
                    EmptyStateView(
                        icon: "bell",
                        title: "No notifications yet",
                        message: "Updates and announcements will appear here."
                    )
                } else {
                    List(store.items) { item in
                        NotificationRow(
                            item: item,
                            isHighlighted: store.highlightedIDs.contains(item.id)
                        ) {
                            if let actionUrl = item.actionUrl, let url = URL(string: actionUrl) {
                                openURL(url)
                            }
                        }
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: PlantbillSpacing.xs, leading: PlantbillSpacing.md, bottom: PlantbillSpacing.xs, trailing: PlantbillSpacing.md))
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .refreshable { await store.refresh() }
                }
            }
            .background(PlantbillColor.background)
            .navigationTitle("Notifications")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") { dismiss() }
                }
            }
            .task {
                await store.refresh()
                store.markAllVisibleAsRead()
            }
        }
    }
}

private struct NotificationRow: View {
    let item: NotificationItem
    let isHighlighted: Bool
    let onOpenAction: () -> Void

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                HStack {
                    Text(item.title)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    if isHighlighted {
                        Circle()
                            .fill(PlantbillColor.green)
                            .frame(width: 8, height: 8)
                    }
                    Spacer()
                    Text(item.createdAt, style: .relative)
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
                Text(item.body)
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
                if item.actionUrl != nil {
                    Button("Open", action: onOpenAction)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.green)
                        .padding(.top, PlantbillSpacing.xs)
                }
            }
        }
    }
}

#Preview {
    NotificationsView()
        .environment(NotificationsStore())
}
