import Foundation

/// Mirrors backend `ExpenseCreate`/`ExpenseUpdate` (app/schemas/expense.py).
struct ExpenseRequest: Encodable {
    let amount: String
    let reason: String
    let paymentMethod: String
}

/// Mirrors backend `SettlementCreate` (app/schemas/settlement.py).
struct SettlementCreateRequest: Encodable {
    let billId: UUID
    let cashAmount: String
    let upiAmount: String
}

/// Mirrors backend `SettlementActionResult`.
struct SettlementActionResult: Decodable {
    let id: UUID
    let billId: UUID
    let status: String
    let cashAmount: String
    let upiAmount: String
}

/// Mirrors backend `BillUpdate` (app/schemas/bill.py). Every field is
/// optional — the manager's payment-only edit (Dues settle) sends just
/// cash/upi/due, while the full line-item edit (Bill Edit) also sends
/// items/discount.
struct BillUpdateRequest: Encodable {
    struct Item: Encodable {
        let productId: UUID
        let quantity: Int
        let unitPrice: String
    }

    var cashAmount: String? = nil
    var upiAmount: String? = nil
    var dueAmount: String? = nil
    var remarks: String? = nil
    var items: [Item]? = nil
    var discountType: String? = nil
    var discountValue: String? = nil
}
