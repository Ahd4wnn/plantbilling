import SwiftUI
import Observation

@Observable
@MainActor
final class ShopSettingsViewModel {
    private(set) var isLoading = true
    private(set) var loadError: String?
    private(set) var settings: ShopSettings?

    func load() async {
        isLoading = true
        loadError = nil
        do {
            settings = try await APIClient.shared.send(Endpoint(path: "shop"))
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

struct ShopSettingsView: View {
    @State private var viewModel = ShopSettingsViewModel()

    var body: some View {
        content
            .navigationTitle("Shop details")
            .navigationBarTitleDisplayMode(.inline)
            .background(PlantbillColor.background)
            .task { await viewModel.load() }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            LoadingStateView(message: "Loading shop details…")
        } else if let error = viewModel.loadError {
            ErrorStateView(message: LocalizedStringKey(error)) { Task { await viewModel.load() } }
        } else if let settings = viewModel.settings {
            List {
                Section {
                    detailField("Business name", settings.businessName)
                    detailField("Address", settings.businessAddress)
                    detailField("Phone", settings.businessPhone)
                    detailField("Email", settings.businessEmail)
                    detailField("UPI ID", settings.businessUpi)
                } footer: {
                    Text("This is what shows on your printed and shared receipts.")
                        .font(PlantbillTypography.caption)
                        .foregroundStyle(PlantbillColor.textSecondary)
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
        }
    }

    private func detailField(_ label: LocalizedStringKey, _ value: String?) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(PlantbillTypography.caption)
                .foregroundStyle(PlantbillColor.textSecondary)
            Text(value?.isEmpty == false ? value! : "Not set")
                .font(PlantbillTypography.bodyEmphasized)
                .foregroundStyle(PlantbillColor.textPrimary)
        }
        .padding(.vertical, PlantbillSpacing.xs)
    }
}
