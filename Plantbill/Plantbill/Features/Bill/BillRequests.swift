import Foundation

/// Mirrors backend `BillCreate` (app/schemas/bill.py).
struct BillCreateRequest: Encodable {
    struct Item: Encodable {
        let productId: UUID
        let quantity: Int
        let unitPrice: String
    }
    struct NewCustomer: Encodable {
        let name: String
        let phone: String?
    }

    let idempotencyKey: String
    let items: [Item]
    let discountType: String
    let discountValue: String
    let cashAmount: String
    let upiAmount: String
    let dueAmount: String
    let remarks: String?
    let newCustomer: NewCustomer?
}

/// Mirrors backend `BillOut`.
struct BillOut: Decodable {
    struct Item: Decodable {
        let productId: UUID?
        let productName: String
        let unitPrice: String
        let quantity: Int
        let lineTotal: String
    }

    let id: UUID
    let billType: String
    let subtotal: String
    let discountType: String
    let discountValue: String
    let discountAmount: String
    let total: String
    let cashAmount: String
    let upiAmount: String
    let dueAmount: String
    let customerId: UUID?
    let customerName: String?
    let remarks: String?
    let isEdited: Bool
    let createdAt: Date
    let items: [Item]
    let idempotentReplay: Bool
    let idempotencyKey: String?
}
