import Foundation

/// Everything needed to render a printable receipt — a common shape both
/// `BillOut` (right after checkout) and `BillDetail` (viewing/reprinting
/// later) can map to, so the print layout only needs to be built once.
struct ReceiptPrintData {
    struct Item {
        let name: String
        let quantity: Int
        let unitPrice: Money
        let lineTotal: Money
    }

    let businessName: String
    let businessAddress: String?
    let businessPhone: String?
    let createdAt: Date
    let customerName: String?
    let customerPhone: String?
    let items: [Item]
    let subtotal: Money
    let discountAmount: Money
    let total: Money
    let cashAmount: Money
    let upiAmount: Money
    let dueAmount: Money
    let remarks: String?
    let isEdited: Bool
}

extension BillDetail {
    var receiptData: ReceiptPrintData {
        ReceiptPrintData(
            businessName: businessName ?? shopName ?? "Receipt",
            businessAddress: businessAddress,
            businessPhone: businessPhone,
            createdAt: createdAt,
            customerName: customerName,
            customerPhone: customerPhone,
            items: items.map { .init(name: $0.productName, quantity: $0.quantity, unitPrice: $0.unitPriceMoney, lineTotal: $0.lineTotalMoney) },
            subtotal: subtotalMoney,
            discountAmount: discountAmountMoney,
            total: totalMoney,
            cashAmount: cashAmountMoney,
            upiAmount: upiAmountMoney,
            dueAmount: dueAmountMoney,
            remarks: remarks,
            isEdited: isEdited
        )
    }
}

extension BillOut {
    var receiptData: ReceiptPrintData {
        ReceiptPrintData(
            businessName: BusinessProfile.shared.businessName ?? BusinessProfile.shared.shopName ?? "Receipt",
            businessAddress: nil,
            businessPhone: nil,
            createdAt: createdAt,
            customerName: customerName,
            customerPhone: nil,
            items: items.map { .init(name: $0.productName, quantity: $0.quantity, unitPrice: Money.parse($0.unitPrice), lineTotal: Money.parse($0.lineTotal)) },
            subtotal: Money.parse(subtotal),
            discountAmount: Money.parse(discountAmount),
            total: Money.parse(total),
            cashAmount: Money.parse(cashAmount),
            upiAmount: Money.parse(upiAmount),
            dueAmount: Money.parse(dueAmount),
            remarks: remarks,
            isEdited: isEdited
        )
    }
}
