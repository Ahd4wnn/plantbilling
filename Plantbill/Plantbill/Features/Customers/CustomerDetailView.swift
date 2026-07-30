import SwiftUI

struct CustomerDetailView: View {
    let customerId: UUID
    let isManager: Bool

    @State private var viewModel: CustomerDetailViewModel

    init(customerId: UUID, isManager: Bool) {
        self.customerId = customerId
        self.isManager = isManager
        _viewModel = State(initialValue: CustomerDetailViewModel(customerId: customerId))
    }

    var body: some View {
        content
            .navigationTitle(viewModel.name ?? "Customer")
            .navigationBarTitleDisplayMode(.inline)
            .background(PlantbillColor.background)
            .task { await viewModel.load() }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading customer…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else {
            List {
                Section {
                    PlantbillCard {
                        HStack {
                            ledgerStat(title: "Total spent") {
                                Text(viewModel.totalSpent.format())
                                    .font(PlantbillTypography.headline)
                                    .foregroundStyle(PlantbillColor.textPrimary)
                            }
                            Spacer()
                            ledgerStat(title: "Bills") {
                                Text("\(viewModel.bills.count)")
                                    .font(PlantbillTypography.headline)
                                    .foregroundStyle(PlantbillColor.textPrimary)
                            }
                            Spacer()
                            ledgerStat(title: "On credit") {
                                Text("\(viewModel.creditBillCount)")
                                    .font(PlantbillTypography.headline)
                                    .foregroundStyle(viewModel.creditBillCount > 0 ? PlantbillColor.error : PlantbillColor.textPrimary)
                            }
                        }
                    }

                    Text("Purchase history")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)
                        .padding(.top, PlantbillSpacing.sm)

                    if viewModel.bills.isEmpty {
                        Text("No bills yet for this customer.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }

                    ForEach(viewModel.bills) { bill in
                        NavigationLink(value: CustomerDestination.billDetail(bill.id)) {
                            LedgerBillRow(bill: bill)
                        }
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

    private func ledgerStat(title: LocalizedStringKey, @ViewBuilder value: () -> some View) -> some View {
        VStack(spacing: 2) {
            Text(title)
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)
            value()
        }
    }
}

private struct LedgerBillRow: View {
    let bill: BillListEntry

    var body: some View {
        PlantbillCard {
            HStack {
                Image(systemName: "receipt")
                    .foregroundStyle(PlantbillColor.textSecondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(ShopCalendar.billTime(bill.createdAt))
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text("\(bill.itemCount) item\(bill.itemCount == 1 ? "" : "s") • \(paymentLabel)")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
                Spacer()
                Text(bill.totalMoney.format())
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
            }
        }
    }

    private var paymentLabel: String {
        switch bill.paymentMethod {
        case .cash: return "Cash"
        case .upi: return "UPI"
        case .split: return "Split"
        case .due: return "Due"
        }
    }
}
