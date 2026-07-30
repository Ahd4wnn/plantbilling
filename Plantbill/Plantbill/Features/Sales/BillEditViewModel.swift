import Foundation
import Observation

/// Owner-only bill editing: change a line's price/quantity, add or remove
/// plants, adjust the discount and payment split. The server recomputes
/// every amount and marks the bill edited.
@Observable
@MainActor
final class BillEditViewModel {
    let billId: UUID

    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var products: [Product] = []
    private(set) var lines: [CartLine] = []

    var discountType: DiscountType = .flat
    var discountValueText: String = ""
    var paymentMode: PaymentMode = .cash
    var cashPartText: String = ""
    var dueAmountText: String = ""
    var remarks: String = ""
    private(set) var customerPhone: String?

    var showingAddPicker = false
    private(set) var isSaving = false
    var saveError: String?
    var saved = false

    init(billId: UUID) {
        self.billId = billId
    }

    var subtotal: Money { CartMath.subtotal(lines) }
    var discountValueMoney: Money { Money.parse(discountValueText) }
    var discountAmount: Money { CartMath.discountAmount(subtotal: subtotal, type: discountType, value: discountValueMoney) }
    var total: Money { CartMath.total(subtotal: subtotal, discount: discountAmount) }
    var isEmpty: Bool { lines.isEmpty }

    private var cashPartMoney: Money { Money.parse(cashPartText) }
    private var dueMoney: Money {
        let raw = Money.parse(dueAmountText)
        return raw > total ? total : raw
    }
    var cashAmount: Money {
        switch paymentMode {
        case .cash: return total - dueMoney
        case .upi: return .zero
        case .split: return min(cashPartMoney, total - dueMoney)
        }
    }
    var upiAmount: Money {
        let remainder = total - dueMoney - cashAmount
        return remainder.isNegative ? .zero : remainder
    }

    func load() async {
        isLoading = true
        loadError = nil
        async let detailTask: BillDetail? = try? APIClient.shared.send(Endpoint(path: "bills/\(billId)"))
        async let productsTask: [Product] = (try? APIClient.shared.send(Endpoint(path: "products", queryItems: [URLQueryItem(name: "active", value: "true")]))) ?? []
        let (detail, catalog) = await (detailTask, productsTask)
        products = catalog
        guard let detail else {
            isLoading = false
            loadError = "Couldn't load this bill."
            return
        }
        seed(from: detail, catalog: catalog)
        isLoading = false
    }

    private func seed(from detail: BillDetail, catalog: [Product]) {
        lines = detail.items.compactMap { item -> CartLine? in
            guard let productId = item.productId else { return nil }
            let name = catalog.first { $0.id == productId }?.name ?? item.productName
            return CartLine(productId: productId, productName: name, unitPrice: item.unitPriceMoney, quantity: item.quantity)
        }
        discountType = DiscountType(rawValue: detail.discountType) ?? .flat
        let discountValue = Money.parse(detail.discountValue)
        discountValueText = discountValue.isPositive ? discountValue.toInput() : ""
        if detail.cashAmountMoney.isPositive && detail.upiAmountMoney.isPositive {
            paymentMode = .split
        } else if detail.upiAmountMoney.isPositive {
            paymentMode = .upi
        } else {
            paymentMode = .cash
        }
        cashPartText = detail.cashAmountMoney.isPositive ? detail.cashAmountMoney.toInput() : ""
        dueAmountText = detail.dueAmountMoney.isPositive ? detail.dueAmountMoney.toInput() : ""
        remarks = detail.remarks ?? ""
        customerPhone = detail.customerPhone
    }

    // MARK: Line editing

    func setQuantity(lineId: UUID, quantity: Int) {
        if quantity <= 0 {
            lines.removeAll { $0.id == lineId }
        } else if let index = lines.firstIndex(where: { $0.id == lineId }) {
            lines[index].quantity = quantity
        }
    }

    func setUnitPrice(lineId: UUID, price: Money) {
        guard let index = lines.firstIndex(where: { $0.id == lineId }) else { return }
        lines[index].unitPrice = price
    }

    func removeLine(lineId: UUID) {
        lines.removeAll { $0.id == lineId }
    }

    func addProduct(_ product: Product) {
        lines.append(CartLine(productId: product.id, productName: product.name, unitPrice: product.price, quantity: 1))
        showingAddPicker = false
    }

    // MARK: Save

    func save() async {
        guard !isEmpty, !isSaving else { return }
        let due = dueMoney
        if due.isPositive, (customerPhone?.filter(\.isNumber).count ?? 0) < 10 {
            saveError = "This bill has no phone on file — can't leave money due without one."
            return
        }
        isSaving = true
        saveError = nil
        do {
            let request = BillUpdateRequest(
                cashAmount: cashAmount.toWire(),
                upiAmount: upiAmount.toWire(),
                dueAmount: due.toWire(),
                remarks: remarks.trimmingCharacters(in: .whitespacesAndNewlines),
                items: lines.map { .init(productId: $0.productId, quantity: $0.quantity, unitPrice: $0.unitPrice.toWire()) },
                discountType: discountType.rawValue,
                discountValue: discountValueMoney.toWire()
            )
            let body = try APIClient.shared.encode(request)
            let _: BillDetail = try await APIClient.shared.send(Endpoint(path: "bills/\(billId)", method: .patch, body: body))
            isSaving = false
            saved = true
        } catch let error as APIError {
            isSaving = false
            saveError = error.userMessage
        } catch {
            isSaving = false
            saveError = APIError.unknown.userMessage
        }
    }
}
