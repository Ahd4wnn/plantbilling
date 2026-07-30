import SwiftUI
import Charts

struct ReportView: View {
    let isManager: Bool

    @State private var viewModel: ReportViewModel
    @State private var shareURL: URL?
    @State private var showingShareSheet = false
    @State private var downloadError: String?

    init(isManager: Bool) {
        self.isManager = isManager
        _viewModel = State(initialValue: ReportViewModel(isManager: isManager))
    }

    var body: some View {
        content
            .navigationTitle("Reports")
            .navigationBarTitleDisplayMode(.inline)
            .background(PlantbillColor.background)
            .task { await viewModel.onAppear() }
            .sheet(isPresented: $showingShareSheet) {
                if let shareURL { ActivityView(items: [shareURL]) }
            }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                filtersCard

                if viewModel.isLoading {
                    LoadingStateView(message: "Crunching the numbers…")
                        .frame(height: 200)
                } else if let error = viewModel.loadError {
                    ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
                        .frame(height: 240)
                } else if let report = viewModel.report {
                    kpiGrid(report)
                    if !report.categories.isEmpty { categoryChart(report) }
                    if !report.topProducts.isEmpty { topProductsChart(report) }
                    downloadSection

                    if let downloadError {
                        InlineErrorText(message: LocalizedStringKey(downloadError))
                    }
                }
            }
            .padding(PlantbillSpacing.lg)
        }
    }

    private var filtersCard: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                HStack {
                    DatePicker("From", selection: Binding(get: { viewModel.dateFrom }, set: { viewModel.setRange(from: $0, to: viewModel.dateTo) }), in: ...viewModel.dateTo, displayedComponents: .date)
                        .datePickerStyle(.compact)
                        .font(PlantbillTypography.body)
                }
                HStack {
                    DatePicker("To", selection: Binding(get: { viewModel.dateTo }, set: { viewModel.setRange(from: viewModel.dateFrom, to: $0) }), in: viewModel.dateFrom...ShopCalendar.today(), displayedComponents: .date)
                        .datePickerStyle(.compact)
                        .font(PlantbillTypography.body)
                }
                if isManager {
                    Menu {
                        Button("All staff") { viewModel.selectStaff(nil) }
                        ForEach(viewModel.staff) { sp in
                            Button(sp.email) { viewModel.selectStaff(sp.id) }
                        }
                    } label: {
                        HStack {
                            Image(systemName: "person.2").foregroundStyle(PlantbillColor.green)
                            Text(viewModel.selectedStaffEmail ?? "All staff")
                                .font(PlantbillTypography.body)
                                .foregroundStyle(PlantbillColor.textPrimary)
                            Spacer()
                            Image(systemName: "chevron.right").foregroundStyle(PlantbillColor.textSecondary)
                        }
                        .contentShape(Rectangle())
                    }
                }
            }
        }
    }

    private func kpiGrid(_ report: DetailedReport) -> some View {
        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: PlantbillSpacing.sm) {
            kpiCard("Total sales", report.totalSalesMoney.format())
            kpiCard("Bills", "\(report.billCount)")
            kpiCard("Average bill", report.averageBillValueMoney.format())
            kpiCard("Cash collected", report.cashTotalMoney.format())
            kpiCard("UPI collected", report.upiTotalMoney.format())
            kpiCard("Due outstanding", report.dueTotalMoney.format(), tint: report.dueTotalMoney.isPositive ? PlantbillColor.error : PlantbillColor.textPrimary)
            kpiCard("Total expenses", report.totalExpensesMoney.format())
            kpiCard("Net income", report.netSalesMoney.format(), tint: PlantbillColor.green)
        }
    }

    private func kpiCard(_ title: LocalizedStringKey, _ value: String, tint: Color = PlantbillColor.textPrimary) -> some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.textSecondary)
                Text(value)
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(tint)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private func categoryChart(_ report: DetailedReport) -> some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                Text("Sales by category")
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Chart(report.categories) { row in
                    BarMark(
                        x: .value("Sales", (row.totalSalesMoney.amount as NSDecimalNumber).doubleValue),
                        y: .value("Category", row.category ?? "Uncategorized")
                    )
                    .foregroundStyle(PlantbillColor.green)
                }
                .frame(height: CGFloat(report.categories.count) * 36 + 20)
            }
        }
    }

    private func topProductsChart(_ report: DetailedReport) -> some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.sm) {
                Text("Top products")
                    .font(PlantbillTypography.bodyEmphasized)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Chart(report.topProducts.prefix(10)) { row in
                    BarMark(
                        x: .value("Sales", (row.totalSalesMoney.amount as NSDecimalNumber).doubleValue),
                        y: .value("Product", row.productName)
                    )
                    .foregroundStyle(PlantbillColor.green)
                }
                .frame(height: CGFloat(min(report.topProducts.count, 10)) * 36 + 20)
            }
        }
    }

    private var downloadSection: some View {
        VStack(spacing: PlantbillSpacing.sm) {
            SecondaryButton(title: "Download report (.xlsx)", isLoading: viewModel.isDownloading) {
                downloadError = nil
                Task {
                    guard let data = await viewModel.downloadWorkbook() else {
                        downloadError = "Couldn't download the report."
                        return
                    }
                    let url = FileManager.default.temporaryDirectory.appendingPathComponent("plantbill-report.xlsx")
                    do {
                        try data.write(to: url, options: .atomic)
                        shareURL = url
                        showingShareSheet = true
                    } catch {
                        downloadError = "Couldn't download the report."
                    }
                }
            }
        }
    }
}
