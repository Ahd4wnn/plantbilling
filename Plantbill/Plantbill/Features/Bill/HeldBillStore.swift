import Foundation

/// A fully self-contained cart snapshot — doesn't depend on the live
/// product catalog still containing the item. Device-local only, never
/// synced to the server until final checkout. Mirrors Android's
/// `HeldBillStore` (DataStore JSON) using UserDefaults instead.
struct HeldBill: Identifiable, Codable, Equatable {
    struct Line: Codable, Equatable {
        let id: UUID
        let productId: UUID
        let productName: String
        let unitPriceWire: String
        let quantity: Int
    }

    let id: UUID
    let savedAt: Date
    let idempotencyKey: String
    var lines: [Line]
    var discountType: String
    var discountValueText: String
    var paymentMode: String
    var cashAmountText: String
    var upiAmountText: String
    var dueAmountText: String
    var customerName: String
    var customerPhone: String
    var remarks: String
}

enum HeldBillStore {
    private static let key = "held_bills"

    static func load() -> [HeldBill] {
        guard let data = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([HeldBill].self, from: data)) ?? []
    }

    static func save(_ bills: [HeldBill]) {
        guard let data = try? JSONEncoder().encode(bills) else { return }
        UserDefaults.standard.set(data, forKey: key)
    }

    static func add(_ bill: HeldBill) {
        var bills = load()
        bills.insert(bill, at: 0)
        save(bills)
    }

    static func remove(id: UUID) {
        save(load().filter { $0.id != id })
    }
}
