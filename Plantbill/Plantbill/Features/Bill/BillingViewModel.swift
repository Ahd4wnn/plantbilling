import Foundation
import Observation

@Observable
@MainActor
final class BillingViewModel {
    enum ProductLoadState {
        case loading
        case loaded([Product])
        case empty
        case error(String)
    }

    enum CheckoutState: Equatable {
        case idle
        case submitting
        case success(BillOut)
        case error(String)

        static func == (lhs: CheckoutState, rhs: CheckoutState) -> Bool {
            switch (lhs, rhs) {
            case (.idle, .idle), (.submitting, .submitting): return true
            case (.success(let a), .success(let b)): return a.id == b.id
            case (.error(let a), .error(let b)): return a == b
            default: return false
            }
        }
    }

    // MARK: Product browsing

    private(set) var productState: ProductLoadState = .loading
    var searchText: String = "" { didSet { scheduleReload() } }
    var selectedCategory: String? = nil { didSet { Task { await loadProducts() } } }
    private(set) var categories: [String] = []
    private var searchDebounceTask: Task<Void, Never>?

    // MARK: Cart

    private(set) var cartLines: [CartLine] = []
    var discountType: DiscountType = .flat
    var discountValueText: String = ""
    var paymentMode: PaymentMode = .cash
    var cashPartText: String = ""
    var dueAmountText: String = ""
    var customerName: String = ""
    var customerPhone: String = ""
    var remarks: String = ""
    private(set) var idempotencyKey = UUID().uuidString

    // MARK: Held bills

    private(set) var heldBills: [HeldBill] = HeldBillStore.load()

    // MARK: Checkout

    private(set) var checkoutState: CheckoutState = .idle

    // MARK: Computed (display-only preview — server is authoritative)

    var subtotal: Money { CartMath.subtotal(cartLines) }
    var discountValueMoney: Money { Money.parse(discountValueText) }
    var discountAmount: Money { CartMath.discountAmount(subtotal: subtotal, type: discountType, value: discountValueMoney) }
    var total: Money { CartMath.total(subtotal: subtotal, discount: discountAmount) }
    var dueAmount: Money { Money.parse(dueAmountText) }
    var amountToCollect: Money {
        let remainder = total - dueAmount
        return remainder.isNegative ? .zero : remainder
    }
    var cashAmount: Money {
        switch paymentMode {
        case .cash: return amountToCollect
        case .upi: return .zero
        case .split: return min(Money.parse(cashPartText), amountToCollect)
        }
    }
    var upiAmount: Money {
        switch paymentMode {
        case .cash: return .zero
        case .upi: return amountToCollect
        case .split: return amountToCollect - cashAmount
        }
    }
    var requiresCustomerPhone: Bool { dueAmount.isPositive }

    // MARK: Product loading

    private func scheduleReload() {
        searchDebounceTask?.cancel()
        searchDebounceTask = Task {
            try? await Task.sleep(for: .milliseconds(300))
            guard !Task.isCancelled else { return }
            await loadProducts()
        }
    }

    func loadProducts() async {
        productState = .loading
        do {
            var query: [URLQueryItem] = [URLQueryItem(name: "active", value: "true")]
            let trimmed = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { query.append(URLQueryItem(name: "q", value: trimmed)) }
            if let selectedCategory { query.append(URLQueryItem(name: "category", value: selectedCategory)) }

            let products: [Product] = try await APIClient.shared.send(Endpoint(path: "products", queryItems: query))
            categories = Array(Set(products.compactMap { $0.category?.isEmpty == false ? $0.category : nil })).sorted()
            productState = products.isEmpty ? .empty : .loaded(products)
        } catch let error as APIError {
            productState = .error(error.userMessage)
        } catch {
            productState = .error(APIError.unknown.userMessage)
        }
    }

    // MARK: Cart line editing

    func addToCart(_ product: Product) {
        cartLines.append(CartLine(productId: product.id, productName: product.name, unitPrice: product.price, quantity: 1))
    }

    func updateQuantity(lineId: UUID, quantity: Int) {
        guard let index = cartLines.firstIndex(where: { $0.id == lineId }) else { return }
        if quantity <= 0 {
            cartLines.remove(at: index)
        } else {
            cartLines[index].quantity = quantity
        }
    }

    func updatePrice(lineId: UUID, price: Money) {
        guard let index = cartLines.firstIndex(where: { $0.id == lineId }) else { return }
        cartLines[index].unitPrice = price
    }

    func removeLine(lineId: UUID) {
        cartLines.removeAll { $0.id == lineId }
    }

    func clearCart() {
        cartLines = []
        discountType = .flat
        discountValueText = ""
        paymentMode = .cash
        cashPartText = ""
        dueAmountText = ""
        customerName = ""
        customerPhone = ""
        remarks = ""
        idempotencyKey = UUID().uuidString
        checkoutState = .idle
    }

    // MARK: Quick add

    func quickAdd(name: String, price: Money, quantity: Int) async -> Bool {
        do {
            let body = try APIClient.shared.encode(
                ProductCreateRequest(name: name, category: "Quick Add", retailPrice: price.toWire(), lastWholesalePrice: nil)
            )
            let product: Product = try await APIClient.shared.send(Endpoint(path: "products", method: .post, body: body))
            cartLines.append(CartLine(productId: product.id, productName: product.name, unitPrice: price, quantity: quantity))
            return true
        } catch {
            return false
        }
    }

    // MARK: Held bills

    func holdCurrentBill() {
        guard !cartLines.isEmpty else { return }
        HeldBillStore.add(snapshotCurrentBill())
        heldBills = HeldBillStore.load()
        clearCart()
    }

    func resume(_ held: HeldBill) {
        if !cartLines.isEmpty {
            HeldBillStore.add(snapshotCurrentBill())
        }
        cartLines = held.lines.map {
            CartLine(id: $0.id, productId: $0.productId, productName: $0.productName, unitPrice: Money.parse($0.unitPriceWire), quantity: $0.quantity)
        }
        discountType = DiscountType(rawValue: held.discountType) ?? .flat
        discountValueText = held.discountValueText
        paymentMode = PaymentMode(rawValue: held.paymentMode) ?? .cash
        cashPartText = held.cashAmountText
        dueAmountText = held.dueAmountText
        customerName = held.customerName
        customerPhone = held.customerPhone
        remarks = held.remarks
        idempotencyKey = held.idempotencyKey
        checkoutState = .idle

        HeldBillStore.remove(id: held.id)
        heldBills = HeldBillStore.load()
    }

    func discardHeld(_ held: HeldBill) {
        HeldBillStore.remove(id: held.id)
        heldBills = HeldBillStore.load()
    }

    private func snapshotCurrentBill() -> HeldBill {
        HeldBill(
            id: UUID(),
            savedAt: Date(),
            idempotencyKey: idempotencyKey,
            lines: cartLines.map {
                HeldBill.Line(id: $0.id, productId: $0.productId, productName: $0.productName, unitPriceWire: $0.unitPrice.toWire(), quantity: $0.quantity)
            },
            discountType: discountType.rawValue,
            discountValueText: discountValueText,
            paymentMode: paymentMode.rawValue,
            cashAmountText: cashPartText,
            upiAmountText: "",
            dueAmountText: dueAmountText,
            customerName: customerName,
            customerPhone: customerPhone,
            remarks: remarks
        )
    }

    // MARK: Checkout

    func checkout() async {
        guard !cartLines.isEmpty else { return }
        if requiresCustomerPhone && !isValidPhone(customerPhone) {
            checkoutState = .error("Enter a valid 10-digit phone number since money is owed on this bill.")
            return
        }

        checkoutState = .submitting

        let trimmedName = customerName.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedPhone = customerPhone.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedRemarks = remarks.trimmingCharacters(in: .whitespacesAndNewlines)

        let request = BillCreateRequest(
            idempotencyKey: idempotencyKey,
            items: cartLines.map { .init(productId: $0.productId, quantity: $0.quantity, unitPrice: $0.unitPrice.toWire()) },
            discountType: discountType.rawValue,
            discountValue: discountValueMoney.toWire(),
            cashAmount: cashAmount.toWire(),
            upiAmount: upiAmount.toWire(),
            dueAmount: dueAmount.toWire(),
            remarks: trimmedRemarks.isEmpty ? nil : trimmedRemarks,
            newCustomer: trimmedName.isEmpty ? nil : .init(name: trimmedName, phone: trimmedPhone.isEmpty ? nil : trimmedPhone)
        )

        do {
            let body = try APIClient.shared.encode(request)
            let bill: BillOut = try await APIClient.shared.send(Endpoint(path: "bills", method: .post, body: body))
            checkoutState = .success(bill)
        } catch let error as APIError {
            checkoutState = .error(error.userMessage)
        } catch {
            checkoutState = .error(APIError.unknown.userMessage)
        }
    }

    func startNewBill() {
        clearCart()
    }

    private func isValidPhone(_ phone: String) -> Bool {
        let digits = phone.filter(\.isNumber)
        return digits.count == 10
    }
}
