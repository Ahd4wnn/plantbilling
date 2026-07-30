import SwiftUI

/// Mirrors Android's `Tab` enum + `tabsFor(role)`/`homeTabFor(role)`:
/// managers don't personally bill (Products instead), salespeople don't
/// manage the catalog (Bill instead) — both get Sales/Customers/More.
private enum AppTab: String, CaseIterable {
    case bill, products, sales, customers, more

    var title: LocalizedStringKey {
        switch self {
        case .bill: return "Bill"
        case .products: return "Products"
        case .sales: return "Sales"
        case .customers: return "Customers"
        case .more: return "More"
        }
    }

    var icon: String {
        switch self {
        case .bill: return "cart.fill"
        case .products: return "leaf.fill"
        case .sales: return "chart.bar.fill"
        case .customers: return "person.2.fill"
        case .more: return "ellipsis.circle.fill"
        }
    }

    static func tabs(for role: Role) -> [AppTab] {
        switch role {
        case .manager: return [.products, .sales, .customers, .more]
        case .salesperson: return [.bill, .sales, .customers, .more]
        default: return [.more]
        }
    }

    static func home(for role: Role) -> AppTab {
        role == .manager ? .products : .bill
    }
}

struct MainTabView: View {
    let user: CurrentUser

    @State private var selection: AppTab
    @State private var notificationsStore = NotificationsStore()
    @Environment(\.scenePhase) private var scenePhase

    init(user: CurrentUser) {
        self.user = user
        _selection = State(initialValue: AppTab.home(for: user.role))
    }

    var body: some View {
        TabView(selection: $selection) {
            ForEach(AppTab.tabs(for: user.role), id: \.self) { tab in
                tabContent(tab)
                    .tabItem { Label(tab.title, systemImage: tab.icon) }
                    .tag(tab)
            }
        }
        .tint(PlantbillColor.green)
        .environment(notificationsStore)
        .task { await notificationsStore.refresh() }
        .onChange(of: scenePhase) { _, newPhase in
            if newPhase == .active {
                Task { await notificationsStore.refresh() }
            }
        }
    }

    @ViewBuilder
    private func tabContent(_ tab: AppTab) -> some View {
        switch tab {
        case .bill:
            BillView()
        case .products:
            ProductsListView(canManage: user.role == .manager)
        case .sales:
            SalesView(isManager: user.role == .manager)
        case .customers:
            CustomersView(isManager: user.role == .manager)
        case .more:
            MoreView(user: user)
        }
    }
}
