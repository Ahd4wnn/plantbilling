import SwiftUI

struct BorrowingsView: View {
    @State private var viewModel = BorrowingsViewModel()
    @State private var pendingDelete: Borrowing?

    var body: some View {
        content
            .navigationTitle("Borrowings")
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
            .sheet(isPresented: Binding(get: { viewModel.addEditor != nil }, set: { if !$0 { viewModel.closeAdd() } })) {
                AddBorrowingSheet(viewModel: viewModel)
            }
            .sheet(isPresented: Binding(get: { viewModel.payEditor != nil }, set: { if !$0 { viewModel.closePay() } })) {
                PayBorrowingSheet(viewModel: viewModel)
            }
            .confirmationDialog(
                "Delete this borrowing?",
                isPresented: Binding(get: { pendingDelete != nil }, set: { if !$0 { pendingDelete = nil } }),
                titleVisibility: .visible
            ) {
                Button("Delete", role: .destructive) {
                    if let b = pendingDelete { Task { await viewModel.delete(b.id) } }
                    pendingDelete = nil
                }
                Button("Cancel", role: .cancel) { pendingDelete = nil }
            }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading borrowings…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else {
            List {
                Section {
                    PlantbillCard {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Still owed to lenders")
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                            Text(viewModel.totalOutstanding.format())
                                .font(PlantbillTypography.title)
                                .foregroundStyle(PlantbillColor.error)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }

                    HStack(spacing: PlantbillSpacing.sm) {
                        FilterChip(title: "All", isSelected: viewModel.filter == .all) { viewModel.setFilter(.all) }
                        FilterChip(title: "Open", isSelected: viewModel.filter == .open) { viewModel.setFilter(.open) }
                        FilterChip(title: "Paid", isSelected: viewModel.filter == .paid) { viewModel.setFilter(.paid) }
                    }

                    PrimaryButton(title: "Add borrowing") { viewModel.openAdd() }

                    if viewModel.items.isEmpty {
                        Text(viewModel.filter == .paid ? "No paid-off borrowings yet." : "No open borrowings.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }

                    ForEach(viewModel.items) { b in
                        BorrowingRow(borrowing: b, onPay: { viewModel.openPay(b) }, onDelete: { pendingDelete = b })
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

private func methodLabel(_ method: String) -> String {
    switch method {
    case "cash": return "Cash"
    case "upi": return "UPI"
    case "split": return "Split"
    default: return method
    }
}

private struct BorrowingRow: View {
    let borrowing: Borrowing
    let onPay: () -> Void
    let onDelete: () -> Void

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.md) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: PlantbillSpacing.sm) {
                            Text(borrowing.lenderName)
                                .font(PlantbillTypography.bodyEmphasized)
                                .foregroundStyle(PlantbillColor.textPrimary)
                            if borrowing.isPaid {
                                Text("Paid")
                                    .font(PlantbillTypography.caption)
                                    .fontWeight(.bold)
                                    .foregroundStyle(PlantbillColor.green)
                            } else if borrowing.outstandingMoney < borrowing.amountMoney {
                                Text("Partly paid")
                                    .font(PlantbillTypography.caption)
                                    .fontWeight(.bold)
                                    .foregroundStyle(PlantbillColor.warning)
                            }
                        }
                        if let phone = borrowing.lenderPhone, !phone.isEmpty {
                            Text(phone)
                                .font(PlantbillTypography.caption)
                                .foregroundStyle(PlantbillColor.textSecondary)
                        }
                        Text("\(ShopCalendar.billTime(borrowing.createdAt)) • Received via \(methodLabel(borrowing.method))" + (borrowing.isPaid ? " • Paid via \(methodLabel(borrowing.paidMethod))" : ""))
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        if let remarks = borrowing.remarks, !remarks.isEmpty {
                            Text(remarks)
                                .font(PlantbillTypography.body)
                                .foregroundStyle(PlantbillColor.textPrimary)
                        }
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(borrowing.amountMoney.format())
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(borrowing.isPaid ? PlantbillColor.textSecondary : PlantbillColor.textPrimary)
                            .strikethrough(borrowing.isPaid)
                        if !borrowing.isPaid && borrowing.outstandingMoney < borrowing.amountMoney {
                            Text("\(borrowing.outstandingMoney.format()) left")
                                .font(PlantbillTypography.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(PlantbillColor.error)
                        }
                    }
                }

                HStack(spacing: PlantbillSpacing.sm) {
                    if !borrowing.isPaid {
                        PrimaryButton(title: "Pay back", action: onPay)
                    }
                    SecondaryButton(title: "Delete", tint: PlantbillColor.error, action: onDelete)
                }
            }
        }
    }
}

private struct AddBorrowingSheet: View {
    @Bindable var viewModel: BorrowingsViewModel
    @Environment(\.dismiss) private var dismiss

    private var editor: AddEditor { viewModel.addEditor ?? AddEditor() }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text("Add borrowing")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    PlantbillTextField(label: "Borrowed from (name)", text: Binding(get: { editor.name }, set: { viewModel.addEditor?.name = $0; viewModel.addEditor?.error = nil }))
                    PlantbillTextField(label: "Phone (optional)", text: Binding(get: { editor.phone }, set: { viewModel.addEditor?.phone = $0 }), keyboardType: .phonePad)
                    PlantbillTextField(label: "Amount (₹)", text: Binding(get: { editor.amount }, set: { viewModel.addEditor?.amount = $0; viewModel.addEditor?.error = nil }), placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)

                    MethodPicker(
                        mode: editor.mode, total: editor.total, splitCash: editor.splitCash,
                        onMode: { viewModel.setAddMode($0) },
                        onSplitCash: { viewModel.addEditor?.splitCash = $0; viewModel.addEditor?.error = nil }
                    )

                    PlantbillTextField(label: "Remarks (optional)", text: Binding(get: { editor.remarks }, set: { viewModel.addEditor?.remarks = $0 }))

                    if let error = editor.error {
                        InlineErrorText(message: LocalizedStringKey(error))
                    }

                    PrimaryButton(title: "Save", isLoading: editor.saving, isDisabled: !editor.canSave) {
                        Task { await viewModel.saveAdd() }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closeAdd() }
                }
            }
        }
    }
}

private struct PayBorrowingSheet: View {
    @Bindable var viewModel: BorrowingsViewModel
    @Environment(\.dismiss) private var dismiss

    private var editor: PayEditor { viewModel.payEditor ?? PayEditor(borrowing: Borrowing(id: UUID(), lenderName: "", lenderPhone: nil, amount: "0.00", cashAmount: "0.00", upiAmount: "0.00", method: "none", remarks: nil, isPaid: false, paidCashAmount: "0.00", paidUpiAmount: "0.00", paidMethod: "none", outstanding: "0.00", paidAt: nil, createdAt: Date())) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text("Pay back \(editor.borrowing.lenderName)")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    HStack {
                        Text("Still owed")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Spacer()
                        Text(editor.borrowing.outstandingMoney.format())
                            .font(PlantbillTypography.headline)
                            .foregroundStyle(PlantbillColor.error)
                    }
                    .padding(PlantbillSpacing.md)
                    .background(RoundedRectangle(cornerRadius: PlantbillSpacing.controlCornerRadius).fill(PlantbillColor.greenTint))

                    PlantbillTextField(label: "Paying now (₹)", text: Binding(get: { editor.amount }, set: { viewModel.payEditor?.amount = $0; viewModel.payEditor?.error = nil }), placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)
                    if editor.total > editor.borrowing.outstandingMoney {
                        InlineErrorText(message: LocalizedStringKey("Can't be more than \(editor.borrowing.outstandingMoney.format())."))
                    }

                    MethodPicker(
                        mode: editor.mode, total: editor.total, splitCash: editor.splitCash,
                        onMode: { viewModel.setPayMode($0) },
                        onSplitCash: { viewModel.payEditor?.splitCash = $0; viewModel.payEditor?.error = nil }
                    )

                    if let error = editor.error {
                        InlineErrorText(message: LocalizedStringKey(error))
                    }

                    PrimaryButton(title: "Pay \(editor.total.format())", isLoading: editor.saving, isDisabled: !editor.canSave) {
                        Task { await viewModel.savePay() }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closePay() }
                }
            }
        }
    }
}

private struct MethodPicker: View {
    let mode: BorrowMode
    let total: Money
    let splitCash: String
    let onMode: (BorrowMode) -> Void
    let onSplitCash: (String) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
            Text("Method")
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)
            HStack(spacing: PlantbillSpacing.sm) {
                FilterChip(title: "Cash", isSelected: mode == .cash) { onMode(.cash) }
                FilterChip(title: "UPI", isSelected: mode == .upi) { onMode(.upi) }
                FilterChip(title: "Split", isSelected: mode == .split) { onMode(.split) }
            }
            if mode == .split {
                let cash = min(Money.parse(splitCash), total)
                PlantbillTextField(label: "Cash part", text: Binding(get: { splitCash }, set: onSplitCash), placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)
                Text("UPI part: \((total - cash).format())")
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.textSecondary)
            }
        }
    }
}
