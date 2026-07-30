import Foundation
import Observation

@Observable
@MainActor
final class ReportViewModel {
    let isManager: Bool

    var dateFrom: Date = ShopCalendar.today()
    var dateTo: Date = ShopCalendar.today()
    private(set) var staff: [Salesperson] = []
    var selectedStaffId: UUID?

    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var report: DetailedReport?
    private(set) var isDownloading = false

    init(isManager: Bool) {
        self.isManager = isManager
    }

    var selectedStaffEmail: String? { staff.first { $0.id == selectedStaffId }?.email }

    func onAppear() async {
        if isManager && staff.isEmpty {
            staff = (try? await APIClient.shared.send(Endpoint(path: "shop/users"))) ?? []
        }
        await load()
    }

    func setRange(from: Date, to: Date) {
        dateFrom = from
        dateTo = to
        Task { await load() }
    }

    func selectStaff(_ id: UUID?) {
        selectedStaffId = id
        Task { await load() }
    }

    private var queryItems: [URLQueryItem] {
        var items = [
            URLQueryItem(name: "date_from", value: ShopCalendar.apiDateString(dateFrom)),
            URLQueryItem(name: "date_to", value: ShopCalendar.apiDateString(dateTo)),
        ]
        if let selectedStaffId { items.append(URLQueryItem(name: "created_by", value: selectedStaffId.uuidString)) }
        return items
    }

    func load() async {
        isLoading = true
        loadError = nil
        do {
            report = try await APIClient.shared.send(Endpoint(path: "bills/summary/report", queryItems: queryItems))
            isLoading = false
        } catch let error as APIError {
            isLoading = false
            loadError = error.userMessage
        } catch {
            isLoading = false
            loadError = APIError.unknown.userMessage
        }
    }

    func downloadWorkbook() async -> Data? {
        isDownloading = true
        defer { isDownloading = false }
        return try? await APIClient.shared.download(Endpoint(path: "bills/summary/report/download", queryItems: queryItems))
    }
}
