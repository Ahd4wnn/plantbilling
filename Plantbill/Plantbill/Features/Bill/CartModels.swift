import Foundation

/// A line in the in-progress cart. Client-generated id, never coalesced —
/// tapping the same product twice makes two lines (plants of the same kind
/// often sell at different prices by size), mirroring Android's CartLine.
struct CartLine: Identifiable, Equatable {
    let id: UUID
    let productId: UUID
    var productName: String
    var unitPrice: Money
    var quantity: Int

    var lineTotal: Money { unitPrice * quantity }

    init(id: UUID = UUID(), productId: UUID, productName: String, unitPrice: Money, quantity: Int = 1) {
        self.id = id
        self.productId = productId
        self.productName = productName
        self.unitPrice = unitPrice
        self.quantity = quantity
    }
}

enum DiscountType: String {
    case flat
    case percent
}

enum PaymentMode: String {
    case cash, upi, split
}

/// DISPLAY ONLY — mirrors the server's rules so the UI shows a live, correct
/// preview before submit, but the server recomputes and is authoritative on
/// every checkout.
enum CartMath {
    static func subtotal(_ lines: [CartLine]) -> Money {
        lines.reduce(Money.zero) { $0 + $1.lineTotal }
    }

    static func discountAmount(subtotal: Money, type: DiscountType, value: Money) -> Money {
        switch type {
        case .flat:
            return min(value, subtotal)
        case .percent:
            let clampedPercent = min(value.amount, 100)
            return Money(amount: subtotal.amount * clampedPercent / 100)
        }
    }

    static func total(subtotal: Money, discount: Money) -> Money {
        subtotal - discount
    }
}
