import Foundation
import Observation

enum LabourPayMode: String {
    case cash, upi, split
}

/// Amount = wage per day × number of days.
private func wageFor(wagePerDay: Money, days: String) -> Money {
    let d = Decimal(string: days.trimmingCharacters(in: .whitespaces), locale: Locale(identifier: "en_US_POSIX")) ?? 0
    return Money(amount: wagePerDay.amount * d)
}

struct WorkerEditor: Equatable {
    var id: UUID?
    var name: String = ""
    var phone: String = ""
    var aadhaar: String = ""
    var gender: String = "male"
    var wage: String = ""
    var saving = false
    var error: String?

    var canSave: Bool { !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !saving }
}

struct PaymentEditor: Equatable {
    var id: UUID?
    var labourerId: UUID?
    var labourerName: String = ""
    var isAdvance = false
    var wagePerDay: Money = .zero
    var days: String = "1"
    var amount: String = ""
    var mode: LabourPayMode = .cash
    var splitCash: String = ""
    var note: String = ""
    var saving = false
    var error: String?

    var total: Money { Money.parse(amount) }
    var cash: Money {
        switch mode {
        case .cash: return total
        case .upi: return .zero
        case .split: return Money.parse(splitCash)
        }
    }
    var upi: Money {
        switch mode {
        case .upi: return total
        case .cash: return .zero
        case .split:
            let remainder = total - Money.parse(splitCash)
            return remainder.isNegative ? .zero : remainder
        }
    }
    var canSave: Bool {
        labourerId != nil && !amount.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !saving
            && (mode != .split || Money.parse(splitCash) <= total)
    }
}

/// The worker whose statement/history sheet is open.
struct WorkerDetail: Equatable {
    let labourer: Labourer
    var loading = true
    var payments: [LabourPayment] = []
}

@Observable
@MainActor
final class LabourViewModel {
    let isManager: Bool

    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var labourers: [Labourer] = []
    private(set) var payments: [LabourPayment] = []
    var query: String = ""

    var workerEditor: WorkerEditor?
    var paymentEditor: PaymentEditor?
    var detail: WorkerDetail?

    var showAttendance = false
    private(set) var attendance: [UUID: Attendance] = [:]
    private(set) var attendanceBusyId: UUID?

    var message: String?

    init(isManager: Bool) {
        self.isManager = isManager
    }

    var filteredLabourers: [Labourer] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return labourers }
        return labourers.filter { $0.name.localizedCaseInsensitiveContains(trimmed) || ($0.phone?.contains(trimmed) ?? false) }
    }

    private var todayString: String { ShopCalendar.apiDateString(ShopCalendar.today()) }

    func load() async {
        isLoading = true
        loadError = nil
        do {
            async let workersTask: [Labourer] = APIClient.shared.send(Endpoint(path: "labour/labourers"))
            async let paymentsTask: [LabourPayment] = APIClient.shared.send(Endpoint(path: "labour/payments"))
            async let attendanceTask: [Attendance] = APIClient.shared.send(Endpoint(path: "labour/attendance", queryItems: [URLQueryItem(name: "day", value: todayString)]))
            let (workers, pays, att) = try await (workersTask, paymentsTask, attendanceTask)
            labourers = workers
            payments = pays
            attendance = Dictionary(uniqueKeysWithValues: att.map { ($0.labourerId, $0) })
            isLoading = false
        } catch let error as APIError {
            isLoading = false
            loadError = error.userMessage
        } catch {
            isLoading = false
            loadError = APIError.unknown.userMessage
        }
    }

    // MARK: Worker editor

    func openAddWorker() { workerEditor = WorkerEditor() }
    func openEditWorker(_ l: Labourer) {
        workerEditor = WorkerEditor(id: l.id, name: l.name, phone: l.phone ?? "", aadhaar: l.aadhaar ?? "", gender: l.gender, wage: l.defaultWageMoney.toInput())
    }
    func closeWorker() { workerEditor = nil }

    func saveWorker() async {
        guard var editor = workerEditor, editor.canSave else { return }
        editor.saving = true
        workerEditor = editor
        do {
            let name = editor.name.trimmingCharacters(in: .whitespacesAndNewlines)
            let phone = editor.phone.trimmingCharacters(in: .whitespacesAndNewlines)
            let aadhaar = editor.aadhaar.trimmingCharacters(in: .whitespacesAndNewlines)
            let wage = Money.parse(editor.wage).toWire()
            if let id = editor.id {
                let request = LabourerRequest(name: name, phone: phone, aadhaar: aadhaar, gender: editor.gender, defaultWage: wage)
                let body = try APIClient.shared.encode(request)
                let _: Labourer = try await APIClient.shared.send(Endpoint(path: "labour/labourers/\(id)", method: .patch, body: body))
            } else {
                let request = LabourerRequest(name: name, phone: phone.isEmpty ? nil : phone, aadhaar: aadhaar.isEmpty ? nil : aadhaar, gender: editor.gender, defaultWage: wage)
                let body = try APIClient.shared.encode(request)
                let _: Labourer = try await APIClient.shared.send(Endpoint(path: "labour/labourers", method: .post, body: body))
            }
            workerEditor = nil
            message = "Saved."
            await load()
        } catch let error as APIError {
            editor.saving = false
            editor.error = error.userMessage
            workerEditor = editor
        } catch {
            editor.saving = false
            editor.error = APIError.unknown.userMessage
            workerEditor = editor
        }
    }

    func deleteWorker(_ id: UUID) async {
        do {
            try await APIClient.shared.sendNoContent(Endpoint(path: "labour/labourers/\(id)", method: .delete))
            message = "Worker removed."
            detail = nil
            await load()
        } catch let error as APIError {
            message = error.userMessage
        } catch {
            message = APIError.unknown.userMessage
        }
    }

    // MARK: Payment editor

    func openRecordPayment(worker: Labourer? = nil, advance: Bool = false) {
        if labourers.isEmpty {
            message = "Add a worker first."
            return
        }
        paymentEditor = PaymentEditor(
            labourerId: worker?.id,
            labourerName: worker?.name ?? "",
            isAdvance: advance,
            wagePerDay: worker?.defaultWageMoney ?? .zero,
            days: "1",
            amount: (worker != nil && !advance) ? wageFor(wagePerDay: worker!.defaultWageMoney, days: "1").toInput() : ""
        )
    }

    func openEditPayment(_ p: LabourPayment) {
        let mode: LabourPayMode
        switch p.paymentMethod {
        case .upi: mode = .upi
        case .split: mode = .split
        default: mode = .cash
        }
        let wagePerDay = labourers.first { $0.id == p.labourerId }?.defaultWageMoney ?? .zero
        paymentEditor = PaymentEditor(
            id: p.id, labourerId: p.labourerId, labourerName: p.labourerName,
            isAdvance: p.kind == "advance", wagePerDay: wagePerDay, days: p.days ?? "1",
            amount: Money.parse(p.wageAmount).toInput(), mode: mode, splitCash: p.cashAmountMoney.toInput(), note: p.note ?? ""
        )
    }

    func closePayment() { paymentEditor = nil }

    func selectPaymentLabourer(_ l: Labourer) {
        guard var editor = paymentEditor else { return }
        let amount = editor.isAdvance ? editor.amount : wageFor(wagePerDay: l.defaultWageMoney, days: editor.days).toInput()
        editor.labourerId = l.id
        editor.labourerName = l.name
        editor.wagePerDay = l.defaultWageMoney
        editor.amount = amount
        editor.error = nil
        paymentEditor = editor
    }

    func setPaymentDays(_ v: String) {
        guard var editor = paymentEditor else { return }
        editor.days = v
        if !editor.isAdvance { editor.amount = wageFor(wagePerDay: editor.wagePerDay, days: v).toInput() }
        editor.error = nil
        paymentEditor = editor
    }

    func setPaymentAdvance(_ advance: Bool) {
        guard var editor = paymentEditor else { return }
        editor.isAdvance = advance
        editor.amount = advance ? "" : wageFor(wagePerDay: editor.wagePerDay, days: editor.days).toInput()
        editor.error = nil
        paymentEditor = editor
    }

    func setPaymentMode(_ mode: LabourPayMode) {
        guard var editor = paymentEditor else { return }
        if mode == .split && editor.splitCash.isEmpty { editor.splitCash = editor.total.toInput() }
        editor.mode = mode
        editor.error = nil
        paymentEditor = editor
    }

    func savePayment() async {
        guard var editor = paymentEditor, editor.canSave, let labourerId = editor.labourerId else { return }
        editor.saving = true
        paymentEditor = editor
        do {
            if let id = editor.id {
                let request = LabourPaymentUpdateRequest(
                    wageAmount: editor.amount.isEmpty ? nil : Money.parse(editor.amount).toWire(),
                    days: editor.isAdvance ? nil : editor.days,
                    cashAmount: editor.cash.toWire(), upiAmount: editor.upi.toWire(), dueAmount: "0.00",
                    note: editor.note.trimmingCharacters(in: .whitespacesAndNewlines)
                )
                let body = try APIClient.shared.encode(request)
                let _: LabourPayment = try await APIClient.shared.send(Endpoint(path: "labour/payments/\(id)", method: .patch, body: body))
            } else {
                let request = LabourPaymentCreateRequest(
                    labourerId: labourerId, kind: editor.isAdvance ? "advance" : "wage",
                    wageAmount: Money.parse(editor.amount).toWire(), days: editor.isAdvance ? nil : editor.days,
                    cashAmount: editor.cash.toWire(), upiAmount: editor.upi.toWire(), dueAmount: "0.00",
                    note: editor.note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : editor.note.trimmingCharacters(in: .whitespacesAndNewlines)
                )
                let body = try APIClient.shared.encode(request)
                let _: LabourPayment = try await APIClient.shared.send(Endpoint(path: "labour/payments", method: .post, body: body))
            }
            paymentEditor = nil
            message = "Payment recorded."
            await load()
            refreshDetail()
        } catch let error as APIError {
            editor.saving = false
            editor.error = error.userMessage
            paymentEditor = editor
        } catch {
            editor.saving = false
            editor.error = APIError.unknown.userMessage
            paymentEditor = editor
        }
    }

    func deletePayment(_ id: UUID) async {
        do {
            try await APIClient.shared.sendNoContent(Endpoint(path: "labour/payments/\(id)", method: .delete))
            message = "Payment deleted."
            await load()
            refreshDetail()
        } catch let error as APIError {
            message = error.userMessage
        } catch {
            message = APIError.unknown.userMessage
        }
    }

    // MARK: Worker detail (statement + history)

    func openDetail(_ l: Labourer) {
        detail = WorkerDetail(labourer: l, loading: true)
        Task {
            do {
                let list: [LabourPayment] = try await APIClient.shared.send(Endpoint(path: "labour/payments", queryItems: [URLQueryItem(name: "labourer_id", value: l.id.uuidString)]))
                detail?.loading = false
                detail?.payments = list
            } catch let error as APIError {
                detail?.loading = false
                message = error.userMessage
            } catch {
                detail?.loading = false
            }
        }
    }

    func closeDetail() { detail = nil }

    private func refreshDetail() {
        guard let current = detail else { return }
        let fresh = labourers.first { $0.id == current.labourer.id } ?? current.labourer
        openDetail(fresh)
    }

    // MARK: Attendance

    func openAttendance() { showAttendance = true }
    func closeAttendance() { showAttendance = false }

    func mark(labourerId: UUID, status: String) async {
        guard attendanceBusyId == nil else { return }
        attendanceBusyId = labourerId
        do {
            let request = AttendanceMarkRequest(labourerId: labourerId, day: todayString, status: status)
            let body = try APIClient.shared.encode(request)
            let rec: Attendance = try await APIClient.shared.send(Endpoint(path: "labour/attendance", method: .post, body: body))
            attendance[labourerId] = rec
            attendanceBusyId = nil
            await load()
        } catch let error as APIError {
            attendanceBusyId = nil
            message = error.userMessage
        } catch {
            attendanceBusyId = nil
            message = APIError.unknown.userMessage
        }
    }

    func dismissMessage() { message = nil }
}
