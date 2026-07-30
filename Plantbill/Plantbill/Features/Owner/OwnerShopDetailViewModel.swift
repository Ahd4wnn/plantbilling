import Foundation
import Observation

struct NewStaffForm: Equatable {
    var email: String = ""
    var password: String = ""
    var role: String = "salesperson"
    var saving = false
    var error: String?

    var canSave: Bool { email.contains("@") && password.count >= 8 && !saving }
}

/// The bill whose full detail (items + totals) sheet is open.
struct OwnerBillDetailState {
    var loading = true
    var bill: BillDetail?
}

@Observable
@MainActor
final class OwnerShopDetailViewModel {
    let shopId: UUID

    private(set) var report: DetailedReport?
    private(set) var bills: [OwnerBillRow] = []
    private(set) var billsLoading = false
    private(set) var staff: [OwnerStaff] = []
    private(set) var cashInHand: OwnerCashInHand?
    var cashFull = true
    private(set) var labourers: [Labourer] = []

    var labourerDetail: WorkerDetail?
    var billDetail: OwnerBillDetailState?
    var newStaff = NewStaffForm()
    var message: String?

    var period: OwnerPeriod = .today {
        didSet {
            if oldValue != period {
                Task { await loadReport() }
                Task { await loadBills() }
                Task { await loadCashInHand() }
            }
        }
    }
    var customFrom: Date = ShopCalendar.calendar.date(byAdding: .day, value: -6, to: ShopCalendar.today()) ?? ShopCalendar.today() {
        didSet {
            if period == .custom {
                Task { await loadReport() }
                Task { await loadBills() }
            }
        }
    }
    var customTo: Date = ShopCalendar.today() {
        didSet {
            if period == .custom {
                Task { await loadReport() }
                Task { await loadBills() }
            }
        }
    }

    init(shopId: UUID) {
        self.shopId = shopId
    }

    func onAppear() async {
        async let reportTask: Void = loadReport()
        async let billsTask: Void = loadBills()
        async let staffTask: Void = loadStaff()
        async let cashTask: Void = loadCashInHand()
        async let labourTask: Void = loadLabourers()
        _ = await (reportTask, billsTask, staffTask, cashTask, labourTask)
    }

    private var currentRange: (Date, Date) { period.range(customFrom: customFrom, customTo: customTo) }

    func loadReport() async {
        let (from, to) = currentRange
        let query = [
            URLQueryItem(name: "date_from", value: ShopCalendar.apiDateString(from)),
            URLQueryItem(name: "date_to", value: ShopCalendar.apiDateString(to)),
        ]
        report = try? await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/report", queryItems: query))
    }

    func loadBills() async {
        let (from, to) = currentRange
        billsLoading = true
        let query = [
            URLQueryItem(name: "date_from", value: ShopCalendar.apiDateString(from)),
            URLQueryItem(name: "date_to", value: ShopCalendar.apiDateString(to)),
        ]
        if let page: OwnerBillList = try? await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/bills", queryItems: query)) {
            bills = page.items
        }
        billsLoading = false
    }

    func loadStaff() async {
        staff = (try? await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/staff"))) ?? []
    }

    func loadCashInHand() async {
        let (_, to) = currentRange
        let query = [URLQueryItem(name: "date", value: ShopCalendar.apiDateString(to))]
        cashInHand = try? await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/cash-in-hand", queryItems: query))
    }

    func loadLabourers() async {
        labourers = (try? await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/labourers"))) ?? []
    }

    // MARK: Labourer detail

    func openLabourer(_ l: Labourer) {
        labourerDetail = WorkerDetail(labourer: l, loading: true)
        Task {
            do {
                let payments: [LabourPayment] = try await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/labourers/\(l.id)/payments"))
                labourerDetail?.loading = false
                labourerDetail?.payments = payments
            } catch {
                labourerDetail?.loading = false
            }
        }
    }
    func closeLabourer() { labourerDetail = nil }

    // MARK: Bill detail

    func openBill(_ id: UUID) {
        billDetail = OwnerBillDetailState(loading: true)
        Task {
            do {
                let bill: BillDetail = try await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/bills/\(id)"))
                billDetail = OwnerBillDetailState(loading: false, bill: bill)
            } catch let error as APIError {
                billDetail = nil
                message = error.userMessage
            } catch {
                billDetail = nil
                message = APIError.unknown.userMessage
            }
        }
    }
    func closeBill() { billDetail = nil }

    // MARK: Staff

    func addStaff() async {
        guard newStaff.canSave else { return }
        newStaff.saving = true
        do {
            let request = OwnerStaffCreateRequest(email: newStaff.email.trimmingCharacters(in: .whitespacesAndNewlines), password: newStaff.password, role: newStaff.role)
            let body = try APIClient.shared.encode(request)
            let _: OwnerStaff = try await APIClient.shared.send(Endpoint(path: "owner/shops/\(shopId)/staff", method: .post, body: body))
            newStaff = NewStaffForm()
            message = "Staff added."
            await loadStaff()
        } catch let error as APIError {
            newStaff.saving = false
            newStaff.error = error.userMessage
        } catch {
            newStaff.saving = false
            newStaff.error = APIError.unknown.userMessage
        }
    }

    func deleteStaff(_ s: OwnerStaff) async {
        do {
            try await APIClient.shared.sendNoContent(Endpoint(path: "owner/shops/\(shopId)/staff/\(s.id)", method: .delete))
            message = "\(s.email) removed."
            await loadStaff()
        } catch let error as APIError {
            message = error.userMessage
        } catch {
            message = APIError.unknown.userMessage
        }
    }

    func dismissMessage() { message = nil }
}
