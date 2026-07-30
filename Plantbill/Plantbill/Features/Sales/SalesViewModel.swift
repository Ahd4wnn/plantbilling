import Foundation
import Observation

private let pageSize = 20

/// A salesperson and their sales total for the selected day (leaderboard row).
struct StaffSales: Identifiable, Equatable {
    let salesperson: Salesperson
    let sales: Money
    var id: UUID { salesperson.id }
}

/// State for the add/edit expense sheet. `id == nil` → create.
///
/// The backend's expense record only stores ONE payment method (`cash` or
/// `upi` — there's no split field on it, unlike a bill). "Split" is
/// therefore only offered when creating a new expense: on save it becomes
/// TWO separate expense rows (a cash one and a UPI one) whose amounts add up
/// to what was entered — each still valid against the existing schema, and
/// the day summary's cash/UPI expense totals (which sum per-row) come out
/// correct either way. Editing an existing row is always single-method,
/// since it's always one of those two rows.
struct ExpenseEditor: Equatable {
    var id: UUID? = nil
    var amount: String = ""
    var reason: String = ""
    /// "cash", "upi", or (create-only) "split".
    var paymentMethod: String = "cash"
    /// The cash portion when `paymentMethod == "split"`; the UPI portion is
    /// the remainder of `amount`.
    var splitCashText: String = ""
    var saving = false
    var error: String? = nil

    var amountMoney: Money { Money.parse(amount) }
    var splitCashMoney: Money { Money.parse(splitCashText) }
    var splitUpiMoney: Money {
        let remainder = amountMoney - splitCashMoney
        return remainder.isNegative ? .zero : remainder
    }

    var canSave: Bool {
        guard amountMoney.isPositive, !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !saving else {
            return false
        }
        if paymentMethod == "split" {
            return splitCashMoney.isPositive && splitCashMoney <= amountMoney
        }
        return true
    }
}

@Observable
@MainActor
final class SalesViewModel {
    enum SummaryState: Equatable {
        case loading
        case loaded(DaySummary)
        case error(String)

        static func == (lhs: SummaryState, rhs: SummaryState) -> Bool {
            switch (lhs, rhs) {
            case (.loading, .loading): return true
            case (.loaded(let a), .loaded(let b)): return a.date == b.date && a.totalSales == b.totalSales
            case (.error(let a), .error(let b)): return a == b
            default: return false
            }
        }
    }

    let isManager: Bool

    private(set) var selectedDate: Date = ShopCalendar.today()
    private(set) var staff: [Salesperson] = []
    private(set) var staffSales: [StaffSales] = []
    private(set) var selectedStaffId: UUID?

    private(set) var summaryState: SummaryState = .loading
    private(set) var bills: [BillListEntry] = []
    private(set) var billsLoading = true
    private(set) var loadingMore = false
    private(set) var hasMore = false

    var expenseEditor: ExpenseEditor?
    var message: String?

    init(isManager: Bool) {
        self.isManager = isManager
    }

    var isToday: Bool { ShopCalendar.isToday(selectedDate) }
    var selectedStaffEmail: String? { staff.first { $0.id == selectedStaffId }?.email }

    func onAppear() async {
        if isManager && staff.isEmpty {
            await loadStaff()
        }
        await load()
    }

    private func loadStaff() async {
        do {
            staff = try await APIClient.shared.send(Endpoint(path: "shop/users"))
        } catch {
            staff = []
        }
    }

    /// Ranks salespeople by their sales for the selected day (fans out one
    /// day-summary request per staff member — small N).
    private func refreshLeaderboard() async {
        guard isManager, !staff.isEmpty else { return }
        let dateString = ShopCalendar.apiDateString(selectedDate)
        var rows: [StaffSales] = []
        for sp in staff {
            let query = [URLQueryItem(name: "date", value: dateString), URLQueryItem(name: "created_by", value: sp.id.uuidString)]
            if let summary: DaySummary = try? await APIClient.shared.send(Endpoint(path: "bills/summary/today", queryItems: query)) {
                rows.append(StaffSales(salesperson: sp, sales: summary.totalSalesMoney))
            }
        }
        staffSales = rows.filter { $0.sales.isPositive }.sorted { $0.sales > $1.sales }
    }

    func load() async {
        summaryState = .loading
        billsLoading = true

        let dateString = ShopCalendar.apiDateString(selectedDate)
        var mutableSummaryQuery = [URLQueryItem(name: "date", value: dateString)]
        if let selectedStaffId { mutableSummaryQuery.append(URLQueryItem(name: "created_by", value: selectedStaffId.uuidString)) }
        let summaryQuery = mutableSummaryQuery

        async let summaryTask: Void = loadSummary(query: summaryQuery)
        async let billsTask: Void = loadBills(dateString: dateString, reset: true)
        async let leaderboardTask: Void = refreshLeaderboard()
        _ = await (summaryTask, billsTask, leaderboardTask)
    }

    private func loadSummary(query: [URLQueryItem]) async {
        do {
            let summary: DaySummary = try await APIClient.shared.send(Endpoint(path: "bills/summary/today", queryItems: query))
            summaryState = .loaded(summary)
        } catch let error as APIError {
            summaryState = .error(error.userMessage)
        } catch {
            summaryState = .error(APIError.unknown.userMessage)
        }
    }

    private func loadBills(dateString: String, reset: Bool) async {
        do {
            var query = [
                URLQueryItem(name: "date_from", value: dateString),
                URLQueryItem(name: "date_to", value: dateString),
                URLQueryItem(name: "limit", value: "\(pageSize)"),
                URLQueryItem(name: "offset", value: "\(reset ? 0 : bills.count)"),
            ]
            if let selectedStaffId { query.append(URLQueryItem(name: "created_by", value: selectedStaffId.uuidString)) }
            let page: BillListPage = try await APIClient.shared.send(Endpoint(path: "bills", queryItems: query))
            bills = reset ? page.items : bills + page.items
            hasMore = page.hasMore
            billsLoading = false
            loadingMore = false
        } catch let error as APIError {
            billsLoading = false
            loadingMore = false
            message = error.userMessage
        } catch {
            billsLoading = false
            loadingMore = false
        }
    }

    func loadMore() async {
        guard !loadingMore, hasMore else { return }
        loadingMore = true
        await loadBills(dateString: ShopCalendar.apiDateString(selectedDate), reset: false)
    }

    func changeDate(_ date: Date) {
        selectedDate = ShopCalendar.calendar.startOfDay(for: date)
        Task { await load() }
    }

    func goToPreviousDay() {
        changeDate(ShopCalendar.calendar.date(byAdding: .day, value: -1, to: selectedDate) ?? selectedDate)
    }

    func goToNextDay() {
        let next = ShopCalendar.calendar.date(byAdding: .day, value: 1, to: selectedDate) ?? selectedDate
        if next <= ShopCalendar.today() { changeDate(next) }
    }

    func selectStaff(_ id: UUID?) {
        selectedStaffId = id
        Task { await load() }
    }

    // MARK: Expense editor

    func openCreateExpense() { expenseEditor = ExpenseEditor() }
    func openEditExpense(_ expense: Expense) {
        expenseEditor = ExpenseEditor(id: expense.id, amount: expense.amount, reason: expense.reason, paymentMethod: expense.paymentMethod)
    }
    func closeExpenseEditor() { expenseEditor = nil }

    func saveExpense() async {
        guard var editor = expenseEditor, editor.canSave else { return }
        editor.saving = true
        expenseEditor = editor
        let reason = editor.reason.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            if editor.id == nil, editor.paymentMethod == "split" {
                // No split field on the backend's expense model — post the
                // cash and UPI portions as two separate rows instead.
                if editor.splitCashMoney.isPositive {
                    let body = try APIClient.shared.encode(ExpenseRequest(amount: editor.splitCashMoney.toWire(), reason: reason, paymentMethod: "cash"))
                    let _: Expense = try await APIClient.shared.send(Endpoint(path: "expenses", method: .post, body: body))
                }
                if editor.splitUpiMoney.isPositive {
                    let body = try APIClient.shared.encode(ExpenseRequest(amount: editor.splitUpiMoney.toWire(), reason: reason, paymentMethod: "upi"))
                    let _: Expense = try await APIClient.shared.send(Endpoint(path: "expenses", method: .post, body: body))
                }
            } else {
                let body = try APIClient.shared.encode(ExpenseRequest(amount: editor.amountMoney.toWire(), reason: reason, paymentMethod: editor.paymentMethod))
                if let id = editor.id {
                    let _: Expense = try await APIClient.shared.send(Endpoint(path: "expenses/\(id)", method: .patch, body: body))
                } else {
                    let _: Expense = try await APIClient.shared.send(Endpoint(path: "expenses", method: .post, body: body))
                }
            }
            expenseEditor = nil
            await load()
        } catch let error as APIError {
            editor.saving = false
            editor.error = error.userMessage
            expenseEditor = editor
        } catch {
            editor.saving = false
            editor.error = APIError.unknown.userMessage
            expenseEditor = editor
        }
    }

    func deleteExpense(_ id: UUID) async {
        do {
            try await APIClient.shared.sendNoContent(Endpoint(path: "expenses/\(id)", method: .delete))
            await load()
        } catch let error as APIError {
            message = error.userMessage
        } catch {
            message = APIError.unknown.userMessage
        }
    }

    func dismissMessage() { message = nil }
}
