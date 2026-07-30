import SwiftUI

struct LabourView: View {
    let isManager: Bool

    @State private var viewModel: LabourViewModel

    init(isManager: Bool) {
        self.isManager = isManager
        _viewModel = State(initialValue: LabourViewModel(isManager: isManager))
    }

    var body: some View {
        content
            .navigationTitle("Labour")
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
            .sheet(isPresented: Binding(get: { viewModel.workerEditor != nil }, set: { if !$0 { viewModel.closeWorker() } })) {
                WorkerEditorSheet(viewModel: viewModel)
            }
            .sheet(isPresented: Binding(get: { viewModel.paymentEditor != nil }, set: { if !$0 { viewModel.closePayment() } })) {
                PaymentSheet(viewModel: viewModel)
            }
            .sheet(isPresented: Binding(get: { viewModel.detail != nil }, set: { if !$0 { viewModel.closeDetail() } })) {
                WorkerDetailSheet(viewModel: viewModel)
            }
            .sheet(isPresented: Binding(get: { viewModel.showAttendance }, set: { viewModel.showAttendance = $0 })) {
                AttendanceSheet(viewModel: viewModel)
            }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading labour…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else {
            List {
                Section {
                    HStack(spacing: PlantbillSpacing.sm) {
                        PrimaryButton(title: "Record payment") { viewModel.openRecordPayment() }
                        SecondaryButton(title: "Attendance") { viewModel.openAttendance() }
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)

                Section {
                    HStack {
                        Text("Workers")
                            .font(PlantbillTypography.headline)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Spacer()
                        SecondaryButton(title: "Add") { viewModel.openAddWorker() }
                            .frame(width: 100)
                    }
                    PlantbillTextField(label: "Search", text: $viewModel.query, placeholder: "Name or phone")

                    if viewModel.labourers.isEmpty {
                        Text("No workers added yet.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    ForEach(viewModel.filteredLabourers) { l in
                        WorkerRow(
                            labourer: l,
                            canManage: isManager,
                            onOpen: { viewModel.openDetail(l) },
                            onEdit: { viewModel.openEditWorker(l) },
                            onDelete: { Task { await viewModel.deleteWorker(l.id) } }
                        )
                    }
                }
                .listRowSeparator(.hidden)
                .listRowBackground(Color.clear)

                Section {
                    Text("Recent payments")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    if viewModel.payments.isEmpty {
                        Text("No payments recorded yet.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    ForEach(viewModel.payments) { p in
                        PaymentRow(
                            payment: p,
                            canManage: isManager,
                            onEdit: { viewModel.openEditPayment(p) },
                            onDelete: { Task { await viewModel.deletePayment(p.id) } }
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

private func genderLabel(_ gender: String) -> String { gender == "female" ? "Female" : "Male" }

private func paymentLabel(_ method: PaymentMethodKind) -> String {
    switch method {
    case .cash: return "Cash"
    case .upi: return "UPI"
    case .split: return "Split"
    case .due: return "Due"
    }
}

private struct WorkerRow: View {
    let labourer: Labourer
    let canManage: Bool
    let onOpen: () -> Void
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        PlantbillCard {
            HStack(alignment: .top) {
                Button(action: onOpen) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(labourer.name)
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Text([genderLabel(labourer.gender), "\(labourer.defaultWageMoney.format())/day", labourer.phone].compactMap { $0 }.joined(separator: " • "))
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        if labourer.balanceToPayMoney.isPositive {
                            Text("Balance to pay: \(labourer.balanceToPayMoney.format())")
                                .font(PlantbillTypography.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(PlantbillColor.error)
                        } else if labourer.balanceToPayMoney.isNegative {
                            Text("Paid ahead: \((Money.zero - labourer.balanceToPayMoney).format())")
                                .font(PlantbillTypography.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(PlantbillColor.green)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                if canManage {
                    Button(action: onEdit) {
                        Image(systemName: "pencil")
                            .foregroundStyle(PlantbillColor.textSecondary)
                            .frame(width: PlantbillSpacing.minTouchTarget, height: PlantbillSpacing.minTouchTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button(action: onDelete) {
                        Image(systemName: "trash")
                            .foregroundStyle(PlantbillColor.error)
                            .frame(width: PlantbillSpacing.minTouchTarget, height: PlantbillSpacing.minTouchTarget)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

private struct PaymentRow: View {
    let payment: LabourPayment
    let canManage: Bool
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        PlantbillCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(payment.labourerName + tag)
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text("\(ShopCalendar.billTime(payment.createdAt)) • \(paymentLabel(payment.paymentMethod))\(daysPart)")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(payment.totalAmountMoney.format())
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    if canManage {
                        HStack(spacing: PlantbillSpacing.sm) {
                            Button(action: onEdit) {
                                Image(systemName: "pencil").foregroundStyle(PlantbillColor.textSecondary)
                            }
                            .buttonStyle(.plain)
                            Button(action: onDelete) {
                                Image(systemName: "trash").foregroundStyle(PlantbillColor.error)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
    }

    private var tag: String {
        switch payment.kind {
        case "advance": return " • Advance"
        case "due_clear": return " • Due cleared"
        default: return ""
        }
    }

    private var daysPart: String {
        guard payment.kind == "wage", let days = payment.days else { return "" }
        return " • \(days) days"
    }
}

private struct WorkerEditorSheet: View {
    @Bindable var viewModel: LabourViewModel
    @Environment(\.dismiss) private var dismiss

    private var editor: WorkerEditor { viewModel.workerEditor ?? WorkerEditor() }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text(editor.id == nil ? "Add worker" : "Edit worker")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    PlantbillTextField(label: "Name", text: Binding(get: { editor.name }, set: { viewModel.workerEditor?.name = $0; viewModel.workerEditor?.error = nil }))
                    PlantbillTextField(label: "Phone (optional)", text: Binding(get: { editor.phone }, set: { viewModel.workerEditor?.phone = $0 }), keyboardType: .phonePad)
                    PlantbillTextField(label: "Aadhaar (optional)", text: Binding(get: { editor.aadhaar }, set: { viewModel.workerEditor?.aadhaar = $0 }), keyboardType: .numberPad)

                    VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                        Text("Gender")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        HStack(spacing: PlantbillSpacing.sm) {
                            FilterChip(title: "Male", isSelected: editor.gender == "male") { viewModel.workerEditor?.gender = "male" }
                            FilterChip(title: "Female", isSelected: editor.gender == "female") { viewModel.workerEditor?.gender = "female" }
                        }
                    }

                    PlantbillTextField(label: "Wage per day (₹)", text: Binding(get: { editor.wage }, set: { viewModel.workerEditor?.wage = $0; viewModel.workerEditor?.error = nil }), placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)

                    if let error = editor.error {
                        InlineErrorText(message: LocalizedStringKey(error))
                    }

                    PrimaryButton(title: editor.id == nil ? "Add worker" : "Save changes", isLoading: editor.saving, isDisabled: !editor.canSave) {
                        Task { await viewModel.saveWorker() }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closeWorker() }
                }
            }
        }
    }
}

private struct PaymentSheet: View {
    @Bindable var viewModel: LabourViewModel
    @Environment(\.dismiss) private var dismiss

    private var editor: PaymentEditor { viewModel.paymentEditor ?? PaymentEditor() }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text(editor.id != nil ? "Edit payment" : (editor.isAdvance ? "Give advance" : "Record payment"))
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    if editor.id != nil {
                        Text(editor.labourerName)
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                    } else {
                        Text("Worker")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: PlantbillSpacing.sm) {
                                ForEach(viewModel.labourers) { l in
                                    FilterChip(title: LocalizedStringKey(l.name), isSelected: editor.labourerId == l.id) { viewModel.selectPaymentLabourer(l) }
                                }
                            }
                        }

                        HStack(spacing: PlantbillSpacing.sm) {
                            FilterChip(title: "Wage payment", isSelected: !editor.isAdvance) { viewModel.setPaymentAdvance(false) }
                            FilterChip(title: "Advance", isSelected: editor.isAdvance) { viewModel.setPaymentAdvance(true) }
                        }
                    }

                    if !editor.isAdvance {
                        Text("Wage per day: \(editor.wagePerDay.format())")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        PlantbillTextField(label: "Number of days", text: Binding(get: { editor.days }, set: { viewModel.setPaymentDays($0) }), keyboardType: .decimalPad)
                    }

                    PlantbillTextField(
                        label: editor.isAdvance ? "Advance amount (₹)" : "Amount (₹)",
                        text: Binding(get: { editor.amount }, set: { viewModel.paymentEditor?.amount = $0; viewModel.paymentEditor?.error = nil }),
                        placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true
                    )

                    Text("Payment method")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                    HStack(spacing: PlantbillSpacing.sm) {
                        FilterChip(title: "Cash", isSelected: editor.mode == .cash) { viewModel.setPaymentMode(.cash) }
                        FilterChip(title: "UPI", isSelected: editor.mode == .upi) { viewModel.setPaymentMode(.upi) }
                        FilterChip(title: "Split", isSelected: editor.mode == .split) { viewModel.setPaymentMode(.split) }
                    }
                    if editor.mode == .split {
                        PlantbillTextField(label: "Cash part", text: Binding(get: { editor.splitCash }, set: { viewModel.paymentEditor?.splitCash = $0; viewModel.paymentEditor?.error = nil }), placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)
                        Text("UPI part: \(editor.upi.format())")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }

                    PlantbillTextField(label: "Note (optional)", text: Binding(get: { editor.note }, set: { viewModel.paymentEditor?.note = $0 }))

                    HStack {
                        Text("Total")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Spacer()
                        Text(editor.total.format())
                            .font(PlantbillTypography.headline)
                            .foregroundStyle(PlantbillColor.green)
                    }

                    if let error = editor.error {
                        InlineErrorText(message: LocalizedStringKey(error))
                    }

                    PrimaryButton(
                        title: editor.id != nil ? "Save changes" : (editor.isAdvance ? "Give advance" : "Record payment"),
                        isLoading: editor.saving, isDisabled: !editor.canSave
                    ) {
                        Task { await viewModel.savePayment() }
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { viewModel.closePayment() }
                }
            }
        }
    }
}

private struct WorkerDetailSheet: View {
    @Bindable var viewModel: LabourViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                if let d = viewModel.detail {
                    let l = d.labourer
                    VStack(alignment: .leading, spacing: PlantbillSpacing.md) {
                        Text(l.name)
                            .font(PlantbillTypography.headline)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Text([genderLabel(l.gender), l.phone, l.aadhaar].compactMap { $0 }.joined(separator: " • "))
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

                        HStack(spacing: PlantbillSpacing.sm) {
                            SecondaryButton(title: "Give advance") {
                                viewModel.closeDetail()
                                viewModel.openRecordPayment(worker: l, advance: true)
                            }
                            PrimaryButton(title: "Record payment") {
                                viewModel.closeDetail()
                                viewModel.openRecordPayment(worker: l, advance: false)
                            }
                        }

                        Text("Payment history")
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)

                        if d.loading {
                            LoadingStateView(message: "Loading…").frame(height: 100)
                        } else if d.payments.isEmpty {
                            Text("No payments recorded yet.")
                                .font(PlantbillTypography.body)
                                .foregroundStyle(PlantbillColor.textSecondary)
                        } else {
                            ForEach(d.payments.prefix(50)) { p in
                                HStack {
                                    Text("\(ShopCalendar.billTime(p.createdAt)) • \(paymentLabel(p.paymentMethod))\(historyTag(p))")
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
                    Button("Close") { viewModel.closeDetail() }
                }
            }
        }
    }

    private func historyTag(_ p: LabourPayment) -> String {
        switch p.kind {
        case "advance": return " • Advance"
        case "due_clear": return " • Due cleared"
        default:
            if let days = p.days { return " • \(days) days" }
            return ""
        }
    }

    private func statementRow(_ label: LocalizedStringKey, _ value: String) -> some View {
        HStack {
            Text(label)
                .font(PlantbillTypography.body)
                .foregroundStyle(PlantbillColor.textSecondary)
            Spacer()
            Text(value)
                .font(PlantbillTypography.bodyEmphasized)
                .foregroundStyle(PlantbillColor.textPrimary)
        }
    }
}

private struct AttendanceSheet: View {
    @Bindable var viewModel: LabourViewModel

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                    Text("Today's attendance")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    if viewModel.labourers.isEmpty {
                        Text("Add a worker first.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }

                    ForEach(viewModel.labourers) { l in
                        VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                            HStack {
                                Text(l.name)
                                    .font(PlantbillTypography.bodyEmphasized)
                                    .foregroundStyle(PlantbillColor.textPrimary)
                                Spacer()
                                Text("\(l.daysWorked) days")
                                    .font(PlantbillTypography.caption)
                                    .foregroundStyle(PlantbillColor.textSecondary)
                            }
                            HStack(spacing: PlantbillSpacing.xs) {
                                attChip("Present", viewModel.attendance[l.id]?.status == "present", viewModel.attendanceBusyId == l.id) {
                                    Task { await viewModel.mark(labourerId: l.id, status: "present") }
                                }
                                attChip("Half-day", viewModel.attendance[l.id]?.status == "half_day", viewModel.attendanceBusyId == l.id) {
                                    Task { await viewModel.mark(labourerId: l.id, status: "half_day") }
                                }
                                attChip("Absent", viewModel.attendance[l.id]?.status == "absent", viewModel.attendanceBusyId == l.id) {
                                    Task { await viewModel.mark(labourerId: l.id, status: "absent") }
                                }
                            }
                        }
                        Divider()
                    }
                }
                .padding(PlantbillSpacing.lg)
            }
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Close") { viewModel.closeAttendance() }
                }
            }
        }
    }

    private func attChip(_ title: LocalizedStringKey, _ selected: Bool, _ busy: Bool, _ action: @escaping () -> Void) -> some View {
        FilterChip(title: title, isSelected: selected, action: action)
            .disabled(busy)
            .opacity(busy ? 0.5 : 1)
    }
}
