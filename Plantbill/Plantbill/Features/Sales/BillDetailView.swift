import SwiftUI

struct BillDetailView: View {
    let billId: UUID
    let isManager: Bool

    @State private var viewModel: BillDetailViewModel
    @State private var showingDeleteConfirm = false
    @State private var showingEdit = false
    @Environment(\.dismiss) private var dismiss

    init(billId: UUID, isManager: Bool) {
        self.billId = billId
        self.isManager = isManager
        _viewModel = State(initialValue: BillDetailViewModel(billId: billId))
    }

    var body: some View {
        content
            .navigationTitle("Bill")
            .navigationBarTitleDisplayMode(.inline)
            .background(PlantbillColor.background)
            .toolbar {
                if isManager, case .loaded = viewModel.state {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showingEdit = true } label: {
                            Image(systemName: "pencil")
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(role: .destructive) { showingDeleteConfirm = true } label: {
                            Image(systemName: "trash")
                                .foregroundStyle(PlantbillColor.error)
                        }
                    }
                }
            }
            .task { await viewModel.load() }
            .onChange(of: viewModel.deleted) { _, deleted in
                if deleted { dismiss() }
            }
            .confirmationDialog("Delete this bill?", isPresented: $showingDeleteConfirm, titleVisibility: .visible) {
                Button("Delete", role: .destructive) { Task { await viewModel.delete() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This can't be undone.")
            }
            .sheet(isPresented: $showingEdit) {
                BillEditView(billId: billId) {
                    showingEdit = false
                    Task { await viewModel.load() }
                }
            }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .loading:
            LoadingStateView(message: "Loading bill…")
        case .error(let message):
            ErrorStateView(message: LocalizedStringKey(message)) { Task { await viewModel.load() } }
        case .loaded(let detail):
            BillDetailBody(detail: detail, canEdit: isManager, onEdit: { showingEdit = true })
        }
    }
}

private struct BillDetailBody: View {
    let detail: BillDetail
    let canEdit: Bool
    let onEdit: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: PlantbillSpacing.md) {
                headerCard
                itemsCard
                paymentCard

                if canEdit {
                    SecondaryButton(title: "Edit bill", action: onEdit)
                }

                SecondaryButton(title: "Print receipt") {
                    ReceiptPrinter.print(detail.receiptData)
                }
            }
            .padding(PlantbillSpacing.lg)
        }
    }

    private var headerCard: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                Text(detail.businessName ?? detail.shopName ?? "Receipt")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Text(ShopCalendar.billTime(detail.createdAt))
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
                if let name = detail.customerName {
                    Text(detail.customerPhone.map { "\(name) • \($0)" } ?? name)
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.textPrimary)
                        .padding(.top, PlantbillSpacing.xs)
                }
                if detail.isEdited {
                    Text("Edited")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.error)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var itemsCard: some View {
        PlantbillCard {
            VStack(spacing: PlantbillSpacing.xs) {
                ForEach(detail.items) { item in
                    HStack {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(item.productName)
                                .font(PlantbillTypography.body)
                                .foregroundStyle(PlantbillColor.textPrimary)
                            Text("\(item.quantity) × \(item.unitPriceMoney.format())")
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                        }
                        Spacer()
                        Text(item.lineTotalMoney.format())
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textPrimary)
                    }
                    .padding(.vertical, 2)
                }

                Divider().padding(.vertical, PlantbillSpacing.xs)

                detailRow("Subtotal", detail.subtotalMoney)
                if detail.discountAmountMoney.isPositive {
                    detailRow("Discount", Money.zero - detail.discountAmountMoney)
                }
                HStack {
                    Text("Total")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Spacer()
                    Text(detail.totalMoney.format())
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.green)
                }
                .padding(.top, PlantbillSpacing.xs)
            }
        }
    }

    private var paymentCard: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                Text("Payment")
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                if detail.cashAmountMoney.isPositive { detailRow("Cash", detail.cashAmountMoney) }
                if detail.upiAmountMoney.isPositive { detailRow("UPI", detail.upiAmountMoney) }
                if detail.dueAmountMoney.isPositive { detailRow("Due", detail.dueAmountMoney, tint: PlantbillColor.error) }
                if let remarks = detail.remarks, !remarks.isEmpty {
                    Text("Remarks: \(remarks)")
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.textSecondary)
                        .padding(.top, PlantbillSpacing.xs)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func detailRow(_ label: LocalizedStringKey, _ value: Money, tint: Color = PlantbillColor.textPrimary) -> some View {
        HStack {
            Text(label)
                .font(PlantbillTypography.body)
                .foregroundStyle(PlantbillColor.textSecondary)
            Spacer()
            Text(value.format())
                .font(PlantbillTypography.body)
                .foregroundStyle(tint)
        }
    }
}
