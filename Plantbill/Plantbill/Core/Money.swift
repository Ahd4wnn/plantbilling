import Foundation

/// Money value type — mirrors Android's `domain/Money.kt` exactly. The
/// server is the source of truth for all money; this type only displays and
/// sends amounts. Wire format is always a 2-decimal string ("120.00"),
/// matching the backend's `MoneyOut`/`NUMERIC(12,2)` convention. Never uses
/// Double.
struct Money: Comparable, Equatable {
    let amount: Decimal

    static let zero = Money(amount: 0)

    /// Parse a server/string amount; blank or invalid → zero.
    static func parse(_ raw: String?) -> Money {
        guard let raw else { return .zero }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, let decimal = Decimal(string: trimmed, locale: Locale(identifier: "en_US_POSIX")) else {
            return .zero
        }
        return Money(amount: decimal)
    }

    /// API wire value: exactly 2 decimals, e.g. "120.00".
    func toWire() -> String {
        Self.wireFormatter.string(from: NSDecimalNumber(decimal: rounded(scale: 2))) ?? "0.00"
    }

    /// Display value in whole rupees with en-IN (lakh/crore) grouping, e.g.
    /// "₹1,23,456". Paise are rounded away for display only.
    func format() -> String {
        "₹" + (Self.displayFormatter.string(from: NSDecimalNumber(decimal: rounded(scale: 0))) ?? "0")
    }

    /// Editable-field value with no decimals, e.g. "120" (for price inputs).
    func toInput() -> String {
        Self.inputFormatter.string(from: NSDecimalNumber(decimal: rounded(scale: 0))) ?? "0"
    }

    var isZero: Bool { rounded(scale: 2) == 0 }
    var isPositive: Bool { rounded(scale: 2) > 0 }
    var isNegative: Bool { rounded(scale: 2) < 0 }

    static func + (lhs: Money, rhs: Money) -> Money { Money(amount: lhs.amount + rhs.amount) }
    static func - (lhs: Money, rhs: Money) -> Money { Money(amount: lhs.amount - rhs.amount) }
    static func * (lhs: Money, qty: Int) -> Money { Money(amount: lhs.amount * Decimal(qty)) }

    static func < (lhs: Money, rhs: Money) -> Bool { lhs.rounded(scale: 2) < rhs.rounded(scale: 2) }
    static func == (lhs: Money, rhs: Money) -> Bool { lhs.rounded(scale: 2) == rhs.rounded(scale: 2) }

    private func rounded(scale: Int16) -> Decimal {
        var result = Decimal()
        var mutableAmount = amount
        NSDecimalRound(&result, &mutableAmount, Int(scale), .plain)
        return result
    }

    private static let wireFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        return formatter
    }()

    private static let displayFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_IN")
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 0
        return formatter
    }()

    private static let inputFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 0
        return formatter
    }()
}
