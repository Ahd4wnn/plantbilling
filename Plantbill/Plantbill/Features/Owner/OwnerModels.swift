import Foundation
import SwiftUI

/// Mirrors backend `OwnerShop` (app/schemas/owner.py) — one shop the owner owns.
struct OwnerShop: Decodable, Identifiable, Equatable {
    let id: UUID
    let name: String
    let isActive: Bool
    let businessName: String?
    let businessAddress: String?
    let businessPhone: String?
    let businessEmail: String?
    let businessUpi: String?
}

/// Mirrors backend `ShopOverviewRow` — one shop's takings within the period.
struct ShopOverviewRow: Decodable, Identifiable, Equatable {
    let shopId: UUID
    let shopName: String
    let totalSales: String
    let billCount: Int
    let cashTotal: String
    let upiTotal: String
    let dueTotal: String
    let totalExpenses: String
    let netSales: String

    var id: UUID { shopId }
    var totalSalesMoney: Money { Money.parse(totalSales) }
    var cashTotalMoney: Money { Money.parse(cashTotal) }
    var upiTotalMoney: Money { Money.parse(upiTotal) }
    var dueTotalMoney: Money { Money.parse(dueTotal) }
    var totalExpensesMoney: Money { Money.parse(totalExpenses) }
    var netSalesMoney: Money { Money.parse(netSales) }
}

/// Mirrors backend `StaffPerformance` — a staff member's sales across the
/// owner's shops in the period (leaderboard row).
struct StaffPerformance: Decodable, Identifiable, Equatable {
    let userId: UUID?
    let email: String?
    let shopId: UUID
    let shopName: String
    let role: String
    let totalSales: String
    let billCount: Int

    var id: String { "\(userId?.uuidString ?? "unknown")-\(shopId.uuidString)" }
    var totalSalesMoney: Money { Money.parse(totalSales) }
}

/// Mirrors backend `OwnerOverview` — aggregate across all owned shops.
struct OwnerOverview: Decodable {
    let startDate: String
    let endDate: String
    let shopCount: Int
    let totalSales: String
    let billCount: Int
    let cashTotal: String
    let upiTotal: String
    let dueTotal: String
    let totalExpenses: String
    let netSales: String
    let shops: [ShopOverviewRow]
    let staff: [StaffPerformance]

    var totalSalesMoney: Money { Money.parse(totalSales) }
    var cashTotalMoney: Money { Money.parse(cashTotal) }
    var upiTotalMoney: Money { Money.parse(upiTotal) }
    var dueTotalMoney: Money { Money.parse(dueTotal) }
    var totalExpensesMoney: Money { Money.parse(totalExpenses) }
    var netSalesMoney: Money { Money.parse(netSales) }
}

/// Mirrors backend `OwnerBillRow` — one saved bill in an owned shop.
struct OwnerBillRow: Decodable, Identifiable, Equatable {
    let id: UUID
    let createdAt: Date
    let total: String
    let dueAmount: String
    let paymentMethod: String
    let customerName: String?
    let customerPhone: String?
    let salespersonEmail: String?
    let salespersonRole: String?
    let itemCount: Int

    var totalMoney: Money { Money.parse(total) }
    var dueAmountMoney: Money { Money.parse(dueAmount) }
}

struct OwnerBillList: Decodable {
    let items: [OwnerBillRow]
    let limit: Int
    let offset: Int
    let hasMore: Bool
}

/// Mirrors backend `OwnerCashInHand`.
/// Mirrors backend `OwnerCashInHand` (defined inline in routers/owner.py).
/// Unlike every other money field in this backend, this one has no
/// `@field_serializer` forcing a 2dp string, so Pydantic serializes its
/// `Decimal` fields as raw JSON numbers — decode either shape defensively.
struct OwnerCashInHand: Decodable {
    let date: String
    let runningMoney: Money
    let todayMoney: Money

    private enum CodingKeys: String, CodingKey {
        case date, cashInHandRunning, cashInHandToday
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        date = try container.decode(String.self, forKey: .date)
        runningMoney = Self.decodeMoney(container, .cashInHandRunning)
        todayMoney = Self.decodeMoney(container, .cashInHandToday)
    }

    private static func decodeMoney(_ container: KeyedDecodingContainer<CodingKeys>, _ key: CodingKeys) -> Money {
        if let string = try? container.decode(String.self, forKey: key) {
            return Money.parse(string)
        }
        if let value = try? container.decode(Double.self, forKey: key) {
            return Money(amount: Decimal(value))
        }
        return .zero
    }
}

/// Mirrors backend `OwnerStaffOut`.
struct OwnerStaff: Decodable, Identifiable, Equatable {
    let id: UUID
    let email: String
    let role: String
    let isActive: Bool
    let shopId: UUID?
    let createdAt: Date
}

struct OwnerStaffCreateRequest: Encodable {
    let email: String
    let password: String
    let role: String
}

struct OwnerStaffActivateRequest: Encodable {
    let isActive: Bool
}

struct OwnerStaffResetPasswordRequest: Encodable {
    let newPassword: String
}

struct OwnerShopUpdateRequest: Encodable {
    var businessName: String? = nil
    var businessAddress: String? = nil
    var businessPhone: String? = nil
    var businessEmail: String? = nil
    var businessUpi: String? = nil
}

enum OwnerPeriod: String, CaseIterable {
    case today, week, month, custom

    var title: LocalizedStringKey {
        switch self {
        case .today: return "Today"
        case .week: return "Week"
        case .month: return "Month"
        case .custom: return "Custom"
        }
    }

    /// [from, to] inclusive, in shop time.
    func range(customFrom: Date, customTo: Date) -> (Date, Date) {
        let today = ShopCalendar.today()
        switch self {
        case .today:
            return (today, today)
        case .week:
            return (ShopCalendar.calendar.date(byAdding: .day, value: -6, to: today) ?? today, today)
        case .month:
            let comps = ShopCalendar.calendar.dateComponents([.year, .month], from: today)
            let startOfMonth = ShopCalendar.calendar.date(from: comps) ?? today
            return (startOfMonth, today)
        case .custom:
            return (customFrom, customTo)
        }
    }
}
