import SwiftUI

struct OwnerShopDetailView: View {
    let shopId: UUID
    let shopName: String

    @State private var viewModel: OwnerShopDetailViewModel
    @State private var pendingStaffDelete: OwnerStaff?

    init(shopId: UUID, shopName: String) {
        self.shopId = shopId
        self.shopName = shopName
        _viewModel = State(initialValue: OwnerShopDetailViewModel(shopId: shopId))
    }

    var body: some View {
        List {
            Section {
                OwnerPeriodSelector(period: $viewModel.period, customFrom: $viewModel.customFrom, customTo: $viewModel.customTo)
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)

            if let report = viewModel.report {
                Section {
                    HStack(spacing: PlantbillSpacing.sm) {
                        kpiCard("Sales", report.totalSalesMoney.format())
                        kpiCard("Expenses", report.totalExpensesMoney.format())
                        kpiCard("Net", report.netSalesMoney.format(), tint: PlantbillColor.green)
                    }
                    HStack(spacing: PlantbillSpacing.sm) {
                        kpiCard("Cash", report.cashTotalMoney.format())
                        kpiCard("UPI", report.upiTotalMoney.format())
                        kpiCard("Due", report.dueTotalMoney.format(), tint: report.dueTotalMoney.isPositive ? PlantbillColor.error : PlantbillColor.textPrimary)
                    }
                    cashInHandCard

                    if !report.expenses.isEmpty {
                        Text("Expenses")
                            .font(PlantbillTypography.headline)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        ForEach(report.expenses) { e in
                            HStack {
                                Text(e.reason).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textPrimary)
                                Spacer()
                                Text(e.amountMoney.format()).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.error)
                            }
                        }
                    }

                    if !report.topProducts.isEmpty {
                        Text("Top products")
                            .font(PlantbillTypography.headline)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        ForEach(report.topProducts.prefix(8)) { p in
                            HStack {
                                Text("\(p.productName) × \(p.quantity)").font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textPrimary)
                                Spacer()
                                Text(p.totalSalesMoney.format()).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textPrimary)
                            }
                        }
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)
            }

            Section {
                Text("Bills")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                if viewModel.billsLoading && viewModel.bills.isEmpty {
                    Text("Loading bills…").font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textSecondary)
                } else if viewModel.bills.isEmpty {
                    Text("No bills in this period.").font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textSecondary)
                } else {
                    ForEach(viewModel.bills) { bill in
                        OwnerBillRowView(bill: bill) { viewModel.openBill(bill.id) }
                    }
                }
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)

            Section {
                Text("Labour")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                if viewModel.labourers.isEmpty {
                    Text("No workers on this shop's roster.").font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textSecondary)
                } else {
                    ForEach(viewModel.labourers) { l in
                        OwnerLabourerRowView(labourer: l) { viewModel.openLabourer(l) }
                    }
                }
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)

            Section {
                Text("Staff")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                ForEach(viewModel.staff) { s in
                    OwnerStaffRowView(staff: s) { pendingStaffDelete = s }
                }
                AddStaffCard(viewModel: viewModel)
            }
            .listRowSeparator(.hidden)
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(PlantbillColor.background)
        .navigationTitle(shopName)
        .navigationBarTitleDisplayMode(.inline)
        .task { await viewModel.onAppear() }
        .refreshable { await viewModel.onAppear() }
        .alert("Something went wrong", isPresented: Binding(
            get: { viewModel.message != nil },
            set: { if !$0 { viewModel.dismissMessage() } }
        )) {
            Button("OK") { viewModel.dismissMessage() }
        } message: {
            Text(viewModel.message ?? "")
        }
        .sheet(isPresented: Binding(get: { viewModel.labourerDetail != nil }, set: { if !$0 { viewModel.closeLabourer() } })) {
            OwnerLabourerDetailSheet(viewModel: viewModel)
        }
        .sheet(isPresented: Binding(get: { viewModel.billDetail != nil }, set: { if !$0 { viewModel.closeBill() } })) {
            OwnerBillDetailSheet(viewModel: viewModel)
        }
        .confirmationDialog(
            "Remove this staff member?",
            isPresented: Binding(get: { pendingStaffDelete != nil }, set: { if !$0 { pendingStaffDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button("Remove", role: .destructive) {
                if let s = pendingStaffDelete { Task { await viewModel.deleteStaff(s) } }
                pendingStaffDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingStaffDelete = nil }
        }
    }

    private var cashInHandCard: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                Text("Cash in hand")
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.textSecondary)
                Text((viewModel.cashFull ? viewModel.cashInHand?.runningMoney : viewModel.cashInHand?.todayMoney)?.format() ?? Money.zero.format())
                    .font(PlantbillTypography.title)
                    .foregroundStyle(PlantbillColor.green)
                HStack(spacing: PlantbillSpacing.sm) {
                    FilterChip(title: "All time", isSelected: viewModel.cashFull) { viewModel.cashFull = true }
                    FilterChip(title: "This day", isSelected: !viewModel.cashFull) { viewModel.cashFull = false }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func kpiCard(_ title: LocalizedStringKey, _ value: String, tint: Color = PlantbillColor.textPrimary) -> some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(PlantbillTypography.caption).foregroundStyle(PlantbillColor.textSecondary)
                Text(value).font(PlantbillTypography.bodyEmphasized).foregroundStyle(tint).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private func ownerGenderLabel(_ gender: String) -> String { gender == "female" ? "Female" : "Male" }

private func ownerPaymentLabel(_ method: String) -> String {
    switch method.lowercased() {
    case "cash": return "Cash"
    case "upi": return "UPI"
    case "split": return "Split"
    case "due": return "Due"
    default: return method
    }
}

private struct OwnerBillRowView: View {
    let bill: OwnerBillRow
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            PlantbillCard {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(bill.customerName?.isEmpty == false ? bill.customerName! : "Walk-in customer")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Text("\(ShopCalendar.billTime(bill.createdAt)) • \(bill.salespersonEmail?.components(separatedBy: "@").first ?? "Unknown")")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Text("\(bill.itemCount) item\(bill.itemCount == 1 ? "" : "s") • \(ownerPaymentLabel(bill.paymentMethod))" + (bill.dueAmountMoney.isPositive ? " • Due \(bill.dueAmountMoney.format())" : ""))
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(bill.dueAmountMoney.isPositive ? PlantbillColor.error : PlantbillColor.textSecondary)
                    }
                    Spacer()
                    Text(bill.totalMoney.format())
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.green)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

private struct OwnerLabourerRowView: View {
    let labourer: Labourer
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            PlantbillCard {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(labourer.name)
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Text([ownerGenderLabel(labourer.gender), "\(labourer.defaultWageMoney.format())/day", "\(labourer.daysWorked) days", labourer.phone].compactMap { $0 }.joined(separator: " • "))
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    Spacer()
                    VStack(alignment: .trailing, spacing: 1) {
                        Text((labourer.balanceToPayMoney.isNegative ? Money.zero - labourer.balanceToPayMoney : labourer.balanceToPayMoney).format())
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(labourer.balanceToPayMoney.isPositive ? PlantbillColor.error : (labourer.balanceToPayMoney.isNegative ? PlantbillColor.green : PlantbillColor.textPrimary))
                        Text(labourer.balanceToPayMoney.isNegative ? "Paid ahead" : "To pay")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                }
            }
        }
        .buttonStyle(.plain)
    }
}

private struct OwnerStaffRowView: View {
    let staff: OwnerStaff
    let onRemove: () -> Void

    var body: some View {
        PlantbillCard {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(staff.email)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text("\(roleLabel(staff.role)) • \(staff.isActive ? "Active" : "Inactive")")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
                Spacer()
                Button("Remove", role: .destructive, action: onRemove)
                    .font(PlantbillTypography.caption)
            }
        }
    }
}

private struct AddStaffCard: View {
    @Bindable var viewModel: OwnerShopDetailViewModel

    var body: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                Text("Add staff")
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                PlantbillTextField(label: "Login email", text: $viewModel.newStaff.email, placeholder: "you@example.com", keyboardType: .emailAddress)
                PlantbillTextField(label: "Password (8+ characters)", text: $viewModel.newStaff.password)
                HStack(spacing: PlantbillSpacing.sm) {
                    FilterChip(title: "Salesperson", isSelected: viewModel.newStaff.role == "salesperson") { viewModel.newStaff.role = "salesperson" }
                    FilterChip(title: "Manager", isSelected: viewModel.newStaff.role == "manager") { viewModel.newStaff.role = "manager" }
                }
                if let error = viewModel.newStaff.error {
                    InlineErrorText(message: LocalizedStringKey(error))
                }
                PrimaryButton(title: "Add staff", isLoading: viewModel.newStaff.saving, isDisabled: !viewModel.newStaff.canSave) {
                    Task { await viewModel.addStaff() }
                }
            }
        }
    }
}

private struct OwnerLabourerDetailSheet: View {
    @Bindable var viewModel: OwnerShopDetailViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                if let d = viewModel.labourerDetail {
                    let l = d.labourer
                    VStack(alignment: .leading, spacing: PlantbillSpacing.md) {
                        Text(l.name).font(PlantbillTypography.headline).foregroundStyle(PlantbillColor.textPrimary)
                        Text([ownerGenderLabel(l.gender), l.phone, l.aadhaar].compactMap { $0 }.joined(separator: " • "))
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)

                        statementRow("Days worked", l.daysWorked)
                        Divider()
                        statementRow("Earned (\(l.defaultWageMoney.format())/day)", l.earnedMoney.format())
                        Divider()
                        statementRow("Total paid", l.totalPaidMoney.format())
                        Divider()
                        HStack {
                            Text(l.balanceToPayMoney.isNegative ? "Paid ahead" : "Balance to pay")
                                .font(PlantbillTypography.bodyEmphasized)
                                .foregroundStyle(PlantbillColor.textPrimary)
                            Spacer()
                            Text((l.balanceToPayMoney.isNegative ? Money.zero - l.balanceToPayMoney : l.balanceToPayMoney).format())
                                .font(PlantbillTypography.headline)
                                .foregroundStyle(l.balanceToPayMoney.isPositive ? PlantbillColor.error : (l.balanceToPayMoney.isNegative ? PlantbillColor.green : PlantbillColor.textPrimary))
                        }

                        Text("Payment history")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        if d.loading {
                            LoadingStateView(message: "Loading…").frame(height: 100)
                        } else if d.payments.isEmpty {
                            Text("No payments recorded yet.").font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textSecondary)
                        } else {
                            ForEach(d.payments.prefix(50)) { p in
                                HStack {
                                    Text("\(ShopCalendar.billTime(p.createdAt)) • \(ownerPaymentLabel(p.paymentMethod.rawValue))")
                                        .font(PlantbillTypography.body)
                                        .foregroundStyle(PlantbillColor.textPrimary)
                                    Spacer()
                                    Text(p.totalAmountMoney.format())
                                        .font(PlantbillTypography.bodyEmphasized)
                                        .foregroundStyle(PlantbillColor.textPrimary)
                                }
                            }
                        }
                    }
                    .padding(PlantbillSpacing.lg)
                }
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { viewModel.closeLabourer() }
                }
            }
        }
    }

    private func statementRow(_ label: LocalizedStringKey, _ value: String) -> some View {
        HStack {
            Text(label).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textSecondary)
            Spacer()
            Text(value).font(PlantbillTypography.bodyEmphasized).foregroundStyle(PlantbillColor.textPrimary)
        }
    }
}

private struct OwnerBillDetailSheet: View {
    @Bindable var viewModel: OwnerShopDetailViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                if let state = viewModel.billDetail {
                    if state.loading || state.bill == nil {
                        LoadingStateView(message: "Loading bill…").frame(height: 200)
                    } else if let bill = state.bill {
                        VStack(alignment: .leading, spacing: PlantbillSpacing.md) {
                            Text("\(ShopCalendar.billTime(bill.createdAt)) • \(bill.salespersonEmail?.components(separatedBy: "@").first ?? "Unknown")" + (bill.isEdited ? " • Edited" : ""))
                                .font(PlantbillTypography.body)
                                .foregroundStyle(PlantbillColor.textSecondary)
                            Text(bill.customerName?.isEmpty == false ? bill.customerName! : "Walk-in customer")
                                .font(PlantbillTypography.headline)
                                .foregroundStyle(PlantbillColor.textPrimary)
                            if let phone = bill.customerPhone, !phone.isEmpty {
                                Text(phone).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textSecondary)
                            }

                            ForEach(bill.items) { item in
                                HStack {
                                    VStack(alignment: .leading, spacing: 1) {
                                        Text(item.productName).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textPrimary)
                                        Text("\(item.unitPriceMoney.format()) × \(item.quantity)").font(PlantbillTypography.caption).foregroundStyle(PlantbillColor.textSecondary)
                                    }
                                    Spacer()
                                    Text(item.lineTotalMoney.format()).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textPrimary)
                                }
                            }

                            Divider()
                            row("Subtotal", bill.subtotalMoney)
                            if bill.discountAmountMoney.isPositive { row("Discount", Money.zero - bill.discountAmountMoney) }
                            row("Total", bill.totalMoney, emphasized: true)
                            if bill.cashAmountMoney.isPositive { row("Cash", bill.cashAmountMoney) }
                            if bill.upiAmountMoney.isPositive { row("UPI", bill.upiAmountMoney) }
                            if bill.dueAmountMoney.isPositive { row("Due", bill.dueAmountMoney, tint: PlantbillColor.error) }
                            if let remarks = bill.remarks, !remarks.isEmpty {
                                Text(remarks).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textSecondary)
                            }
                        }
                        .padding(PlantbillSpacing.lg)
                    }
                }
            }
            .background(PlantbillColor.background)
            .navigationTitle("Bill details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { viewModel.closeBill() }
                }
            }
        }
    }

    private func row(_ label: LocalizedStringKey, _ value: Money, emphasized: Bool = false, tint: Color = PlantbillColor.textPrimary) -> some View {
        HStack {
            Text(label)
                .font(emphasized ? PlantbillTypography.headline : PlantbillTypography.body)
                .foregroundStyle(emphasized ? PlantbillColor.textPrimary : PlantbillColor.textSecondary)
            Spacer()
            Text(value.format())
                .font(emphasized ? PlantbillTypography.headline : PlantbillTypography.body)
                .foregroundStyle(tint)
        }
    }
}
