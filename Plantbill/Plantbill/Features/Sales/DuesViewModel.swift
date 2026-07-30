import Foundation
import Observation

/// Dues unpaid for at least this many days are flagged "priority" (overdue).
private let priorityDays = 30

enum SettleMode: String {
    case cash, upi, split
}

/// The "collect this due" sheet state. A partial amount may be collected —
/// the rest stays owed. `amount` is how much to collect now (≤ the due).
struct SettleTarget: Equatable {
    let entry: BillListEntry
    var amount: String
    var mode: SettleMode = .cash
    var splitCash: String = ""
    var submitting = false
    var error: String?

    init(entry: BillListEntry) {
        self.entry = entry
        self.amount = entry.dueAmountMoney.toInput()
    }

    var total: Money { Money.parse(amount) }

    var cashAmount: Money {
        switch mode {
        case .cash: return total
        case .upi: return .zero
        case .split: return Money.parse(splitCash)
        }
    }

    var upiAmount: Money {
        switch mode {
        case .cash: return .zero
        case .upi: return total
        case .split:
            let remainder = total - Money.parse(splitCash)
            return remainder.isNegative ? .zero : remainder
        }
    }

    var valid: Bool {
        total.isPositive && total <= entry.dueAmountMoney && (mode != .split || Money.parse(splitCash) <= total)
    }
}

@Observable
@MainActor
final class DuesViewModel {
    let isManager: Bool

    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var dues: [BillListEntry] = []
    var query: String = ""
    var settle: SettleTarget?
    var message: String?

    init(isManager: Bool) {
        self.isManager = isManager
    }

    var totalOwed: Money { dues.reduce(Money.zero) { $0 + $1.dueAmountMoney } }

    private var filtered: [BillListEntry] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return dues }
        return dues.filter {
            ($0.customerName?.localizedCaseInsensitiveContains(trimmed) ?? false)
                || ($0.customerPhone?.contains(trimmed) ?? false)
        }
    }

    var priorityDues: [BillListEntry] {
        filtered.filter { $0.daysSinceCreated >= priorityDays }.sorted { $0.daysSinceCreated > $1.daysSinceCreated }
    }

    var otherDues: [BillListEntry] {
        filtered.filter { $0.daysSinceCreated < priorityDays }
    }

    var hasResults: Bool { !filtered.isEmpty }

    func load() async {
        isLoading = true
        loadError = nil
        do {
            let query = [
                URLQueryItem(name: "has_due", value: "true"),
                URLQueryItem(name: "limit", value: "100"),
            ]
            let page: BillListPage = try await APIClient.shared.send(Endpoint(path: "bills", queryItems: query))
            dues = page.items.filter { $0.dueAmountMoney.isPositive }
            isLoading = false
        } catch let error as APIError {
            isLoading = false
            loadError = error.userMessage
        } catch {
            isLoading = false
            loadError = APIError.unknown.userMessage
        }
    }

    // MARK: Settle sheet

    func openSettle(_ entry: BillListEntry) { settle = SettleTarget(entry: entry) }
    func closeSettle() { settle = nil }

    func setSettleMode(_ mode: SettleMode) {
        guard var target = settle else { return }
        if mode == .split && target.splitCash.isEmpty {
            target.splitCash = target.total.toInput()
        }
        target.mode = mode
        target.error = nil
        settle = target
    }

    func confirmSettle() async {
        guard var target = settle, !target.submitting else { return }
        guard target.valid else {
            target.error = "Enter an amount up to what's owed (\(target.entry.dueAmountMoney.format()))."
            settle = target
            return
        }
        target.submitting = true
        target.error = nil
        settle = target

        do {
            let request = SettlementCreateRequest(billId: target.entry.id, cashAmount: target.cashAmount.toWire(), upiAmount: target.upiAmount.toWire())
            let body = try APIClient.shared.encode(request)
            let result: SettlementActionResult = try await APIClient.shared.send(Endpoint(path: "settlements", method: .post, body: body))
            let remainingDue = target.entry.dueAmountMoney - target.total
            let remaining = remainingDue.isNegative ? Money.zero : remainingDue

            if result.status == "approved" {
                if remaining.isPositive {
                    if let index = dues.firstIndex(where: { $0.id == target.entry.id }) {
                        dues[index] = replacingDue(dues[index], due: remaining)
                    }
                    message = "Collected \(target.total.format()) — \(remaining.format()) still owed."
                } else {
                    dues.removeAll { $0.id == target.entry.id }
                    message = "Collected \(target.total.format())."
                }
            } else {
                if let index = dues.firstIndex(where: { $0.id == target.entry.id }) {
                    dues[index] = replacingPending(dues[index])
                }
                message = "Sent to your manager for approval."
            }
            settle = nil
        } catch let error as APIError {
            target.submitting = false
            target.error = error.userMessage
            settle = target
        } catch {
            target.submitting = false
            target.error = APIError.unknown.userMessage
            settle = target
        }
    }

    private func replacingDue(_ entry: BillListEntry, due: Money) -> BillListEntry {
        BillListEntry(id: entry.id, createdAt: entry.createdAt, total: entry.total, dueAmount: due.toWire(), customerName: entry.customerName, customerPhone: entry.customerPhone, itemCount: entry.itemCount, paymentMethod: entry.paymentMethod, isEdited: entry.isEdited, pendingSettlement: entry.pendingSettlement)
    }

    private func replacingPending(_ entry: BillListEntry) -> BillListEntry {
        BillListEntry(id: entry.id, createdAt: entry.createdAt, total: entry.total, dueAmount: entry.dueAmount, customerName: entry.customerName, customerPhone: entry.customerPhone, itemCount: entry.itemCount, paymentMethod: entry.paymentMethod, isEdited: entry.isEdited, pendingSettlement: true)
    }

    func dismissMessage() { message = nil }
}
