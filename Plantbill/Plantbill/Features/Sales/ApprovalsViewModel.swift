import Foundation
import Observation

/// The manager's queue of salesperson due-collections awaiting approval.
/// Approving applies the cash/UPI split to the bill and closes the due;
/// rejecting leaves the due outstanding.
@Observable
@MainActor
final class ApprovalsViewModel {
    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var items: [PendingSettlement] = []
    private(set) var actingId: UUID?
    var message: String?

    func load() async {
        isLoading = true
        loadError = nil
        do {
            let list: PendingSettlementList = try await APIClient.shared.send(Endpoint(path: "settlements/pending"))
            items = list.items
            isLoading = false
        } catch let error as APIError {
            isLoading = false
            loadError = error.userMessage
        } catch {
            isLoading = false
            loadError = APIError.unknown.userMessage
        }
    }

    func approve(_ item: PendingSettlement) async { await act(item, approve: true) }
    func reject(_ item: PendingSettlement) async { await act(item, approve: false) }

    private func act(_ item: PendingSettlement, approve: Bool) async {
        guard actingId == nil else { return }
        actingId = item.id
        do {
            let path = "settlements/\(item.id)/\(approve ? "approve" : "reject")"
            let _: SettlementActionResult = try await APIClient.shared.send(Endpoint(path: path, method: .post))
            items.removeAll { $0.id == item.id }
            message = approve ? "Approved — \(item.amountMoney.format()) collected." : "Rejected."
            actingId = nil
        } catch let error as APIError {
            actingId = nil
            message = error.userMessage
        } catch {
            actingId = nil
            message = APIError.unknown.userMessage
        }
    }

    func dismissMessage() { message = nil }
}
