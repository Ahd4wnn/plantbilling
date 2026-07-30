import Foundation
import Observation

@Observable
@MainActor
final class OwnerDashboardViewModel {
    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var overview: OwnerOverview?

    var period: OwnerPeriod = .today {
        didSet { if oldValue != period { Task { await load() } } }
    }
    var customFrom: Date = ShopCalendar.calendar.date(byAdding: .day, value: -6, to: ShopCalendar.today()) ?? ShopCalendar.today() {
        didSet { if period == .custom { Task { await load() } } }
    }
    var customTo: Date = ShopCalendar.today() {
        didSet { if period == .custom { Task { await load() } } }
    }

    func load() async {
        isLoading = true
        loadError = nil
        let (from, to) = period.range(customFrom: customFrom, customTo: customTo)
        do {
            let query = [
                URLQueryItem(name: "date_from", value: ShopCalendar.apiDateString(from)),
                URLQueryItem(name: "date_to", value: ShopCalendar.apiDateString(to)),
            ]
            overview = try await APIClient.shared.send(Endpoint(path: "owner/overview", queryItems: query))
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
