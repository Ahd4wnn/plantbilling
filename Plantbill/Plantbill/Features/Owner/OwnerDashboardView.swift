import SwiftUI
import Charts

enum OwnerDestination: Hashable {
    case shopDetail(UUID, String)
}

struct OwnerDashboardView: View {
    let email: String
    let onLogout: () -> Void

    @State private var viewModel = OwnerDashboardViewModel()
    @State private var path = NavigationPath()

    var body: some View {
        NavigationStack(path: $path) {
            content
                .navigationTitle("Your business")
                .navigationBarTitleDisplayMode(.inline)
                .background(PlantbillColor.background)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(action: onLogout) {
                            Image(systemName: "rectangle.portrait.and.arrow.right")
                        }
                    }
                }
                .navigationDestination(for: OwnerDestination.self) { destination in
                    switch destination {
                    case .shopDetail(let id, let name):
                        OwnerShopDetailView(shopId: id, shopName: name)
                    }
                }
                .task { await viewModel.load() }
                .refreshable { await viewModel.load() }
        }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                OwnerPeriodSelector(
                    period: $viewModel.period,
                    customFrom: $viewModel.customFrom,
                    customTo: $viewModel.customTo
                )

                if viewModel.isLoading && viewModel.overview == nil {
                    LoadingStateView(message: "Loading your business…")
                        .frame(height: 240)
                } else if let error = viewModel.loadError, viewModel.overview == nil {
                    ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
                        .frame(height: 240)
                } else if let overview = viewModel.overview {
                    kpiGrid(overview)

                    if overview.shops.contains(where: { $0.totalSalesMoney.isPositive }) {
                        salesByShopCard(overview)
                    }
                    if overview.cashTotalMoney.isPositive || overview.upiTotalMoney.isPositive || overview.dueTotalMoney.isPositive {
                        paymentMixCard(overview)
                    }

                    Text("\(overview.shopCount) shop\(overview.shopCount == 1 ? "" : "s")")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    if overview.shops.isEmpty {
                        Text("No shops yet.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    ForEach(overview.shops) { shop in
                        ShopOverviewCard(shop: shop) {
                            path.append(OwnerDestination.shopDetail(shop.shopId, shop.shopName))
                        }
                    }

                    Text("Top sellers")
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)

                    if overview.staff.isEmpty {
                        Text("No sales in this period yet.")
                            .font(PlantbillTypography.body)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    ForEach(overview.staff) { row in
                        StaffPerformanceRow(row: row)
                    }
                }
            }
            .padding(PlantbillSpacing.lg)
        }
    }

    private func kpiGrid(_ overview: OwnerOverview) -> some View {
        VStack(spacing: PlantbillSpacing.sm) {
            HStack(spacing: PlantbillSpacing.sm) {
                kpiCard("Total sales", overview.totalSalesMoney.format())
                kpiCard("Net income", overview.netSalesMoney.format(), tint: PlantbillColor.green)
            }
            HStack(spacing: PlantbillSpacing.sm) {
                kpiCard("Expenses", overview.totalExpensesMoney.format())
                kpiCard("Bills", "\(overview.billCount)")
            }
        }
    }

    private func kpiCard(_ title: LocalizedStringKey, _ value: String, tint: Color = PlantbillColor.textPrimary) -> some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.textSecondary)
                Text(value)
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(tint)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func salesByShopCard(_ overview: OwnerOverview) -> some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                Text("Sales by shop")
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Chart(overview.shops) { shop in
                    BarMark(
                        x: .value("Sales", (shop.totalSalesMoney.amount as NSDecimalNumber).doubleValue),
                        y: .value("Shop", shop.shopName)
                    )
                    .foregroundStyle(PlantbillColor.green)
                }
                .frame(height: CGFloat(overview.shops.count) * 36 + 20)
            }
        }
    }

    private func paymentMixCard(_ overview: OwnerOverview) -> some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.md) {
                Text("Payment mix")
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                mixBar(title: "Cash", value: overview.cashTotalMoney, total: mixTotal(overview), color: PlantbillColor.green)
                mixBar(title: "UPI", value: overview.upiTotalMoney, total: mixTotal(overview), color: .blue)
                mixBar(title: "Due", value: overview.dueTotalMoney, total: mixTotal(overview), color: PlantbillColor.error)
            }
        }
    }

    private func mixTotal(_ overview: OwnerOverview) -> Decimal {
        max(overview.cashTotalMoney.amount + overview.upiTotalMoney.amount + overview.dueTotalMoney.amount, 0.0001)
    }

    private func mixBar(title: LocalizedStringKey, value: Money, total: Decimal, color: Color) -> some View {
        let valueDouble = (value.amount as NSDecimalNumber).doubleValue
        let totalDouble = (total as NSDecimalNumber).doubleValue
        let fraction = totalDouble > 0 ? min(max(valueDouble / totalDouble, 0), 1) : 0
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(title).font(PlantbillTypography.caption).foregroundStyle(PlantbillColor.textSecondary)
                Spacer()
                Text(value.format()).font(PlantbillTypography.body).foregroundStyle(PlantbillColor.textPrimary)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 4).fill(PlantbillColor.background)
                    RoundedRectangle(cornerRadius: 4).fill(color).frame(width: geo.size.width * fraction)
                }
            }
            .frame(height: 8)
        }
    }
}

struct OwnerPeriodSelector: View {
    @Binding var period: OwnerPeriod
    @Binding var customFrom: Date
    @Binding var customTo: Date

    var body: some View {
        VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
            HStack(spacing: PlantbillSpacing.xs) {
                ForEach(OwnerPeriod.allCases, id: \.self) { p in
                    FilterChip(title: p.title, isSelected: period == p) { period = p }
                        .frame(maxWidth: .infinity)
                }
            }
            if period == .custom {
                DatePicker("From", selection: $customFrom, in: ...customTo, displayedComponents: .date)
                    .datePickerStyle(.compact)
                DatePicker("To", selection: $customTo, in: customFrom...ShopCalendar.today(), displayedComponents: .date)
                    .datePickerStyle(.compact)
            }
        }
    }
}

private struct ShopOverviewCard: View {
    let shop: ShopOverviewRow
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            PlantbillCard {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(shop.shopName)
                            .font(PlantbillTypography.bodyEmphasized)
                            .foregroundStyle(PlantbillColor.textPrimary)
                        Text("\(shop.billCount) bill\(shop.billCount == 1 ? "" : "s") • Net \(shop.netSalesMoney.format()) • Due \(shop.dueTotalMoney.format())")
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                    }
                    Spacer()
                    Text(shop.totalSalesMoney.format())
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.green)
                    Image(systemName: "chevron.right")
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
            }
        }
        .buttonStyle(.plain)
    }
}

private struct StaffPerformanceRow: View {
    let row: StaffPerformance

    var body: some View {
        PlantbillCard {
            HStack {
                Image(systemName: "trophy.fill").foregroundStyle(PlantbillColor.green)
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.email ?? "Unknown")
                        .font(PlantbillTypography.bodyEmphasized)
                        .foregroundStyle(PlantbillColor.textPrimary)
                    Text("\(row.shopName) • \(roleLabel(row.role)) • \(row.billCount) bill\(row.billCount == 1 ? "" : "s")")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
                Spacer()
                Text(row.totalSalesMoney.format())
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
            }
        }
    }
}

func roleLabel(_ role: String) -> String {
    switch role.lowercased() {
    case "manager": return "Manager"
    case "salesperson": return "Salesperson"
    case "owner", "shop_owner": return "Owner"
    default: return role
    }
}
