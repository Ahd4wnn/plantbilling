import Foundation
import Observation

@Observable
@MainActor
final class BillDetailViewModel {
    enum State {
        case loading
        case loaded(BillDetail)
        case error(String)
    }

    let billId: UUID
    private(set) var state: State = .loading
    private(set) var isDeleting = false
    var deleted = false
    var deleteError: String?

    init(billId: UUID) {
        self.billId = billId
    }

    func load() async {
        state = .loading
        do {
            let detail: BillDetail = try await APIClient.shared.send(Endpoint(path: "bills/\(billId)"))
            state = .loaded(detail)
        } catch let error as APIError {
            state = .error(error.userMessage)
        } catch {
            state = .error(APIError.unknown.userMessage)
        }
    }

    func delete() async {
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await APIClient.shared.sendNoContent(Endpoint(path: "bills/\(billId)", method: .delete))
            deleted = true
        } catch let error as APIError {
            deleteError = error.userMessage
        } catch {
            deleteError = APIError.unknown.userMessage
        }
    }
}
