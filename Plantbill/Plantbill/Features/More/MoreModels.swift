import Foundation

/// Mirrors backend `ShopSettingsOut` (app/schemas/shop.py).
struct ShopSettings: Decodable {
    let id: UUID
    let name: String
    let ownerName: String?
    let ownerPhone: String?
    let isActive: Bool
    let businessName: String?
    let businessAddress: String?
    let businessPhone: String?
    let businessEmail: String?
    let businessUpi: String?
}

/// Mirrors backend `CashInHandOut`.
struct CashInHandOut: Decodable {
    let cashInHandRunning: String
    var cashInHandRunningMoney: Money { Money.parse(cashInHandRunning) }
}

struct CashInHandSetRequest: Encodable {
    let amount: String
}

// MARK: Staff (salespeople)

struct SalespersonCreateRequest: Encodable {
    let email: String
    let password: String
}

struct SalespersonActivateRequest: Encodable {
    let isActive: Bool
}

struct SalespersonResetPasswordRequest: Encodable {
    let newPassword: String
}

// MARK: Labour

/// Mirrors backend `LabourerOut` (app/schemas/labour.py).
struct Labourer: Decodable, Identifiable, Equatable {
    let id: UUID
    let name: String
    let phone: String?
    let aadhaar: String?
    /// "male" | "female"
    let gender: String
    let defaultWage: String
    let isActive: Bool
    let daysWorked: String
    let totalPaid: String
    let earned: String
    let balanceToPay: String
    let createdAt: Date

    var defaultWageMoney: Money { Money.parse(defaultWage) }
    var totalPaidMoney: Money { Money.parse(totalPaid) }
    var earnedMoney: Money { Money.parse(earned) }
    var balanceToPayMoney: Money { Money.parse(balanceToPay) }
}

struct LabourerRequest: Encodable {
    var name: String? = nil
    var phone: String? = nil
    var aadhaar: String? = nil
    var gender: String? = nil
    var defaultWage: String? = nil
    var isActive: Bool? = nil
}

/// Mirrors backend `LabourPaymentOut`.
struct LabourPayment: Decodable, Identifiable, Equatable {
    let id: UUID
    let labourerId: UUID?
    let labourerName: String
    let gender: String
    /// "wage" | "advance" | "due_clear"
    let kind: String
    let wageAmount: String
    let days: String?
    let totalAmount: String
    let cashAmount: String
    let upiAmount: String
    let dueAmount: String
    let paymentMethod: PaymentMethodKind
    let note: String?
    let recordedByEmail: String?
    let createdAt: Date

    var totalAmountMoney: Money { Money.parse(totalAmount) }
    var cashAmountMoney: Money { Money.parse(cashAmount) }
    var upiAmountMoney: Money { Money.parse(upiAmount) }
}

/// Mirrors backend `LabourPaymentCreate`.
struct LabourPaymentCreateRequest: Encodable {
    let labourerId: UUID
    let kind: String
    let wageAmount: String
    let days: String?
    let cashAmount: String
    let upiAmount: String
    let dueAmount: String
    let note: String?
}

struct LabourPaymentUpdateRequest: Encodable {
    var wageAmount: String? = nil
    var days: String? = nil
    var cashAmount: String? = nil
    var upiAmount: String? = nil
    var dueAmount: String? = nil
    var note: String? = nil
}

/// Mirrors backend `AttendanceOut`.
struct Attendance: Decodable, Identifiable, Equatable {
    let id: UUID
    let labourerId: UUID
    let labourerName: String
    /// "YYYY-MM-DD"
    let day: String
    /// "present" | "absent" | "half_day"
    let status: String
    let createdAt: Date
}

struct AttendanceMarkRequest: Encodable {
    let labourerId: UUID
    let day: String
    let status: String
}

// MARK: Borrowings

/// Mirrors backend `BorrowingOut`.
struct Borrowing: Decodable, Identifiable, Equatable {
    let id: UUID
    let lenderName: String
    let lenderPhone: String?
    let amount: String
    let cashAmount: String
    let upiAmount: String
    /// "cash" | "upi" | "split" | "none"
    let method: String
    let remarks: String?
    let isPaid: Bool
    let paidCashAmount: String
    let paidUpiAmount: String
    let paidMethod: String
    let outstanding: String
    let paidAt: Date?
    let createdAt: Date

    var amountMoney: Money { Money.parse(amount) }
    var outstandingMoney: Money { Money.parse(outstanding) }
}

struct BorrowingList: Decodable {
    let items: [Borrowing]
    let totalOutstanding: String
    var totalOutstandingMoney: Money { Money.parse(totalOutstanding) }
}

struct BorrowingCreateRequest: Encodable {
    let lenderName: String
    let lenderPhone: String?
    let amount: String
    let cashAmount: String
    let upiAmount: String
    let remarks: String?
}

struct BorrowingPayRequest: Encodable {
    let paidCashAmount: String
    let paidUpiAmount: String
}
