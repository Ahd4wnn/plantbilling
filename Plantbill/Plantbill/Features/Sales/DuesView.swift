import SwiftUI

struct DuesView: View {
    let isManager: Bool

    @State private var viewModel: DuesViewModel

    init(isManager: Bool) {
        self.isManager = isManager
        _viewModel = State(initialValue: DuesViewModel(isManager: isManager))
    }

    var body: some View {
        content
            .navigationTitle("Dues")
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
            .sheet(isPresented: Binding(get: { viewModel.settle != nil }, set: { if !$0 { viewModel.closeSettle() } })) {
                SettleSheet(viewModel: viewModel)
            }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading dues…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else if viewModel.dues.isEmpty {
            EmptyStateView(icon: "wallet.pass", title: "No dues outstanding", message: "Every bill has been paid in full.")
        } else {
            List {
                Section {
                    PlantbillCard {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Total outstanding")
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                            Text(viewModel.totalOwed.format())
                                .font(PlantbillTypography.title)
                                .foregroundStyle(PlantbillColor.error)
                            Text("\(viewModel.dues.count) customer\(viewModel.dues.count == 1 ? "" : "s")")
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    PlantbillTextField(label: "Search", text: $viewModel.query, placeholder: "Name or phone")

                    if !viewModel.hasResults {
                        Text("No matches for \"\(viewModel.query)\".")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)

                if !viewModel.priorityDues.isEmpty {
                    Section {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(PlantbillColor.error)
                            Text("Priority — 30+ days")
                                .font(PlantbillTypography.bodyEmphasized)
                                .foregroundStyle(PlantbillColor.error)
                        }
                        ForEach(viewModel.priorityDues) { entry in
                            DueRow(entry: entry, overdue: true, onCollect: { viewModel.openSettle(entry) })
                        }
                    }
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }

                if !viewModel.otherDues.isEmpty {
                    Section {
                        if !viewModel.priorityDues.isEmpty {
                            Text("Other dues")
                                .font(PlantbillTypography.bodyEmphasized)
                                .foregroundStyle(PlantbillColor.textPrimary)
                        }
                        ForEach(viewModel.otherDues) { entry in
                            DueRow(entry: entry, overdue: false, onCollect: { viewModel.openSettle(entry) })
                        }
                    }
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .refreshable { await viewModel.load() }
        }
    }
}

private struct DueRow: View {
    let entry: BillListEntry
    let overdue: Bool
    let onCollect: () -> Void

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                NavigationLink(value: SalesDestination.billDetail(entry.id)) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(entry.customerName ?? entry.customerPhone ?? "Walk-in customer")
                                .font(PlantbillTypography.bodyEmphasized)
                                .foregroundStyle(PlantbillColor.textPrimary)
                            Text("\(ShopCalendar.billTime(entry.createdAt)) • \(entry.totalMoney.format())")
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                            if overdue {
                                Text("\(entry.daysSinceCreated) days overdue")
                                    .font(PlantbillTypography.caption)
                                    .fontWeight(.semibold)
                                    .foregroundStyle(PlantbillColor.error)
                            }
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 2) {
                            Text("Owes")
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                            Text(entry.dueAmountMoney.format())
                                .font(PlantbillTypography.bodyEmphasized)
                                .foregroundStyle(PlantbillColor.error)
                        }
                    }
                }
                .buttonStyle(.plain)

                if entry.pendingSettlement {
                    HStack {
                        Image(systemName: "hourglass").foregroundStyle(PlantbillColor.green)
                        Text("Waiting for manager approval")
                            .font(PlantbillTypography.caption)
                            .fontWeight(.semibold)
                            .foregroundStyle(PlantbillColor.green)
                    }
                } else {
                    PrimaryButton(title: "Collect \(entry.dueAmountMoney.format())", action: onCollect)
                }
            }
        }
    }
}

private struct SettleSheet: View {
    @Bindable var viewModel: DuesViewModel
    @Environment(\.dismiss) private var dismiss

    private var target: SettleTarget { viewModel.settle ?? SettleTarget(entry: BillListEntry(id: UUID(), createdAt: Date(), total: "0.00", dueAmount: "0.00", customerName: nil, customerPhone: nil, itemCount: 0, paymentMethod: .due, isEdited: false, pendingSettlement: false)) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text(target.entry.customerName ?? target.entry.customerPhone ?? "Walk-in customer")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    HStack {
                        Text("Amount owed")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Spacer()
                        Text(target.entry.dueAmountMoney.format())
                            .font(PlantbillTypography.headline)
                            .foregroundStyle(PlantbillColor.error)
                    }

                    PlantbillTextField(
                        label: "Collecting now (₹)",
                        text: Binding(get: { target.amount }, set: { viewModel.settle?.amount = $0; viewModel.settle?.error = nil }),
                        placeholder: "0",
                        keyboardType: .numberPad,
                        selectAllOnFocus: true
                    )

                    Text("How was it paid?")
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    HStack(spacing: PlantbillSpacing.sm) {
                        FilterChip(title: "Cash", isSelected: target.mode == .cash) { viewModel.setSettleMode(.cash) }
                        FilterChip(title: "UPI", isSelected: target.mode == .upi) { viewModel.setSettleMode(.upi) }
                        FilterChip(title: "Split", isSelected: target.mode == .split) { viewModel.setSettleMode(.split) }
                    }

                    if target.mode == .split {
                        PlantbillTextField(
                            label: "Cash part",
                            text: Binding(get: { target.splitCash }, set: { viewModel.settle?.splitCash = $0; viewModel.settle?.error = nil }),
                            placeholder: "0",
                            keyboardType: .numberPad,
                            selectAllOnFocus: true
                        )
                        HStack {
                            Text("UPI part")
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                            Spacer()
                            Text(target.upiAmount.format())
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                        }
                    }

                    if let error = target.error {
                        InlineErrorText(message: LocalizedStringKey(error))
                    }

                    if !viewModel.isManager {
                        Text("A manager will need to approve this collection before it's applied.")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }

                    PrimaryButton(
                        title: viewModel.isManager ? "Collect \(target.total.format())" : "Send for approval",
                        isLoading: target.submitting,
                        isDisabled: !target.valid || target.submitting
                    ) {
                        Task { await viewModel.confirmSettle() }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closeSettle() }
                }
            }
        }
    }
}
