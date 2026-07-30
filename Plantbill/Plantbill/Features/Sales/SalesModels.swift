import Foundation

/// Mirrors backend `ExpenseOut` (app/schemas/expense.py).
struct Expense: Decodable, Identifiable, Equatable {
    let id: UUID
    let amount: String
    let reason: String
    /// "cash" (out of the drawer) or "upi".
    let paymentMethod: String
    let createdAt: Date

    var amountMoney: Money { Money.parse(amount) }
}

/// Mirrors backend `BillSummaryOut` — a day's takings + cash book (shop
/// timezone, computed server-side).
struct DaySummary: Decodable {
    let date: String
    let totalSales: String
    let billCount: Int
    let cashTotal: String
    let upiTotal: String
    let dueTotal: String
    let totalExpenses: String
    let cashExpenses: String
    let upiExpenses: String
    let labourTotal: String
    let labourCash: String
    let borrowCashToday: String
    let cashInHandRunning: String
    let netSales: String
    let expenses: [Expense]

    var totalSalesMoney: Money { Money.parse(totalSales) }
    var cashTotalMoney: Money { Money.parse(cashTotal) }
    var upiTotalMoney: Money { Money.parse(upiTotal) }
    var dueTotalMoney: Money { Money.parse(dueTotal) }
    var totalExpensesMoney: Money { Money.parse(totalExpenses) }
    var cashExpensesMoney: Money { Money.parse(cashExpenses) }
    var labourTotalMoney: Money { Money.parse(labourTotal) }
    var labourCashMoney: Money { Money.parse(labourCash) }
    var borrowCashTodayMoney: Money { Money.parse(borrowCashToday) }
    var cashInHandRunningMoney: Money { Money.parse(cashInHandRunning) }
    var netSalesMoney: Money { Money.parse(netSales) }

    /// Cash left in the drawer today only: cash sales − cash expenses −
    /// labour paid in cash + net cash borrowed in today.
    var cashInHandToday: Money {
        cashTotalMoney - cashExpensesMoney - labourCashMoney + borrowCashTodayMoney
    }
}

/// Mirrors backend `PaymentMethod` literal.
enum PaymentMethodKind: String, Decodable {
    case cash, upi, split, due
}

/// A compact bill row for the history list — mirrors `BillListItem`.
struct BillListEntry: Decodable, Identifiable, Equatable {
    let id: UUID
    let createdAt: Date
    let total: String
    let dueAmount: String
    let customerName: String?
    let customerPhone: String?
    let itemCount: Int
    let paymentMethod: PaymentMethodKind
    let isEdited: Bool
    let pendingSettlement: Bool

    var totalMoney: Money { Money.parse(total) }
    var dueAmountMoney: Money { Money.parse(dueAmount) }

    /// Days since creation (shop-relevant, whole days) — used to flag
    /// priority (30+ day overdue) dues.
    var daysSinceCreated: Int { ShopCalendar.daysSince(createdAt) }
}

struct BillListPage: Decodable {
    let items: [BillListEntry]
    let limit: Int
    let offset: Int
    let hasMore: Bool
}

/// A complete, self-contained bill for the detail / edit surface — mirrors
/// `BillDetailOut`.
struct BillDetail: Decodable {
    struct Item: Decodable, Identifiable {
        let productId: UUID?
        let productName: String
        let unitPrice: String
        let quantity: Int
        let lineTotal: String

        var id: String { "\(productId?.uuidString ?? "adhoc")-\(productName)-\(unitPrice)-\(quantity)" }
        var unitPriceMoney: Money { Money.parse(unitPrice) }
        var lineTotalMoney: Money { Money.parse(lineTotal) }
    }

    let id: UUID
    let shopName: String?
    let businessName: String?
    let businessAddress: String?
    let businessPhone: String?
    let subtotal: String
    let discountType: String
    let discountValue: String
    let discountAmount: String
    let total: String
    let cashAmount: String
    let upiAmount: String
    let dueAmount: String
    let paymentMethod: PaymentMethodKind
    let customerId: UUID?
    let customerName: String?
    let customerPhone: String?
    let salespersonEmail: String?
    let remarks: String?
    let isEdited: Bool
    let createdAt: Date
    let items: [Item]

    var subtotalMoney: Money { Money.parse(subtotal) }
    var discountAmountMoney: Money { Money.parse(discountAmount) }
    var totalMoney: Money { Money.parse(total) }
    var cashAmountMoney: Money { Money.parse(cashAmount) }
    var upiAmountMoney: Money { Money.parse(upiAmount) }
    var dueAmountMoney: Money { Money.parse(dueAmount) }
}

/// A salesperson's collection of a due, awaiting manager approval — mirrors
/// `SettlementOut`.
struct PendingSettlement: Decodable, Identifiable, Equatable {
    let id: UUID
    let billId: UUID
    let status: String
    let cashAmount: String
    let upiAmount: String
    let billTotal: String
    let customerName: String?
    let customerPhone: String?
    let requestedByEmail: String?
    let createdAt: Date

    var cashAmountMoney: Money { Money.parse(cashAmount) }
    var upiAmountMoney: Money { Money.parse(upiAmount) }
    var amountMoney: Money { cashAmountMoney + upiAmountMoney }
}

struct PendingSettlementList: Decodable {
    let items: [PendingSettlement]
}

/// Mirrors backend `SalespersonOut` (app/schemas/shop_user.py).
struct Salesperson: Decodable, Identifiable, Equatable {
    let id: UUID
    let email: String
    let isActive: Bool
}

/// Mirrors backend `DetailedReportResponse` (app/schemas/report.py).
struct DetailedReport: Decodable {
    struct CategorySales: Decodable, Identifiable {
        let category: String?
        let quantity: Int
        let totalSales: String
        var id: String { category ?? "Uncategorized" }
        var totalSalesMoney: Money { Money.parse(totalSales) }
    }
    struct ProductSales: Decodable, Identifiable {
        let productName: String
        let quantity: Int
        let totalSales: String
        var id: String { productName }
        var totalSalesMoney: Money { Money.parse(totalSales) }
    }

    let startDate: String
    let endDate: String
    let totalSales: String
    let billCount: Int
    let cashTotal: String
    let upiTotal: String
    let dueTotal: String
    let averageBillValue: String
    let totalExpenses: String
    let netSales: String
    let expenses: [Expense]
    let categories: [CategorySales]
    let topProducts: [ProductSales]

    var totalSalesMoney: Money { Money.parse(totalSales) }
    var cashTotalMoney: Money { Money.parse(cashTotal) }
    var upiTotalMoney: Money { Money.parse(upiTotal) }
    var dueTotalMoney: Money { Money.parse(dueTotal) }
    var averageBillValueMoney: Money { Money.parse(averageBillValue) }
    var totalExpensesMoney: Money { Money.parse(totalExpenses) }
    var netSalesMoney: Money { Money.parse(netSales) }
}
