import SwiftUI

struct ApprovalsView: View {
    @State private var viewModel = ApprovalsViewModel()

    var body: some View {
        content
            .navigationTitle("Approvals")
            .navigationBarTitleDisplayMode(.inline)
            .background(PlantbillColor.background)
            .task { await viewModel.load() }
            .alert("Something went wrong", isPresented: Binding(
                get: { viewModel.message != nil },
                set: { if !$0 { viewModel.dismissMessage() } }
            )) {
                Button("OK") { viewModel.dismissMessage() }
            } message: {
                Text(viewModel.message ?? "")
            }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading approvals…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else if viewModel.items.isEmpty {
            EmptyStateView(icon: "checkmark.seal", title: "Nothing to approve", message: "Salesperson due-collections will show up here.")
        } else {
            List {
                Section {
                    Text("\(viewModel.items.count) collection\(viewModel.items.count == 1 ? "" : "s") waiting")
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.textSecondary)

                    ForEach(viewModel.items) { item in
                        ApprovalRow(
                            item: item,
                            acting: viewModel.actingId == item.id,
                            onApprove: { Task { await viewModel.approve(item) } },
                            onReject: { Task { await viewModel.reject(item) } }
                        )
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable { await viewModel.load() }
        }
    }
}

private struct ApprovalRow: View {
    let item: PendingSettlement
    let acting: Bool
    let onApprove: () -> Void
    let onReject: () -> Void

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(item.customerName ?? item.customerPhone ?? "Walk-in customer")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Text([ShopCalendar.billTime(item.createdAt), item.requestedByEmail].compactMap { $0 }.joined(separator: " • "))
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("Collecting")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Text(item.amountMoney.format())
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                    }
                }
                Text("Cash \(item.cashAmountMoney.format()) • UPI \(item.upiAmountMoney.format())")
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.textSecondary)
                HStack(spacing: PlantbillSpacing.sm) {
                    SecondaryButton(title: "Reject", tint: PlantbillColor.error, isDisabled: acting, action: onReject)
                    PrimaryButton(title: "Approve", isLoading: acting, isDisabled: acting, action: onApprove)
                }
            }
        }
    }
}
