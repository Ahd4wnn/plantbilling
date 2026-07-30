import Foundation
import Observation

enum BorrowMode: String {
    case cash, upi, split
}

enum BorrowFilter: String {
    case all, open, paid
}

/// Resolve a total + mode + cash-part into a (cash, upi) pair that sums to the total.
private func split(total: Money, mode: BorrowMode, splitCash: String) -> (Money, Money) {
    switch mode {
    case .cash: return (total, .zero)
    case .upi: return (.zero, total)
    case .split:
        let cash = min(Money.parse(splitCash), total)
        return (cash, total - cash)
    }
}

/// Add-borrowing form.
struct AddEditor: Equatable {
    var name: String = ""
    var phone: String = ""
    var amount: String = ""
    var mode: BorrowMode = .cash
    var splitCash: String = ""
    var remarks: String = ""
    var saving = false
    var error: String?

    var total: Money { Money.parse(amount) }
    var canSave: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && total.isPositive && !saving
            && (mode != .split || Money.parse(splitCash) <= total)
    }
}

/// Mark-paid form for a specific borrowing.
struct PayEditor: Equatable {
    let borrowing: Borrowing
    var amount: String = ""
    var mode: BorrowMode = .cash
    var splitCash: String = ""
    var saving = false
    var error: String?

    var total: Money { Money.parse(amount) }
    var canSave: Bool {
        !saving && total.isPositive && total <= borrowing.outstandingMoney
            && (mode != .split || Money.parse(splitCash) <= total)
    }
}

@Observable
@MainActor
final class BorrowingsViewModel {
    private(set) var isLoading = true
    private(set) var loadError: String?
    var filter: BorrowFilter = .all
    private(set) var items: [Borrowing] = []
    private(set) var totalOutstanding: Money = .zero

    var addEditor: AddEditor?
    var payEditor: PayEditor?
    var message: String?

    func load() async {
        isLoading = true
        loadError = nil
        do {
            let query = [URLQueryItem(name: "status", value: filter.rawValue)]
            let result: BorrowingList = try await APIClient.shared.send(Endpoint(path: "borrowings", queryItems: query))
            items = result.items
            totalOutstanding = result.totalOutstandingMoney
            isLoading = false
        } catch let error as APIError {
            isLoading = false
            loadError = error.userMessage
        } catch {
            isLoading = false
            loadError = APIError.unknown.userMessage
        }
    }

    func setFilter(_ f: BorrowFilter) {
        filter = f
        Task { await load() }
    }

    // MARK: Add editor

    func openAdd() { addEditor = AddEditor() }
    func closeAdd() { addEditor = nil }

    func setAddMode(_ mode: BorrowMode) {
        guard var editor = addEditor else { return }
        if mode == .split && editor.splitCash.isEmpty { editor.splitCash = editor.total.toInput() }
        editor.mode = mode
        editor.error = nil
        addEditor = editor
    }

    func saveAdd() async {
        guard var editor = addEditor, editor.canSave else { return }
        editor.saving = true
        addEditor = editor
        let (cash, upi) = split(total: editor.total, mode: editor.mode, splitCash: editor.splitCash)
        do {
            let request = BorrowingCreateRequest(
                lenderName: editor.name.trimmingCharacters(in: .whitespacesAndNewlines),
                lenderPhone: editor.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : editor.phone.trimmingCharacters(in: .whitespacesAndNewlines),
                amount: editor.total.toWire(), cashAmount: cash.toWire(), upiAmount: upi.toWire(),
                remarks: editor.remarks.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : editor.remarks.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            let body = try APIClient.shared.encode(request)
            let _: Borrowing = try await APIClient.shared.send(Endpoint(path: "borrowings", method: .post, body: body))
            addEditor = nil
            message = "Borrowing added."
            await load()
        } catch let error as APIError {
            editor.saving = false
            editor.error = error.userMessage
            addEditor = editor
        } catch {
            editor.saving = false
            editor.error = APIError.unknown.userMessage
            addEditor = editor
        }
    }

    // MARK: Pay editor

    func openPay(_ b: Borrowing) { payEditor = PayEditor(borrowing: b, amount: b.outstandingMoney.toInput()) }
    func closePay() { payEditor = nil }

    func setPayMode(_ mode: BorrowMode) {
        guard var editor = payEditor else { return }
        if mode == .split && editor.splitCash.isEmpty { editor.splitCash = editor.total.toInput() }
        editor.mode = mode
        editor.error = nil
        payEditor = editor
    }

    func savePay() async {
        guard var editor = payEditor, editor.canSave else { return }
        editor.saving = true
        payEditor = editor
        let (cash, upi) = split(total: editor.total, mode: editor.mode, splitCash: editor.splitCash)
        do {
            let request = BorrowingPayRequest(paidCashAmount: cash.toWire(), paidUpiAmount: upi.toWire())
            let body = try APIClient.shared.encode(request)
            let _: Borrowing = try await APIClient.shared.send(Endpoint(path: "borrowings/\(editor.borrowing.id)/pay", method: .post, body: body))
            payEditor = nil
            message = "Payment recorded."
            await load()
        } catch let error as APIError {
            editor.saving = false
            editor.error = error.userMessage
            payEditor = editor
        } catch {
            editor.saving = false
            editor.error = APIError.unknown.userMessage
            payEditor = editor
        }
    }

    func delete(_ id: UUID) async {
        do {
            try await APIClient.shared.sendNoContent(Endpoint(path: "borrowings/\(id)", method: .delete))
            message = "Deleted."
            await load()
        } catch let error as APIError {
            message = error.userMessage
        } catch {
            message = APIError.unknown.userMessage
        }
    }

    func dismissMessage() { message = nil }
}
