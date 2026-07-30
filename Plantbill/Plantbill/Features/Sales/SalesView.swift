import SwiftUI

enum SalesDestination: Hashable {
    case billDetail(UUID)
    case dues
    case approvals
    case report
}

struct SalesView: View {
    let isManager: Bool

    @State private var viewModel: SalesViewModel
    @State private var path = NavigationPath()
    @State private var showingDatePicker = false

    init(isManager: Bool) {
        self.isManager = isManager
        _viewModel = State(initialValue: SalesViewModel(isManager: isManager))
    }

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    quickLinksRow
                    if isManager {
                        SecondaryButton(title: "Approve collections") {
                            path.append(SalesDestination.approvals)
                        }
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)

                if isManager {
                    Section {
                        staffFilterRow
                        if !viewModel.staffSales.isEmpty {
                            leaderboardCard
                        }
                    }
                    .listRowSeparator(.hidden)
                    .listRowBackground(Color.clear)
                }

                Section {
                    dateSelectorRow
                    summarySection
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)

                Section {
                    Text("Bills")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    if viewModel.bills.isEmpty && !viewModel.billsLoading {
                        Text("No bills yet for this day.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }

                    ForEach(viewModel.bills) { bill in
                        BillRow(bill: bill)
                            .contentShape(Rectangle())
                            .onTapGesture { path.append(SalesDestination.billDetail(bill.id)) }
                    }

                    if viewModel.hasMore {
                        SecondaryButton(title: viewModel.loadingMore ? "Loading…" : "Load more") {
                            Task { await viewModel.loadMore() }
                        }
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(PlantbillColor.background)
            .navigationTitle("Sales")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: SalesDestination.self) { destination in
                switch destination {
                case .billDetail(let id):
                    BillDetailView(billId: id, isManager: isManager)
                case .dues:
                    DuesView(isManager: isManager)
                case .approvals:
                    ApprovalsView()
                case .report:
                    ReportView(isManager: isManager)
                }
            }
            .task { await viewModel.onAppear() }
            .refreshable { await viewModel.load() }
            .notificationBell()
            .alert("Something went wrong", isPresented: Binding(
                get: { viewModel.message != nil },
                set: { if !$0 { viewModel.dismissMessage() } }
            )) {
                Button("OK") { viewModel.dismissMessage() }
            } message: {
                Text(viewModel.message ?? "")
            }
            .sheet(isPresented: Binding(
                get: { viewModel.expenseEditor != nil },
                set: { if !$0 { viewModel.closeExpenseEditor() } }
            )) {
                ExpenseEditorSheet(viewModel: viewModel)
            }
            .sheet(isPresented: $showingDatePicker) {
                DatePickerSheet(date: viewModel.selectedDate, maxDate: ShopCalendar.today()) { picked in
                    viewModel.changeDate(picked)
                }
            }
        }
    }

    private var quickLinksRow: some View {
        HStack(spacing: PlantbillSpacing.sm) {
            SecondaryButton(title: "Dues") { path.append(SalesDestination.dues) }
            SecondaryButton(title: "Reports") { path.append(SalesDestination.report) }
        }
    }

    private var staffFilterRow: some View {
        Menu {
            Button("All staff") { viewModel.selectStaff(nil) }
            ForEach(viewModel.staff) { sp in
                Button(sp.email) { viewModel.selectStaff(sp.id) }
            }
        } label: {
            PlantbillCard {
                HStack {
                    Image(systemName: "person.2")
                        .foregroundStyle(PlantbillColor.green)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("View by staff")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Text(viewModel.selectedStaffEmail ?? "All staff")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
            }
        }
    }

    private var leaderboardCard: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                HStack {
                    Image(systemName: "trophy.fill").foregroundStyle(PlantbillColor.green)
                    Text("Top sellers today")
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                }
                ForEach(Array(viewModel.staffSales.enumerated()), id: \.element.id) { index, row in
                    HStack {
                        Text("\(index + 1).")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Text(row.salesperson.email)
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textPrimary)
                            .lineLimit(1)
                        Spacer()
                        Text(row.sales.format())
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.green)
                    }
                }
            }
        }
    }

    private var dateSelectorRow: some View {
        HStack {
            Button { viewModel.goToPreviousDay() } label: {
                Image(systemName: "chevron.left")
                    .frame(width: PlantbillSpacing.minTouchTarget, height: PlantbillSpacing.minTouchTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button { showingDatePicker = true } label: {
                Text(ShopCalendar.displayDateString(viewModel.selectedDate))
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button { viewModel.goToNextDay() } label: {
                Image(systemName: "chevron.right")
                    .frame(width: PlantbillSpacing.minTouchTarget, height: PlantbillSpacing.minTouchTarget)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isToday)
            .opacity(viewModel.isToday ? 0.35 : 1)
        }
        .foregroundStyle(PlantbillColor.textPrimary)
    }

    @ViewBuilder
    private var summarySection: some View {
        switch viewModel.summaryState {
        case .loading:
            LoadingStateView(message: "Loading today's numbers…")
                .frame(height: 160)
        case .error(let message):
            ErrorStateView(message: LocalizedStringKey(message)) { Task { await viewModel.load() } }
                .frame(height: 220)
        case .loaded(let summary):
            SummaryHero(
                summary: summary,
                onAddExpense: { viewModel.openCreateExpense() },
                onEditExpense: { viewModel.openEditExpense($0) },
                onDeleteExpense: { expense in Task { await viewModel.deleteExpense(expense.id) } }
            )
        }
    }
}

private struct BillRow: View {
    let bill: BillListEntry

    var body: some View {
        PlantbillCard {
            HStack {
                Image(systemName: "receipt")
                    .foregroundStyle(PlantbillColor.textSecondary)
                VStack(alignment: .leading, spacing: 2) {
                    Text(bill.customerName ?? "Walk-in customer")
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text(rowSubtitle)
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

    private var rowSubtitle: String {
        var parts = [ShopCalendar.billTime(bill.createdAt), "\(bill.itemCount) item\(bill.itemCount == 1 ? "" : "s")", paymentLabel]
        if bill.isEdited { parts.append("Edited") }
        return parts.joined(separator: " • ")
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

private struct SummaryHero: View {
    let summary: DaySummary
    let onAddExpense: () -> Void
    let onEditExpense: (Expense) -> Void
    let onDeleteExpense: (Expense) -> Void

    @AppStorage("cash_in_hand_cumulative") private var cashInHandCumulative = false

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.md) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Total sales")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                    Text(summary.totalSalesMoney.format())
                        .font(PlantbillTypography.largeTitle)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text("\(summary.billCount) bill\(summary.billCount == 1 ? "" : "s")")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }

                Divider()

                HStack {
                    statColumn(title: "Cash", value: summary.cashTotalMoney)
                    statColumn(title: "UPI", value: summary.upiTotalMoney)
                    statColumn(title: "Due", value: summary.dueTotalMoney, tint: summary.dueTotalMoney.isPositive ? PlantbillColor.error : PlantbillColor.textPrimary)
                }

                Divider()

                HStack {
                    statColumn(title: "Expenses", value: summary.totalExpensesMoney)
                    statColumn(title: "Net", value: summary.netSalesMoney)
                    statColumn(
                        title: cashInHandCumulative ? "Cash in hand (all time)" : "Cash in hand",
                        value: cashInHandCumulative ? summary.cashInHandRunningMoney : summary.cashInHandToday
                    )
                }

                Divider()

                VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                    HStack {
                        Text("Expenses")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Spacer()
                        Button("Add expense", action: onAddExpense)
                            .font(PlantbillTypography.caption)
                    }
                    if summary.expenses.isEmpty {
                        Text("No expenses logged for this day.")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    ForEach(summary.expenses) { expense in
                        HStack {
                            VStack(alignment: .leading, spacing: 1) {
                                Text(expense.reason)
                                    .font(PlantbillTypography.body)
                                    .foregroundStyle(PlantbillColor.textPrimary)
                                Text(expense.paymentMethod == "cash" ? "Cash" : "UPI")
                                    .font(PlantbillTypography.caption)
                                    .foregroundStyle(PlantbillColor.textSecondary)
                            }
                            Spacer()
                            Text(expense.amountMoney.format())
                                .font(PlantbillTypography.body)
                                .foregroundStyle(PlantbillColor.textPrimary)
                            Menu {
                                Button("Edit") { onEditExpense(expense) }
                                Button("Delete", role: .destructive) { onDeleteExpense(expense) }
                            } label: {
                                Image(systemName: "ellipsis.circle")
                                    .foregroundStyle(PlantbillColor.textSecondary)
                                    .frame(width: PlantbillSpacing.minTouchTarget, height: PlantbillSpacing.minTouchTarget)
                                    .contentShape(Rectangle())
                            }
                        }
                    }
                }
            }
        }
    }

    private func statColumn(title: LocalizedStringKey, value: Money, tint: Color = PlantbillColor.textPrimary) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)
            Text(value.format())
                .font(PlantbillTypography.bodyEmphasized)
                .foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct DatePickerSheet: View {
    let maxDate: Date
    @State private var picked: Date
    @Environment(\.dismiss) private var dismiss
    let onPick: (Date) -> Void

    init(date: Date, maxDate: Date, onPick: @escaping (Date) -> Void) {
        self.maxDate = maxDate
        self.onPick = onPick
        _picked = State(initialValue: date)
    }

    var body: some View {
        NavigationStack {
            DatePicker("Date", selection: $picked, in: ...maxDate, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .padding(PlantbillSpacing.lg)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Done") { onPick(picked); dismiss() }
                    }
                }
        }
        .presentationDetents([.medium])
    }
}

private struct ExpenseEditorSheet: View {
    @Bindable var viewModel: SalesViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: PlantbillSpacing.lg) {
                Text(editor.id == nil ? "Add expense" : "Edit expense")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)

                PlantbillTextField(
                    label: "Amount (₹)",
                    text: Binding(get: { editor.amount }, set: setAmount),
                    placeholder: "0",
                    keyboardType: .numberPad,
                    selectAllOnFocus: true
                )

                PlantbillTextField(label: "Reason", text: Binding(get: { editor.reason }, set: setReason), placeholder: "e.g. Electricity bill")

                HStack(spacing: PlantbillSpacing.sm) {
                    FilterChip(title: "Cash", isSelected: editor.paymentMethod == "cash") { setMethod("cash") }
                    FilterChip(title: "UPI", isSelected: editor.paymentMethod == "upi") { setMethod("upi") }
                    if editor.id == nil {
                        FilterChip(title: "Split", isSelected: editor.paymentMethod == "split") { setMethod("split") }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                if editor.paymentMethod == "split" {
                    PlantbillTextField(
                        label: "Cash part (₹)",
                        text: Binding(get: { editor.splitCashText }, set: setSplitCash),
                        placeholder: "0",
                        keyboardType: .numberPad,
                        selectAllOnFocus: true
                    )
                    HStack {
                        Text("UPI part")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Spacer()
                        Text(editor.splitUpiMoney.format())
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                }

                if let error = editor.error {
                    InlineErrorText(message: LocalizedStringKey(error))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                PrimaryButton(title: "Save expense", isLoading: editor.saving, isDisabled: !editor.canSave) {
                    Task { await viewModel.saveExpense() }
                }

                Spacer()
            }
            .padding(PlantbillSpacing.lg)
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closeExpenseEditor() }
                }
            }
        }
    }

    private var editor: ExpenseEditor { viewModel.expenseEditor ?? ExpenseEditor() }
    private func setAmount(_ v: String) { viewModel.expenseEditor?.amount = v; viewModel.expenseEditor?.error = nil }
    private func setReason(_ v: String) { viewModel.expenseEditor?.reason = v; viewModel.expenseEditor?.error = nil }
    private func setMethod(_ v: String) { viewModel.expenseEditor?.paymentMethod = v; viewModel.expenseEditor?.error = nil }
    private func setSplitCash(_ v: String) { viewModel.expenseEditor?.splitCashText = v; viewModel.expenseEditor?.error = nil }
}
