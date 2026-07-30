import Foundation
import Observation

@Observable
@MainActor
final class CustomerDetailViewModel {
    let customerId: UUID

    private(set) var isLoading = true
    private(set) var loadError: String?
    /// The customer's name, taken from the first bill that recorded one —
    /// nil when no bill has it (UI shows a fallback).
    private(set) var name: String?
    private(set) var bills: [BillListEntry] = []

    init(customerId: UUID) {
        self.customerId = customerId
    }

    var totalSpent: Money { bills.reduce(Money.zero) { $0 + $1.totalMoney } }
    var creditBillCount: Int { bills.filter { $0.paymentMethod == .due }.count }

    func load() async {
        isLoading = true
        loadError = nil
        do {
            let query = [
                URLQueryItem(name: "customer_id", value: customerId.uuidString),
                URLQueryItem(name: "limit", value: "100"),
            ]
            let page: BillListPage = try await APIClient.shared.send(Endpoint(path: "bills", queryItems: query))
            bills = page.items
            name = page.items.first { $0.customerName?.isEmpty == false }?.customerName
            isLoading = false
        } catch let error as APIError {
            isLoading = false
            loadError = error.userMessage
        } catch {
            isLoading = false
            loadError = APIError.unknown.userMessage
        }
    }
}
