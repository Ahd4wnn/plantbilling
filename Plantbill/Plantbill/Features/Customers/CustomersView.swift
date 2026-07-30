import SwiftUI

enum CustomerDestination: Hashable {
    case detail(UUID)
    case billDetail(UUID)
}

struct CustomersView: View {
    let isManager: Bool

    @State private var viewModel = CustomersViewModel()
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            content
                .navigationTitle("Customers")
                .navigationBarTitleDisplayMode(.inline)
                .background(PlantbillColor.background)
                .searchable(text: $viewModel.query, prompt: Text("Name or phone"))
                .navigationDestination(for: CustomerDestination.self) { destination in
                    switch destination {
                    case .detail(let id):
                        CustomerDetailView(customerId: id, isManager: isManager)
                    case .billDetail(let id):
                        BillDetailView(billId: id, isManager: isManager)
                    }
                }
                .task { await viewModel.load() }
                .notificationBell()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingStateView(message: "Loading customers…")
        case .error(let message):
            ErrorStateView(message: LocalizedStringKey(message)) { Task { await viewModel.load() } }
        case .loaded:
            if viewModel.visible.isEmpty {
                EmptyStateView(
                    icon: "person.2",
                    title: "No customers yet",
                    message: viewModel.query.isEmpty
                        ? "Customers you enter while billing will show up here."
                        : "No matches for \"\(viewModel.query)\"."
                )
            } else {
                List(viewModel.visible) { customer in
                    CustomerRow(customer: customer)
                        .contentShape(Rectangle())
                        .onTapGesture { path.append(CustomerDestination.detail(customer.id)) }
                        .listRowSeparator(.hidden)
                        .listRowBackground(Color.clear)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
                .refreshable { await viewModel.load() }
            }
        }
    }
}

private struct CustomerRow: View {
    let customer: Customer

    var body: some View {
        PlantbillCard {
            HStack(spacing: PlantbillSpacing.md) {
                Circle()
                    .fill(PlantbillColor.greenTint)
                    .frame(width: 44, height: 44)
                    .overlay {
                        Text(customer.name.prefix(1).uppercased())
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.green)
                    }
                VStack(alignment: .leading, spacing: 2) {
                    Text(customer.name)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                        .lineLimit(1)
                    Text(customer.phone ?? "No phone on file")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundStyle(PlantbillColor.textSecondary)
            }
        }
    }
}
