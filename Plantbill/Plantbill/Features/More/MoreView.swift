import SwiftUI

enum MoreDestination: Hashable {
    case shopSettings
    case staff
    case labour
    case borrowings
    case printerSetup
}

struct MoreView: View {
    let user: CurrentUser

    @Environment(AuthSession.self) private var session
    @Environment(LanguageStore.self) private var languageStore
    @State private var showingLanguagePicker = false
    @State private var showingCashSet = false
    @State private var path = NavigationPath()
    @AppStorage("cash_in_hand_cumulative") private var cashInHandCumulative = false

    private var isManager: Bool { user.role == .manager }

    var body: some View {
        NavigationStack(path: $path) {
            List {
                Section {
                    userCard
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())

                Section {
                    Button {
                        showingLanguagePicker = true
                    } label: {
                        settingsRow(icon: "globe", title: "Language", value: Text(languageStore.current.nativeName))
                    }

                    Button {
                        path.append(MoreDestination.shopSettings)
                    } label: {
                        settingsRow(icon: "storefront", title: "Shop details", value: nil)
                    }

                    if isManager {
                        Button {
                            path.append(MoreDestination.staff)
                        } label: {
                            settingsRow(icon: "person.2", title: "Salespeople", value: nil)
                        }
                    }

                    Button {
                        path.append(MoreDestination.labour)
                    } label: {
                        settingsRow(icon: "figure.wave", title: "Labour", value: nil)
                    }

                    Button {
                        path.append(MoreDestination.borrowings)
                    } label: {
                        settingsRow(icon: "banknote", title: "Borrowings", value: nil)
                    }

                    if user.role == .salesperson {
                        Button {
                            path.append(MoreDestination.printerSetup)
                        } label: {
                            settingsRow(icon: "printer", title: "Printer", value: nil)
                        }
                    }
                }

                Section {
                    Toggle(isOn: $cashInHandCumulative) {
                        HStack {
                            Image(systemName: "wallet.pass")
                                .foregroundStyle(PlantbillColor.green)
                                .frame(width: 28)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Running cash in hand")
                                    .font(PlantbillTypography.body)
                                    .fontWeight(.medium)
                                    .foregroundStyle(PlantbillColor.textPrimary)
                                Text("Show the all-time total instead of just today's")
                                    .font(PlantbillTypography.caption)
                                    .foregroundStyle(PlantbillColor.textSecondary)
                            }
                        }
                    }
                    .tint(PlantbillColor.green)

                    if isManager {
                        Button {
                            showingCashSet = true
                        } label: {
                            settingsRow(icon: "arrow.triangle.2.circlepath", title: "Set cash in hand", value: nil)
                        }
                    }
                }

                Section {
                    Button {
                        openSupportChat()
                    } label: {
                        settingsRow(icon: "questionmark.circle", title: "Support", value: Text("+91 79754 02266"))
                    }
                }

                Section {
                    Button(role: .destructive) {
                        session.logout()
                    } label: {
                        Text("Log out")
                            .font(PlantbillTypography.bodyEmphasized)
                            .frame(maxWidth: .infinity)
                            .contentShape(Rectangle())
                    }
                }

                Section {
                    HStack {
                        Spacer()
                        Text(versionString)
                            .font(PlantbillTypography.caption)
                            .foregroundStyle(PlantbillColor.textSecondary)
                        Spacer()
                    }
                }
                .listRowBackground(Color.clear)
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .background(PlantbillColor.background)
            .navigationTitle("More")
            .navigationBarTitleDisplayMode(.inline)
            .notificationBell()
            .navigationDestination(for: MoreDestination.self) { destination in
                switch destination {
                case .shopSettings: ShopSettingsView()
                case .staff: StaffManagementView()
                case .labour: LabourView(isManager: isManager)
                case .borrowings: BorrowingsView()
                case .printerSetup: PrinterSetupView(shopName: BusinessProfile.shared.businessName ?? BusinessProfile.shared.shopName ?? user.shopName ?? "Receipt")
                }
            }
            .sheet(isPresented: $showingLanguagePicker) {
                LanguagePickerView()
            }
            .sheet(isPresented: $showingCashSet) {
                SetCashInHandSheet()
            }
        }
    }

    private func openSupportChat() {
        guard let url = URL(string: "https://wa.me/917975402266") else { return }
        UIApplication.shared.open(url)
    }

    private var userCard: some View {
        PlantbillCard {
            VStack(alignment: .leading, spacing: PlantbillSpacing.xs) {
                if let shopName = user.shopName, !shopName.isEmpty {
                    Text(shopName)
                        .font(PlantbillTypography.headline)
                        .foregroundStyle(PlantbillColor.textPrimary)
                }
                Text(user.email)
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
                Text(user.role.displayName)
                    .font(PlantbillTypography.caption)
                    .foregroundStyle(PlantbillColor.green)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, PlantbillSpacing.md)
        .padding(.top, PlantbillSpacing.sm)
    }

    @ViewBuilder
    private func settingsRow(icon: String, title: LocalizedStringKey, value: Text?) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundStyle(PlantbillColor.green)
                .frame(width: 28)
            Text(title)
                .font(PlantbillTypography.body)
                .fontWeight(.medium)
                .foregroundStyle(PlantbillColor.textPrimary)
            Spacer()
            if let value {
                value
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)
            }
            Image(systemName: "chevron.right")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(PlantbillColor.textSecondary)
        }
        .frame(minHeight: PlantbillSpacing.minTouchTarget)
        .contentShape(Rectangle())
    }

    private var versionString: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "Plantbill v\(version) (\(build))"
    }
}

private struct SetCashInHandSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var amount = ""
    @State private var saving = false
    @State private var error: String?
    @State private var successMessage: String?

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: PlantbillSpacing.lg) {
                Text("Set cash in hand")
                    .font(PlantbillTypography.headline)
                    .foregroundStyle(PlantbillColor.textPrimary)
                Text("Resets the running cash-in-hand total to exactly this amount, effective now.")
                    .font(PlantbillTypography.body)
                    .foregroundStyle(PlantbillColor.textSecondary)

                PlantbillTextField(label: "Cash in hand (₹)", text: $amount, placeholder: "0", keyboardType: .numberPad, selectAllOnFocus: true)

                if let error {
                    InlineErrorText(message: LocalizedStringKey(error))
                }
                if let successMessage {
                    Text(successMessage)
                        .font(PlantbillTypography.body)
                        .foregroundStyle(PlantbillColor.green)
                }

                PrimaryButton(title: "Save", isLoading: saving, isDisabled: amount.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || saving, action: save)

                Spacer()
            }
            .padding(PlantbillSpacing.lg)
            .background(PlantbillColor.background)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }

    private func save() {
        error = nil
        successMessage = nil
        saving = true
        Task {
            do {
                let request = CashInHandSetRequest(amount: Money.parse(amount).toWire())
                let body = try APIClient.shared.encode(request)
                let result: CashInHandOut = try await APIClient.shared.send(Endpoint(path: "shop/cash-in-hand", method: .post, body: body))
                saving = false
                successMessage = "Cash in hand set to \(result.cashInHandRunningMoney.format())."
            } catch let apiError as APIError {
                saving = false
                error = apiError.userMessage
            } catch {
                saving = false
                self.error = APIError.unknown.userMessage
            }
        }
    }
}

#Preview {
    MoreView(user: CurrentUser(id: UUID(), email: "owner@example.com", role: .manager, shopId: UUID(), isActive: true, shopName: "Green Leaf Nursery", businessName: nil, businessUpi: nil))
        .environment(AuthSession())
        .environment(LanguageStore())
        .environment(NotificationsStore())
}
